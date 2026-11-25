package com.arcturus.haven.launcher.core.data

import com.arcturus.haven.launcher.core.model.WorkspaceWidgetSize
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetResizeRulesTest {
    @Test
    fun clampsBothAxesToProviderBounds() {
        val bounds = WidgetResizeBounds(120, 90, 360, 520, true, true)
        assertEquals(WorkspaceWidgetSize(360, 90), WidgetResizeRules.resize(WorkspaceWidgetSize(240, 150), 500, -500, bounds))
    }

    @Test
    fun preservesAxesTheProviderCannotResize() {
        val bounds = WidgetResizeBounds(120, 90, 360, 520, false, true)
        assertEquals(WorkspaceWidgetSize(240, 300), WidgetResizeRules.resize(WorkspaceWidgetSize(240, 180), 100, 120, bounds))
    }
}
