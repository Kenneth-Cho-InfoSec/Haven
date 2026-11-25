package com.arcturus.haven.launcher.launcher

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.arcturus.haven.R
import com.arcturus.haven.launcher.core.model.WorkspaceIconSource
import com.arcturus.haven.launcher.core.settings.LauncherTypography
import com.arcturus.haven.launcher.core.ui.dp
import com.arcturus.haven.launcher.core.ui.roundedDrawable
import com.arcturus.haven.launcher.core.ui.selectableBackground
import com.arcturus.haven.launcher.core.ui.showFullscreen

/** Full-screen per-shortcut source picker modeled after Smart Launcher's Edit icon page. */
class WorkspaceIconEditorDialog(
    private val activity: Activity,
    private val label: String,
    private val preview: Drawable,
    private val teresArtwork: Drawable,
    private val onSelect: (WorkspaceIconSource) -> Unit,
    private val onGallery: () -> Unit,
    private val onInstalledPacks: () -> Unit,
    private val onBackgroundColor: () -> Unit,
    private val onIconPackStudio: () -> Unit,
) {
    private val backgroundColor = Color.rgb(34, 37, 42)
    private val secondaryColor = Color.rgb(181, 183, 188)

    fun show() {
        val dialog = Dialog(activity, R.style.Theme_Teres_FullscreenDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(buildContent(dialog))
        dialog.window?.setDimAmount(0f)
        dialog.showFullscreen(backgroundColor, lightSystemBars = false)
    }

    private fun buildContent(dialog: Dialog) = ScrollView(activity).apply {
        isFillViewport = true
        clipToPadding = false
        setBackgroundColor(backgroundColor)
        fun dismissThen(action: () -> Unit): () -> Unit = {
            dialog.dismiss()
            action()
        }

        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(52), activity.dp(24), activity.dp(48))
            addView(
                styledLabel(activity.getString(R.string.icon_editor_title), 34f, Color.WHITE, 500).apply {
                    contentDescription = "Edit icon for $label"
                },
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = activity.dp(34) },
            )
            addView(sectionHeader("SUGGESTIONS"), LinearLayout.LayoutParams(-1, -2))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val options = listOf(
                    WorkspaceIconSource.DEFAULT to "Default",
                    WorkspaceIconSource.ADAPTIVE to "Adaptive icons",
                    WorkspaceIconSource.NOT_ADAPTIVE to "Not adaptive",
                    WorkspaceIconSource.TERES to "Haven",
                )
                options.forEachIndexed { index, (source, optionLabel) ->
                    addView(
                        suggestion(optionLabel, if (index == 3) teresArtwork else preview, dismissThen { onSelect(source) }),
                        LinearLayout.LayoutParams(0, activity.dp(124), 1f).apply {
                            if (index > 0) marginStart = activity.dp(3)
                        },
                    )
                }
            }, LinearLayout.LayoutParams(-1, activity.dp(136)).apply { topMargin = activity.dp(10) })

            addView(
                packStudioAction(dismissThen(onIconPackStudio)),
                LinearLayout.LayoutParams(-1, activity.dp(84)).apply {
                    topMargin = activity.dp(18)
                    bottomMargin = activity.dp(35)
                },
            )
            addView(sectionHeader("FROM ICON PACK"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = activity.dp(2) })
            val rowParams = { topMarginDp: Int ->
                LinearLayout.LayoutParams(-1, activity.dp(76)).apply {
                    if (topMarginDp > 0) topMargin = activity.dp(topMarginDp)
                }
            }
            addView(sourceRow("From gallery", android.R.drawable.ic_menu_gallery, onClick = dismissThen(onGallery)), rowParams(10))
            addView(sourceRow("Haven", null, teresArtwork, dismissThen { onSelect(WorkspaceIconSource.TERES) }), rowParams(0))
            addView(sourceRow("Installed icon packs", android.R.drawable.ic_menu_manage, onClick = dismissThen(onInstalledPacks)), rowParams(0))
            addView(sourceRow("Circle background color", android.R.drawable.ic_menu_edit, onClick = dismissThen(onBackgroundColor)), rowParams(0))
        }, ViewGroup.LayoutParams(-1, -2))
    }

    private fun sectionHeader(value: String) = styledLabel(value, 13f, 0xFF9A9CA1.toInt(), 600).apply {
        letterSpacing = 0.07f
    }

    private fun suggestion(optionLabel: String, artwork: Drawable, onClick: () -> Unit) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        isFocusable = true
        isClickable = true
        contentDescription = "$optionLabel icon source"
        background = activity.selectableBackground()
        addView(ImageView(activity).apply {
            setImageDrawable(copyDrawable(artwork))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "$label icon preview"
        }, LinearLayout.LayoutParams(activity.dp(54), activity.dp(54)).apply { topMargin = activity.dp(5) })
        addView(
            styledLabel(optionLabel, 11.5f, secondaryColor, 400).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = activity.dp(8)
                marginStart = activity.dp(2)
                marginEnd = activity.dp(2)
            },
        )
        setOnClickListener { onClick() }
    }

    private fun packStudioAction(onClick: () -> Unit) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        contentDescription = "Edit with Icon Pack Studio"
        background = roundedDrawable(0xFF3B3E43.toInt(), activity.dp(12))
        addView(ImageView(activity).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            setColorFilter(0xFFFFC448.toInt())
            contentDescription = null
        }, LinearLayout.LayoutParams(activity.dp(34), activity.dp(34)).apply { marginEnd = activity.dp(14) })
        addView(styledLabel(activity.getString(R.string.icon_editor_pack_studio), 15f, Color.WHITE, 600).apply {
            maxLines = 1
        })
        setOnClickListener { onClick() }
    }

    private fun sourceRow(title: String, glyph: Int?, artwork: Drawable? = null, onClick: () -> Unit) =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            contentDescription = title
            background = activity.selectableBackground()
            val iconSurface = ImageView(activity).apply {
                if (artwork != null) {
                    setImageDrawable(copyDrawable(artwork))
                } else {
                    setImageResource(glyph ?: android.R.drawable.ic_menu_gallery)
                    setColorFilter(Color.WHITE)
                    background = roundedDrawable(0xFF3B3E43.toInt(), activity.dp(27))
                    val pad = activity.dp(12)
                    setPadding(pad, pad, pad, pad)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
            }
            addView(iconSurface, LinearLayout.LayoutParams(activity.dp(50), activity.dp(50)).apply { marginEnd = activity.dp(16) })
            addView(styledLabel(title, 16f, Color.WHITE, 400).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
            setOnClickListener { onClick() }
        }

    private fun styledLabel(value: String, sizeSp: Float, color: Int, weight: Int) = TextView(activity).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        typeface = LauncherTypography.typeface(activity, weight)
    }

    private fun copyDrawable(drawable: Drawable): Drawable =
        drawable.constantState?.newDrawable(activity.resources)?.mutate() ?: drawable.mutate()
}
