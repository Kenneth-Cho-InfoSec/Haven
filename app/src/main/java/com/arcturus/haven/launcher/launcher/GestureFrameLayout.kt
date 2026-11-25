package com.arcturus.haven.launcher.launcher

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import com.arcturus.haven.launcher.core.model.WorkspaceGestureClassifier
import com.arcturus.haven.launcher.core.model.WorkspaceSurfaceGesture
import kotlin.math.abs

class GestureFrameLayout(context: Context) : FrameLayout(context) {
    var onSwipe: ((dx: Float, dy: Float) -> Unit)? = null
    /** Empty workspace regions can observe a swipe even when an underlying child disallows interception. */
    var shouldTrackSwipe: ((startX: Float, startY: Float) -> Boolean)? = null
    /** The fixed icon canvas owns empty-area swipes even when a widget scroller lies beneath it. */
    var shouldPrioritizeSwipeOverScrollable: ((startX: Float, startY: Float) -> Boolean)? = null
    var onDoubleTap: ((x: Float, y: Float) -> Unit)? = null
    var onLongPress: ((x: Float, y: Float) -> Unit)? = null
    var onMultiGesture: ((WorkspaceSurfaceGesture) -> Unit)? = null
    var shouldTrackMultiGesture: ((x1: Float, y1: Float, x2: Float, y2: Float) -> Boolean)? = null
    private val threshold = ViewConfiguration.get(context).scaledTouchSlop * 4
    private val transformThreshold = ViewConfiguration.get(context).scaledTouchSlop * 2
    private var downX = 0f
    private var downY = 0f
    private var dispatched = false
    private var multiSequence = false
    private var multiTracking = false
    private var firstPointerId = MotionEvent.INVALID_POINTER_ID
    private var secondPointerId = MotionEvent.INVALID_POINTER_ID
    private var multiStart: WorkspaceGestureClassifier.PointerPair? = null
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true

        override fun onDoubleTap(event: MotionEvent): Boolean {
            this@GestureFrameLayout.onDoubleTap?.invoke(event.rawX, event.rawY)
            return true
        }

        override fun onLongPress(event: MotionEvent) {
            this@GestureFrameLayout.onLongPress?.invoke(event.x, event.y)
        }
    })

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) resetDown(event)
        if (!multiSequence && event.pointerCount == 1) gestureDetector.onTouchEvent(event)

        var directWorkspaceSwipe = false
        var workspaceSwipeDx = 0f
        var workspaceSwipeDy = 0f
        if (event.actionMasked == MotionEvent.ACTION_MOVE && !multiSequence && !dispatched) {
            val dx = event.x - downX
            val dy = event.y - downY
            if (canRecognizeWorkspaceSwipe(dx, dy)) {
                // Preserve ViewGroup's intercept path so the child receives ACTION_CANCEL.
                requestDisallowInterceptTouchEvent(false)
                directWorkspaceSwipe = true
                workspaceSwipeDx = dx
                workspaceSwipeDy = dy
            }
        }
        val handled = super.dispatchTouchEvent(event)
        // ViewGroup does not call onInterceptTouchEvent when it owns the stream itself.
        // In that case, dispatch the recognized gesture after normal child routing completes.
        if (directWorkspaceSwipe && !dispatched) {
            dispatched = true
            onSwipe?.invoke(workspaceSwipeDx, workspaceSwipeDy)
        }
        return handled
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> resetDown(event)
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiSequence = true
                if (event.pointerCount == 2 && !dispatched) {
                    firstPointerId = event.getPointerId(0)
                    secondPointerId = event.getPointerId(1)
                    val start = pointerPair(event, 0, 1).also { multiStart = it }
                    multiTracking = shouldTrackMultiGesture?.invoke(
                        start.first.x, start.first.y, start.second.x, start.second.y,
                    ) != false
                } else {
                    multiTracking = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (multiSequence) {
                    val start = multiStart
                    if (multiTracking && start != null) {
                        val firstIndex = event.findPointerIndex(firstPointerId)
                        val secondIndex = event.findPointerIndex(secondPointerId)
                        if (firstIndex >= 0 && secondIndex >= 0) {
                            WorkspaceGestureClassifier.twoFinger(
                                start = start,
                                current = pointerPair(event, firstIndex, secondIndex),
                                translationThreshold = threshold.toFloat(),
                                transformThreshold = transformThreshold.toFloat(),
                            )?.let { gesture ->
                                dispatched = true
                                multiTracking = false
                                onMultiGesture?.invoke(gesture)
                                return true
                            }
                        }
                    }
                    return false
                }
                if (event.pointerCount != 1) return false
                val dx = event.x - downX
                val dy = event.y - downY
                if (!dispatched && canRecognizeWorkspaceSwipe(dx, dy)) {
                    dispatched = true
                    onSwipe?.invoke(dx, dy)
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                multiSequence = true
                multiTracking = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetMultiGesture()
        }
        return super.onInterceptTouchEvent(event)
    }

    private fun canRecognizeWorkspaceSwipe(dx: Float, dy: Float): Boolean {
        if (maxOf(abs(dx), abs(dy)) <= threshold || shouldTrackSwipe?.invoke(downX, downY) == false) return false
        if (shouldPrioritizeSwipeOverScrollable?.invoke(downX, downY) == true) return true
        val horizontal = abs(dx) > abs(dy)
        return !hasScrollableDescendantAt(
            this,
            downX,
            downY,
            if (horizontal) (if (dx > 0) -1 else 1) else 0,
            if (horizontal) 0 else (if (dy > 0) -1 else 1),
        )
    }

    private fun resetDown(event: MotionEvent) {
        downX = event.x
        downY = event.y
        dispatched = false
        resetMultiGesture()
    }

    private fun pointerPair(event: MotionEvent, firstIndex: Int, secondIndex: Int) = WorkspaceGestureClassifier.PointerPair(
        WorkspaceGestureClassifier.Point(event.getX(firstIndex), event.getY(firstIndex)),
        WorkspaceGestureClassifier.Point(event.getX(secondIndex), event.getY(secondIndex)),
    )

    private fun resetMultiGesture() {
        multiSequence = false
        multiTracking = false
        firstPointerId = MotionEvent.INVALID_POINTER_ID
        secondPointerId = MotionEvent.INVALID_POINTER_ID
        multiStart = null
    }

    private fun hasScrollableDescendantAt(
        parent: ViewGroup,
        x: Float,
        y: Float,
        directionX: Int,
        directionY: Int,
    ): Boolean {
        for (index in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(index)
            if (child.visibility != View.VISIBLE || child.width == 0 || child.height == 0) continue

            // Convert the point into child coordinates, accounting for the parent's scroll
            // offset and the child's translation used by page and drawer transitions.
            val childX = x + parent.scrollX - child.left - child.translationX
            val childY = y + parent.scrollY - child.top - child.translationY
            if (childX < 0f || childY < 0f || childX >= child.width || childY >= child.height) continue

            if (child is ViewGroup && hasScrollableDescendantAt(child, childX, childY, directionX, directionY)) {
                return true
            }
            if ((directionX != 0 && child.canScrollHorizontally(directionX)) ||
                (directionY != 0 && child.canScrollVertically(directionY))
            ) return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && !dispatched) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
