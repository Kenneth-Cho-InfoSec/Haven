package com.arcturus.haven.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.arcturus.haven.R
import com.arcturus.haven.core.HavenExperienceMode
import com.arcturus.haven.core.HavenModePreferences
import com.arcturus.haven.launcher.launcher.HomeActivity
import com.arcturus.haven.util.Utility
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class WelcomeModeActivity : AppCompatActivity() {

    private var selectedMode: HavenExperienceMode = HavenExperienceMode.BOTH
    private lateinit var modeCards: Map<HavenExperienceMode, MaterialCardView>

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Utility.isProfileOwner(this)) {
            // Also covers a profile that was just transferred to us: the migration bridge
            // starts this activity once inside the work profile. This applies the
            // profile-owner policies and hides the launcher entry again.
            Utility.adoptTransferredWorkProfile(this)
            finish()
            return
        }

        val isReconfigure = intent.getBooleanExtra(EXTRA_RECONFIGURE, false)
        if (!isReconfigure && HavenModePreferences.isWelcomeCompleted(this)) {
            routeToMode(HavenModePreferences.getMode(this))
            return
        }

        setContentView(R.layout.activity_welcome_mode)

        findViewById<View>(R.id.welcome_content_root).apply {
            val (l, t, r, b) = listOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
                WindowInsetsCompat.CONSUMED
            }
        }

        selectedMode = if (HavenModePreferences.isWelcomeCompleted(this)) {
            HavenModePreferences.getMode(this)
        } else {
            HavenExperienceMode.BOTH
        }

        modeCards = mapOf(
            HavenExperienceMode.BOTH to findViewById<MaterialCardView>(R.id.card_mode_both),
            HavenExperienceMode.LAUNCHER_ONLY to findViewById(R.id.card_mode_launcher),
            HavenExperienceMode.ISOLATION_ONLY to findViewById(R.id.card_mode_isolation),
        ).onEach { (mode, card) -> card.setOnClickListener { selectMode(mode) } }
        selectMode(selectedMode)

        findViewById<MaterialButton>(R.id.welcome_continue_button).apply {
            setText(if (isReconfigure) R.string.welcome_mode_apply else R.string.welcome_mode_continue)
            setOnClickListener {
                HavenModePreferences.setMode(this@WelcomeModeActivity, selectedMode)
                routeToMode(selectedMode)
            }
        }
    }

    private fun selectMode(mode: HavenExperienceMode) {
        selectedMode = mode
        val primary = ContextCompat.getColor(this, R.color.md_theme_primary)
        val outline = ContextCompat.getColor(this, R.color.md_theme_outlineVariant)
        val selectedSurface = ContextCompat.getColor(this, R.color.md_theme_primaryContainer)
        val defaultSurface = ContextCompat.getColor(this, R.color.md_theme_surfaceContainer)
        val density = resources.displayMetrics.density
        val strokeSelectedPx = (2f * density).toInt().coerceAtLeast(1)
        val strokeDefaultPx = density.toInt().coerceAtLeast(1)

        modeCards.forEach { (cardMode, card) ->
            val active = cardMode == mode
            card.apply {
                isChecked = active
                strokeWidth = if (active) strokeSelectedPx else strokeDefaultPx
                strokeColor = if (active) primary else outline
                setCardBackgroundColor(if (active) selectedSurface else defaultSurface)
            }
        }
    }

    private fun routeToMode(mode: HavenExperienceMode) {
        val targetClass = if (mode.launcherEnabled) HomeActivity::class.java else MainActivity::class.java
        startActivity(Intent(this, targetClass).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    companion object {
        const val EXTRA_RECONFIGURE = "com.arcturus.haven.extra.RECONFIGURE"
    }
}
