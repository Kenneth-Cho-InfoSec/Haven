package com.arcturus.haven.launcher.core.data

/** Maps saved Home page indices to their displayed order and resolves horizontal page swipes. */
object WorkspacePageOrder {
    fun displayIndex(pageIndex: Int, pageCount: Int, reversed: Boolean): Int {
        val safeCount = pageCount.coerceAtLeast(1)
        val safePage = pageIndex.coerceIn(0, safeCount - 1)
        return if (reversed) safeCount - 1 - safePage else safePage
    }

    /** A left swipe has delta +1 in the normal order and -1 when page order is reversed. */
    fun pageAfterHorizontalSwipe(
        currentPage: Int,
        delta: Int,
        pageCount: Int,
        reversed: Boolean,
    ): Int {
        require(delta == -1 || delta == 1) { "Horizontal swipe delta must be -1 or 1" }
        val safeCount = pageCount.coerceAtLeast(1)
        val step = if (reversed) -delta else delta
        return (currentPage.coerceIn(0, safeCount - 1) + step).coerceIn(0, safeCount - 1)
    }
}
