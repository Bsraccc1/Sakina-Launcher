package app.sakinalauncher.helper

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import app.sakinalauncher.R
import app.sakinalauncher.data.BoundWidget
import app.sakinalauncher.data.ProductiveWidgetStore
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Lifecycle wrapper around [AppWidgetHost] for the Productive Widgets tab.
 *
 * Layout rules:
 * - Default size follows the provider's minWidth × minHeight (pixels).
 * - Wide providers (search bars) expand to full panel width, height keeps aspect.
 * - Compact/square providers keep native size so content is not clipped/stretched.
 * - Side-by-side packing when sizes fit; otherwise stack.
 * - Resize updates layout **live while dragging**.
 * - Remove / resize chrome only after long-press.
 */
class ProductiveWidgetHostHelper(
    private val context: Context,
    private val store: ProductiveWidgetStore,
) {
    private val appContext = context.applicationContext
    private val appWidgetManager = AppWidgetManager.getInstance(appContext)
    val host = AppWidgetHost(appContext, HOST_ID)

    private var listening = false
    private var activeEditFrame: ResizableWidgetFrame? = null
    private var flowLayout: WidgetFlowLayout? = null
    private var lastContainerWidthPx: Int = 0
    private var lastDensity: Float = 1f
    private var lastMaxWidthDp: Int = 200

    fun startListening() {
        if (listening) return
        runCatching {
            host.startListening()
            listening = true
        }
    }

    fun stopListening() {
        if (!listening) return
        runCatching { host.stopListening() }
        listening = false
        clearEditMode()
    }

    fun destroy() {
        stopListening()
        flowLayout = null
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(appWidgetId: Int) {
        runCatching { host.deleteAppWidgetId(appWidgetId) }
        store.removeWidget(appWidgetId)
    }

    fun installedProviders(): List<AppWidgetProviderInfo> {
        return appWidgetManager.installedProviders.orEmpty()
            .sortedBy { it.loadLabel(appContext.packageManager)?.toString().orEmpty() }
    }

    fun createPickIntent(appWidgetId: Int): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, arrayListOf<AppWidgetProviderInfo>())
        }
    }

    fun createBindIntent(appWidgetId: Int, provider: ComponentName): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }
    }

    fun tryBind(appWidgetId: Int, provider: ComponentName): Boolean {
        return runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
        }.getOrDefault(false)
    }

    fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo? =
        appWidgetManager.getAppWidgetInfo(appWidgetId)

    fun needsConfigure(info: AppWidgetProviderInfo?): Boolean {
        return info?.configure != null
    }

    fun createConfigureIntent(appWidgetId: Int, info: AppWidgetProviderInfo): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = info.configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    fun persistBound(
        appWidgetId: Int,
        provider: ComponentName? = null,
        heightDp: Int = 0,
        widthDp: Int = 0,
    ): Boolean {
        val info = providerInfo(appWidgetId)
        val flattened = info?.provider?.flattenToString()
            ?: provider?.flattenToString()
            ?: return false
        // widthDp 0 = full panel width (stacked by default).
        store.addWidget(BoundWidget(appWidgetId, flattened, heightDp, widthDp))
        return true
    }

    fun reconcileStoreFromBoundProviders() {
        val known = store.getWidgets().map { it.appWidgetId }.toMutableSet()
        val additions = mutableListOf<BoundWidget>()
        for (info in installedProviders()) {
            val provider = info.provider ?: continue
            val ids = runCatching { appWidgetManager.getAppWidgetIds(provider) }.getOrNull() ?: continue
            for (id in ids) {
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID || id in known) continue
                val boundInfo = appWidgetManager.getAppWidgetInfo(id) ?: continue
                val ownsId = runCatching {
                    val view = host.createView(appContext, id, boundInfo)
                    (view.parent as? ViewGroup)?.removeView(view)
                    true
                }.getOrDefault(false)
                if (!ownsId) continue
                val flat = boundInfo.provider?.flattenToString() ?: provider.flattenToString()
                additions.add(BoundWidget(id, flat, 0, 0))
                known.add(id)
            }
        }
        if (additions.isNotEmpty()) {
            store.setWidgets(store.getWidgets() + additions)
        }
    }

    fun clearEditMode() {
        activeEditFrame?.setEditMode(false)
        activeEditFrame = null
    }

    /**
     * Rebuild host views into a flow that packs left→right and wraps when needed.
     * Full-width cards always take their own row; narrower ones share a row when they fit.
     */
    fun inflateInto(container: ViewGroup, onRemove: (Int) -> Unit) {
        startListening()
        clearEditMode()
        container.removeAllViews()

        val density = appContext.resources.displayMetrics.density
        val gapPx = appContext.resources.getDimensionPixelSize(R.dimen.productive_widget_gap)
        val containerWidthPx = when {
            container.width > 0 -> container.width
            container.measuredWidth > 0 -> container.measuredWidth
            else -> appContext.resources.displayMetrics.widthPixels -
                (40 * density).roundToInt()
        }.coerceAtLeast((200 * density).roundToInt())
        val maxWidthDp = (containerWidthPx / density).roundToInt().coerceAtLeast(200)

        lastContainerWidthPx = containerWidthPx
        lastDensity = density
        lastMaxWidthDp = maxWidthDp

        val flow = WidgetFlowLayout(appContext).apply {
            this.gapPx = gapPx
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        flowLayout = flow

        val kept = mutableListOf<BoundWidget>()
        for (bound in store.getWidgets()) {
            val info = appWidgetManager.getAppWidgetInfo(bound.appWidgetId)
            if (info == null) {
                val pkg = bound.providerComponent()?.packageName
                val stillInstalled = pkg != null && runCatching {
                    appContext.packageManager.getApplicationInfo(pkg, 0)
                    true
                }.getOrDefault(false)
                if (!stillInstalled) {
                    runCatching { host.deleteAppWidgetId(bound.appWidgetId) }
                } else {
                    kept.add(bound)
                }
                continue
            }
            kept.add(bound)

            val (widthPx, heightPx) = resolveHostSizePx(
                info = info,
                bound = bound,
                containerWidthPx = containerWidthPx,
                density = density,
            )
            val widthDp = (widthPx / density).roundToInt().coerceAtLeast(1)
            val heightDp = (heightPx / density).roundToInt().coerceAtLeast(1)

            val card = createCard(
                bound = bound,
                info = info,
                widthPx = widthPx,
                heightPx = heightPx,
                widthDp = widthDp,
                heightDp = heightDp,
                density = density,
                containerWidthPx = containerWidthPx,
                maxWidthDp = maxWidthDp,
                onRemove = onRemove,
            )
            flow.addView(card, WidgetFlowLayout.LayoutParams(widthPx, heightPx))
        }
        container.addView(flow)

        if (kept.size != store.getWidgets().size) {
            store.setWidgets(kept)
        }
    }

    private fun createCard(
        bound: BoundWidget,
        info: AppWidgetProviderInfo,
        widthPx: Int,
        heightPx: Int,
        widthDp: Int,
        heightDp: Int,
        density: Float,
        containerWidthPx: Int,
        maxWidthDp: Int,
        onRemove: (Int) -> Unit,
    ): ResizableWidgetFrame {
        val hostView = host.createView(appContext, bound.appWidgetId, info)
        hostView.setAppWidget(bound.appWidgetId, info)
        applyWidgetOptions(hostView, bound.appWidgetId, widthDp, heightDp, maxWidthDp)

        val pad = (8 * density).roundToInt()
        // Larger chrome so remove/resize are easy once edit mode is on.
        val removeSize = (40 * density).roundToInt()
        val handleSize = (36 * density).roundToInt()

        val wrap = ResizableWidgetFrame(appContext).apply {
            tag = bound.appWidgetId
            clipChildren = true
            clipToPadding = true
            this.hostView = hostView
            minWidthPx = providerMinSizePx(info).first.coerceAtLeast((MIN_WIDTH_DP * density).roundToInt())
                .coerceAtMost(containerWidthPx)
            maxWidthPx = containerWidthPx
            minHeightPx = providerMinSizePx(info).second.coerceAtLeast((MIN_HEIGHT_DP * density).roundToInt())
                .coerceAtMost((MAX_HEIGHT_DP * density).roundToInt())
            maxHeightPx = (MAX_HEIGHT_DP * density).roundToInt()
            onEnterEdit = {
                if (activeEditFrame !== this) {
                    activeEditFrame?.setEditMode(false)
                    activeEditFrame = this
                }
                setEditMode(true)
            }
            onExitEdit = {
                if (activeEditFrame === this) activeEditFrame = null
            }
            onSizeLive = { wPx, hPx ->
                val wDp = (wPx / density).roundToInt().coerceIn(MIN_WIDTH_DP, maxWidthDp)
                val hDp = (hPx / density).roundToInt().coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
                applyWidgetOptions(hostView, bound.appWidgetId, wDp, hDp, maxWidthDp)
                (parent as? View)?.requestLayout()
            }
            onSizeCommitted = { wPx, hPx ->
                val wDp = (wPx / density).roundToInt().coerceIn(MIN_WIDTH_DP, maxWidthDp)
                val hDp = (hPx / density).roundToInt().coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
                store.updateSize(bound.appWidgetId, wDp, hDp)
                applyWidgetOptions(hostView, bound.appWidgetId, wDp, hDp, maxWidthDp)
                (parent as? View)?.requestLayout()
            }
            addView(
                hostView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        val removeBtn = ImageButton(appContext).apply {
            setImageResource(R.drawable.ic_close)
            contentDescription = appContext.getString(R.string.remove_widget)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(220, 20, 20, 24))
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(pad, pad, pad, pad)
            elevation = 12 * density
            visibility = View.GONE
            // Keep clicks on the button; never fall through to the host.
            isClickable = true
            isFocusable = true
            setOnClickListener {
                clearEditMode()
                onRemove(bound.appWidgetId)
            }
        }
        wrap.removeButton = removeBtn
        wrap.addView(
            removeBtn,
            FrameLayout.LayoutParams(removeSize, removeSize).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = (4 * density).roundToInt()
                marginEnd = (4 * density).roundToInt()
            },
        )

        val resizeHandle = ResizeHandleView(appContext).apply {
            contentDescription = appContext.getString(R.string.resize_widget)
            elevation = 12 * density
            visibility = View.GONE
            isClickable = true
        }
        wrap.attachResizeHandle(resizeHandle)
        wrap.addView(
            resizeHandle,
            FrameLayout.LayoutParams(handleSize, handleSize).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                bottomMargin = (4 * density).roundToInt()
                marginEnd = (4 * density).roundToInt()
            },
        )

        return wrap
    }

    private fun applyWidgetOptions(
        hostView: AppWidgetHostView,
        appWidgetId: Int,
        widthDp: Int,
        heightDp: Int,
        maxWidthDp: Int,
    ) {
        // OPTION_* values must be in **dp**, not px.
        val w = widthDp.coerceIn(1, maxWidthDp)
        val h = heightDp.coerceIn(1, MAX_HEIGHT_DP)
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(SizeF(w.toFloat(), h.toFloat())),
                )
            }
        }
        runCatching { appWidgetManager.updateAppWidgetOptions(appWidgetId, options) }
        @Suppress("DEPRECATION")
        runCatching {
            hostView.updateAppWidgetSize(options, w, h, w, h)
        }
    }

    /**
     * [AppWidgetProviderInfo.minWidth]/[minHeight] are **pixels** (loaded via
     * getDimensionPixelSize). Dumpsys may print raw complex TypedValues; handle both.
     */
    private fun providerMinSizePx(info: AppWidgetProviderInfo): Pair<Int, Int> {
        val metrics = appContext.resources.displayMetrics
        fun toPx(raw: Int): Int {
            if (raw <= 0) return 0
            // Unconverted complex dimension (dumpsys-style, typically > 10_000).
            if (raw > 10_000) {
                return runCatching {
                    TypedValue.complexToDimensionPixelSize(raw, metrics)
                }.getOrDefault(0)
            }
            return raw
        }
        var w = toPx(info.minWidth)
        var h = toPx(info.minHeight)
        // API 31+: target cells ≈ 70dp each when min is missing/zero.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (w <= 0 && info.targetCellWidth > 0) {
                w = (info.targetCellWidth * 70 * metrics.density).roundToInt()
            }
            if (h <= 0 && info.targetCellHeight > 0) {
                h = (info.targetCellHeight * 70 * metrics.density).roundToInt()
            }
        }
        return w to h
    }

    /**
     * Host frame size in **pixels**.
     * - User override: stored widthDp/heightDp.
     * - Default: provider min size; wide widgets fill panel width (aspect kept).
     */
    private fun resolveHostSizePx(
        info: AppWidgetProviderInfo,
        bound: BoundWidget,
        containerWidthPx: Int,
        density: Float,
    ): Pair<Int, Int> {
        val maxHpx = (MAX_HEIGHT_DP * density).roundToInt()
        val minWpx = (MIN_WIDTH_DP * density).roundToInt()
        val minHpx = (MIN_HEIGHT_DP * density).roundToInt()

        // Explicit user resize wins.
        if (bound.widthDp > 0 && bound.heightDp > 0) {
            val w = (bound.widthDp * density).roundToInt()
                .coerceIn(minWpx, containerWidthPx)
            val h = (bound.heightDp * density).roundToInt()
                .coerceIn(minHpx, maxHpx)
            return w to h
        }

        val (provW, provH) = providerMinSizePx(info)
        val pW = provW.coerceAtLeast(minWpx)
        val pH = provH.coerceAtLeast(minHpx)

        // No provider data — sensible fallback: full width × default height.
        if (provW <= 0 || provH <= 0) {
            return containerWidthPx to (DEFAULT_HEIGHT_DP * density).roundToInt()
        }

        val aspect = pH.toFloat() / pW.toFloat()
        val isWide = (pW.toFloat() / pH >= 2.0f) || (pW >= containerWidthPx * 0.55f)

        return if (isWide) {
            // Search bars / horizontal widgets: fill width, scale height by aspect.
            val h = (containerWidthPx * aspect).roundToInt().coerceIn(minHpx, maxHpx)
            containerWidthPx to h
        } else {
            // Compact / square (e.g. Chrome Dino 110×110dp): keep provider size.
            // Do NOT stretch to full width — that clips or letterboxes content.
            val w = pW.coerceAtMost(containerWidthPx)
            val h = pH.coerceAtMost(maxHpx)
            w to h
        }
    }

    /**
     * Flow: pack children left→right; if the next card does not fit the remaining
     * width of the row, wrap to a new line. Sizes come from each child's LayoutParams.
     */
    private class WidgetFlowLayout(context: Context) : ViewGroup(context) {
        var gapPx: Int = 0

        class LayoutParams(width: Int, height: Int) : ViewGroup.LayoutParams(width, height)

        override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams =
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, DEFAULT_HEIGHT_DP)

        override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams {
            return LayoutParams(
                p?.width ?: ViewGroup.LayoutParams.MATCH_PARENT,
                p?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(0)
            var x = 0
            var y = 0
            var rowH = 0
            val count = childCount
            var any = false
            for (i in 0 until count) {
                val child = getChildAt(i)
                if (child.visibility == View.GONE) continue
                any = true
                val lp = child.layoutParams
                val cw = when {
                    lp.width > 0 -> lp.width.coerceAtMost(width)
                    else -> width
                }.coerceAtLeast(1)
                val ch = when {
                    lp.height > 0 -> lp.height
                    else -> (DEFAULT_HEIGHT_DP * resources.displayMetrics.density).roundToInt()
                }.coerceAtLeast(1)
                child.measure(
                    MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY),
                )
                if (x > 0 && x + gapPx + cw > width) {
                    x = 0
                    y += rowH + gapPx
                    rowH = 0
                }
                x += cw + gapPx
                rowH = max(rowH, ch)
            }
            val height = if (!any) 0 else y + rowH
            setMeasuredDimension(
                resolveSize(width, widthMeasureSpec),
                resolveSize(height, heightMeasureSpec),
            )
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val width = r - l
            var x = 0
            var y = 0
            var rowH = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility == View.GONE) continue
                val cw = child.measuredWidth
                val ch = child.measuredHeight
                if (x > 0 && x + gapPx + cw > width) {
                    x = 0
                    y += rowH + gapPx
                    rowH = 0
                }
                child.layout(x, y, x + cw, y + ch)
                x += cw + gapPx
                rowH = max(rowH, ch)
            }
        }

    }

    /**
     * Long-press shows remove + resize **without opening the widget**.
     * While editing, the AppWidgetHostView is blocked so taps go only to chrome.
     */
    private class ResizableWidgetFrame(context: Context) : FrameLayout(context) {
        var onEnterEdit: (() -> Unit)? = null
        var onExitEdit: (() -> Unit)? = null
        var onSizeLive: ((widthPx: Int, heightPx: Int) -> Unit)? = null
        var onSizeCommitted: ((widthPx: Int, heightPx: Int) -> Unit)? = null
        var minWidthPx: Int = 200
        var maxWidthPx: Int = 1000
        var minHeightPx: Int = 200
        var maxHeightPx: Int = 2000
        var removeButton: View? = null
        var hostView: View? = null

        private var resizeHandle: View? = null
        private var editing = false
        private var resizing = false
        /** After long-press, swallow the rest of the gesture so the host never clicks. */
        private var blockHostUntilUp = false
        private var startRawX = 0f
        private var startRawY = 0f
        private var startW = 0
        private var startH = 0

        private val detector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onLongPress(e: MotionEvent) {
                    if (resizing) return
                    blockHostUntilUp = true
                    // Cancel any in-progress press inside the widget (prevents open-on-release).
                    cancelHostTouch()
                    onEnterEdit?.invoke()
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                }
            },
        ).apply {
            setIsLongpressEnabled(true)
        }

        fun setEditMode(enabled: Boolean) {
            editing = enabled
            removeButton?.visibility = if (enabled) View.VISIBLE else View.GONE
            resizeHandle?.visibility = if (enabled) View.VISIBLE else View.GONE
            // Hard-block widget interaction while chrome is visible.
            hostView?.let { host ->
                host.isEnabled = !enabled
                host.isClickable = !enabled
                host.isLongClickable = false
            }
            if (!enabled) {
                blockHostUntilUp = false
                onExitEdit?.invoke()
            }
        }

        fun attachResizeHandle(handle: View) {
            resizeHandle = handle
            handle.setOnTouchListener { _, event -> handleResizeTouch(event) }
        }

        private fun cancelHostTouch() {
            val host = hostView ?: return
            host.isPressed = false
            host.cancelLongPress()
            val now = SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            runCatching { host.dispatchTouchEvent(cancel) }
            cancel.recycle()
        }

        private fun isOnEditChrome(ev: MotionEvent): Boolean {
            val x = ev.x
            val y = ev.y
            return listOfNotNull(removeButton, resizeHandle).any { chrome ->
                if (chrome.visibility != View.VISIBLE) return@any false
                x >= chrome.left && x < chrome.right && y >= chrome.top && y < chrome.bottom
            }
        }

        private fun applyLiveSize(newW: Int, newH: Int) {
            val lp = layoutParams ?: return
            if (lp.width != newW || lp.height != newH) {
                lp.width = newW
                lp.height = newH
                layoutParams = lp
                (parent as? View)?.requestLayout()
                onSizeLive?.invoke(newW, newH)
            }
        }

        private fun handleResizeTouch(event: MotionEvent): Boolean {
            if (!editing) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resizing = true
                    blockHostUntilUp = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    (parent?.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startW = width.takeIf { it > 0 } ?: (layoutParams?.width ?: minWidthPx)
                    startH = height.takeIf { it > 0 } ?: (layoutParams?.height ?: minHeightPx)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!resizing) return false
                    val dw = (event.rawX - startRawX).roundToInt()
                    val dh = (event.rawY - startRawY).roundToInt()
                    val newW = (startW + dw).coerceIn(minWidthPx, maxWidthPx)
                    val newH = (startH + dh).coerceIn(minHeightPx, maxHeightPx)
                    applyLiveSize(newW, newH)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (resizing) {
                        resizing = false
                        parent?.requestDisallowInterceptTouchEvent(false)
                        (parent?.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(false)
                        val lp = layoutParams
                        if (lp != null && lp.width > 0 && lp.height > 0) {
                            onSizeCommitted?.invoke(lp.width, lp.height)
                        }
                    }
                    return true
                }
            }
            return false
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            val action = ev.actionMasked

            // Edit chrome (✕ / resize) always receives touches first when visible.
            if (editing && isOnEditChrome(ev)) {
                detector.onTouchEvent(ev)
                return super.dispatchTouchEvent(ev)
            }

            // Feed long-press detector without letting host open on hold.
            if (!resizing) {
                detector.onTouchEvent(ev)
            }

            // After long-press (or while editing the body): never deliver to AppWidgetHostView.
            if (editing || blockHostUntilUp) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    blockHostUntilUp = false
                    // Tap empty body while editing → exit edit mode (optional, clear chrome).
                    if (editing && action == MotionEvent.ACTION_UP && !resizing) {
                        // Keep edit mode so user can still hit ✕ / resize; only exit if they
                        // tap outside this card (handled by next long-press on another card).
                    }
                }
                // Consume — host does not see this gesture.
                return true
            }

            // Normal short taps: allow widget interaction.
            return super.dispatchTouchEvent(ev)
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            // Once we are editing or blocked the host, take the stream (except chrome).
            if ((editing || blockHostUntilUp) && !isOnEditChrome(ev)) {
                return true
            }
            return super.onInterceptTouchEvent(ev)
        }
    }

    private class ResizeHandleView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            strokeWidth = 3f * resources.displayMetrics.density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        init {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(180, 20, 20, 24))
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val d = resources.displayMetrics.density
            val pad = 8f * d
            canvas.drawLine(pad, height - pad, width - pad, pad, paint)
            canvas.drawLine(pad + 5 * d, height - pad, width - pad, pad + 5 * d, paint)
        }
    }

    companion object {
        const val HOST_ID = 1024
        const val MIN_WIDTH_DP = 120
        const val MIN_HEIGHT_DP = 80
        const val MAX_HEIGHT_DP = 600
        const val DEFAULT_HEIGHT_DP = 160
    }
}
