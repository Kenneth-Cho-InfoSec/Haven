package com.arcturus.haven.launcher.core.data

import android.content.Context
import androidx.core.content.edit
import com.arcturus.haven.launcher.core.model.WorkspaceIconGesture
import com.arcturus.haven.launcher.core.model.WorkspaceIconGestureAction
import com.arcturus.haven.launcher.core.model.WorkspaceIconGestureAssignment
import com.arcturus.haven.launcher.core.model.WorkspaceIconSource
import com.arcturus.haven.launcher.core.model.WorkspaceItem
import com.arcturus.haven.launcher.core.model.WorkspaceLayout
import com.arcturus.haven.launcher.core.model.WorkspaceShortcut
import com.arcturus.haven.launcher.core.model.WorkspaceWidgetSize
import com.arcturus.haven.launcher.core.model.WorkspaceWidgetStack
import com.arcturus.haven.launcher.core.model.WorkspaceWidgetStackOrientation
import org.json.JSONArray
import org.json.JSONObject

class WorkspaceRepository(context: Context) {
    private val preferences = context.getSharedPreferences("teres_workspace", Context.MODE_PRIVATE)

    fun load(): WorkspaceLayout {
        val raw = preferences.getString(KEY_LAYOUT, null) ?: return WorkspaceLayout()
        return runCatching { decode(raw) }.getOrDefault(WorkspaceLayout())
    }

    /** Distinguishes a new install from a workspace the user intentionally cleared. */
    fun hasSavedLayout(): Boolean = preferences.contains(KEY_LAYOUT)

    fun save(layout: WorkspaceLayout) {
        preferences.edit { putString(KEY_LAYOUT, encode(layout)) }
    }

    /** Use for user-requested removals that must survive an immediate activity/process switch. */
    fun saveImmediately(layout: WorkspaceLayout): Boolean {
        preferences.edit(commit = true) { putString(KEY_LAYOUT, encode(layout)) }
        return true
    }

    fun encode(layout: WorkspaceLayout): String = JSONObject().apply {
        put("items", layout.items.toJsonArray { item ->
            JSONObject().apply {
                put("page", item.page)
                put("cell", item.cell)
                putIfNotNull("shortcut", item.shortcut?.toJson())
                put("folder", item.folder.toJsonArray { it.toJson() })
                if (item.isFolder) put("folderLabel", item.folderLabel)
                put("isFolder", item.isFolder)
                putIfNotNull("iconBackgroundColor", item.iconBackgroundColor)
                put("iconGestures", item.iconGestureAssignments.toJsonArray { assignment ->
                    JSONObject().apply {
                        put("gesture", assignment.gesture.storageKey)
                        put("action", assignment.action.storageKey)
                        putIfNotNull("targetPackage", assignment.targetPackage)
                        putIfNotNull("targetClass", assignment.targetClass)
                        putIfNotNull("targetLabel", assignment.targetLabel)
                        putIfNotNull("targetShortcutId", assignment.targetShortcutId)
                    }
                })
                if (item.iconSource != WorkspaceIconSource.DEFAULT.storageKey) put("iconSource", item.iconSource)
                putIfNotNull("iconUri", item.iconUri)
                putIfNotNull("iconPackPackageName", item.iconPackPackageName)
            }
        })
        put("widgets", layout.widgetIds.toJsonArray())
        put("widgetSizes", JSONObject().apply {
            layout.widgetSizes.forEach { (widgetId, size) ->
                put(widgetId.toString(), JSONObject().put("widthDp", size.widthDp).put("heightDp", size.heightDp))
            }
        })
        put("widgetCells", JSONObject().apply {
            layout.widgetCells.forEach { (widgetId, cell) -> put(widgetId.toString(), cell) }
        })
        put("widgetGridColumns", layout.widgetGridColumns)
        put("widgetStacks", layout.widgetStacks.toJsonArray { rawStack ->
            val stack = rawStack.normalized()
            JSONObject()
                .put("stackId", stack.stackId)
                .put("widgetIds", stack.widgetIds.toJsonArray())
                .put("orientation", stack.orientation.storageKey)
                .put("autoRotate", stack.autoRotate)
                .put("rotateIntervalSeconds", stack.rotateIntervalSeconds)
                .put("resetOnReturn", stack.resetOnReturn)
                .put("currentIndex", stack.currentIndex)
                .put("widthDp", stack.size.widthDp)
                .put("heightDp", stack.size.heightDp)
        })
    }.toString()

