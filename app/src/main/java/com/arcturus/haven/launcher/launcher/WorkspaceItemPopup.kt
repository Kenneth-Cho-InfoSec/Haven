package com.arcturus.haven.launcher.launcher

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import com.arcturus.haven.launcher.core.settings.LauncherPalette
import com.arcturus.haven.launcher.core.settings.LauncherTypography
import com.arcturus.haven.launcher.core.ui.dp
import com.arcturus.haven.launcher.core.ui.roundedDrawable
import com.arcturus.haven.launcher.settings.SettingsGlyphView

data class WorkspacePopupAction(
    val label: String,
    val glyph: String,
    val subtitle: String? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** Anchored item actions used by app icons and launcher-owned widgets. */
class WorkspaceItemPopup(
    private val activity: Activity,
    private val palette: LauncherPalette,
    private val anchor: View,
    private val heading: String,
    private val subtitle: String? = null,
    private val artwork: Drawable? = null,
    private val actions: List<WorkspacePopupAction>,
    private val onInfo: (() -> Unit)? = null,
    private val onDismiss: (() -> Unit)? = null,
    private val anchorBottomOffsetDp: Int = 0,
) {
    private val panel = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedDrawable(palette.surfaceVariant, activity.dp(28)).apply {
            setStroke(activity.dp(1), ColorUtils.setAlphaComponent(palette.textSecondary, 0x24))
        }
        clipToOutline = true
        val pad = activity.dp(4)
        setPadding(pad, pad, pad, pad)
    }
    private val popup = PopupWindow(
        panel,
        minOf(activity.dp(220), activity.resources.displayMetrics.widthPixels - activity.dp(24)),
        ViewGroup.LayoutParams.WRAP_CONTENT,
        true,
    ).apply {
        isOutsideTouchable = true
        setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        elevation = activity.dp(6).toFloat()
        animationStyle = android.R.style.Animation_Dialog
        setOnDismissListener { onDismiss?.invoke() }
    }

    fun show(): WorkspaceItemPopup {
        panel.addView(buildHeader(), LinearLayout.LayoutParams(-1, activity.dp(if (subtitle == null) 48 else 52)))
        actions.forEachIndexed { index, action ->
            if (action.destructive && index > 0) {
                panel.addView(
                    View(activity).apply { setBackgroundColor(0x443C484A) },
                    LinearLayout.LayoutParams(-1, activity.dp(1)).apply {
                        topMargin = activity.dp(5)
                        bottomMargin = activity.dp(5)
                    },
                )
            }
            panel.addView(actionRow(action), LinearLayout.LayoutParams(-1, activity.dp(if (action.subtitle == null) 49 else 56)))
        }
        val position = IntArray(2).also(anchor::getLocationOnScreen)
        val screen = activity.resources.displayMetrics
        val margin = activity.dp(12)
        val x = (position[0] + anchor.width / 2 - popup.width / 2).coerceIn(margin, screen.widthPixels - popup.width - margin)
        val anchorTop = position[1]
        val anchorHeight = anchor.height + activity.dp(anchorBottomOffsetDp)
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(popup.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screen.heightPixels, View.MeasureSpec.AT_MOST),
        )
        val belowY = anchorTop + anchorHeight + activity.dp(4)
        val y = if (belowY + panel.measuredHeight <= screen.heightPixels - activity.dp(30)) {
            belowY
        } else {
            (anchorTop - panel.measuredHeight + activity.dp(4)).coerceAtLeast(activity.dp(28))
        }
        popup.showAtLocation(anchor, Gravity.TOP or Gravity.START, x, y)
        return this
    }

    fun dismiss() {
        if (popup.isShowing) popup.dismiss()
    }

    private fun buildHeader() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(activity.dp(10), 0, activity.dp(2), 0)
        artwork?.let { drawable ->
            addView(ImageView(activity).apply {
                setImageDrawable(drawable)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = heading
            }, LinearLayout.LayoutParams(activity.dp(32), activity.dp(32)).apply { marginEnd = activity.dp(10) })
        }
        addView(textColumn(heading, 14f, 700, palette.textPrimary, subtitle, 11f), LinearLayout.LayoutParams(0, -1, 1f))
        onInfo?.let { infoAction ->
            addView(LinearLayout(activity).apply {
                gravity = Gravity.CENTER
                isFocusable = true
                contentDescription = "Get details"
                background = rippleBackground()
                addView(
                    SettingsGlyphView(activity, "info", palette.textSecondary),
                    LinearLayout.LayoutParams(activity.dp(22), activity.dp(22)),
                )
                setOnClickListener { popup.dismiss(); infoAction() }
            }, LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)))
        }
    }

    private fun actionRow(action: WorkspacePopupAction) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(activity.dp(12), 0, activity.dp(12), 0)
        isFocusable = true
        isClickable = true
        contentDescription = listOfNotNull(action.label, action.subtitle).joinToString(". ")
        background = rippleBackground()
        val tint = if (action.destructive) {
            if (palette.isDark) 0xFFFFB4AB.toInt() else 0xFFB3261E.toInt()
        } else {
            palette.textPrimary
        }
        addView(
            SettingsGlyphView(activity, action.glyph, tint),
            LinearLayout.LayoutParams(activity.dp(23), activity.dp(23)).apply { marginEnd = activity.dp(18) },
        )
        addView(textColumn(action.label, 15f, 500, tint, action.subtitle, 12f), LinearLayout.LayoutParams(0, -2, 1f))
        setOnClickListener { popup.dismiss(); action.onClick() }
    }

    private fun textColumn(
        title: String,
        titleSizeSp: Float,
        titleWeight: Int,
        titleColor: Int,
        detail: String?,
        detailSizeSp: Float,
    ) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(activity).apply {
            text = title
            textSize = titleSizeSp
            setTextColor(titleColor)
            typeface = LauncherTypography.typeface(activity, titleWeight)
            maxLines = 1
        })
        detail?.let { value ->
            addView(TextView(activity).apply {
                text = value
                textSize = detailSizeSp
                setTextColor(palette.textSecondary)
                typeface = LauncherTypography.typeface(activity, 400)
                maxLines = 1
            })
        }
    }

    private fun rippleBackground(): Drawable = RippleDrawable(
        ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.accent, 0x38)),
        null,
        roundedDrawable(Color.WHITE, activity.dp(24)),
    )
}
