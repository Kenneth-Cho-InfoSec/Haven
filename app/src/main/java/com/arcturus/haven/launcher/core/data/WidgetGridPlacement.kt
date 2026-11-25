package com.arcturus.haven.launcher.core.data

import kotlin.math.abs
import kotlin.math.ceil

data class WidgetGridSpan(val columns: Int, val rows: Int)

/** Pure placement helpers shared by the widget workspace renderer and tests. */
object WidgetGridPlacement {
    fun spanFor(
        widthDp: Int,
        heightDp: Int,
        cellWidthDp: Int,
        cellHeightDp: Int,
        columns: Int,
        outerPaddingDp: Int = 16,
    ): WidgetGridSpan {
        require(columns > 0 && cellWidthDp > 0 && cellHeightDp > 0)
        return WidgetGridSpan(
            ceil((widthDp + outerPaddingDp).toDouble() / cellWidthDp).toInt().coerceIn(1, columns),
            ceil((heightDp + outerPaddingDp).toDouble() / cellHeightDp).toInt().coerceAtLeast(1),
        )
    }

    fun cell(index: Int, columns: Int): Pair<Int, Int> {
        require(columns > 0)
        return (index % columns) to (index / columns)
    }

    fun index(column: Int, row: Int, columns: Int): Int {
        require(columns > 0)
        return row.coerceAtLeast(0) * columns + column.coerceIn(0, columns - 1)
    }

    fun firstAvailable(
        span: WidgetGridSpan,
        columns: Int,
        occupied: Map<Int, WidgetGridSpan>,
        maxRows: Int = 256,
    ): Int? = nearestAvailable(0, 0, span, columns, occupied, maxRows)

    /** Finds the closest non-overlapping top-left cell, preferring the requested location on ties. */
    fun nearestAvailable(
        requestedColumn: Int,
        requestedRow: Int,
        span: WidgetGridSpan,
        columns: Int,
        occupied: Map<Int, WidgetGridSpan>,
        maxRows: Int = 256,
    ): Int? {
        require(columns > 0 && span.columns > 0 && span.rows > 0 && maxRows > 0)
        if (span.columns > columns || span.rows > maxRows) return null
        val targetColumn = requestedColumn.coerceIn(0, columns - span.columns)
        val targetRow = requestedRow.coerceIn(0, maxRows - span.rows)
        var best: Int? = null
        var bestDistance = Int.MAX_VALUE
        for (row in 0..(maxRows - span.rows)) {
            for (column in 0..(columns - span.columns)) {
                if (overlaps(column, row, span, columns, occupied)) continue
                val distance = abs(column - targetColumn) + abs(row - targetRow)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = index(column, row, columns)
                    if (distance == 0) return best
                }
            }
        }
        return best
    }

    fun overlaps(
        column: Int,
        row: Int,
        span: WidgetGridSpan,
        columns: Int,
        occupied: Map<Int, WidgetGridSpan>,
    ): Boolean = occupied.any { (cell, otherSpan) ->
        val (otherColumn, otherRow) = cell(cell, columns)
        column < otherColumn + otherSpan.columns && column + span.columns > otherColumn &&
            row < otherRow + otherSpan.rows && row + span.rows > otherRow
    }
}
