package app.sakinalauncher.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ScrollView
import kotlin.math.abs

class SwipeAwareScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ScrollView(context, attrs, defStyleAttr) {

    var onHorizontalSwipeLeft: (() -> Unit)? = null
    var onHorizontalSwipeRight: (() -> Unit)? = null

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
        ): Boolean {
            val diffY = e2.y - (e1?.y ?: 0f)
            val diffX = e2.x - (e1?.x ?: 0f)
            // Require horizontal component to be at least 2x the vertical, and min 80px distance
            if (abs(diffX) > abs(diffY) * 2 && abs(diffX) > 80 && abs(velocityX) > 80) {
                if (diffX > 0) onHorizontalSwipeRight?.invoke()
                else onHorizontalSwipeLeft?.invoke()
                return true
            }
            return false
        }
    })
    private var initialX = 0f
    private var initialY = 0f
    private var shouldDisallowScroll = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = ev.x
                initialY = ev.y
                shouldDisallowScroll = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!shouldDisallowScroll) {
                    val dx = abs(ev.x - initialX)
                    val dy = abs(ev.y - initialY)
                    if (dx > dy && dx > 24) {
                        shouldDisallowScroll = true
                    }
                }
            }
        }
        return if (shouldDisallowScroll) false else super.onInterceptTouchEvent(ev)
    }
}
