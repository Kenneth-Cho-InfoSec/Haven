package com.arcturus.haven.launcher.launcher

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.view.Gravity
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.arcturus.haven.R
import com.arcturus.haven.launcher.core.settings.LauncherPalette
import com.arcturus.haven.launcher.core.settings.LauncherTypography
import com.arcturus.haven.launcher.core.ui.dp
import com.arcturus.haven.launcher.core.ui.roundedDrawable
import com.arcturus.haven.launcher.core.ui.showFullscreen

/** Full-screen, dismissible Smart-style popup surface for the launcher-owned Battery widget. */
class BatteryPopupDialog(
    private val activity: Activity,
    private val palette: LauncherPalette,
    private val percent: Int?,
) {
    fun show() {
        if (activity.isFinishing || activity.isDestroyed) return
        val dialog = Dialog(activity, R.style.Theme_Teres_FullscreenDialog).apply {
            setCancelable(true)
            setCanceledOnTouchOutside(true)
        }
        val card = FrameLayout(activity).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Battery popup${percent?.let { ", $it percent" } ?: ""}. Press Back to dismiss."
            background = roundedDrawable(
                ColorUtils.blendARGB(0xFF3F465A.toInt(), palette.accent, 0.22f),
                activity.dp(32),
            )
            translationY = activity.dp(32).toFloat()
            addView(TextView(activity).apply {
                text = percent?.let { "$it%" } ?: "—%"
                textSize = 68f
                setTextColor(Color.WHITE)
                typeface = LauncherTypography.typeface(activity, 600)
                gravity = Gravity.BOTTOM or Gravity.START
                includeFontPadding = false
                contentDescription = percent?.let { "Battery level $it percent" } ?: "Battery level unavailable"
                setPadding(activity.dp(18), 0, activity.dp(18), activity.dp(14))
            }, FrameLayout.LayoutParams(-1, -1))
        }
        val root = FrameLayout(activity).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Battery popup. Tap outside or press Back to dismiss."
            setBackgroundColor(0xA8000000.toInt())
            setOnClickListener { dialog.dismiss() }
            val metrics = activity.resources.displayMetrics
            addView(
                card,
                FrameLayout.LayoutParams(
                    (metrics.widthPixels - activity.dp(64)).coerceAtLeast(activity.dp(240)),
                    (metrics.heightPixels * 0.70f).toInt(),
                    Gravity.CENTER,
                ),
            )
        }
        dialog.setContentView(root)
        dialog.window?.setDimAmount(0f)
        dialog.showFullscreen(Color.TRANSPARENT, lightSystemBars = false)
        card.post {
            card.alpha = 0f
            card.scaleX = 0.94f
            card.scaleY = 0.94f
            card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(360)
                .setInterpolator(OvershootInterpolator(0.75f))
                .start()
        }
    }
}