    fun decode(raw: String): WorkspaceLayout {
        val root = JSONObject(raw)
        val items = root.optJSONArray("items").mapObjects { item ->
            val shortcut = item.optJSONObject("shortcut")?.toShortcut()
            val folder = item.optJSONArray("folder").mapObjects { it.toShortcut() }
            val isFolder = item.optBoolean(
                "isFolder",
                shortcut == null && (folder.isNotEmpty() || item.has("folderLabel")),
            )
            if (shortcut == null && folder.isEmpty() && !isFolder) return@mapObjects null
            val assignments = if (item.has("iconGestures")) {
                item.optJSONArray("iconGestures").toGestureAssignments()
            } else {
                item.toLegacyGestureAssignment()?.let(::listOf).orEmpty()
            }
            WorkspaceItem(
                page = item.optInt("page", 0),
                cell = item.optInt("cell", 0),
                shortcut = shortcut,
                folder = folder,
                folderLabel = item.optString("folderLabel").ifBlank { "Folder" },
                iconBackgroundColor = if (item.has("iconBackgroundColor")) item.optInt("iconBackgroundColor") else null,
                iconGestureAssignments = assignments,
                iconSource = WorkspaceIconSource.fromStorage(item.optString("iconSource")).storageKey,
                iconUri = item.optBlankNull("iconUri"),
                iconPackPackageName = item.optBlankNull("iconPackPackageName"),
            )
        }
        val widgets = root.optJSONArray("widgets").toInts { it > 0 }
        val widgetSizes = buildMap {
            val source = root.optJSONObject("widgetSizes") ?: return@buildMap
            source.keys().asSequence().forEach { key ->
                val id = key.toIntOrNull()?.takeIf { it > 0 && it in widgets } ?: return@forEach
                val size = source.optJSONObject(key) ?: return@forEach
                val width = size.optInt("widthDp", 0)
                val height = size.optInt("heightDp", 0)
                if (width > 0 && height > 0) put(id, WorkspaceWidgetSize(width, height))
            }
        }
        val widgetCells = buildMap {
            val source = root.optJSONObject("widgetCells") ?: return@buildMap
            source.keys().asSequence().forEach { key ->
                val id = key.toIntOrNull()?.takeIf { it != 0 && (it < 0 || it in widgets) } ?: return@forEach
                val cell = source.optInt(key, -1)
                if (cell >= 0) put(id, cell)
            }
        }
        val widgetStacks = root.optJSONArray("widgetStacks").mapObjects { value ->
            val stackId = value.optInt("stackId", 0).takeIf { it < 0 } ?: return@mapObjects null
            WorkspaceWidgetStack(
                stackId = stackId,
                widgetIds = value.optJSONArray("widgetIds").toInts { it > 0 && it in widgets }.distinct(),
                orientation = WorkspaceWidgetStackOrientation.fromStorage(value.optString("orientation")),
                autoRotate = value.optBoolean("autoRotate", false),
                rotateIntervalSeconds = value.optInt("rotateIntervalSeconds", 30),
                resetOnReturn = value.optBoolean("resetOnReturn", false),
                currentIndex = value.optInt("currentIndex", 0),
                size = WorkspaceWidgetSize(value.optInt("widthDp", 244), value.optInt("heightDp", 150)),
            ).normalized()
        }.distinctBy(WorkspaceWidgetStack::stackId)
        return WorkspaceLayout(
            items = items,
            widgetIds = widgets,
            widgetSizes = widgetSizes,
            widgetCells = widgetCells,
            widgetGridColumns = root.optInt("widgetGridColumns", 4).coerceIn(1, 6),
            widgetStacks = widgetStacks,
        )
    }

    private fun WorkspaceShortcut.toJson() = JSONObject().apply {
        put("package", packageName)
        put("class", className)
        put("label", label)
        if (profile != WorkspaceShortcut.PROFILE_PERSONAL) put("profile", profile)
        if (actionType != WorkspaceShortcut.ACTION_LAUNCH) put("actionType", actionType)
        if (linkedPackages.isNotEmpty()) put("linkedPackages", JSONArray(linkedPackages))
    }

