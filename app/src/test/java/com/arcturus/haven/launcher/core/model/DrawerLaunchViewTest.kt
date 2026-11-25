package com.arcturus.haven.launcher.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerLaunchViewTest {
    @Test
    fun storedValuesResolveAndUnknownValuesFallBackToAllApps() {
        DrawerLaunchView.entries.forEach { view ->
            assertEquals(view, DrawerLaunchView.fromPreference(view.preferenceValue))
        }
        assertEquals(DrawerLaunchView.ALL_APPS, DrawerLaunchView.fromPreference(null))
        assertEquals(DrawerLaunchView.ALL_APPS, DrawerLaunchView.fromPreference("removed-view"))
    }
}
