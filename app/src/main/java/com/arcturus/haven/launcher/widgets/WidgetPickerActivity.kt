package com.arcturus.haven.launcher.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
import com.arcturus.haven.launcher.launcher.CircularAppIconDrawable
import java.util.Locale

/** Smart-style catalog over public Android AppWidget providers plus Teres-owned widgets. */
class WidgetPickerActivity : ComponentActivity() {
    private data class PreviewApp(val label: String, val icon: Drawable)

    private lateinit var palette: LauncherPalette
    private val dark: Boolean get() = palette.isDark
    private var query = ""
    private val expandedGroups = mutableSetOf<String>()
    private lateinit var content: LinearLayout
    private lateinit var providers: List<AppWidgetProviderInfo>
    private val stackTargetPicker: Boolean by lazy { intent.getBooleanExtra(EXTRA_STACK_TARGET_PICKER, false) }
    private var previewApps = emptyList<PreviewApp>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        palette = LauncherAppearance.resolve(this)
        providers = runCatching { WidgetHostController.loadProviders(this) }.getOrDefault(emptyList())
        previewApps = queryPreviewApps()
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
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@WidgetPickerActivity).apply {
                text = "‹"
                textSize = 30f
                gravity = Gravity.CENTER
                setTextColor(primaryTextColor)
                contentDescription = "Back"
                isClickable = true
                isFocusable = true
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(48), dp(56)))
            addView(label(if (stackTargetPicker) "Add to stack" else "Widgets", 27f, primaryTextColor, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(header)

        val search = EditText(this).apply {
            hint = "Search widgets"
            isSingleLine = true
            textSize = 14f
            setTextColor(primaryTextColor)
            setHintTextColor(secondaryTextColor)
            background = roundedDrawable(if (dark) 0xFF30343C.toInt() else 0xFFF0F1F5.toInt(), dp(28))
            setPadding(dp(16), 0, dp(16), 0)
            doOnTextChanged { s, _, _, _ ->
                query = s?.toString().orEmpty().trim()
                renderContent()
            }
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8); bottomMargin = dp(14) })

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(20), dp(8) + bars.top, dp(20), dp(8) + bars.bottom)
            insets
        }
        renderContent()
        return root
    }

    private fun renderContent() {
        content.removeAllViews()
        val ownWidgets = providers.filter { it.provider.packageName == packageName }
        val iconGroup = ownWidgets.firstOrNull { it.provider.className == IconGroupWidgetProvider::class.java.name }
        val batteryWidget = ownWidgets.firstOrNull { it.provider.className == BatteryWidgetProvider::class.java.name }
        val vaultWidget = ownWidgets.firstOrNull { it.provider.className == VaultControlWidgetProvider::class.java.name }
        if (query.isBlank() && !stackTargetPicker) {
            addSectionTitle("Haven widgets", "Launcher & isolation widgets made for Home")
            val cards = buildList {
                iconGroup?.let { add(iconGroupCard(it)) }
                add(widgetStackCard())
                batteryWidget?.let { add(batteryWidgetCard(it)) }
                vaultWidget?.let { add(vaultControlWidgetCard(it)) }
            }
            val grid = GridLayout(this).apply {
                columnCount = 2
                alignmentMode = GridLayout.ALIGN_BOUNDS
                useDefaultMargins = false
                cards.forEachIndexed { index, card ->
                    addView(card, GridLayout.LayoutParams(
                        GridLayout.spec(index / 2, 1, GridLayout.FILL),
                        GridLayout.spec(index % 2, 1, GridLayout.FILL, 1f),
                    ).apply {
                        width = 0
                        height = -2
                        setMargins(dp(4), dp(4), dp(4), dp(10))
                    })
                }
            }
            content.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        } else if (query.isBlank()) {
            addSectionTitle("Available widgets", "Choose a widget to add to this stack")
        }

        val matchingOwnWidgets = if (stackTargetPicker || query.isNotBlank()) ownWidgets.filter { it.matchesQuery() } else emptyList()
        if (matchingOwnWidgets.isNotEmpty()) {
            addSectionTitle("Haven widgets", "Launcher widgets made for Home")
            matchingOwnWidgets.forEach { content.addView(widgetRow(it), LinearLayout.LayoutParams(-1, dp(68))) }
        }

        val others = providers.filter { it.provider.packageName != packageName && it.matchesQuery(includeProfile = true) }
        addSectionTitle("All widgets", "Widgets from apps installed on this device")
        val myUser = Process.myUserHandle()
        val groups = others.groupBy { "${it.provider.packageName}|${it.profile.hashCode()}" }
            .entries
            .map { (groupKey, packageWidgets) ->
                val sample = packageWidgets.first()
                val suffix = if (sample.profile != myUser) " (Work)" else ""
                Triple(groupKey, appLabel(sample.provider.packageName, sample.profile) + suffix, packageWidgets)
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
        if (groups.isEmpty() && matchingOwnWidgets.isEmpty()) {
            content.addView(label(
                if (query.isBlank()) "No Android widgets are installed" else "No widgets match your search",
                14f, secondaryTextColor,
            ).apply { setPadding(dp(4), dp(10), dp(4), dp(24)) })
        }
        groups.forEach { (groupKey, _, packageWidgets) ->
            val sample = packageWidgets.first()
            val expanded = query.isNotBlank() || groupKey in expandedGroups
            content.addView(
                providerGroupRow(groupKey, sample.provider.packageName, sample.profile, packageWidgets.size, expanded),
                LinearLayout.LayoutParams(-1, dp(68)),
            )
            if (expanded) packageWidgets.forEach { content.addView(widgetRow(it), LinearLayout.LayoutParams(-1, dp(68))) }
        }
    }

    private fun AppWidgetProviderInfo.matchesQuery(includeProfile: Boolean = false): Boolean =
        query.isBlank() ||
            loadLabel(packageManager).toString().contains(query, true) ||
            appLabel(provider.packageName, if (includeProfile) profile else Process.myUserHandle()).contains(query, true)

    private fun addSectionTitle(title: String, subtitle: String) {
        content.addView(label(title, 19f, primaryTextColor, bold = true).apply { setPadding(dp(4), dp(6), dp(4), dp(2)) })
        content.addView(label(subtitle, 13f, secondaryTextColor).apply { setPadding(dp(4), 0, dp(4), dp(10)) })
    }

    private fun showcaseCard(
        title: String,
        summary: String,
        description: String,
        outerPadDp: Int,
        previewRadiusDp: Int,
        titleSizeSp: Float,
        summarySizeSp: Float,
        textHorizontalPadDp: Int,
        textTopPadDp: Int,
        clipPreviewChildren: Boolean = true,
        onClick: () -> Unit,
        buildPreview: FrameLayout.() -> Unit,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = outlineCard()
        val p = dp(outerPadDp)
        setPadding(p, p, p, p)
        isClickable = true
        isFocusable = true
        contentDescription = description
        setOnClickListener { onClick() }
        val previewFrame = FrameLayout(this@WidgetPickerActivity).apply {
            background = roundedDrawable(if (dark) 0xFF252A31.toInt() else 0xFFE9EBF2.toInt(), dp(previewRadiusDp))
            clipChildren = clipPreviewChildren
            buildPreview()
        }
        addView(previewFrame, LinearLayout.LayoutParams(-1, dp(158)))
        val hPad = dp(textHorizontalPadDp)
        addView(label(title, titleSizeSp, primaryTextColor, bold = true).apply { setPadding(hPad, dp(textTopPadDp), hPad, 0) })
        addView(label(summary, summarySizeSp, secondaryTextColor).apply { setPadding(hPad, dp(2), hPad, 0) })
    }

    private fun iconGroupCard(info: AppWidgetProviderInfo): View = showcaseCard(
        title = info.loadLabel(packageManager).toString(),
        summary = getString(R.string.widget_picker_icon_group_summary),
        description = "Icon group, 4 by 4 app widget, ${previewApps.size} app Flower preview",
        outerPadDp = 14, previewRadiusDp = 22, titleSizeSp = 17f, summarySizeSp = 12f,
        textHorizontalPadDp = 4, textTopPadDp = 10, onClick = { choose(info) },
    ) {
        val flowerCells = IconGroupLayout.FLOWER.cellOrder()
        val flowerApps = arrayOfNulls<PreviewApp>(IconGroupLayout.CELL_COUNT)
        previewApps.forEachIndexed { index, app -> if (index < 6) flowerApps[flowerCells[index]] = app }
        val preview = LinearLayout(this@WidgetPickerActivity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            val pad = dp(8)
            setPadding(pad, pad, pad, pad)
        }
        repeat(IconGroupLayout.ROW_COUNT) { rowIndex ->
            val row = LinearLayout(this@WidgetPickerActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            repeat(IconGroupLayout.COLUMN_COUNT) { columnIndex ->
                val cell = FrameLayout(this@WidgetPickerActivity).apply {
                    clipChildren = false
                    clipToPadding = false
                }
                flowerApps[rowIndex * IconGroupLayout.COLUMN_COUNT + columnIndex]?.let { app ->
                    cell.addView(ImageView(this@WidgetPickerActivity).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(CircularAppIconDrawable(app.icon, palette.accent))
                        contentDescription = app.label
                    }, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
                }
                row.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
            }
            preview.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        addView(preview, FrameLayout.LayoutParams(-1, dp(158)))
    }

    /** Uses the narrow launcher-activity query declared in the manifest for this gallery preview. */
    private fun queryPreviewApps(): List<PreviewApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val results = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
        }.getOrDefault(emptyList())

        return results.asSequence()
            .filter { it.activityInfo != null && it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            .take(6)
            .mapNotNull { info ->
                runCatching { PreviewApp(info.loadLabel(packageManager).toString(), info.loadIcon(packageManager)) }.getOrNull()
            }
            .toList()
    }

    private fun widgetStackCard(): View = showcaseCard(
        title = getString(R.string.widget_picker_stack_title),
        summary = getString(R.string.widget_picker_stack_summary),
        description = "Widget Stack, combine widgets and swipe between them",
        outerPadDp = 14, previewRadiusDp = 22, titleSizeSp = 17f, summarySizeSp = 12f,
        textHorizontalPadDp = 4, textTopPadDp = 10, clipPreviewChildren = false, onClick = ::chooseStack,
    ) {
        addView(label(getString(R.string.widget_picker_calendar_sample), 13f, primaryTextColor).apply {
            gravity = Gravity.CENTER
            background = roundedDrawable(if (dark) 0xFF3A414D.toInt() else Color.WHITE, dp(18))
            elevation = dp(2).toFloat()
        }, FrameLayout.LayoutParams(dp(110), dp(68), Gravity.CENTER).apply { leftMargin = dp(34); topMargin = dp(22) })
        addView(label(getString(R.string.widget_picker_clock_sample), 22f, primaryTextColor, bold = true).apply {
            gravity = Gravity.CENTER
            background = roundedDrawable(if (dark) 0xFF303945.toInt() else Color.WHITE, dp(18))
            elevation = dp(5).toFloat()
        }, FrameLayout.LayoutParams(dp(116), dp(74), Gravity.CENTER).apply { rightMargin = dp(32); bottomMargin = dp(18) })
    }

    private fun batteryWidgetCard(info: AppWidgetProviderInfo): View = showcaseCard(
        title = info.loadLabel(packageManager).toString(),
        summary = getString(R.string.widget_picker_battery_sizes),
        description = "Battery widget, responsive landscape and portrait sizes",
        outerPadDp = 10, previewRadiusDp = 18, titleSizeSp = 16f, summarySizeSp = 11f,
        textHorizontalPadDp = 2, textTopPadDp = 8, onClick = { choose(info) },
    ) {
        val gauge = FrameLayout(this@WidgetPickerActivity).apply {
            addView(ImageView(this@WidgetPickerActivity).apply {
                setImageBitmap(BatteryWidgetProvider.renderGaugeBitmap(this@WidgetPickerActivity, 50, 200, 100))
                scaleType = ImageView.ScaleType.FIT_XY
                contentDescription = null
            }, FrameLayout.LayoutParams(-1, -1))
            addView(label(getString(R.string.widget_picker_sample_battery_percent), 22f, 0xFFC6FFE7.toInt(), bold = true).apply {
                gravity = Gravity.CENTER
                contentDescription = null
            }, FrameLayout.LayoutParams(-1, -1))
        }
        addView(gauge, FrameLayout.LayoutParams(-1, dp(74), Gravity.CENTER))
    }

    private fun vaultControlWidgetCard(info: AppWidgetProviderInfo): View = showcaseCard(
        title = getString(R.string.widget_vault_title),
        summary = getString(R.string.widget_picker_vault_summary),
        description = "Haven Vault widget, quick vault open and batch freeze",
        outerPadDp = 10, previewRadiusDp = 18, titleSizeSp = 16f, summarySizeSp = 11f,
        textHorizontalPadDp = 2, textTopPadDp = 8, onClick = { choose(info) },
    ) {
        val pad = dp(10)
        setPadding(pad, pad, pad, pad)
        val pill = LinearLayout(this@WidgetPickerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedDrawable(0xFF182226.toInt(), dp(20))
            setPadding(pad, pad, pad, pad)
            addView(ImageView(this@WidgetPickerActivity).apply {
                setImageResource(R.drawable.ic_vault_shield)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(8) })
            addView(label(getString(R.string.widget_vault_open_button), 13f, Color.WHITE, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(label(getString(R.string.widget_vault_freeze_button), 11f, Color.WHITE, bold = true).apply {
                background = roundedDrawable(palette.accent, dp(12))
                setPadding(dp(8), dp(4), dp(8), dp(4))
            })
        }
        addView(pill, FrameLayout.LayoutParams(-1, dp(56), Gravity.CENTER))
    }

    private fun providerGroupRow(
        groupKey: String,
        providerPackage: String,
        profile: UserHandle,
        count: Int,
        expanded: Boolean,
    ): View {
        val isWork = profile != Process.myUserHandle()
        val labelText = appLabel(providerPackage, profile)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            isClickable = true
            isFocusable = true
            background = selectableBackground()
            val baseIcon = runCatching {
                if (isWork) {
                    getSystemService(LauncherApps::class.java)
                        ?.getActivityList(providerPackage, profile)
                        ?.firstOrNull()
                        ?.getBadgedIcon(resources.displayMetrics.densityDpi)
                } else {
                    packageManager.getApplicationIcon(providerPackage)
                }
            }.getOrNull() ?: runCatching { packageManager.getApplicationIcon(providerPackage) }.getOrNull()
            addView(ImageView(this@WidgetPickerActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(baseIcon)
                contentDescription = labelText
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(LinearLayout(this@WidgetPickerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, dp(8), 0)
                addView(label(labelText, 15f, primaryTextColor))
                if (isWork) addView(label(getString(R.string.widget_picker_work_profile_label), 11f, palette.accent))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(label(String.format(Locale.getDefault(), "%d", count), 13f, Color.WHITE, bold = true).apply {
                gravity = Gravity.CENTER
                background = roundedDrawable(palette.accent, dp(18))
            }, LinearLayout.LayoutParams(dp(34), dp(30)))
            setOnClickListener {
                if (expanded) expandedGroups.remove(groupKey) else expandedGroups.add(groupKey)
                renderContent()
            }
        }
    }

    private fun widgetRow(info: AppWidgetProviderInfo): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(52), dp(6), dp(8), dp(6))
        isClickable = true
        isFocusable = true
        background = selectableBackground()
        contentDescription = "${info.loadLabel(packageManager)}, ${appLabel(info.provider.packageName, info.profile)}"
        setOnClickListener { choose(info) }
        addView(ImageView(this@WidgetPickerActivity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(
                runCatching { info.loadIcon(this@WidgetPickerActivity, resources.displayMetrics.densityDpi) }.getOrNull()
                    ?: runCatching { packageManager.getApplicationIcon(info.provider.packageName) }.getOrNull(),
            )
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        addView(label(info.loadLabel(packageManager).toString(), 14f, primaryTextColor).apply {
            setPadding(dp(14), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun choose(info: AppWidgetProviderInfo) {
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_PROVIDER, info.provider.flattenToString())
                .putExtra(EXTRA_PROVIDER_USER, info.profile),
        )
        finish()
    }

    private fun chooseStack() {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_CREATE_STACK, true))
        finish()
    }

    private fun appLabel(providerPackage: String, profile: UserHandle = Process.myUserHandle()): String {
        if (profile != Process.myUserHandle()) {
            val fromLauncherApps = runCatching {
                getSystemService(LauncherApps::class.java)
                    ?.getActivityList(providerPackage, profile)
                    ?.firstOrNull()
                    ?.applicationInfo
                    ?.let { packageManager.getApplicationLabel(it).toString() }
            }.getOrNull()
            if (!fromLauncherApps.isNullOrBlank()) return fromLauncherApps
        }
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(providerPackage, 0)).toString()
        }.getOrDefault(providerPackage)
    }

    private fun label(value: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun outlineCard() = roundedDrawable(if (dark) 0xFF282D35.toInt() else Color.WHITE, dp(24)).apply {
        setStroke(dp(1), if (dark) 0xFF414650.toInt() else 0xFFE0E2E8.toInt())
    }

    private val surfaceColor: Int get() = if (dark) 0xFF20242A.toInt() else 0xFFFAF9FF.toInt()
    private val primaryTextColor: Int get() = if (dark) 0xFFF3F2F8.toInt() else 0xFF191B20.toInt()
    private val secondaryTextColor: Int get() = if (dark) 0xFFBDC1CC.toInt() else 0xFF5F626B.toInt()

    companion object {
        const val EXTRA_PROVIDER = "com.arcturus.haven.launcher.extra.WIDGET_PROVIDER"
        const val EXTRA_PROVIDER_USER = "com.arcturus.haven.launcher.extra.WIDGET_PROVIDER_USER"
        const val EXTRA_CREATE_STACK = "com.arcturus.haven.launcher.extra.CREATE_WIDGET_STACK"
        const val EXTRA_STACK_TARGET_PICKER = "com.arcturus.haven.launcher.extra.STACK_TARGET_PICKER"
    }
}
