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
import android.widget.TextView
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
    private var observedContainer: ViewGroup? = null
    private var containerLayoutListener: View.OnLayoutChangeListener? = null
    private var lastContainerWidthPx: Int = 0
    private var storeReconciled = false

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
        containerLayoutListener?.let { listener ->
            observedContainer?.removeOnLayoutChangeListener(listener)
        }
        containerLayoutListener = null
        observedContainer = null
        flowLayout = null
        lastContainerWidthPx = 0
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
        if (storeReconciled) return
        storeReconciled = true
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
        val containerWidthPx = container.width.takeIf { it > 0 }
            ?: container.measuredWidth.takeIf { it > 0 }
            ?: return
        val maxWidthDp = (containerWidthPx / density).roundToInt().coerceAtLeast(1)

        observeContainerWidth(container, onRemove)
        lastContainerWidthPx = containerWidthPx

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

    private fun observeContainerWidth(container: ViewGroup, onRemove: (Int) -> Unit) {
        if (observedContainer === container && containerLayoutListener != null) return
        containerLayoutListener?.let { observedContainer?.removeOnLayoutChangeListener(it) }
        val listener = View.OnLayoutChangeListener { view, left, _, right, _, oldLeft, _, oldRight, _ ->
            val width = right - left
            val oldWidth = oldRight - oldLeft
            if (width > 0 && oldWidth > 0 && width != oldWidth && width != lastContainerWidthPx) {
                view.post {
                    if (view.isAttachedToWindow && view.width == width) {
                        inflateInto(container, onRemove)
                    }
                }
            }
        }
        observedContainer = container
        containerLayoutListener = listener
        container.addOnLayoutChangeListener(listener)
    }

    private fun moveCardBy(frame: ResizableWidgetFrame, offset: Int) {
        val layout = flowLayout ?: return
        val from = layout.indexOfChild(frame)
        val target = (from + offset).coerceIn(0, layout.childCount - 1)
        if (from < 0 || target == from) return
        layout.removeViewAt(from)
        layout.addView(frame, target)
        val current = store.getWidgets().associateBy { it.appWidgetId }
        store.setWidgets((0 until layout.childCount).mapNotNull {
            (layout.getChildAt(it).tag as? Int)?.let(current::get)
        })
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
        applyWidgetOptions(bound.appWidgetId, widthDp, heightDp, maxWidthDp)

        val pad = (7 * density).roundToInt()
        val removeSize = (32 * density).roundToInt()
        val handleWidth = (52 * density).roundToInt()
        val handleHeight = (24 * density).roundToInt()
        val chromeColor = themedColor(R.attr.primaryColorInverseTrans80, Color.argb(190, 20, 20, 24))

        val wrap = ResizableWidgetFrame(appContext).apply {
            tag = bound.appWidgetId
            clipChildren = true
            clipToPadding = true
            this.hostView = hostView
            minWidthPx = providerResizeMinSizePx(info).first.coerceAtLeast((MIN_WIDTH_DP * density).roundToInt())
                .coerceAtMost(containerWidthPx)
            maxWidthPx = containerWidthPx
            minHeightPx = providerResizeMinSizePx(info).second.coerceAtLeast((MIN_HEIGHT_DP * density).roundToInt())
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
            // Keep drag feedback local. Provider/Binder updates are sent once on commit.
            onSizeLive = null
            onSizeCommitted = { wPx, hPx ->
                val wDp = maxWidthDp
                val hDp = (hPx / density).roundToInt().coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
                store.updateSize(bound.appWidgetId, wDp, hDp)
                applyWidgetOptions(bound.appWidgetId, wDp, hDp, maxWidthDp)
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
                setColor(chromeColor)
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(pad, pad, pad, pad)
            elevation = 6 * density
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
        wrap.addView(removeBtn, FrameLayout.LayoutParams(removeSize, removeSize).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = (6 * density).roundToInt()
            marginEnd = (6 * density).roundToInt()
        })

        fun moveButton(label: String, description: String, offset: Int): TextView = TextView(appContext).apply {
            text = label
            contentDescription = description
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(themedColor(R.attr.primaryColor, Color.WHITE))
            background = GradientDrawable().apply {
                cornerRadius = 10 * density
                setColor(chromeColor)
            }
            elevation = 4 * density
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setOnClickListener { moveCardBy(wrap, offset) }
        }
        val upButton = moveButton("↑", "Move widget up", -1)
        val downButton = moveButton("↓", "Move widget down", 1)
        wrap.addView(upButton, FrameLayout.LayoutParams(removeSize, removeSize).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = (6 * density).roundToInt()
            marginEnd = (44 * density).roundToInt()
        })
        wrap.addView(downButton, FrameLayout.LayoutParams(removeSize, removeSize).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = (6 * density).roundToInt()
            marginEnd = (82 * density).roundToInt()
        })
        wrap.editButtons = listOf(removeBtn, upButton, downButton)

        val resizeHandle = ResizeHandleView(appContext).apply {
            contentDescription = appContext.getString(R.string.resize_widget)
            elevation = 6 * density
            visibility = View.GONE
            isClickable = true
        }
        wrap.attachResizeHandle(resizeHandle)
        wrap.addView(
            resizeHandle,
            FrameLayout.LayoutParams(handleWidth, handleHeight).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (6 * density).roundToInt()
            },
        )

        return wrap
    }

    private fun themedColor(attribute: Int, fallback: Int): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attribute, value, true)) value.data else fallback
    }

    private fun applyWidgetOptions(
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

    private fun providerResizeMinSizePx(info: AppWidgetProviderInfo): Pair<Int, Int> {
        val (defaultWidth, defaultHeight) = providerMinSizePx(info)
        val resizeWidth = info.minResizeWidth.takeIf { it > 0 } ?: defaultWidth
        val resizeHeight = info.minResizeHeight.takeIf { it > 0 } ?: defaultHeight
        return resizeWidth to resizeHeight
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
        if (bound.heightDp > 0) {
            val h = (bound.heightDp * density).roundToInt().coerceIn(minHpx, maxHpx)
            return containerWidthPx to h
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

        // A single full-width column makes widgets easier to scan and reorder.
        val h = if (isWide) {
            (containerWidthPx * aspect).roundToInt()
        } else {
            pH
        }.coerceIn(minHpx, maxHpx)
        return containerWidthPx to h
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
        var editButtons: List<View> = emptyList()
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
            editButtons.forEach { it.visibility = if (enabled) View.VISIBLE else View.GONE }
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
            for (chrome in editButtons) {
                if (chrome.visibility == View.VISIBLE &&
                    x >= chrome.left && x < chrome.right && y >= chrome.top && y < chrome.bottom
                ) return true
            }
            val handle = resizeHandle ?: return false
            return handle.visibility == View.VISIBLE &&
                x >= handle.left && x < handle.right && y >= handle.top && y < handle.bottom
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
                    val dh = (event.rawY - startRawY).roundToInt()
                    val newW = maxWidthPx
                    val newH = (startH + dh).coerceIn(minHeightPx, maxHeightPx)
                    applyLiveSize(newW, newH)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (resizing) {
                        resizing = false
                        parent?.requestDisallowInterceptTouchEvent(false)
                        (parent?.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(false)
                        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                            applyLiveSize(startW, startH)
                        } else {
                            val lp = layoutParams
                            if (lp != null && lp.width > 0 && lp.height > 0) {
                                onSizeCommitted?.invoke(lp.width, lp.height)
                            }
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
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * resources.displayMetrics.density
                setColor(Color.argb(180, 20, 20, 24))
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val d = resources.displayMetrics.density
            val centerY = height / 2f
            val halfWidth = 12f * d
            canvas.drawLine(width / 2f - halfWidth, centerY - 3f * d, width / 2f + halfWidth, centerY - 3f * d, paint)
            canvas.drawLine(width / 2f - halfWidth, centerY + 3f * d, width / 2f + halfWidth, centerY + 3f * d, paint)
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