    private fun JSONObject.toShortcut(): WorkspaceShortcut? {
        val pkg = optString("package")
        val cls = optString("class")
        val action = optString("actionType", WorkspaceShortcut.ACTION_LAUNCH)
            .takeIf { it in VALID_ACTIONS } ?: WorkspaceShortcut.ACTION_LAUNCH
        val profile = optString("profile", WorkspaceShortcut.PROFILE_PERSONAL)
            .takeIf { it in VALID_PROFILES } ?: WorkspaceShortcut.PROFILE_PERSONAL
        val linkedArray = optJSONArray("linkedPackages")
        val linked = if (linkedArray != null) {
            List(linkedArray.length(), linkedArray::optString).filter(String::isNotBlank)
        } else {
            emptyList()
        }
        return WorkspaceShortcut(
            packageName = pkg,
            className = cls,
            label = optString("label").ifBlank { pkg },
            profile = profile,
            actionType = action,
            linkedPackages = linked,
        ).takeIf { it.packageName.isNotBlank() && (it.className.isNotBlank() || it.actionType != WorkspaceShortcut.ACTION_LAUNCH) }
    }

    private fun JSONArray?.toGestureAssignments(): List<WorkspaceIconGestureAssignment> = mapObjects { value ->
        val gesture = WorkspaceIconGesture.fromStorage(value.optString("gesture"))
        val action = WorkspaceIconGestureAction.fromStorage(value.optString("action"))
        if (gesture == WorkspaceIconGesture.NONE || action == WorkspaceIconGestureAction.NONE) return@mapObjects null
        WorkspaceIconGestureAssignment(
            gesture = gesture,
            action = action,
            targetPackage = value.optBlankNull("targetPackage"),
            targetClass = value.optBlankNull("targetClass"),
            targetLabel = value.optBlankNull("targetLabel"),
            targetShortcutId = value.optBlankNull("targetShortcutId"),
        )
    }.distinctBy(WorkspaceIconGestureAssignment::gesture)

    private fun JSONObject.toLegacyGestureAssignment(): WorkspaceIconGestureAssignment? {
        val gesture = WorkspaceIconGesture.fromStorage(optString("iconSwipeGesture"))
        if (gesture == WorkspaceIconGesture.NONE) return null
        val action = if (has("iconGestureAction")) {
            WorkspaceIconGestureAction.fromStorage(optString("iconGestureAction"))
        } else {
            // Prior Teres versions treated a missing action as "open this app".
            WorkspaceIconGestureAction.OPEN_APP
        }
        if (action == WorkspaceIconGestureAction.NONE) return null
        return WorkspaceIconGestureAssignment(
            gesture = gesture,
            action = action,
            targetPackage = optBlankNull("iconGestureTargetPackage"),
            targetClass = optBlankNull("iconGestureTargetClass"),
            targetLabel = optBlankNull("iconGestureTargetLabel"),
        )
    }

    private inline fun <T> Iterable<T>.toJsonArray(transform: (T) -> Any = { it as Any }): JSONArray =
        JSONArray().apply { this@toJsonArray.forEach { put(transform(it)) } }

    private fun JSONObject.putIfNotNull(key: String, value: Any?) {
        if (value != null) put(key, value)
    }

    private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T?): List<T> {
        val source = this ?: return emptyList()
        return List(source.length()) { index -> source.optJSONObject(index)?.let(transform) }.filterNotNull()
    }

    private inline fun JSONArray?.toInts(predicate: (Int) -> Boolean): List<Int> {
        val source = this ?: return emptyList()
        return List(source.length()) { index -> source.optInt(index, -1) }.filter(predicate)
    }

    private fun JSONObject.optBlankNull(key: String): String? = optString(key).takeIf(String::isNotBlank)

    private companion object {
        const val KEY_LAYOUT = "layout_v1"
        val VALID_ACTIONS = setOf(
            WorkspaceShortcut.ACTION_LAUNCH,
            WorkspaceShortcut.ACTION_UNFREEZE_LAUNCH,
            WorkspaceShortcut.ACTION_FREEZE_ALL,
        )
        val VALID_PROFILES = setOf(WorkspaceShortcut.PROFILE_PERSONAL, WorkspaceShortcut.PROFILE_WORK)
    }
}
