package com.arcturus.haven.launcher.widgets

import android.appwidget.AppWidgetManager
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Process
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doOnTextChanged
import com.arcturus.haven.R
import com.arcturus.haven.launcher.core.data.IconGroupLayout
import com.arcturus.haven.launcher.core.settings.LauncherAppearance
import com.arcturus.haven.launcher.core.settings.LauncherPalette
import com.arcturus.haven.launcher.core.ui.dp
import com.arcturus.haven.launcher.core.ui.roundedDrawable
import com.arcturus.haven.launcher.core.ui.selectableBackground
import com.arcturus.haven.launcher.core.ui.toast
import com.arcturus.haven.launcher.launcher.CircularAppIconDrawable

/** Configuration Activity for the launcher-owned 4x4 Icon group AppWidget. */
class IconGroupWidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var palette: LauncherPalette
    private val dark: Boolean get() = palette.isDark
    private var selectedLayout = IconGroupLayout.FLOWER
    private val selectedComponents = linkedSetOf<String>()
    private var launchables: List<LauncherActivityInfo> = emptyList()
    private lateinit var rows: LinearLayout
    private lateinit var selectionCount: TextView
    private lateinit var addButton: Button
    private lateinit var layoutButtons: Map<IconGroupLayout, TextView>
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        palette = LauncherAppearance.resolve(this)
        selectedComponents.addAll(IconGroupWidgetProvider.selectedComponents(this, appWidgetId))
        selectedLayout = IconGroupWidgetProvider.selectedLayout(this, appWidgetId)
        launchables = runCatching {
            getSystemService(LauncherApps::class.java)
                .getActivityList(null, Process.myUserHandle())
                .sortedBy { it.label.toString().lowercase() }
        }.getOrDefault(emptyList())
        if (launchables.isEmpty()) {
            Toast.makeText(this, "No launchable apps are available", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val screen = buildScreen()
        setContentView(screen)
        WindowInsetsControllerCompat(window, screen).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@IconGroupWidgetConfigActivity).apply {
                text = "‹"
                textSize = 30f
                gravity = Gravity.CENTER
                setTextColor(primaryTextColor)
                contentDescription = "Back"
                isClickable = true
                isFocusable = true
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(48), dp(52)))
            addView(label(getString(R.string.icon_group_config_title), 26f, primaryTextColor, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(header)
        root.addView(label(getString(R.string.icon_group_config_subtitle), 14f, secondaryTextColor).apply {
            setPadding(dp(4), dp(4), dp(4), dp(12))
        })

        selectionCount = label("", 13f, primaryTextColor, bold = true)
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(selectionCount, LinearLayout.LayoutParams(0, -2, 1f))
            addView(label(getString(R.string.icon_group_config_layout), 13f, secondaryTextColor))
        }, LinearLayout.LayoutParams(-1, dp(32)))

        val styles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        layoutButtons = IconGroupLayout.entries.associateWith { layout ->
            TextView(this).apply {
                text = layout.title
                textSize = 11f
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(8), dp(4), dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedLayout = layout
                    updateLayoutButtons()
                }
                styles.addView(this, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    if (layout != IconGroupLayout.entries.first()) marginStart = dp(5)
                })
            }
        }
        root.addView(styles, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(4) })
        updateLayoutButtons()

        val search = EditText(this).apply {
            hint = "Search apps"
            textSize = 14f
            isSingleLine = true
            setTextColor(primaryTextColor)
            setHintTextColor(secondaryTextColor)
            background = roundedDrawable(if (dark) 0xFF30343C.toInt() else 0xFFF0F1F5.toInt(), dp(26))
            setPadding(dp(16), 0, dp(16), 0)
            doOnTextChanged { s, _, _, _ ->
                query = s?.toString().orEmpty().trim()
                renderRows()
            }
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })

        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(6), 0, dp(8))
            addView(rows)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        addButton = Button(this).apply {
            text = getString(R.string.icon_group_config_add_to_home)
            textSize = 15f
            setTextColor(Color.WHITE)
            background = roundedDrawable(palette.accent, dp(28))
            isAllCaps = false
            setOnClickListener { finishConfiguration() }
        }
        root.addView(addButton, LinearLayout.LayoutParams(-1, dp(54)).apply {
            topMargin = dp(8)
            bottomMargin = dp(4)
        })
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(dp(20), dp(12) + bars.top, dp(20), dp(12) + maxOf(bars.bottom, ime.bottom))
            insets
        }
        renderRows()
        return root
    }

    private fun renderRows() {
        rows.removeAllViews()
        launchables.asSequence()
            .filter { it.label.toString().contains(query, ignoreCase = true) }
            .forEach { app ->
                val component = app.componentName.flattenToString()
                val check = CheckBox(this).apply {
                    isChecked = component in selectedComponents
                    buttonTintList = ColorStateList.valueOf(palette.accent)
                    setOnCheckedChangeListener { _, checked ->
                        if (checked && selectedComponents.size >= IconGroupLayout.MAX_APPS) {
                            isChecked = false
                            toast("Icon groups can contain up to 16 apps")
                        } else {
                            if (checked) selectedComponents.add(component) else selectedComponents.remove(component)
                        }
                        updateSelectionCount()
                    }
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    isClickable = true
                    isFocusable = true
                    background = selectableBackground()
                    addView(ImageView(this@IconGroupWidgetConfigActivity).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(runCatching { CircularAppIconDrawable(app.getIcon(0), palette.accent) }.getOrNull())
                        contentDescription = app.label.toString()
                    }, LinearLayout.LayoutParams(dp(44), dp(44)))
                    addView(label(app.label.toString(), 15f, primaryTextColor).apply {
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(dp(14), 0, dp(8), 0)
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(check, LinearLayout.LayoutParams(dp(48), dp(48)))
                    setOnClickListener { check.isChecked = !check.isChecked }
                }
                rows.addView(row, LinearLayout.LayoutParams(-1, dp(60)))
            }
        updateSelectionCount()
    }

    private fun updateSelectionCount() {
        selectionCount.text = resources.getQuantityString(
            R.plurals.icon_group_config_selected_count,
            selectedComponents.size,
            selectedComponents.size,
            IconGroupLayout.MAX_APPS,
        )
        addButton.isEnabled = selectedComponents.isNotEmpty()
        addButton.alpha = if (addButton.isEnabled) 1f else 0.55f
    }

    private fun updateLayoutButtons() {
        layoutButtons.forEach { (layout, button) ->
            button.setTextColor(if (layout == selectedLayout) Color.WHITE else secondaryTextColor)
            button.background = roundedDrawable(
                if (layout == selectedLayout) palette.accent else if (dark) 0xFF30343C.toInt() else 0xFFF0F1F5.toInt(),
                dp(18),
            )
        }
    }

    private fun finishConfiguration() {
        if (selectedComponents.isEmpty()) {
            toast("Choose at least one app")
            return
        }
        IconGroupWidgetProvider.saveConfiguration(this, appWidgetId, selectedComponents.toList(), selectedLayout)
        IconGroupWidgetProvider.updateWidget(this, appWidgetId)
        setResult(RESULT_OK, intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    private fun label(value: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private val surfaceColor: Int get() = if (dark) 0xFF20242A.toInt() else 0xFFFAF9FF.toInt()
    private val primaryTextColor: Int get() = if (dark) 0xFFF3F2F8.toInt() else 0xFF191B20.toInt()
    private val secondaryTextColor: Int get() = if (dark) 0xFFBDC1CC.toInt() else 0xFF5F626B.toInt()
}
