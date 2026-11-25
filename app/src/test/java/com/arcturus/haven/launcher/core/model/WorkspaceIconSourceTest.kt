package com.arcturus.haven.launcher.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceIconSourceTest {
    @Test
    fun editorSourcesHaveStableKeysAndFallbackForUnknownValues() {
        assertEquals(
            listOf("default", "adaptive", "not_adaptive", "teres", "gallery", "icon_pack"),
            WorkspaceIconSource.entries.map { it.storageKey },
        )
        WorkspaceIconSource.entries.forEach { source ->
            assertEquals(source, WorkspaceIconSource.fromStorage(source.storageKey))
        }
        assertEquals(WorkspaceIconSource.DEFAULT, WorkspaceIconSource.fromStorage("removed-source"))
        assertEquals(WorkspaceIconSource.DEFAULT, WorkspaceIconSource.fromStorage(null))
    }
}
