package com.arcturus.haven.launcher.core.ui

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.arcturus.haven.launcher.core.settings.LauncherSettings
import com.arcturus.haven.launcher.core.settings.launcherPrefs
import kotlin.math.roundToInt

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun roundedDrawable(color: Int, radiusPx: Int): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radiusPx.toFloat()
}

internal fun Context.selectableBackground(fallbackColor: Int = Color.TRANSPARENT): Drawable {
    val typed = TypedValue()
    theme.resolveAttribute(android.R.attr.selectableItemBackground, typed, true)
    return if (typed.resourceId != 0) {
        AppCompatResources.getDrawable(this, typed.resourceId) ?: fallbackColor.toDrawable()
    } else {
        fallbackColor.toDrawable()
    }
}

internal fun Context.toast(message: CharSequence) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

internal fun Dialog.showFullscreen(backgroundColor: Int, lightSystemBars: Boolean) {
    window?.apply {
        setBackgroundDrawable(backgroundColor.toDrawable())
        addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        WindowCompat.setDecorFitsSystemWindows(this, false)
    }
    show()
    window?.apply {
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        WindowCompat.setDecorFitsSystemWindows(this, false)
        WindowInsetsControllerCompat(this, decorView).apply {
            isAppearanceLightStatusBars = lightSystemBars
            isAppearanceLightNavigationBars = lightSystemBars
        }
    }
}

internal fun Context.chooseClockPosition(onSaved: () -> Unit = {}) {
    val offsets = intArrayOf(-30, 0, 30)
    val labels = arrayOf("Higher", "Center", "Lower")
    val prefs = launcherPrefs()
    val selected = offsets.indexOf(prefs.getInt(LauncherSettings.KEY_CLOCK_VERTICAL_OFFSET, 0)).coerceAtLeast(0)
    AlertDialog.Builder(this)
        .setTitle("Clock position")
        .setSingleChoiceItems(labels, selected) { dialog, which ->
            prefs.edit { putInt(LauncherSettings.KEY_CLOCK_VERTICAL_OFFSET, offsets[which]) }
            dialog.dismiss()
            onSaved()
        }
        .setNegativeButton("Cancel", null)
        .show()
}
