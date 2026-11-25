package com.arcturus.haven.launcher.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceGestureRoutingTest {
    @Test
    fun disabledCustomGesturesKeepDefaultSingleFingerNavigation() {
        assertTrue(WorkspaceGestureRouting.shouldRunLegacyNavigation(WorkspaceSurfaceGesture.SWIPE_UP, hasAssignment = true, assignedGesturesEnabled = false))
    }

    @Test
    fun enabledAssignmentTakesPrecedenceOverLegacyNavigation() {
        assertTrue(WorkspaceGestureRouting.shouldRunAssignedAction(hasAssignment = true, assignedGesturesEnabled = true))
        assertFalse(WorkspaceGestureRouting.shouldRunLegacyNavigation(WorkspaceSurfaceGesture.SWIPE_UP, hasAssignment = true, assignedGesturesEnabled = true))
    }

    @Test
    fun unsetSingleFingerGestureUsesLegacyNavigation() {
        assertTrue(WorkspaceGestureRouting.shouldRunLegacyNavigation(WorkspaceSurfaceGesture.SWIPE_UP, hasAssignment = false, assignedGesturesEnabled = true))
    }

    @Test
    fun unassignedMultiTouchGestureDoesNotUseLegacyNavigation() {
        assertFalse(WorkspaceGestureRouting.shouldRunLegacyNavigation(WorkspaceSurfaceGesture.DUAL_SWIPE_UP, hasAssignment = false, assignedGesturesEnabled = false))
    }
}
