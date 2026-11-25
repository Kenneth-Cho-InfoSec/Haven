package com.arcturus.haven.launcher.core.settings

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import androidx.annotation.ColorInt
import java.util.concurrent.Executors
import kotlin.math.pow

data class LauncherPalette(
    val isDark: Boolean,
    @ColorInt val accent: Int,
    @ColorInt val onAccent: Int,
    @ColorInt val surface: Int,
    @ColorInt val surfaceVariant: Int,
    @ColorInt val textPrimary: Int,
    @ColorInt val textSecondary: Int,
)

object LauncherAppearance {
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Teres-wallpaper-colors").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun resolve(context: Context, wallpaperAccent: Int? = null): LauncherPalette {
        val preferences = context.launcherPrefs()
        val isDark = when (preferences.getString(LauncherSettings.KEY_THEME_MODE, MODE_SYSTEM)) {
            MODE_LIGHT -> false
            MODE_DARK -> true
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        val accent = when (preferences.getString(LauncherSettings.KEY_THEME_ACCENT, ACCENT_WALLPAPER)) {
            ACCENT_TEAL -> DEFAULT_ACCENT
            ACCENT_BLUE -> 0xFF5267C9.toInt()
            ACCENT_VIOLET -> 0xFF8259B7.toInt()
            ACCENT_CORAL -> 0xFFC65E52.toInt()
            else -> wallpaperAccent ?: DEFAULT_ACCENT
        }
        val onAccent = if (contrastRatio(accent, Color.WHITE) >= contrastRatio(accent, INK_DARK)) Color.WHITE else INK_DARK
        return LauncherPalette(
            isDark = isDark,
            accent = accent,
            onAccent = onAccent,
            surface = if (isDark) 0xFF12191B.toInt() else 0xFFF5F7F6.toInt(),
            surfaceVariant = if (isDark) 0xFF202A2C.toInt() else 0xFFFFFFFF.toInt(),
            textPrimary = if (isDark) 0xFFF1F5F4.toInt() else INK_DARK,
            textSecondary = if (isDark) 0xFFD0D9D7.toInt() else 0xFF4B5A58.toInt(),
        )
    }

    fun load(context: Context, onLoaded: (LauncherPalette) -> Unit) {
        val appContext = context.applicationContext
        worker.execute {
            val wallpaperAccent = if (
                appContext.launcherPrefs().getString(LauncherSettings.KEY_THEME_ACCENT, ACCENT_WALLPAPER) == ACCENT_WALLPAPER
            ) {
                runCatching {
                    WallpaperManager.getInstance(appContext)
                        .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                        ?.primaryColor
                        ?.toArgb()
                }.getOrNull()
            } else {
                null
            }
            val palette = resolve(appContext, wallpaperAccent)
            mainHandler.post { onLoaded(palette) }
        }
    }

    private fun contrastRatio(first: Int, second: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) normalized / 12.92 else ((normalized + 0.055) / 1.055).pow(2.4)
        }
        fun luminance(color: Int): Double =
            0.2126 * channel(Color.red(color)) + 0.7152 * channel(Color.green(color)) + 0.0722 * channel(Color.blue(color))
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"
    const val ACCENT_WALLPAPER = "wallpaper"
    const val ACCENT_TEAL = "teal"
    const val ACCENT_BLUE = "blue"
    const val ACCENT_VIOLET = "violet"
    const val ACCENT_CORAL = "coral"
    private const val DEFAULT_ACCENT = 0xFF4CACA5.toInt()
    private const val INK_DARK = 0xFF172021.toInt()
}
