package com.arcturus.haven.launcher.launcher

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.arcturus.haven.R
import com.arcturus.haven.launcher.core.settings.LauncherPalette
import com.arcturus.haven.launcher.core.settings.LauncherTypography
import com.arcturus.haven.launcher.core.ui.dp
import com.arcturus.haven.launcher.core.ui.roundedDrawable
import com.arcturus.haven.launcher.settings.SettingsGlyphView
import kotlin.math.abs

/** Compact workspace actions opened by a long press on empty home space. */
class QuickSettingsSheet(
    context: Context,
    private val palette: LauncherPalette,
    private val isLocked: Boolean,
    private val canRemovePage: Boolean,
    private val actions: Actions,
    private val isolationEnabled: Boolean = true,
) : Dialog(context, R.style.Theme_Teres_FullscreenDialog) {

    data class Actions(
        val onShowAllSettings: () -> Unit,
        val onToggleLock: () -> Unit,
        val onNotifications: () -> Unit,
        val onAddIcon: () -> Unit,
        val onAddFolder: () -> Unit,
        val onAddWidget: () -> Unit,
        val onAddPage: () -> Unit,
        val onRemovePage: () -> Unit,
        val onOpenVault: () -> Unit,
        val onFreezeAll: () -> Unit,
        val onWallpaper: () -> Unit,
        val onAppearance: () -> Unit,
        val onHomeSettings: () -> Unit,
        val onIsolationSettings: () -> Unit,
    )

    private val panel = if (palette.isDark) 0xFF111719.toInt() else Color.WHITE
    private val rowSurface = if (palette.isDark) 0xFF202A2C.toInt() else 0xFFF0F2F5.toInt()
    private val iconAccent = if (palette.isDark) ColorUtils.blendARGB(palette.accent, Color.WHITE, 0.55f) else 0xFF0B72F6.toInt()
    private lateinit var sheetContent: View
    private var openingAllSettings = false

    init {
        setContentView(buildSheet())
        setCanceledOnTouchOutside(true)
        window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.56f)
            setGravity(Gravity.BOTTOM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) isNavigationBarContrastEnforced = false
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowInsetsControllerCompat(this, decorView).isAppearanceLightNavigationBars = !palette.isDark
        }
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun buildSheet(): View {
        val gestureRoot = SwipeUpFrameLayout(context) { animateToAllSettings() }
        val shell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(0, context.dp(8), 0, context.dp(6))
        }
        sheetContent = shell

        val allSettings = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(context.dp(18), 0, context.dp(18), 0)
            minimumHeight = context.dp(48)
            isFocusable = true
            contentDescription = "Show all launcher settings"
            val foregroundColor = if (palette.isDark) palette.textPrimary else Color.WHITE
            background = rippleBackground(context.dp(24), if (palette.isDark) rowSurface else Color.BLACK)
            setOnClickListener { animateToAllSettings() }
            addView(SettingsGlyphView(context, "up", foregroundColor), LinearLayout.LayoutParams(context.dp(18), context.dp(18)).apply {
                marginEnd = context.dp(10)
            })
            addView(TextView(context).apply {
                text = context.getString(R.string.quick_settings_show_all)
                textSize = 14f
                setTextColor(foregroundColor)
                setTypeface(typeface, Typeface.BOLD)
            })
        }
        shell.addView(allSettings, LinearLayout.LayoutParams(-2, context.dp(48)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = context.dp(14)
        })

        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = topRounded(panel, context.dp(28))
            clipToOutline = true
            setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(12), 0, context.dp(4), 0)
            minimumHeight = context.dp(66)
        }
        header.addView(TextView(context).apply {
            text = context.getString(R.string.quick_settings_home_screen)
            textSize = 20f
            setTextColor(palette.textPrimary)
            typeface = LauncherTypography.typeface(context, 500)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(FrameLayout(context).apply {
            background = roundedDrawable(rowSurface, context.dp(28))
            contentDescription = if (isLocked) "Unlock home screen" else "Lock home screen"
            isFocusable = true
            addView(
                SettingsGlyphView(context, if (isLocked) "lock" else "unlock", palette.textPrimary),
                FrameLayout.LayoutParams(context.dp(24), context.dp(24), Gravity.CENTER),
            )
            setOnClickListener { dismiss(); actions.onToggleLock() }
        }, LinearLayout.LayoutParams(context.dp(48), context.dp(48)))
        sheet.addView(header)

        val notificationCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedDrawable(
                if (palette.isDark) ColorUtils.setAlphaComponent(iconAccent, 0x2D) else 0xFFE5EFFF.toInt(),
                context.dp(14),
            )
            setPadding(context.dp(14), context.dp(8), context.dp(12), context.dp(8))
            minimumHeight = context.dp(74)
            isFocusable = true
            contentDescription = "Highlight apps that have active notifications"
            setOnClickListener { dismiss(); actions.onNotifications() }
            addView(
                SettingsGlyphView(context, "notifications", iconAccent),
                LinearLayout.LayoutParams(context.dp(26), context.dp(26)).apply { marginEnd = context.dp(14) },
            )
            addView(TextView(context).apply {
                text = context.getString(R.string.quick_settings_highlight_notifications)
                textSize = 14f
                setTextColor(if (palette.isDark) palette.textPrimary else iconAccent)
                typeface = LauncherTypography.typeface(context, 500)
                maxLines = 2
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(
                SettingsGlyphView(context, "next", iconAccent),
                LinearLayout.LayoutParams(context.dp(22), context.dp(22)).apply { marginStart = context.dp(8) },
            )
        }
        sheet.addView(notificationCard, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = context.dp(4)
            bottomMargin = context.dp(6)
        })

        val actionStrip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOfNotNull(
            Triple("add", "Add icon", actions.onAddIcon),
            Triple("folder_add", "Add folder", actions.onAddFolder),
            Triple("widget", "Add widget", actions.onAddWidget),
            if (isolationEnabled) Triple("security", "Haven Vault", actions.onOpenVault) else null,
            if (isolationEnabled) Triple("lock", "Freeze all", actions.onFreezeAll) else null,
            Triple("page_add", "Add page", actions.onAddPage),
        ).forEach { (glyph, label, action) -> actionStrip.addView(actionTile(glyph, label, action)) }
        actionStrip.addView(actionTile("remove", "Remove page", actions.onRemovePage).apply {
            isEnabled = canRemovePage
            alpha = if (canRemovePage) 1f else 0.42f
        })
        sheet.addView(
            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
                setPadding(0, context.dp(5), 0, context.dp(5))
                addView(actionStrip)
            },
            LinearLayout.LayoutParams(-1, context.dp(102)).apply {
                marginStart = -context.dp(6)
                marginEnd = -context.dp(6)
            },
        )

        sheet.addView(
            View(context).apply { setBackgroundColor(if (palette.isDark) 0xFF354144.toInt() else 0xFFDDE1E5.toInt()) },
            LinearLayout.LayoutParams(-1, context.dp(1)).apply {
                topMargin = context.dp(4)
                bottomMargin = context.dp(4)
            },
        )

        sheet.addView(menuRow("wallpaper", "Wallpaper", 0xFF21A957.toInt(), actions.onWallpaper))
        sheet.addView(menuRow("appearance", "Global appearance", 0xFFF17A16.toInt(), actions.onAppearance))
        sheet.addView(menuRow("home", "Home screen", 0xFF1769E0.toInt(), actions.onHomeSettings))
        if (isolationEnabled) {
            sheet.addView(menuRow("security", "Work Profile & Isolation", 0xFF00897B.toInt(), actions.onIsolationSettings))
        }

        shell.addView(sheet, LinearLayout.LayoutParams(-1, -2))
        gestureRoot.addView(shell, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        return gestureRoot
    }

    private fun animateToAllSettings() {
        if (openingAllSettings) return
        openingAllSettings = true
        sheetContent.animate()
            .translationY(-context.dp(56).toFloat())
            .alpha(0f)
            .setDuration(190L)
            .setInterpolator(DecelerateInterpolator(2f))
            .withEndAction { dismiss(); actions.onShowAllSettings() }
            .start()
    }

    private fun actionTile(glyph: String, label: String, action: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        minimumHeight = context.dp(90)
        val pad = context.dp(4)
        setPadding(pad, pad, pad, pad)
        isFocusable = true
        contentDescription = label
        background = rippleBackground()
        addView(SettingsGlyphView(context, glyph, iconAccent), LinearLayout.LayoutParams(context.dp(25), context.dp(25)))
        addView(TextView(context).apply {
            text = label
            textSize = 11f
            setTextColor(palette.textPrimary)
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, context.dp(8), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        setOnClickListener { dismiss(); action() }
    }.also { it.layoutParams = LinearLayout.LayoutParams(context.dp(88), context.dp(94)) }

    private fun menuRow(glyph: String, label: String, lightColor: Int, action: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(54)
        setPadding(context.dp(5), context.dp(4), context.dp(6), context.dp(4))
        isFocusable = true
        contentDescription = label
        background = rippleBackground()
        val resolvedTint = if (palette.isDark) iconAccent else lightColor
        addView(SettingsGlyphView(context, glyph, resolvedTint), LinearLayout.LayoutParams(context.dp(25), context.dp(25)).apply {
            marginEnd = context.dp(28)
        })
        addView(TextView(context).apply {
            text = label
            textSize = 15f
            setTextColor(palette.textPrimary)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        setOnClickListener { dismiss(); action() }
    }

    private fun topRounded(color: Int, radius: Int) = GradientDrawable().apply {
        val r = radius.toFloat()
        setColor(color)
        cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
    }

    private fun rippleBackground(radius: Int = context.dp(12), fillColor: Int? = null): Drawable = RippleDrawable(
        ColorStateList.valueOf(if (palette.isDark) 0x33FFFFFF else 0x22000000),
        fillColor?.let { roundedDrawable(it, radius) } ?: Color.TRANSPARENT.toDrawable(),
        roundedDrawable(Color.WHITE, radius),
    )
}

private class SwipeUpFrameLayout @JvmOverloads constructor(
    context: Context,
    private val onSwipeUp: () -> Unit = {},
) : FrameLayout(context) {
    private val thresholdPx = (72f * resources.displayMetrics.density).toInt()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var triggered = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                triggered = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val upwardDistance = downY - event.rawY
                if (!triggered && upwardDistance >= thresholdPx && upwardDistance > maxOf(touchSlop.toFloat(), abs(dx) * 1.2f)) {
                    triggered = true
                    onSwipeUp()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> triggered = false
        }
        return false
    }
}
