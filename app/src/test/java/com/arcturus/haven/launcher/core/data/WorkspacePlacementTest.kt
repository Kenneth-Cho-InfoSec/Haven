package com.arcturus.haven.launcher.core.data

import com.arcturus.haven.launcher.core.model.WorkspaceItem
import com.arcturus.haven.launcher.core.model.WorkspaceLayout
import com.arcturus.haven.launcher.core.model.WorkspaceShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacePlacementTest {
    private fun items(cells: IntRange, pkgPrefix: String = "existing", labelPrefix: String = "Existing") =
        cells.map { WorkspaceItem(0, it, WorkspaceShortcut("$pkgPrefix.$it", "Main", "$labelPrefix $it")) }

    private fun place(
        layout: WorkspaceLayout,
        shortcut: WorkspaceShortcut = WorkspaceShortcut("new.app", "Main", "New app"),
        maxPages: Int = 20,
        excludedPackages: Set<String> = emptySet(),
    ) = WorkspacePlacement.placeNewShortcut(
        layout = layout,
        shortcut = shortcut,
        pageCount = 1,
        columns = WorkspaceGridSpec.COLUMNS,
        rows = WorkspaceGridSpec.ROWS,
        maxPages = maxPages,
        excludedPackages = excludedPackages,
    )

    private fun migrate(cells: IntRange) = WorkspacePlacement.migrateForDockClearance(
        layout = WorkspaceLayout(items = items(cells, "pkg", "App")),
        pageCount = 1,
        columns = WorkspaceGridSpec.COLUMNS,
        rows = WorkspaceGridSpec.ROWS,
        maxPages = 20,
    )

    @Test
    fun homeIconCanvasIsFixedAtFourColumnsAndSixRows() {
        assertEquals(4, WorkspaceGridSpec.COLUMNS)
        assertEquals(6, WorkspaceGridSpec.ROWS)
        assertEquals(24, WorkspaceGridSpec.CAPACITY)
    }

    @Test
    fun findsFirstAvailableCell() {
        assertEquals(2, WorkspacePlacement.firstEmptyCell(setOf(0, 1, 3), 8))
    }

    @Test
    fun newHomeIconsUseBottomRowsFirst() {
        assertEquals(16, WorkspacePlacement.firstEmptyCellFromBottom(emptySet(), WorkspaceGridSpec.COLUMNS, WorkspaceGridSpec.ROWS))
        assertEquals(17, WorkspacePlacement.firstEmptyCellFromBottom(setOf(16), WorkspaceGridSpec.COLUMNS, WorkspaceGridSpec.ROWS))
        assertEquals(12, WorkspacePlacement.firstEmptyCellFromBottom((16..23).toSet(), WorkspaceGridSpec.COLUMNS, WorkspaceGridSpec.ROWS))
    }

    @Test
    fun newlyInstalledAppUsesFirstFreeCellOnExistingPage() {
        val result = place(WorkspaceLayout(items = items(16..22)))

        assertEquals(0, result?.page)
        assertEquals(23, result?.cell)
        assertEquals(1, result?.pageCount)
    }

    @Test
    fun newlyInstalledAppCreatesPageWhenAllExistingPagesAreFull() {
        val result = place(WorkspaceLayout(items = items(0 until WorkspaceGridSpec.CAPACITY)))

        assertEquals(1, result?.page)
        assertEquals(20, result?.cell)
        assertEquals(2, result?.pageCount)
    }

    @Test
    fun dockClearanceMigrationKeepsPreviouslyVisibleIconsInPlace() {
        val migrated = migrate(16..19)

        assertEquals(listOf(20, 21, 22, 23), migrated.layout.items.map { it.cell })
        assertEquals(1, migrated.pageCount)
    }

    @Test
    fun dockClearanceMigrationMovesIconsThatWereUnderTheDockIntoTheNextFreeVisibleRow() {
        val migrated = migrate(16..20)

        assertEquals(listOf(20, 21, 22, 23, 16), migrated.layout.items.map { it.cell })
        assertEquals(1, migrated.pageCount)
    }

    @Test
    fun dockClearanceMigrationMovesOverflowToANewPageInsteadOfClockArea() {
        val migrated = migrate(0 until WorkspaceGridSpec.CAPACITY)

        assertEquals(2, migrated.pageCount)
        assertEquals(4, migrated.layout.items.count { it.page == 1 })
        assertEquals(setOf(20, 21, 22, 23), migrated.layout.items.filter { it.page == 1 }.map { it.cell }.toSet())
    }

    @Test
    fun newlyInstalledAppIsNotDuplicatedInWorkspaceOrFolder() {
        val shortcut = WorkspaceShortcut("new.app", "Main", "New app")
        val layout = WorkspaceLayout(items = listOf(
            WorkspaceItem(0, 16, shortcut = shortcut),
            WorkspaceItem(0, 17, folder = listOf(shortcut)),
        ))

        assertNull(place(layout, shortcut))
        assertNull(place(WorkspaceLayout(items = listOf(WorkspaceItem(0, 17, folder = listOf(shortcut)))), shortcut))
        assertNull(place(WorkspaceLayout(), shortcut, excludedPackages = setOf("new.app")))
    }

    @Test
    fun newlyInstalledAppDoesNotExceedMaximumPageCount() {
        assertNull(place(WorkspaceLayout(items = items(0 until WorkspaceGridSpec.CAPACITY)), maxPages = 1))
    }

    @Test
    fun returnsNullWhenPageIsFull() {
        assertNull(WorkspacePlacement.firstEmptyCell((0..3).toSet(), 4))
    }

    @Test
    fun validatesCellBounds() {
        assertTrue(WorkspacePlacement.isValidCell(0, 20))
        assertTrue(WorkspacePlacement.isValidCell(19, 20))
        assertFalse(WorkspacePlacement.isValidCell(-1, 20))
        assertFalse(WorkspacePlacement.isValidCell(20, 20))
    }

    @Test
    fun emptyWorkspaceItemIsStillAFolder() {
        assertTrue(WorkspaceItem(page = 0, cell = 18, folderLabel = "Social").isFolder)
    }

    @Test
    fun appShortcutIsNotAFolder() {
        val item = WorkspaceItem(0, 16, WorkspaceShortcut("com.example", "MainActivity", "Example"))

        assertFalse(item.isFolder)
    }

    @Test
    fun reflowKeepsItemOrderAndMovesOverflowToNextPage() {
        val shortcuts = (0 until 7).map { WorkspaceShortcut("pkg.$it", "Activity$it", "App $it") }
        val layout = WorkspaceLayout(
            items = shortcuts.mapIndexed { index, shortcut -> WorkspaceItem(index / 4, index % 4, shortcut) },
            widgetIds = listOf(41),
        )

        val result = WorkspacePlacement.reflow(layout, columns = 3, rows = 2)

        assertEquals(shortcuts, result.items.mapNotNull { it.shortcut })
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 1), result.items.map { it.page })
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 0), result.items.map { it.cell })
        assertEquals(listOf(41), result.widgetIds)
    }

    @Test
    fun fixedHomeGridReflowKeepsTwentyFourIconsOnEachPage() {
        val items = (0..24).map { WorkspaceItem(0, it, WorkspaceShortcut("pkg.$it", "A$it", "App $it")) }
        val result = WorkspacePlacement.reflow(WorkspaceLayout(items = items), WorkspaceGridSpec.COLUMNS, WorkspaceGridSpec.ROWS)

        assertEquals(WorkspaceGridSpec.CAPACITY, result.items.count { it.page == 0 })
        assertEquals(listOf(0, 1, 2, 3), result.items.take(4).map { it.cell })
        assertEquals(1, result.items.last().page)
        assertEquals(0, result.items.last().cell)
    }
}
