package com.arcturus.haven.launcher.core.model

/** Per-shortcut artwork source shown in the Smart-style Edit icon screen. */
enum class WorkspaceIconSource(val storageKey: String, val label: String) {
    DEFAULT("default", "Default"),
    ADAPTIVE("adaptive", "Adaptive icons"),
    NOT_ADAPTIVE("not_adaptive", "Not adaptive"),
    TERES("teres", "Haven"),
    GALLERY("gallery", "From gallery"),
    ICON_PACK("icon_pack", "From icon pack");

    companion object {
        fun fromStorage(value: String?): WorkspaceIconSource =
            entries.firstOrNull { it.storageKey == value } ?: DEFAULT
    }
}
