package com.arcturus.haven.launcher.core.data

import com.arcturus.haven.launcher.core.model.WorkspaceWidgetSize

data class WidgetResizeBounds(
    val minWidthDp: Int,
    val minHeightDp: Int,
    val maxWidthDp: Int,
    val maxHeightDp: Int,
    val canResizeHorizontally: Boolean,
    val canResizeVertically: Boolean,
) {
    init {
        require(minWidthDp > 0 && minHeightDp > 0)
        require(maxWidthDp >= minWidthDp && maxHeightDp >= minHeightDp)
    }
}

object WidgetResizeRules {
    fun resize(
        initial: WorkspaceWidgetSize,
        deltaWidthDp: Int,
        deltaHeightDp: Int,
        bounds: WidgetResizeBounds,
    ): WorkspaceWidgetSize = WorkspaceWidgetSize(
        widthDp = if (bounds.canResizeHorizontally) {
            (initial.widthDp + deltaWidthDp).coerceIn(bounds.minWidthDp, bounds.maxWidthDp)
        } else initial.widthDp,
        heightDp = if (bounds.canResizeVertically) {
            (initial.heightDp + deltaHeightDp).coerceIn(bounds.minHeightDp, bounds.maxHeightDp)
        } else initial.heightDp,
    )
}
