package com.arcturus.haven.launcher.launcher

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isNotEmpty
import androidx.core.widget.doOnTextChanged
import com.arcturus.haven.R
import com.arcturus.haven.core.HavenModePreferences
import com.arcturus.haven.launcher.core.model.WorkspaceGestureActionGroup
import com.arcturus.haven.launcher.core.model.WorkspaceIconGesture
import com.arcturus.haven.launcher.core.model.WorkspaceIconGestureAction
import com.arcturus.haven.launcher.core.model.WorkspaceIconGestureAssignment
import com.arcturus.haven.launcher.core.settings.LauncherPalette
import com.arcturus.haven.launcher.core.settings.LauncherTypography
import com.arcturus.haven.launcher.core.ui.dp
import com.arcturus.haven.launcher.core.ui.roundedDrawable
import com.arcturus.haven.launcher.core.ui.selectableBackground
import com.arcturus.haven.launcher.core.ui.showFullscreen
import com.arcturus.haven.launcher.settings.SettingsGlyphView

/** Smart-style five-row gesture sheet; each row opens its own action catalog. */
class WorkspaceGestureEditorDialog(
    private val activity: Activity,
    private val palette: LauncherPalette,
    private val iconLabel: String,
    initialAssignments: List<WorkspaceIconGestureAssignment>,
    private val onChooseAction: (WorkspaceIconGesture, WorkspaceIconGestureAssignment?) -> Unit,
    private val onDismiss: () -> Unit = {},
) {
    private val background = if (palette.isDark) 0xFF151B1D.toInt() else Color.WHITE
    private val rowSurface = if (palette.isDark) 0xFF20282A.toInt() else 0xFFF3F5F8.toInt()
    private val lineColor = if (palette.isDark) 0xFF303A3C.toInt() else 0xFFE0E5EC.toInt()
    private var assignments = initialAssignments
    private lateinit var rows: LinearLayout
    private val dialog = Dialog(activity)

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(buildContent())
        dialog.setOnDismissListener { onDismiss() }
        dialog.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setDimAmount(0.42f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.BOTTOM)
            setWindowAnimations(android.R.style.Animation_InputMethod)
        }
        dialog.show()
        dialog.window?.setLayout(activity.resources.displayMetrics.widthPixels - activity.dp(32), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    fun refresh(updatedAssignments: List<WorkspaceIconGestureAssignment>) {
        assignments = updatedAssignments
        if (::rows.isInitialized) renderRows()
    }

    private fun buildContent() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedDrawable(this@WorkspaceGestureEditorDialog.background, activity.dp(28))
        clipToOutline = true
        setPadding(activity.dp(24), activity.dp(22), activity.dp(24), activity.dp(20))
        addView(TextView(activity).apply {
            text = activity.getString(R.string.gesture_sheet_title)
            textSize = 25f
            setTextColor(palette.textPrimary)
            typeface = LauncherTypography.typeface(activity, 600)
            contentDescription = "$iconLabel gestures"
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = activity.dp(20) })
        rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroller = ScrollView(activity).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            background = roundedDrawable(rowSurface, activity.dp(24))
            clipToOutline = true
            addView(rows, ViewGroup.LayoutParams(-1, -2))
        }
        addView(scroller, LinearLayout.LayoutParams(-1, -2).apply {
            height = (activity.resources.displayMetrics.heightPixels * 0.5f).toInt().coerceAtMost(activity.dp(520))
        })
        renderRows()
    }

    private fun renderRows() {
        rows.removeAllViews()
        WorkspaceIconGesture.assignable.forEachIndexed { index, gesture ->
            val assignment = assignments.firstOrNull { it.gesture == gesture }
            rows.addView(gestureRow(gesture, assignment), LinearLayout.LayoutParams(-1, activity.dp(87)))
            if (index < WorkspaceIconGesture.assignable.lastIndex) {
                rows.addView(View(activity).apply { setBackgroundColor(lineColor) }, LinearLayout.LayoutParams(-1, activity.dp(1)))
            }
        }
    }

    private fun gestureRow(
        gesture: WorkspaceIconGesture,
        assignment: WorkspaceIconGestureAssignment?,
    ) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        setPadding(activity.dp(20), 0, activity.dp(18), 0)
        background = activity.selectableBackground()
        val description = assignment?.let { actionSummary(it) } ?: "Not assigned"
        contentDescription = "${gesture.label}, $description. Choose action"
        addView(
            SettingsGlyphView(activity, gestureGlyph(gesture), palette.textPrimary),
            LinearLayout.LayoutParams(activity.dp(38), activity.dp(38)).apply { marginEnd = activity.dp(20) },
        )
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(activity).apply {
                text = gesture.label
                textSize = 17f
                setTextColor(palette.textPrimary)
                typeface = LauncherTypography.typeface(activity, 500)
                maxLines = 1
            })
            addView(TextView(activity).apply {
                text = description
                textSize = 14f
                setTextColor(palette.textSecondary)
                typeface = LauncherTypography.typeface(activity, 400)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, activity.dp(4), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(TextView(activity).apply {
            text = "›"
            textSize = 30f
            setTextColor(palette.textSecondary)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(activity.dp(28), -1))
        setOnClickListener { onChooseAction(gesture, assignment) }
    }

    private fun actionSummary(assignment: WorkspaceIconGestureAssignment): String = when (assignment.action) {
        WorkspaceIconGestureAction.OPEN_APP -> "Open $iconLabel"
        WorkspaceIconGestureAction.POPUP_WIDGET -> "${assignment.targetLabel ?: "Battery"} popup"
        WorkspaceIconGestureAction.LAUNCH_APP,
        WorkspaceIconGestureAction.LAUNCH_SHORTCUT,
        -> assignment.targetLabel ?: assignment.action.label
        else -> assignment.action.label
    }

    private fun gestureGlyph(gesture: WorkspaceIconGesture): String = when (gesture) {
        WorkspaceIconGesture.SWIPE_UP -> "swipe_up"
        WorkspaceIconGesture.SWIPE_DOWN -> "swipe_down"
        WorkspaceIconGesture.SWIPE_LEFT -> "swipe_left"
        WorkspaceIconGesture.SWIPE_RIGHT -> "swipe_right"
        WorkspaceIconGesture.DOUBLE_TAP -> "double_tap"
        WorkspaceIconGesture.NONE -> "gesture"
    }
}

/** Full-page Smart-style searchable action catalog with independent Suggested, Actions, Apps and Shortcuts tabs. */
class WorkspaceGestureActionPickerDialog(
    private val activity: Activity,
    private val palette: LauncherPalette,
    private val catalog: LauncherCatalog,
    private val gesture: WorkspaceIconGesture,
    appsInitiallyLoaded: List<LaunchableApp>,
    private val current: WorkspaceIconGestureAssignment?,
    private val onSelect: (WorkspaceIconGestureAssignment) -> Unit,
    private val gestureLabel: String = gesture.label,
    private val workspaceGesture: Boolean = false,
) {
    private val background = if (palette.isDark) 0xFF101617.toInt() else Color.WHITE
    private val rowSurface = if (palette.isDark) 0xFF20282A.toInt() else 0xFFF3F5F8.toInt()
    private val tabs = if (workspaceGesture) listOf("Actions", "Apps", "Shortcuts") else listOf("Suggested", "Actions", "Apps", "Shortcuts")
    private var activeTab = current?.let { tabFor(it.action) }?.takeIf { it in tabs }
        ?: if (workspaceGesture) "Actions" else "Suggested"
    private var query = ""
    private var showingPopupWidgets = false
    private var apps = appsInitiallyLoaded
    private var shortcuts: List<SearchableShortcut> = emptyList()
    private var shortcutsLoaded = false
    private lateinit var list: LinearLayout
    private lateinit var tabRow: LinearLayout
    private lateinit var dialog: Dialog

    fun show() {
        dialog = Dialog(activity, R.style.Theme_Teres_FullscreenDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(buildContent())
        dialog.window?.setDimAmount(0f)
        dialog.showFullscreen(background, lightSystemBars = !palette.isDark)
        if (apps.isEmpty()) {
            catalog.loadAsync { loaded ->
                if (!activity.isFinishing && dialog.isShowing) {
                    apps = loaded
                    renderList()
                }
            }
        }
        if (activeTab == "Shortcuts") loadShortcuts()
    }

    private fun buildContent() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(this@WorkspaceGestureActionPickerDialog.background)
        setPadding(activity.dp(24), activity.dp(34), activity.dp(24), activity.dp(18))

        addView(TextView(activity).apply {
            text = "‹"
            textSize = 38f
            gravity = Gravity.CENTER
            setTextColor(palette.textPrimary)
            contentDescription = "Back to gesture settings"
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)).apply { bottomMargin = activity.dp(20) })

        addView(styledText(activity.getString(R.string.gesture_assign_action_title, gestureLabel), 25f, palette.textPrimary, 600).apply {
            maxLines = 1
            contentDescription = "Assign action to $gestureLabel"
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = activity.dp(24) })

        val search = EditText(activity).apply {
            hint = "Search"
            setSingleLine(true)
            textSize = 17f
            setTextColor(palette.textPrimary)
            setHintTextColor(palette.textSecondary)
            typeface = LauncherTypography.typeface(activity, 400)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = roundedDrawable(rowSurface, activity.dp(32))
            setPadding(activity.dp(20), 0, activity.dp(20), 0)
            doOnTextChanged { text, _, _, _ ->
                query = text?.toString().orEmpty()
                renderList()
            }
        }
        addView(search, LinearLayout.LayoutParams(-1, activity.dp(56)).apply { bottomMargin = activity.dp(16) })

        tabRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(tabRow, ViewGroup.LayoutParams(-2, -1))
        }, LinearLayout.LayoutParams(-1, activity.dp(56)).apply { bottomMargin = activity.dp(24) })
        renderTabs()

        list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, activity.dp(20))
        }
        addView(ScrollView(activity).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(list, ViewGroup.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        renderList()
    }

    private fun renderTabs() {
        if (!::tabRow.isInitialized) return
        tabRow.removeAllViews()
        tabs.forEach { tab ->
            val selected = tab == activeTab
            tabRow.addView(styledText(tab, 15f, if (selected) palette.onAccent else palette.textPrimary, if (selected) 600 else 500).apply {
                gravity = Gravity.CENTER
                setPadding(activity.dp(14), 0, activity.dp(14), 0)
                background = roundedDrawable(if (selected) palette.accent else rowSurface, activity.dp(28))
                isFocusable = true
                isClickable = true
                contentDescription = "$tab tab${if (selected) ", selected" else ""}"
                setOnClickListener {
                    activeTab = tab
                    renderTabs()
                    renderList()
                    if (tab == "Shortcuts") loadShortcuts()
                }
            }, LinearLayout.LayoutParams(-2, -1).apply {
                if (tabRow.isNotEmpty()) marginStart = activity.dp(8)
            })
        }
    }

    private fun renderList() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val needle = query.trim().lowercase()
        if (showingPopupWidgets) {
            list.addView(styledText(activity.getString(R.string.gesture_back_widgets), 17f, palette.textPrimary, 500).apply {
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                contentDescription = "Back to suggested actions"
                setOnClickListener {
                    showingPopupWidgets = false
                    renderList()
                }
            }, LinearLayout.LayoutParams(-1, activity.dp(52)))
            section("AVAILABLE POPUP WIDGETS")
            if (needle.isEmpty() || "battery".contains(needle)) {
                catalogRow("Battery", "Show battery level on this icon gesture", glyph = "widgets") {
                    select(WorkspaceIconGestureAssignment(gesture, WorkspaceIconGestureAction.POPUP_WIDGET, targetLabel = "Battery"))
                }
            } else {
                emptyState("No matching popup widgets")
            }
            return
        }
        when (activeTab) {
            "Suggested" -> {
                section("SUGGESTED")
                catalogRow("None", "", glyph = "close") { select(WorkspaceIconGestureAction.NONE) }
                if (!workspaceGesture) {
                    catalogRow("Open this app", "", glyph = "apps") { select(WorkspaceIconGestureAction.OPEN_APP) }
                }
                catalogRow("Popup widget", "Choose a widget to show on this gesture", glyph = "widgets") {
                    showingPopupWidgets = true
                    renderList()
                }
            }
            "Actions" -> {
                val isolationEnabled = HavenModePreferences.isIsolationEnabled(activity)
                listOf(
                    WorkspaceGestureActionGroup.SYSTEM to "SYSTEM",
                    WorkspaceGestureActionGroup.NAVIGATION to "NAVIGATION",
                ).forEach { (group, title) ->
                    val actions = WorkspaceIconGestureAction.entries.filter {
                        it.group == group &&
                            (needle.isEmpty() || it.label.lowercase().contains(needle)) &&
                            (isolationEnabled || it !in VAULT_ONLY_GESTURE_ACTIONS)
                    }
                    if (actions.isNotEmpty()) {
                        section(title)
                        actions.forEach { action -> catalogRow(action.label, "", glyph = actionGlyph(action)) { select(action) } }
                    }
                }
            }
            "Apps" -> {
                section("ALL APPS")
                val filtered = apps.filter { needle.isEmpty() || it.entry.label.lowercase().contains(needle) }
                if (filtered.isEmpty()) emptyState("No matching apps")
                filtered.forEach { app ->
                    catalogRow(app.entry.label, app.entry.packageName, app.icon) {
                        select(WorkspaceIconGestureAssignment(
                            gesture = gesture,
                            action = WorkspaceIconGestureAction.LAUNCH_APP,
                            targetPackage = app.entry.packageName,
                            targetClass = app.entry.className,
                            targetLabel = app.entry.label,
                        ))
                    }
                }
            }
            "Shortcuts" -> renderShortcuts(needle)
        }
    }

    private fun renderShortcuts(needle: String) {
        section("APP SHORTCUTS")
        if (!catalog.hasShortcutHostPermission()) {
            infoState("Android only exposes published shortcuts to the selected Home app. Select Teres Launcher as Home to browse shortcuts.")
            return
        }
        if (!shortcutsLoaded) {
            emptyState("Loading shortcuts…")
            return
        }
        val filtered = shortcuts.filter {
            needle.isEmpty() || it.label.lowercase().contains(needle) || it.appLabel.lowercase().contains(needle)
        }
        if (filtered.isEmpty()) emptyState("No matching shortcuts")
        filtered.forEach { item ->
            val shortcut = item.shortcut
            val activityClass = shortcut.activity?.className
                ?: apps.firstOrNull { it.entry.packageName == shortcut.`package` }?.entry?.className
                ?: return@forEach
            catalogRow(item.label, item.appLabel, item.icon) {
                select(WorkspaceIconGestureAssignment(
                    gesture = gesture,
                    action = WorkspaceIconGestureAction.LAUNCH_SHORTCUT,
                    targetPackage = shortcut.`package`,
                    targetClass = activityClass,
                    targetLabel = item.label,
                    targetShortcutId = shortcut.id,
                ))
            }
        }
    }

    private fun loadShortcuts() {
        if (shortcutsLoaded || !catalog.hasShortcutHostPermission()) return
        catalog.loadSearchShortcutsAsync("") { loaded ->
            if (!activity.isFinishing && ::dialog.isInitialized && dialog.isShowing) {
                shortcuts = loaded
                shortcutsLoaded = true
                renderList()
            }
        }
    }

    private fun select(action: WorkspaceIconGestureAction) = select(WorkspaceIconGestureAssignment(gesture, action))

    private fun select(assignment: WorkspaceIconGestureAssignment) {
        onSelect(assignment)
        dialog.dismiss()
    }

    private fun section(title: String) {
        list.addView(styledText(title, 13f, palette.textSecondary, 500).apply {
            letterSpacing = 0.08f
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-1, activity.dp(54)).apply { topMargin = activity.dp(4) })
    }

    private fun catalogRow(
        title: String,
        subtitle: String,
        icon: Drawable? = null,
        glyph: String = "apps",
        onClick: () -> Unit,
    ) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, activity.dp(8), 0, activity.dp(8))
            isFocusable = true
            isClickable = true
            background = activity.selectableBackground()
            contentDescription = listOfNotNull(title, subtitle.takeIf(String::isNotBlank)).joinToString(". ")
            setOnClickListener { onClick() }
            if (icon != null) {
                addView(ImageView(activity).apply {
                    setImageDrawable(icon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                }, LinearLayout.LayoutParams(activity.dp(56), activity.dp(56)).apply { marginEnd = activity.dp(18) })
            } else {
                addView(FrameLayout(activity).apply {
                    background = roundedDrawable(rowSurface, activity.dp(14))
                    addView(SettingsGlyphView(activity, glyph, palette.textPrimary), FrameLayout.LayoutParams(activity.dp(32), activity.dp(32), Gravity.CENTER))
                }, LinearLayout.LayoutParams(activity.dp(56), activity.dp(56)).apply { marginEnd = activity.dp(18) })
            }
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(styledText(title, 17f, palette.textPrimary, 500).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                if (subtitle.isNotBlank()) addView(styledText(subtitle, 13f, palette.textSecondary, 400).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        list.addView(row, LinearLayout.LayoutParams(-1, activity.dp(72)))
    }

    private fun emptyState(message: String) {
        list.addView(styledText(message, 15f, palette.textSecondary, 400).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(4), activity.dp(12), activity.dp(4), activity.dp(12))
        }, LinearLayout.LayoutParams(-1, activity.dp(64)))
    }

    private fun infoState(message: String) {
        list.addView(styledText(message, 15f, palette.textSecondary, 400).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = roundedDrawable(rowSurface, activity.dp(18))
            setPadding(activity.dp(18), activity.dp(14), activity.dp(18), activity.dp(14))
            contentDescription = message
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun styledText(value: String, sizeSp: Float, color: Int, weight: Int) = TextView(activity).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        typeface = LauncherTypography.typeface(activity, weight)
    }

    private fun actionGlyph(action: WorkspaceIconGestureAction): String = when (action) {
        WorkspaceIconGestureAction.NONE -> "close"
        WorkspaceIconGestureAction.POPUP_WIDGET -> "widgets"
        WorkspaceIconGestureAction.OPEN_APP,
        WorkspaceIconGestureAction.APP_DRAWER,
        WorkspaceIconGestureAction.APP_INDEX,
        WorkspaceIconGestureAction.LAUNCH_APP,
        -> "apps"
        WorkspaceIconGestureAction.TOGGLE_DOCK -> "home"
        WorkspaceIconGestureAction.SEARCH -> "search"
        WorkspaceIconGestureAction.ASSISTANT -> "assistant"
        WorkspaceIconGestureAction.SETTINGS,
        WorkspaceIconGestureAction.QUICK_SETTINGS,
        -> "tune"
        WorkspaceIconGestureAction.NOTIFICATIONS -> "notifications"
        WorkspaceIconGestureAction.RECENTS,
        WorkspaceIconGestureAction.LAUNCH_SHORTCUT,
        -> "more"
        WorkspaceIconGestureAction.LOCK_SCREEN,
        WorkspaceIconGestureAction.OPEN_VAULT,
        -> "security"
        WorkspaceIconGestureAction.FREEZE_ALL_WORK,
        WorkspaceIconGestureAction.TOGGLE_WORK_PROFILE,
        -> "lock"
        WorkspaceIconGestureAction.NEXT_HOME_PAGE,
        WorkspaceIconGestureAction.PREVIOUS_HOME_PAGE,
        -> "pages"
    }

    private fun tabFor(action: WorkspaceIconGestureAction): String = when (action.group) {
        WorkspaceGestureActionGroup.SUGGESTED -> "Suggested"
        WorkspaceGestureActionGroup.SYSTEM, WorkspaceGestureActionGroup.NAVIGATION -> "Actions"
        WorkspaceGestureActionGroup.APPS -> "Apps"
        WorkspaceGestureActionGroup.SHORTCUTS -> "Shortcuts"
    }

    private companion object {
        val VAULT_ONLY_GESTURE_ACTIONS = setOf(
            WorkspaceIconGestureAction.OPEN_VAULT,
            WorkspaceIconGestureAction.FREEZE_ALL_WORK,
            WorkspaceIconGestureAction.TOGGLE_WORK_PROFILE,
        )
    }
}
