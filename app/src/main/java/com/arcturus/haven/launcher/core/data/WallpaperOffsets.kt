package com.arcturus.haven.launcher.core.data

data class WallpaperOffset(val stepX: Float, val offsetX: Float)

/** Calculates the wallpaper position for a workspace page and optional device-tilt parallax. */
object WallpaperOffsets {
    fun forPage(
        page: Int,
        pageCount: Int,
        reversed: Boolean,
        scrollable: Boolean,
        parallaxOffsetX: Float = 0f,
    ): WallpaperOffset {
        val count = pageCount.coerceAtLeast(1)
        val displayPage = WorkspacePageOrder.displayIndex(page, count, reversed)
        val step = if (scrollable && count > 1) 1f / (count - 1) else 1f
        val center = if (scrollable && count > 1) displayPage.toFloat() / (count - 1) else 0.5f
        return WallpaperOffset(stepX = step, offsetX = (center + parallaxOffsetX).coerceIn(0f, 1f))
    }
}
