package com.arcturus.haven.launcher.core.model

/** Keeps ordinary Home navigation separate from user-assigned workspace gesture actions. */
object WorkspaceGestureRouting {
    fun shouldRunAssignedAction(hasAssignment: Boolean, assignedGesturesEnabled: Boolean): Boolean =
        hasAssignment && assignedGesturesEnabled

    fun shouldRunLegacyNavigation(
        gesture: WorkspaceSurfaceGesture,
        hasAssignment: Boolean,
        assignedGesturesEnabled: Boolean,
    ): Boolean = !gesture.isMultiTouch && (!hasAssignment || !assignedGesturesEnabled)
}
