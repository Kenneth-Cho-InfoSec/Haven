package com.arcturus.haven.launcher.core.data

/** Cell visit order used by launcher-owned icon groups. The selected apps fill these cells first. */
enum class IconGroupLayout(val storageKey: String, val title: String) {
    FLOWER("flower", "Flower"),
    GRID("grid", "Grid"),
    HONEYCOMB("honeycomb", "Honeycomb"),
    ARCH("arch", "Arch");

    fun cellOrder(): IntArray = when (this) {
        GRID -> IntArray(CELL_COUNT) { it }
        // The reference widget occupies a square 4×4 area. Flower keeps the six-icon
        // silhouette from Smart's gallery preview, then fills the remaining cells.
        FLOWER -> intArrayOf(2, 4, 7, 8, 11, 13, 0, 1, 3, 5, 6, 9, 10, 12, 14, 15)
        HONEYCOMB -> intArrayOf(1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 0, 3, 12, 15)
        ARCH -> intArrayOf(12, 13, 14, 15, 8, 9, 10, 11, 4, 5, 6, 7, 0, 1, 2, 3)
    }

    companion object {
        const val COLUMN_COUNT = 4
        const val ROW_COUNT = 4
        const val CELL_COUNT = COLUMN_COUNT * ROW_COUNT
        const val MAX_APPS = CELL_COUNT

        fun fromStorageKey(key: String?): IconGroupLayout =
            entries.firstOrNull { it.storageKey == key } ?: FLOWER
    }
}
