package com.arcturus.haven.launcher.core.settings

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import com.arcturus.haven.R
import java.util.concurrent.ConcurrentHashMap

/** Shared launcher typography for themes, dynamically-created labels, and previews. */
object LauncherTypography {
    const val DEFAULT_FAMILY = "poppins"
    const val FAMILY_SYSTEM = "system"
    const val FAMILY_SANS = "sans"
    const val FAMILY_SERIF = "serif"
    const val FAMILY_MONO = "mono"

    private data class FamilySpec(val label: String, val themeRes: Int, val base: (Context) -> Typeface)
    private data class TypefaceKey(val family: String, val weight: Int, val italic: Boolean)

    private val families = linkedMapOf(
        DEFAULT_FAMILY to FamilySpec("Poppins", R.style.TeresTypographyPoppins) { it.resources.getFont(R.font.poppins) },
        FAMILY_SYSTEM to FamilySpec("System default", R.style.TeresTypographySystem) { Typeface.DEFAULT },
        FAMILY_SANS to FamilySpec("Sans serif", R.style.TeresTypographySans) { Typeface.create("sans-serif", Typeface.NORMAL) },
        FAMILY_SERIF to FamilySpec("Serif", R.style.TeresTypographySerif) { Typeface.SERIF },
        FAMILY_MONO to FamilySpec("Monospace", R.style.TeresTypographyMono) { Typeface.MONOSPACE },
    )

    val selectableFamilies: List<String> = families.keys.toList()
    private val baseCache = ConcurrentHashMap<String, Typeface>()
    private val typefaceCache = ConcurrentHashMap<TypefaceKey, Typeface>()

    fun normalizeFamily(value: String?): String = if (value in families) value!! else DEFAULT_FAMILY

    fun selectedFamily(context: Context): String =
        normalizeFamily(context.launcherPrefs().getString(LauncherSettings.KEY_FONT_FAMILY, DEFAULT_FAMILY))

    fun labelFor(family: String): String = families.getValue(normalizeFamily(family)).label

    fun typeface(context: Context, weight: Int = 400, italic: Boolean = false): Typeface =
        typeface(context, selectedFamily(context), weight, italic)

    /** Typeface used to render a family option in the font picker preview. */
    fun typefaceForFamily(context: Context, family: String, weight: Int = 400, italic: Boolean = false): Typeface =
        typeface(context, family, weight, italic)

    fun applyTheme(activity: Activity) {
        activity.theme.applyStyle(families.getValue(selectedFamily(activity)).themeRes, true)
    }

    /** Reapply the selected face to views already alive when the user changes fonts. */
    fun applyToViewTree(context: Context, root: View) {
        val family = selectedFamily(context)
        fun visit(view: View) {
            // Hosted widgets own their typography; the launcher font applies to launcher UI only.
            if (view is AppWidgetHostView) return
            if (view is TextView) {
                val previous = view.typeface
                view.typeface = typeface(context, family, previous?.weight ?: 400, previous?.isItalic == true)
            }
            if (view is ViewGroup) view.children.forEach(::visit)
        }
        visit(root)
    }

    private fun typeface(context: Context, family: String, weight: Int, italic: Boolean): Typeface {
        val normalized = normalizeFamily(family)
        val key = TypefaceKey(normalized, weight.coerceIn(100, 900), italic)
        return typefaceCache.getOrPut(key) {
            val base = baseCache.getOrPut(normalized) { families.getValue(normalized).base(context) }
            Typeface.create(base, key.weight, key.italic)
        }
    }
}
