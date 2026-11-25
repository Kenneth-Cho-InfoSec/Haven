package com.arcturus.haven.launcher.core.model

import kotlin.math.abs

/** Gestures that can be assigned to an individual workspace app icon. */
enum class WorkspaceIconGesture(
    val storageKey: String,
    val label: String,
) {
    NONE("none", "Not assigned"),
    SWIPE_UP("up", "Swipe up"),
    SWIPE_DOWN("down", "Swipe down"),
    SWIPE_LEFT("left", "Swipe left"),
    SWIPE_RIGHT("right", "Swipe right"),
    DOUBLE_TAP("double", "Double tap");

    fun menuSubtitle(
        appLabel: String,
        action: WorkspaceIconGestureAction = WorkspaceIconGestureAction.OPEN_APP,
        targetLabel: String? = null,
    ): String = if (this == NONE) label else "$label opens ${actionDescription(appLabel, action, targetLabel)}"

    private fun actionDescription(
        appLabel: String,
        action: WorkspaceIconGestureAction,
        targetLabel: String?,
    ): String = when (action) {
        WorkspaceIconGestureAction.OPEN_APP -> appLabel
        WorkspaceIconGestureAction.LAUNCH_APP,
        WorkspaceIconGestureAction.LAUNCH_SHORTCUT,
        -> targetLabel?.takeIf(String::isNotBlank) ?: action.menuDescription
        WorkspaceIconGestureAction.POPUP_WIDGET -> "${targetLabel?.takeIf(String::isNotBlank) ?: "Battery"} popup"
        else -> action.menuDescription
    }

    val isDirectionalSwipe: Boolean
        get() = this != NONE && this != DOUBLE_TAP

    fun labelFor(action: WorkspaceIconGestureAction = WorkspaceIconGestureAction.OPEN_APP): String =
        if (this == NONE) label else "$label to ${action.gestureActionText}"

    /** Returns whether this assignment matches a movement that exceeded [thresholdPx]. */
    fun matchesSwipe(dx: Float, dy: Float, thresholdPx: Float): Boolean {
        val horizontal = abs(dx) > abs(dy)
        return when (this) {
            SWIPE_UP -> !horizontal && -dy > thresholdPx
            SWIPE_DOWN -> !horizontal && dy > thresholdPx
            SWIPE_LEFT -> horizontal && -dx > thresholdPx
            SWIPE_RIGHT -> horizontal && dx > thresholdPx
            NONE, DOUBLE_TAP -> false
        }
    }

    companion object {
        val selectable: List<WorkspaceIconGesture> = entries
        val assignable: List<WorkspaceIconGesture> = entries.filterNot { it == NONE }

        fun fromStorage(value: String?): WorkspaceIconGesture =
            entries.firstOrNull { it.storageKey == value } ?: NONE
    }
}

/** Human-readable one-line summary for the Smart-style icon options menu. */
fun WorkspaceItem.gestureSummary(appLabel: String): String = when (iconGestureAssignments.size) {
    0 -> "Not assigned"
    1 -> iconGestureAssignments.single().let { it.gesture.menuSubtitle(appLabel, it.action, it.targetLabel) }
    else -> "${iconGestureAssignments.size} gestures assigned"
}

/** Replaces only the selected gesture and keeps the other independent assignments intact. */
fun WorkspaceItem.withGestureAssignment(assignment: WorkspaceIconGestureAssignment?): WorkspaceItem {
    val gesture = assignment?.gesture ?: return this
    val updated = iconGestureAssignments.filterNot { it.gesture == gesture }
    val next = if (assignment.action == WorkspaceIconGestureAction.NONE) updated else updated + assignment
    return copy(iconGestureAssignments = next.sortedBy { it.gesture.ordinal })
}

/** Smart Launcher only shows the secondary action mark for a configured double-tap action. */
fun WorkspaceItem.doubleTapBadgeAssignment(enabled: Boolean): WorkspaceIconGestureAssignment? =
    if (!enabled) null else iconGestureAssignments.firstOrNull {
        it.gesture == WorkspaceIconGesture.DOUBLE_TAP && it.action != WorkspaceIconGestureAction.NONE
    }
