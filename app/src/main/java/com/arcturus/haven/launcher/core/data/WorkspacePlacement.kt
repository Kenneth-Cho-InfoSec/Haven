package com.arcturus.haven.launcher.core.data

import com.arcturus.haven.launcher.core.model.WorkspaceItem
import com.arcturus.haven.launcher.core.model.WorkspaceLayout
import com.arcturus.haven.launcher.core.model.WorkspaceShortcut

/** The icon canvas above the dock: six rows by a configurable four-to-eight columns. */
object WorkspaceGridSpec {
    const val COLUMNS = 4
    const val MIN_COLUMNS = 4
    const val MAX_COLUMNS = 8
    const val ROWS = 6
    const val CAPACITY = COLUMNS * ROWS

    fun columns(value: Int): Int = value.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
}

data class WorkspaceShortcutPlacement(
    val layout: WorkspaceLayout,
    val pageCount: Int,
    val page: Int,
    val cell: Int,
)

data class WorkspaceGridMigration(
    val layout: WorkspaceLayout,
    val pageCount: Int,
)

object WorkspacePlacement {
    fun firstEmptyCell(occupied: Set<Int>, cellCount: Int): Int? =
        (0 until cellCount).firstOrNull { it !in occupied }

    /** Prefer the lower two icon rows so newly placed apps sit close to the dock. */
    fun firstEmptyCellFromBottom(occupied: Set<Int>, columns: Int, rows: Int): Int? {
        val safeColumns = columns.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        val capacity = safeColumns * safeRows
        val lowerRowsStart = (safeRows - 2).coerceAtLeast(0) * safeColumns
        return (lowerRowsStart until capacity).firstOrNull { it !in occupied }
            ?: (safeRows - 3 downTo 0).firstNotNullOfOrNull { row ->
                (row * safeColumns until (row + 1) * safeColumns).firstOrNull { it !in occupied }
            }
    }

    fun isValidCell(cell: Int, cellCount: Int): Boolean = cell in 0 until cellCount

    /** Adds one newly installed app to the first available Home page, creating a page if needed. */
    fun placeNewShortcut(
        layout: WorkspaceLayout,
        shortcut: WorkspaceShortcut,
        pageCount: Int,
        columns: Int,
        rows: Int,
        maxPages: Int,
        excludedPackages: Set<String> = emptySet(),
    ): WorkspaceShortcutPlacement? {
        if (shortcut.packageName in excludedPackages) return null
        if (layout.items.any { it.shortcut?.matches(shortcut) == true || it.folder.any { entry -> entry.matches(shortcut) } }) {
            return null
        }

        val safeColumns = columns.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        val pageLimit = maxPages.coerceAtLeast(1)
        val existingPageCount = effectivePageCount(layout, pageCount, pageLimit)
        val (page, cell) = (0 until existingPageCount).firstNotNullOfOrNull { page ->
            val occupied = layout.items.asSequence().filter { it.page == page }.map { it.cell }.toSet()
            firstEmptyCellFromBottom(occupied, safeColumns, safeRows)?.let { page to it }
        } ?: if (existingPageCount < pageLimit) existingPageCount to (safeRows - 1) * safeColumns else return null
        return WorkspaceShortcutPlacement(
            layout = layout.copy(items = layout.items + WorkspaceItem(page = page, cell = cell, shortcut = shortcut)),
            pageCount = maxOf(existingPageCount, page + 1),
            page = page,
            cell = cell,
        )
    }

    /** Shifts the fixed icon canvas up by one row to clear the dock, preserving visible cell positions. */
    fun migrateForDockClearance(
        layout: WorkspaceLayout,
        pageCount: Int,
        columns: Int,
        rows: Int,
        maxPages: Int,
    ): WorkspaceGridMigration {
        val safeColumns = columns.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        val capacity = safeColumns * safeRows
        val pageLimit = maxPages.coerceAtLeast(1)
        var migratedPageCount = effectivePageCount(layout, pageCount, pageLimit)
        val occupiedByPage = mutableMapOf<Int, MutableSet<Int>>()
        val visibleRowsStart = (safeRows - 3).coerceAtLeast(0) * safeColumns
        val migratedItems = layout.items.sortedWith(compareBy({ it.page }, { it.cell })).map { item ->
            val page = item.page.coerceIn(0, pageLimit - 1)
            val oldCell = item.cell.coerceIn(0, capacity - 1)
            val preferredCell = if (oldCell < capacity - safeColumns) oldCell + safeColumns else oldCell
            val occupied = occupiedByPage.getOrPut(page) { mutableSetOf() }
            val (targetPage, targetCell) = if (preferredCell !in occupied) {
                page to preferredCell
            } else {
                firstEmptyCellFromBottom(occupied, safeColumns, safeRows)
                    ?.takeIf { it >= visibleRowsStart }
                    ?.let { page to it }
                    ?: ((page + 1) until migratedPageCount).firstNotNullOfOrNull { candidate ->
                        firstEmptyCellInBottomRows(occupiedByPage[candidate].orEmpty(), safeColumns, safeRows)
                            ?.let { candidate to it }
                    }
                    ?: if (migratedPageCount < pageLimit) {
                        migratedPageCount++ to (safeRows - 1) * safeColumns
                    } else {
                        page to (firstEmptyCell(occupied, capacity) ?: preferredCell)
                    }
            }
            occupiedByPage.getOrPut(targetPage) { mutableSetOf() }.add(targetCell)
            item.copy(page = targetPage, cell = targetCell)
        }
        return WorkspaceGridMigration(layout.copy(items = migratedItems), migratedPageCount)
    }

    fun reflow(layout: WorkspaceLayout, columns: Int, rows: Int): WorkspaceLayout {
        val capacity = columns.coerceAtLeast(1) * rows.coerceAtLeast(1)
        return layout.copy(
            items = layout.items.sortedWith(compareBy({ it.page }, { it.cell }))
                .mapIndexed { index, item -> item.copy(page = index / capacity, cell = index % capacity) },
        )
    }

    private fun effectivePageCount(layout: WorkspaceLayout, pageCount: Int, pageLimit: Int): Int =
        maxOf(1, pageCount, (layout.items.maxOfOrNull { it.page } ?: 0) + 1).coerceAtMost(pageLimit)

    private fun firstEmptyCellInBottomRows(occupied: Set<Int>, safeColumns: Int, safeRows: Int): Int? =
        (safeRows - 1 downTo (safeRows - 2).coerceAtLeast(0)).firstNotNullOfOrNull { row ->
            (row * safeColumns until (row + 1) * safeColumns).firstOrNull { it !in occupied }
        }

    private fun WorkspaceShortcut.matches(other: WorkspaceShortcut): Boolean =
        packageName == other.packageName && className == other.className
}
