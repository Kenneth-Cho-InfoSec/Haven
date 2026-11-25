package com.arcturus.haven.launcher.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isEmpty
import com.arcturus.haven.R
import com.arcturus.haven.launcher.core.settings.LauncherPalette
import com.arcturus.haven.launcher.core.ui.dp
import com.arcturus.haven.launcher.core.ui.roundedDrawable
import com.arcturus.haven.launcher.core.ui.selectableBackground
import com.arcturus.haven.launcher.settings.SettingsGlyphView

data class ShortcutPopupItem(
    val label: String,
    val icon: Drawable?,
    val onClick: () -> Unit,
)

/** Small anchored menu for the app and its Android-published deep shortcuts. */
class AppShortcutPopup(
    private val activity: Activity,
    private val palette: LauncherPalette,
    private val anchor: View,
    private val title: String,
    private val appIcon: Drawable,
    private val onManage: () -> Unit,
    private val onUninstall: (() -> Unit)?,
) {
    private val panel = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedDrawable(
            if (palette.isDark) 0xF20C1011.toInt() else 0xF2F8FAF9.toInt(),
            activity.dp(24),
        ).apply {
            setStroke(activity.dp(1), if (palette.isDark) 0x553C484A else 0x554C5C59)
        }
        clipToOutline = true
        val pad = activity.dp(8)
        setPadding(pad, pad, pad, pad)
    }
    private val shortcutRows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val popup = PopupWindow(
        panel,
        minOf(activity.dp(260), activity.resources.displayMetrics.widthPixels - activity.dp(24)),
        ViewGroup.LayoutParams.WRAP_CONTENT,
        true,
    ).apply {
        isOutsideTouchable = true
        setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        elevation = activity.dp(12).toFloat()
        animationStyle = android.R.style.Animation_Dialog
    }

    fun show() {
        panel.addView(buildHeader(), LinearLayout.LayoutParams(-1, activity.dp(48)))
        panel.addView(
            ScrollView(activity).apply {
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(shortcutRows, ViewGroup.LayoutParams(-1, -2))
            },
            LinearLayout.LayoutParams(-1, -2),
        )
        onUninstall?.let { uninstall ->
            panel.addView(
                View(activity).apply {
                    setBackgroundColor(if (palette.isDark) 0x554C5C59 else 0x334C5C59)
                },
                LinearLayout.LayoutParams(-1, activity.dp(1)).apply { topMargin = activity.dp(6) },
            )
            panel.addView(buildUninstallAction(uninstall), LinearLayout.LayoutParams(-1, activity.dp(48)))
        }
        val (x, y) = measureAnchoredPosition()
        popup.showAtLocation(anchor, Gravity.TOP or Gravity.START, x, y)
    }

    fun updateShortcuts(shortcuts: List<ShortcutPopupItem>) {
        if (panel.isEmpty()) return
        shortcutRows.removeAllViews()
        shortcuts.forEach { shortcutRows.addView(shortcutRow(it), LinearLayout.LayoutParams(-1, activity.dp(46))) }
        panel.requestLayout()
        panel.post {
            if (!popup.isShowing) return@post
            val (x, y) = measureAnchoredPosition()
            popup.update(x, y, popup.width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun measureAnchoredPosition(): Pair<Int, Int> {
        val screen = activity.resources.displayMetrics
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(popup.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screen.heightPixels, View.MeasureSpec.AT_MOST),
        )
        val position = IntArray(2).also(anchor::getLocationOnScreen)
        val margin = activity.dp(12)
        val x = (position[0] + anchor.width / 2 - popup.width / 2).coerceIn(margin, screen.widthPixels - popup.width - margin)
        val below = position[1] + anchor.height + activity.dp(8)
        val y = if (below + panel.measuredHeight <= screen.heightPixels - activity.dp(32)) {
            below
        } else {
            (position[1] - panel.measuredHeight - activity.dp(8)).coerceAtLeast(activity.dp(28))
        }
        return x to y
    }

    private fun buildHeader(): View = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(activity.dp(8), 0, activity.dp(2), 0)
        addView(ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(appIcon)
            contentDescription = title
        }, LinearLayout.LayoutParams(activity.dp(32), activity.dp(32)))
        addView(singleLineLabel(title, 16f, palette.textPrimary).apply {
            setPadding(activity.dp(12), 0, activity.dp(8), 0)
        }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(FrameLayout(activity).apply {
            contentDescription = "App options"
            isFocusable = true
            background = activity.selectableBackground(0x18FFFFFF)
            addView(
                SettingsGlyphView(activity, "settings", palette.textSecondary),
                FrameLayout.LayoutParams(activity.dp(22), activity.dp(22), Gravity.CENTER),
            )
            dismissOnClick(onManage)
        }, LinearLayout.LayoutParams(activity.dp(42), activity.dp(42)))
    }

    private fun shortcutRow(item: ShortcutPopupItem): View = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        background = activity.selectableBackground(0x18FFFFFF)
        val hPad = activity.dp(10)
        setPadding(hPad, 0, hPad, 0)
        item.icon?.let { icon ->
            addView(ImageView(activity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(icon)
                contentDescription = null
            }, LinearLayout.LayoutParams(activity.dp(30), activity.dp(30)))
        }
        addView(singleLineLabel(item.label, 15f, palette.textPrimary).apply {
            if (item.icon != null) setPadding(activity.dp(14), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -1, 1f))
        contentDescription = item.label
        dismissOnClick(item.onClick)
    }

    private fun buildUninstallAction(onUninstall: () -> Unit): View = singleLineLabel(
        activity.getString(R.string.uninstall_app),
        15f,
        if (palette.isDark) 0xFFFF8A80.toInt() else 0xFFB3261E.toInt(),
    ).apply {
        setPadding(activity.dp(18), 0, activity.dp(10), 0)
        isFocusable = true
        background = activity.selectableBackground(0x18FFFFFF)
        contentDescription = "Uninstall $title"
        dismissOnClick(onUninstall)
    }

    private fun singleLineLabel(value: String, sizeSp: Float, color: Int): TextView = TextView(activity).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun View.dismissOnClick(action: () -> Unit) = setOnClickListener {
        popup.dismiss()
        action()
    }
}
