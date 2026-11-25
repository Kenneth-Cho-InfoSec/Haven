package com.arcturus.haven.launcher.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceSurfaceGestureTest {
    @Test
    fun singleFingerGesturesRequireThresholdAndResolveDominantAxis() {
        assertNull(WorkspaceGestureClassifier.oneFinger(8f, 9f, threshold = 20f))
        assertEquals(WorkspaceSurfaceGesture.SWIPE_LEFT, WorkspaceGestureClassifier.oneFinger(-60f, 12f, threshold = 20f))
        assertEquals(WorkspaceSurfaceGesture.SWIPE_DOWN, WorkspaceGestureClassifier.oneFinger(10f, 60f, threshold = 20f))
    }

    @Test
    fun twoFingerGestureClassifierRecognizesSwipesPinchesAndRotation() {
        fun pair(x1: Float, y1: Float, x2: Float, y2: Float) = WorkspaceGestureClassifier.PointerPair(
            WorkspaceGestureClassifier.Point(x1, y1),
            WorkspaceGestureClassifier.Point(x2, y2),
        )
        val initial = pair(0f, 0f, 100f, 0f)

        assertEquals(WorkspaceSurfaceGesture.PINCH_IN, WorkspaceGestureClassifier.twoFinger(initial, pair(20f, 0f, 80f, 0f), 24f, 18f))
        assertEquals(WorkspaceSurfaceGesture.PINCH_OUT, WorkspaceGestureClassifier.twoFinger(initial, pair(-20f, 0f, 120f, 0f), 24f, 18f))
        assertEquals(WorkspaceSurfaceGesture.ROTATE_RIGHT, WorkspaceGestureClassifier.twoFinger(initial, pair(0f, 0f, 70f, 70f), 24f, 18f))
        assertEquals(WorkspaceSurfaceGesture.ROTATE_LEFT, WorkspaceGestureClassifier.twoFinger(initial, pair(0f, 0f, 70f, -70f), 24f, 18f))
        assertEquals(WorkspaceSurfaceGesture.DUAL_SWIPE_LEFT, WorkspaceGestureClassifier.twoFinger(initial, pair(-50f, 0f, 50f, 0f), 24f, 18f))
        assertNull(WorkspaceGestureClassifier.twoFinger(initial, pair(4f, 0f, 96f, 0f), 24f, 18f))
    }
}
