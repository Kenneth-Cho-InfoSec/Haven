package com.arcturus.haven.launcher.core.model

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Workspace gestures that are independent of gestures assigned to an individual icon. */
enum class WorkspaceSurfaceGesture(
    val storageKey: String,
    val label: String,
    val pickerGesture: WorkspaceIconGesture,
    val isMultiTouch: Boolean = false,
) {
    SWIPE_LEFT("swipe_left", "Swipe left", WorkspaceIconGesture.SWIPE_LEFT),
    SWIPE_RIGHT("swipe_right", "Swipe right", WorkspaceIconGesture.SWIPE_RIGHT),
    SWIPE_UP("swipe_up", "Swipe up", WorkspaceIconGesture.SWIPE_UP),
    SWIPE_DOWN("swipe_down", "Swipe down", WorkspaceIconGesture.SWIPE_DOWN),
    DUAL_SWIPE_LEFT("dual_swipe_left", "Dual swipe left", WorkspaceIconGesture.SWIPE_LEFT, true),
    DUAL_SWIPE_RIGHT("dual_swipe_right", "Dual swipe right", WorkspaceIconGesture.SWIPE_RIGHT, true),
    DUAL_SWIPE_UP("dual_swipe_up", "Dual swipe up", WorkspaceIconGesture.SWIPE_UP, true),
    DUAL_SWIPE_DOWN("dual_swipe_down", "Dual swipe down", WorkspaceIconGesture.SWIPE_DOWN, true),
    PINCH_IN("pinch_in", "Pinch in", WorkspaceIconGesture.SWIPE_UP, true),
    PINCH_OUT("pinch_out", "Pinch out", WorkspaceIconGesture.SWIPE_DOWN, true),
    ROTATE_RIGHT("rotate_right", "Rotate to right", WorkspaceIconGesture.SWIPE_RIGHT, true),
    ROTATE_LEFT("rotate_left", "Rotate to left", WorkspaceIconGesture.SWIPE_LEFT, true),
}

/** Pure geometry classifier so pointer noise and directional boundaries can be tested. */
object WorkspaceGestureClassifier {
    data class Point(val x: Float, val y: Float)
    data class PointerPair(val first: Point, val second: Point)

    fun oneFinger(dx: Float, dy: Float, threshold: Float): WorkspaceSurfaceGesture? = classifyDirection(
        dx, dy, threshold,
        WorkspaceSurfaceGesture.SWIPE_LEFT, WorkspaceSurfaceGesture.SWIPE_RIGHT,
        WorkspaceSurfaceGesture.SWIPE_UP, WorkspaceSurfaceGesture.SWIPE_DOWN,
    )

    fun twoFinger(
        start: PointerPair,
        current: PointerPair,
        translationThreshold: Float,
        transformThreshold: Float,
    ): WorkspaceSurfaceGesture? {
        val startDistance = distance(start)
        val distanceDelta = distance(current) - startDistance
        if (abs(distanceDelta) >= maxOf(transformThreshold, startDistance * 0.16f)) {
            return if (distanceDelta < 0f) WorkspaceSurfaceGesture.PINCH_IN else WorkspaceSurfaceGesture.PINCH_OUT
        }

        val rotation = normalizedAngle(angle(current) - angle(start))
        if (abs(rotation) >= 26f) {
            return if (rotation > 0f) WorkspaceSurfaceGesture.ROTATE_RIGHT else WorkspaceSurfaceGesture.ROTATE_LEFT
        }

        val startCenter = center(start)
        val currentCenter = center(current)
        return classifyDirection(
            currentCenter.x - startCenter.x,
            currentCenter.y - startCenter.y,
            translationThreshold,
            WorkspaceSurfaceGesture.DUAL_SWIPE_LEFT,
            WorkspaceSurfaceGesture.DUAL_SWIPE_RIGHT,
            WorkspaceSurfaceGesture.DUAL_SWIPE_UP,
            WorkspaceSurfaceGesture.DUAL_SWIPE_DOWN,
        )
    }

    private fun classifyDirection(
        dx: Float,
        dy: Float,
        threshold: Float,
        left: WorkspaceSurfaceGesture,
        right: WorkspaceSurfaceGesture,
        up: WorkspaceSurfaceGesture,
        down: WorkspaceSurfaceGesture,
    ): WorkspaceSurfaceGesture? {
        if (maxOf(abs(dx), abs(dy)) < threshold) return null
        return when {
            abs(dx) > abs(dy) -> if (dx < 0f) left else right
            dy < 0f -> up
            else -> down
        }
    }

    private fun distance(pair: PointerPair): Float =
        hypot(pair.second.x - pair.first.x, pair.second.y - pair.first.y)

    private fun angle(pair: PointerPair): Float = Math.toDegrees(
        atan2((pair.second.y - pair.first.y).toDouble(), (pair.second.x - pair.first.x).toDouble()),
    ).toFloat()

    private fun center(pair: PointerPair) = Point(
        (pair.first.x + pair.second.x) / 2f,
        (pair.first.y + pair.second.y) / 2f,
    )

    private fun normalizedAngle(value: Float): Float = when {
        value > 180f -> value - 360f
        value < -180f -> value + 360f
        else -> value
    }
}
