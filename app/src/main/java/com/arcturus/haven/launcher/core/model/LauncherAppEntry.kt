package com.arcturus.haven.launcher.core.model

data class LauncherAppEntry(
    val packageName: String,
    val className: String,
    val label: String,
) {
    val componentKey: String get() = "$packageName/$className"
}

data class WorkspaceShortcut(
    val packageName: String,
    val className: String,
    val label: String,
    val profile: String = PROFILE_PERSONAL,
    val actionType: String = ACTION_LAUNCH,
    val linkedPackages: List<String> = emptyList(),
) {
    companion object {
        const val PROFILE_PERSONAL = "personal"
        const val PROFILE_WORK = "work"
        const val ACTION_LAUNCH = "launch"
        const val ACTION_UNFREEZE_LAUNCH = "unfreeze_launch"
        const val ACTION_FREEZE_ALL = "freeze_all"
    }
}

data class WorkspaceIconGestureAssignment(
    val gesture: WorkspaceIconGesture,
    val action: WorkspaceIconGestureAction,
    val targetPackage: String? = null,
    val targetClass: String? = null,
    val targetLabel: String? = null,
    val targetShortcutId: String? = null,
)

data class WorkspaceItem(
    val page: Int,
    val cell: Int,
    val shortcut: WorkspaceShortcut? = null,
    val folder: List<WorkspaceShortcut> = emptyList(),
    val folderLabel: String = "Folder",
    val iconBackgroundColor: Int? = null,
    val iconGestureAssignments: List<WorkspaceIconGestureAssignment> = emptyList(),
    val iconSource: String = "default",
    val iconUri: String? = null,
    val iconPackPackageName: String? = null,
) {
    val isFolder: Boolean get() = shortcut == null
}

data class WorkspaceLayout(
    val items: List<WorkspaceItem> = emptyList(),
    val widgetIds: List<Int> = emptyList(),
    val widgetSizes: Map<Int, WorkspaceWidgetSize> = emptyMap(),
    /** Top-left grid cell for each hosted widget, stored as row * columnCount + column. */
    val widgetCells: Map<Int, Int> = emptyMap(),
    /** Column count used to encode widgetCells, allowing positions to survive grid changes. */
    val widgetGridColumns: Int = 4,
    val widgetStacks: List<WorkspaceWidgetStack> = emptyList(),
)

data class WorkspaceWidgetSize(
    val widthDp: Int,
    val heightDp: Int,
)

enum class WorkspaceWidgetStackOrientation(val storageKey: String) {
    HORIZONTAL("horizontal"),
    VERTICAL("vertical");

    companion object {
        fun fromStorage(value: String?) = entries.firstOrNull { it.storageKey == value } ?: HORIZONTAL
    }
}

data class WorkspaceWidgetStack(
    /** Stable workspace identity, separate from Android's positive AppWidget IDs. */
    val stackId: Int,
    val widgetIds: List<Int> = emptyList(),
    val orientation: WorkspaceWidgetStackOrientation = WorkspaceWidgetStackOrientation.HORIZONTAL,
    val autoRotate: Boolean = false,
    val rotateIntervalSeconds: Int = 30,
    val resetOnReturn: Boolean = false,
    val currentIndex: Int = 0,
    val size: WorkspaceWidgetSize = WorkspaceWidgetSize(244, 150),
) {
    fun normalized(): WorkspaceWidgetStack {
        val members = widgetIds.filter { it > 0 }.distinct()
        return copy(
            widgetIds = members,
            rotateIntervalSeconds = rotateIntervalSeconds.coerceIn(5, 300),
            currentIndex = if (members.isEmpty()) 0 else currentIndex.coerceIn(0, members.lastIndex),
            size = WorkspaceWidgetSize(size.widthDp.coerceAtLeast(1), size.heightDp.coerceAtLeast(1)),
        )
    }
}
