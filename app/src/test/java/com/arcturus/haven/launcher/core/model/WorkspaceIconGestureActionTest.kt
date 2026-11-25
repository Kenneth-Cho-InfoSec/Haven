package com.arcturus.haven.launcher.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceIconGestureActionTest {
    @Test
    fun actionsHaveStableStorageKeysAndSafeFallback() {
        assertEquals(
            listOf(
                "none", "open_app", "popup_widget", "app_drawer", "toggle_dock", "search", "assistant", "settings", "open_vault", "app_index",
                "next_home_page", "previous_home_page", "notifications", "recents", "quick_settings", "lock_screen",
                "freeze_all_work", "toggle_work_profile", "launch_app", "launch_shortcut",
            ),
            WorkspaceIconGestureAction.selectable.map { it.storageKey },
        )
        WorkspaceIconGestureAction.selectable.forEach { action ->
            assertEquals(action, WorkspaceIconGestureAction.fromStorage(action.storageKey))
        }
        assertEquals(WorkspaceIconGestureAction.NONE, WorkspaceIconGestureAction.fromStorage("removed-action"))
        assertEquals(WorkspaceIconGestureAction.NONE, WorkspaceIconGestureAction.fromStorage(null))
    }

    @Test
    fun actionsAreGroupedForSmartStyleCatalogTabs() {
        assertEquals(WorkspaceGestureActionGroup.SUGGESTED, WorkspaceIconGestureAction.NONE.group)
        assertEquals(WorkspaceGestureActionGroup.SUGGESTED, WorkspaceIconGestureAction.POPUP_WIDGET.group)
        assertEquals(WorkspaceGestureActionGroup.SYSTEM, WorkspaceIconGestureAction.LOCK_SCREEN.group)
        assertEquals(WorkspaceGestureActionGroup.NAVIGATION, WorkspaceIconGestureAction.APP_INDEX.group)
        assertEquals(WorkspaceGestureActionGroup.NAVIGATION, WorkspaceIconGestureAction.NEXT_HOME_PAGE.group)
        assertEquals(WorkspaceGestureActionGroup.NAVIGATION, WorkspaceIconGestureAction.PREVIOUS_HOME_PAGE.group)
        assertEquals(WorkspaceGestureActionGroup.APPS, WorkspaceIconGestureAction.LAUNCH_APP.group)
        assertEquals(WorkspaceGestureActionGroup.SHORTCUTS, WorkspaceIconGestureAction.LAUNCH_SHORTCUT.group)
    }
}
