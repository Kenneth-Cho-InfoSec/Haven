package com.arcturus.haven.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperOffsetsTest {
    @Test
    fun scrollablePagesUseEqualWallpaperSteps() {
        assertEquals(WallpaperOffset(0.5f, 0f), WallpaperOffsets.forPage(0, 3, false, true))
        assertEquals(WallpaperOffset(0.5f, 0.5f), WallpaperOffsets.forPage(1, 3, false, true))
        assertEquals(WallpaperOffset(0.5f, 1f), WallpaperOffsets.forPage(2, 3, false, true))
    }

    @Test
    fun reversedPagesReverseTheWallpaperPosition() {
        assertEquals(WallpaperOffset(0.5f, 1f), WallpaperOffsets.forPage(0, 3, true, true))
        assertEquals(WallpaperOffset(0.5f, 0f), WallpaperOffsets.forPage(2, 3, true, true))
    }

    @Test
    fun disablingScrollCentersWallpaperButKeepsParallax() {
        assertEquals(WallpaperOffset(1f, 0.5f), WallpaperOffsets.forPage(2, 3, false, false))
        assertEquals(WallpaperOffset(1f, 0.55f), WallpaperOffsets.forPage(2, 3, false, false, 0.05f))
    }

    @Test
    fun parallaxIsClampedToValidWallpaperRange() {
        assertEquals(WallpaperOffset(0.5f, 1f), WallpaperOffsets.forPage(2, 3, false, true, 0.2f))
        assertEquals(WallpaperOffset(0.5f, 0f), WallpaperOffsets.forPage(0, 3, false, true, -0.2f))
    }
}
