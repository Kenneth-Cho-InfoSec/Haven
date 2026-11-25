package com.arcturus.haven.launcher.launcher

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.arcturus.haven.R
import com.arcturus.haven.launcher.core.data.NewsFeedCatalog
import com.arcturus.haven.launcher.core.data.NewsFeedCatalogEntry
import com.arcturus.haven.launcher.core.data.NewsFeedRepository
import com.arcturus.haven.launcher.core.model.NewsFeedArticle
import com.arcturus.haven.launcher.core.model.NewsFeedSource
import com.arcturus.haven.launcher.core.settings.LauncherAppearance
import com.arcturus.haven.launcher.core.settings.LauncherPalette
import com.arcturus.haven.launcher.core.settings.LauncherSettings
import com.arcturus.haven.launcher.core.settings.LauncherTypography
import com.arcturus.haven.launcher.core.settings.launcherPrefs
import com.arcturus.haven.launcher.core.ui.dp
import com.arcturus.haven.launcher.core.ui.roundedDrawable
import com.arcturus.haven.launcher.core.ui.selectableBackground
import com.arcturus.haven.launcher.core.ui.toast
import org.xml.sax.SAXException
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Smart-style RSS side panel with a real subscription catalog, source manager, and RSS reader. */
class FeedActivity : ComponentActivity() {
    private enum class Screen { ONBOARDING, FEED, PICKER, MANAGE }

    private val preferences by lazy { launcherPrefs() }
    private lateinit var repository: NewsFeedRepository
    private lateinit var palette: LauncherPalette
    private lateinit var root: FrameLayout
    private lateinit var contentHost: FrameLayout
    private var screen = Screen.ONBOARDING
    private val history = mutableListOf<Screen>()
    private var sources: List<NewsFeedSource> = emptyList()
    private var articles: List<NewsFeedArticle> = emptyList()
    private val fetchErrors = linkedMapOf<String, String>()
    private var expandedTopic: String? = null
    private var pickerQuery = ""
    private var refreshing = false
    private var pendingRequests = 0
    private var refreshGeneration = 0
    private var feedLayout = LAYOUT_COMPACT
    private var darkSurface = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherTypography.applyTheme(this)
        palette = LauncherAppearance.resolve(this)
        repository = NewsFeedRepository(this)
        sources = repository.sources()
        articles = repository.cachedArticles()
        feedLayout = preferences.getString(LauncherSettings.KEY_FEED_LAYOUT, LAYOUT_COMPACT) ?: LAYOUT_COMPACT
        screen = if (sources.isEmpty()) Screen.ONBOARDING else Screen.FEED

        root = FrameLayout(this)
        contentHost = FrameLayout(this)
        root.addView(contentHost, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateBack()
        })
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            (contentHost.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.setMargins(safe.left, safe.top, safe.right, safe.bottom)
                contentHost.layoutParams = params
            }
            insets
        }
        render()
        if (sources.isNotEmpty()) refreshFeeds()
    }

    private fun navigateBack() {
        if (history.isEmpty()) {
            finish()
            return
        }
        sources = repository.sources()
        screen = history.removeAt(history.lastIndex)
        if (screen == Screen.ONBOARDING && sources.isNotEmpty()) screen = Screen.FEED
        if (screen == Screen.FEED && sources.isEmpty()) screen = Screen.ONBOARDING
        articles = repository.cachedArticles()
        render()
    }

    private fun navigate(next: Screen) {
        history += screen
        screen = next
        sources = repository.sources()
        render()
    }

    private fun openPicker() {
        pickerQuery = ""
        navigate(Screen.PICKER)
    }

    private fun render() {
        val light = screen == Screen.PICKER || screen == Screen.MANAGE
        darkSurface = !light
        configureWindow(light)
        contentHost.removeAllViews()
        val content = when (screen) {
            Screen.ONBOARDING -> onboardingScreen()
            Screen.FEED -> feedScreen()
            Screen.PICKER -> sourcePickerScreen()
            Screen.MANAGE -> manageSourcesScreen()
        }
        contentHost.addView(content, FrameLayout.LayoutParams(-1, -1))
        root.requestApplyInsets()
    }

    private fun configureWindow(light: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (light) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            root.setBackgroundColor(Color.WHITE)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            root.setBackgroundColor(0xE6262E46.toInt())
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    private fun onboardingScreen(): View = FrameLayout(this).apply {
        val group = LinearLayout(this@FeedActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(ImageView(this@FeedActivity).apply {
                setImageResource(R.drawable.ic_feed)
                setColorFilter(Color.WHITE)
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(36), dp(36)))
            addView(label("Your Feed", 40f, Color.WHITE, 600).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(22), 0, dp(8))
            }, LinearLayout.LayoutParams(-1, -2))
            addView(
                label(
                    "Keep up with the latest news right from your Home screen by adding your favorite RSS feeds.",
                    18f, 0xFFD1D2DA.toInt(), 400, maxLines = 3,
                ).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-1, -2),
            )
            addView(
                actionButton("Get started", palette.accent, palette.onAccent, 17f) { openPicker() },
                LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(28) },
            )
        }
        addView(group, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply {
            leftMargin = dp(30)
            rightMargin = dp(30)
            topMargin = dp(38)
        })
    }

    private fun feedScreen(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(18))
        }
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(label("Your Feed", 30f, foregroundPrimary(), 600))
        val lastUpdated = repository.lastUpdatedAtMillis()
        titleBlock.addView(label(
            if (lastUpdated > 0L) "Last Update ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(lastUpdated))}"
            else if (refreshing) "Updating your feeds…" else "Tap to refresh your feeds",
            15f,
            foregroundSecondary(),
        ).apply {
            isClickable = true
            contentDescription = "Refresh Your Feed"
            setOnClickListener { refreshFeeds() }
        })
        header.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(iconButton(R.drawable.ic_feed_add, "Add news source", feedButtonSurface()) { openPicker() }, LinearLayout.LayoutParams(dp(56), dp(56)))
        header.addView(iconButton(R.drawable.ic_feed_more, "Feed options", feedButtonSurface()) { showFeedOptions() }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginStart = dp(8) })
        page.addView(header)

        if (refreshing) page.addView(ProgressBar(this).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(24), dp(24)))
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(4), 0, dp(16))
        }
        if (articles.isEmpty()) {
            list.addView(label(
                if (refreshing) "Loading stories from your feeds…" else fetchErrors.values.firstOrNull()
                    ?: "Your sources have no stories yet. Pull to retry or add another source.",
                17f,
                foregroundSecondary(),
            ).apply { setPadding(dp(4), dp(28), dp(4), dp(20)) })
            if (!refreshing && sources.isNotEmpty()) {
                list.addView(actionButton("Refresh", palette.accent, palette.onAccent) { refreshFeeds() })
            }
        } else {
            articles.forEachIndexed { index, article -> list.addView(articleCard(article, index)) }
        }
        scroll.addView(list, FrameLayout.LayoutParams(-1, -2))
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    private fun articleCard(article: NewsFeedArticle, index: Int): View {
        val source = sources.firstOrNull { it.id == article.sourceId }
            ?: NewsFeedCatalog.entries.firstOrNull { it.source.id == article.sourceId }?.source
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(if (feedLayout == LAYOUT_TEXT) 6 else 12), dp(12), dp(12), dp(14))
            isFocusable = true
            if (feedLayout == LAYOUT_COMPACT) {
                background = LayerDrawable(arrayOf(roundedDrawable(0x223E4A68, dp(22)), selectableBackground()))
            }
            contentDescription = "${article.sourceTitle}. ${article.title}. ${relativeTime(article.publishedAtMillis)}"
            setOnClickListener { openArticle(article.link) }
            setOnLongClickListener { showArticleActions(article); true }
        }
        val metadata = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(sourceBadge(source?.title, source?.accentColor ?: palette.accent, 11f, 5), LinearLayout.LayoutParams(dp(28), dp(28)))
            addView(
                label(article.sourceTitle, 14f, foregroundSecondary(), 400, maxLines = 1),
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) },
            )
            addView(label(relativeTime(article.publishedAtMillis), 14f, foregroundSecondary()))
        }
        card.addView(metadata)
        val titleSize = when (feedLayout) {
            LAYOUT_LARGE -> if (index == 0) 24f else 22f
            LAYOUT_COMPACT -> 20f
            else -> 17f
        }
        card.addView(label(article.title, titleSize, foregroundPrimary(), 500, maxLines = if (feedLayout == LAYOUT_TEXT) 3 else 4).apply {
            setPadding(0, dp(8), 0, 0)
        })
        if (feedLayout != LAYOUT_TEXT && article.description.isNotBlank()) {
            card.addView(label(article.description, 14f, foregroundSecondary(), 400, maxLines = 2).apply {
                setPadding(0, dp(4), 0, 0)
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (index > 0) setPadding(0, dp(6), 0, dp(6))
            addView(card, LinearLayout.LayoutParams(-1, -2))
            if (feedLayout == LAYOUT_LARGE) addView(
                View(this@FeedActivity).apply { setBackgroundColor(0x447F8797) },
                LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(4) },
            )
        }
    }

    private fun sourcePickerScreen(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(horizontalHeader("EN-${feedRegion()}", ::showLanguageOptions))
        page.addView(label("Add news source", 30f, Color.BLACK, 600).apply { setPadding(0, dp(12), 0, dp(16)) })
        val search = EditText(this).apply {
            hint = "Search"
            setSingleLine(true)
            textSize = 17f
            setTextColor(0xFF17191C.toInt())
            setHintTextColor(0xFF858A90.toInt())
            setPadding(dp(18), 0, dp(18), 0)
            background = roundedDrawable(0xFFF1F3F6.toInt(), dp(28))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            contentDescription = "Search news sources"
        }
        page.addView(search, LinearLayout.LayoutParams(-1, dp(56)))

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(22), 0, dp(22))
        }
        val popularHeading = label("Most popular", 19f, 0xFF6F7379.toInt(), 500)
        body.addView(popularHeading, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        val popularRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        NewsFeedCatalog.popular.forEachIndexed { index, entry ->
            popularRow.addView(popularCard(entry), LinearLayout.LayoutParams(dp(132), dp(172)).apply {
                if (index > 0) marginStart = dp(12)
            })
        }
        val popularStrip = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(popularRow)
        }
        body.addView(popularStrip, LinearLayout.LayoutParams(-1, dp(172)))
        val searchResults = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isVisible = false
        }
        body.addView(searchResults, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        val topicsTitle = label("Topics", 19f, 0xFF6F7379.toInt(), 500)
        body.addView(topicsTitle, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(26)
            bottomMargin = dp(14)
        })
        val topics = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        NewsFeedCatalog.categories.forEach { category ->
            val section = topicSection(category)
            topics.addView(section, LinearLayout.LayoutParams(-1, -2).apply {
                if (topics.isNotEmpty()) topMargin = dp(8)
            })
        }
        body.addView(topics)
        body.addView(
            actionButton("Add feed by URL", 0xFFF1F3F6.toInt(), 0xFF172021.toInt(), 15f) { showCustomFeedDialog() },
            LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(20) },
        )
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
        search.doOnTextChanged { s, _, _, _ ->
            pickerQuery = s?.toString().orEmpty()
            val active = pickerQuery.isNotBlank()
            listOf(popularHeading, popularStrip, topicsTitle, topics).forEach { it.isVisible = !active }
            searchResults.isVisible = active
            searchResults.removeAllViews()
            if (active) {
                val matches = NewsFeedCatalog.search(pickerQuery)
                if (matches.isEmpty()) {
                    searchResults.addView(label("No matching sources", 16f, 0xFF747A81.toInt()).apply {
                        setPadding(dp(4), dp(18), dp(4), dp(18))
                    })
                } else {
                    matches.forEach { searchResults.addView(sourceRow(it.source, it.tags)) }
                }
            }
        }
        if (pickerQuery.isNotBlank()) search.setText(pickerQuery)
        return page
    }

    private fun horizontalHeader(actionLabel: String, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(backButton(), LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(View(this@FeedActivity), LinearLayout.LayoutParams(0, 1, 1f))
        addView(TextView(this@FeedActivity).apply {
            text = actionLabel
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = LauncherTypography.typeface(this@FeedActivity, 600)
            setTextColor(0xFF17191C.toInt())
            background = roundedDrawable(0xFFF1F3F6.toInt(), dp(18))
            isFocusable = true
            contentDescription = "News language ${feedLanguageLabel()}"
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(dp(78), dp(48)))
    }

    private fun popularCard(entry: NewsFeedCatalogEntry): View = FrameLayout(this).apply {
        background = roundedDrawable(entry.source.accentColor, dp(15))
        addView(label(entry.source.title.split(' ').first().take(2).uppercase(Locale.getDefault()), 22f, Color.WHITE, 600).apply {
            gravity = Gravity.CENTER
            background = roundedDrawable(0x66FFFFFF, dp(10))
        }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = dp(16)
            bottomMargin = dp(47)
        })
        addView(LinearLayout(this@FeedActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(entry.source.title, 14f, Color.WHITE, 600, maxLines = 1))
            addView(label(entry.source.category, 12f, 0xFFE9E9EF.toInt()))
        }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = dp(12)
            rightMargin = dp(10)
            bottomMargin = dp(9)
        })
        addView(sourceAddButton(entry.source), FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(8)
            rightMargin = dp(8)
        })
    }

    private fun topicSection(category: String): View {
        val expanded = expandedTopic == category
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(0xFFF2F4F7.toInt(), dp(26))
            clipToOutline = true
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(4), dp(16), dp(4))
            isClickable = true
            isFocusable = true
            addView(label(category, 18f, 0xFF17191C.toInt(), 600), LinearLayout.LayoutParams(0, -2, 1f))
            addView(label(if (expanded) "⌃" else "⌄", 21f, 0xFF8B9095.toInt(), 600).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(30), dp(48)))
            setOnClickListener {
                expandedTopic = if (expandedTopic == category) null else category
                render()
            }
        }
        card.addView(header, LinearLayout.LayoutParams(-1, dp(64)))
        if (expanded) {
            NewsFeedCatalog.inCategory(category, pickerQuery).forEachIndexed { index, entry ->
                card.addView(sourceRow(entry.source, entry.tags), LinearLayout.LayoutParams(-1, -2).apply {
                    if (index > 0) topMargin = dp(2)
                })
            }
        }
        return card
    }

    private fun sourceRow(source: NewsFeedSource, tags: List<String>): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = dp(12)
        setPadding(pad, pad, pad, pad)
        background = roundedDrawable(0xFFF2F4F7.toInt(), dp(16))
        addView(sourceBadge(source.title, source.accentColor, 16f, 28), LinearLayout.LayoutParams(dp(42), dp(42)))
        addView(LinearLayout(this@FeedActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            addView(label(source.title, 16f, 0xFF17191C.toInt(), 600, maxLines = 1))
            addView(label(source.description, 13f, 0xFF737980.toInt(), 400, maxLines = 2).apply {
                setPadding(0, dp(2), 0, 0)
            })
            if (tags.isNotEmpty()) {
                addView(label(tags.take(4).joinToString("   ") { "#$it" }, 11f, 0xFF747A81.toInt(), maxLines = 1).apply {
                    setPadding(0, dp(4), 0, 0)
                })
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(sourceAddButton(source), LinearLayout.LayoutParams(dp(48), dp(48)))
    }

    private fun sourceAddButton(source: NewsFeedSource): View {
        val current = sources.any { it.id == source.id }
        return FrameLayout(this).apply {
            contentDescription = if (current) "Remove ${source.title}" else "Add ${source.title}"
            isFocusable = true
            background = roundedDrawable(if (current) 0xFF22C55E.toInt() else 0xFF20242B.toInt(), dp(7))
            addView(ImageView(this@FeedActivity).apply {
                setImageResource(if (current) R.drawable.ic_feed_check else R.drawable.ic_feed_add)
                setColorFilter(Color.WHITE)
                contentDescription = null
            }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            setOnClickListener { toggleSource(source) }
        }
    }

    private fun toggleSource(source: NewsFeedSource) {
        sources = repository.sources()
        val existing = sources.firstOrNull { it.id == source.id }
        if (existing != null) {
            removeSource(existing.id)
        } else {
            if (!repository.addSource(source)) {
                toast("This source could not be added")
                return
            }
            sources = repository.sources()
            fetchErrors.remove(source.id)
            fetchSingleSource(source)
        }
        render()
    }

    private fun removeSource(sourceId: String) {
        repository.removeSource(sourceId)
        sources = repository.sources()
        articles = repository.cachedArticles()
        fetchErrors.remove(sourceId)
    }

    private fun fetchSingleSource(source: NewsFeedSource) {
        repository.fetch(source) { result ->
            if (isFinishing || isDestroyed) return@fetch
            result.fold(
                onSuccess = { fetchErrors.remove(source.id) },
                onFailure = { fetchErrors[source.id] = it.message ?: "Could not load this source." },
            )
            articles = repository.cachedArticles()
            if (screen == Screen.FEED) render()
        }
    }

    private fun manageSourcesScreen(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(backButton(), LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(View(this@FeedActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(iconButton(R.drawable.ic_feed_add, "Add news source", 0xFFF1F3F6.toInt()) { openPicker() }, LinearLayout.LayoutParams(dp(56), dp(56)))
        }
        page.addView(toolbar)
        page.addView(label("Manage news sources", 30f, Color.BLACK, 600).apply { setPadding(0, dp(14), 0, dp(4)) })
        page.addView(label("You now have ${sources.size} news source${if (sources.size == 1) "" else "s"}", 15f, 0xFF70757B.toInt()).apply {
            setPadding(0, 0, 0, dp(18))
        })
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (sources.isNotEmpty()) {
            body.addView(label("Your Feed", 19f, 0xFF777C82.toInt(), 500).apply { setPadding(0, dp(10), 0, dp(14)) })
            sources.groupBy(NewsFeedSource::category).forEach { (category, items) ->
                val section = LinearLayout(this@FeedActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    background = roundedDrawable(0xFFF2F4F7.toInt(), dp(25))
                    clipToOutline = true
                    addView(label(category, 18f, 0xFF17191C.toInt(), 600).apply {
                        setPadding(dp(20), dp(17), dp(16), dp(17))
                    })
                    items.forEach { addView(managedSourceRow(it)) }
                }
                body.addView(section, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(22) })
            }
        } else {
            body.addView(label("Add a source to start your news feed.", 15f, 0xFF6F747A.toInt()).apply {
                setPadding(dp(4), dp(20), dp(4), dp(20))
            })
        }
        body.addView(label("Other", 19f, 0xFF777C82.toInt(), 500).apply { setPadding(0, dp(14), 0, dp(12)) })
        body.addView(actionRow("Export data", "Share your RSS subscriptions as an OPML file.") { exportSubscriptions() }, LinearLayout.LayoutParams(-1, dp(64)))
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(14) })
        return page
    }

    private fun managedSourceRow(source: NewsFeedSource): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(8), dp(16), dp(8))
        addView(sourceBadge(source.title, source.accentColor, 15f, 24), LinearLayout.LayoutParams(dp(40), dp(40)))
        addView(
            label(source.title, 15f, 0xFF17191C.toInt(), 500, maxLines = 1),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) },
        )
        addView(manageIcon(R.drawable.ic_feed_open, "Open ${source.title}") { openArticle(source.url) })
        addView(manageIcon(R.drawable.ic_feed_delete, "Remove ${source.title}") {
            removeSource(source.id)
            render()
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(6) })
    }

    private fun manageIcon(icon: Int, description: String, action: () -> Unit): View = ImageView(this).apply {
        setImageResource(icon)
        setColorFilter(if (icon == R.drawable.ic_feed_delete) 0xFFDB3F3F.toInt() else 0xFF747B83.toInt())
        contentDescription = description
        isFocusable = true
        val pad = dp(12)
        setPadding(pad, pad, pad, pad)
        setOnClickListener { action() }
    }

    private fun actionRow(title: String, summary: String, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), 0, dp(16), 0)
        background = roundedDrawable(0xFFF2F4F7.toInt(), dp(24))
        isFocusable = true
        addView(label(title, 17f, 0xFF17191C.toInt(), 500), LinearLayout.LayoutParams(0, -2, 1f))
        addView(label("›", 24f, 0xFF8B9095.toInt()), LinearLayout.LayoutParams(dp(30), -2).apply { gravity = Gravity.CENTER })
        contentDescription = "$title. $summary"
        setOnClickListener { action() }
    }

    private fun showFeedOptions() {
        AlertDialog.Builder(this, R.style.Theme_Teres_Dialog_Dark)
            .setItems(arrayOf("Manage news sources", "Layout", "Your Feed")) { _, which ->
                when (which) {
                    0 -> navigate(Screen.MANAGE)
                    1 -> showLayoutOptions()
                    else -> {
                        sources = repository.sources()
                        if (sources.isEmpty()) screen = Screen.ONBOARDING
                        render()
                    }
                }
            }
            .show()
    }

    private fun showLayoutOptions() = showChoiceDialog(
        R.style.Theme_Teres_Dialog_Dark, "Layout",
        arrayOf("Large", "Compact", "Text only"),
        arrayOf(LAYOUT_LARGE, LAYOUT_COMPACT, LAYOUT_TEXT).indexOf(feedLayout).coerceAtLeast(0),
    ) { which ->
        feedLayout = arrayOf(LAYOUT_LARGE, LAYOUT_COMPACT, LAYOUT_TEXT)[which]
        preferences.edit { putString(LauncherSettings.KEY_FEED_LAYOUT, feedLayout) }
    }

    private fun showLanguageOptions() {
        val codes = arrayOf("en-US", "en-GB", "en-CA")
        showChoiceDialog(
            R.style.Theme_Teres_Dialog_Light, "News language",
            arrayOf("English (United States)", "English (United Kingdom)", "English (Canada)"),
            codes.indexOf(feedLanguageLabel()).coerceAtLeast(0),
        ) { which -> preferences.edit { putString(LauncherSettings.KEY_FEED_LANGUAGE, codes[which]) } }
    }

    private fun showChoiceDialog(themeRes: Int, title: String, labels: Array<String>, selected: Int, onPick: (Int) -> Unit) {
        AlertDialog.Builder(this, themeRes)
            .setTitle(title)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                onPick(which)
                dialog.dismiss()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomFeedDialog() {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), 0)
        }
        val title = EditText(this).apply {
            hint = "Source name"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val url = EditText(this).apply {
            hint = "https://example.com/feed.xml"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        fields.addView(title, LinearLayout.LayoutParams(-1, dp(54)))
        fields.addView(url, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })
        val dialog = AlertDialog.Builder(this, R.style.Theme_Teres_Dialog_Light)
            .setTitle("Add RSS feed")
            .setView(fields)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = title.text.toString().trim()
                val feedUrl = url.text.toString().trim()
                if (name.isBlank()) {
                    title.error = "Enter a source name"
                    return@setOnClickListener
                }
                if (!NewsFeedRepository.isValidFeedUrl(feedUrl)) {
                    url.error = "Enter a valid HTTPS feed URL"
                    return@setOnClickListener
                }
                val source = NewsFeedSource(name, feedUrl, "Other", "Custom RSS source", palette.accent)
                if (!repository.addSource(source)) {
                    url.error = "This source is already in Your Feed"
                    return@setOnClickListener
                }
                sources = repository.sources()
                dialog.dismiss()
                render()
                fetchSingleSource(source)
            }
        }
        dialog.show()
    }

    private fun showArticleActions(article: NewsFeedArticle) {
        AlertDialog.Builder(this, R.style.Theme_Teres_Dialog_Dark)
            .setTitle(article.title)
            .setItems(arrayOf("Open article", "Share")) { _, which ->
                if (which == 0) openArticle(article.link) else shareArticle(article)
            }
            .show()
    }

    private fun shareArticle(article: NewsFeedArticle) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.link}")
        runCatching { startActivity(Intent.createChooser(send, "Share article")) }
            .onFailure { toast("Sharing is unavailable") }
    }

    private fun exportSubscriptions() {
        val outlines = sources.joinToString("\n") { source ->
            "    <outline type=\"rss\" text=\"${xmlEscape(source.title)}\" title=\"${xmlEscape(source.title)}\" xmlUrl=\"${xmlEscape(source.url)}\" />"
        }
        val opml = """<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0"><head><title>Teres Launcher subscriptions</title></head><body>
$outlines
</body></opml>"""
        val send = Intent(Intent.ACTION_SEND).setType("text/x-opml")
            .putExtra(Intent.EXTRA_TEXT, opml)
            .putExtra(Intent.EXTRA_SUBJECT, "Teres Launcher RSS subscriptions")
        runCatching { startActivity(Intent.createChooser(send, "Export news sources")) }
            .onFailure { toast("Export is unavailable") }
    }

    private fun refreshFeeds() {
        sources = repository.sources()
        if (sources.isEmpty()) {
            articles = emptyList()
            screen = Screen.ONBOARDING
            render()
            return
        }
        val currentGeneration = ++refreshGeneration
        fetchErrors.clear()
        refreshing = true
        pendingRequests = sources.size
        articles = repository.cachedArticles()
        render()
        sources.forEach { source ->
            repository.fetch(source) { result ->
                if (isFinishing || isDestroyed || currentGeneration != refreshGeneration) return@fetch
                result.onFailure { fetchErrors[source.id] = userFacingFeedError(it) }
                result.onSuccess { fetchErrors.remove(source.id) }
                articles = repository.cachedArticles()
                pendingRequests = (pendingRequests - 1).coerceAtLeast(0)
                refreshing = pendingRequests > 0
                if (screen == Screen.FEED) render()
            }
        }
    }

    private fun openArticle(link: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, link.toUri())) }
            .onFailure { toast("This story could not be opened") }
    }

    private fun userFacingFeedError(error: Throwable): String {
        val fallback = "Could not update this source. Check your connection and try again."
        return when (error) {
            is SocketTimeoutException -> "The connection timed out. Tap Refresh to retry."
            is SAXException -> "This source returned invalid RSS or Atom data."
            is IOException -> error.message?.takeIf {
                it.startsWith("The feed returned HTTP ") ||
                    it == "No readable stories were found in this feed." ||
                    it == "This feed is too large to read." ||
                    it == "Only secure HTTPS feed URLs are supported."
            } ?: fallback
            else -> fallback
        }
    }

    private fun sourceBadge(title: String?, color: Int, textSizeSp: Float, radiusDp: Int) = TextView(this).apply {
        text = title?.firstOrNull()?.uppercaseChar()?.toString() ?: "N"
        gravity = Gravity.CENTER
        textSize = textSizeSp
        typeface = LauncherTypography.typeface(this@FeedActivity, 600)
        setTextColor(Color.WHITE)
        background = roundedDrawable(color, dp(radiusDp))
    }

    private fun backButton(): View = ImageView(this).apply {
        setImageResource(R.drawable.ic_feed_back)
        contentDescription = "Back"
        isFocusable = true
        setOnClickListener { navigateBack() }
    }

    private fun iconButton(icon: Int, description: String, backgroundColor: Int, action: () -> Unit): View = FrameLayout(this).apply {
        contentDescription = description
        isFocusable = true
        background = LayerDrawable(arrayOf(roundedDrawable(backgroundColor, dp(22)), selectableBackground()))
        addView(ImageView(this@FeedActivity).apply {
            setImageResource(icon)
            setColorFilter(if (darkSurface) Color.WHITE else 0xFF20242B.toInt())
            contentDescription = null
        }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        setOnClickListener { action() }
    }

    private fun actionButton(
        text: String,
        backgroundColor: Int,
        textColor: Int,
        size: Float = 16f,
        action: () -> Unit,
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        typeface = LauncherTypography.typeface(this@FeedActivity, 600)
        setTextColor(textColor)
        gravity = Gravity.CENTER
        background = LayerDrawable(arrayOf(roundedDrawable(backgroundColor, dp(24)), selectableBackground()))
        minHeight = dp(48)
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun label(
        text: String,
        size: Float,
        color: Int,
        weight: Int = 400,
        maxLines: Int = Int.MAX_VALUE,
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        typeface = LauncherTypography.typeface(this@FeedActivity, weight)
        setTextColor(color)
        includeFontPadding = false
        if (maxLines != Int.MAX_VALUE) {
            this.maxLines = maxLines
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun foregroundPrimary(): Int = if (darkSurface) 0xFFF1F5F4.toInt() else palette.textPrimary
    private fun foregroundSecondary(): Int = if (darkSurface) 0xFFD0D9D7.toInt() else palette.textSecondary
    private fun feedButtonSurface(): Int = if (darkSurface) 0x663E485F else palette.surfaceVariant

    private fun feedLanguageLabel(): String =
        preferences.getString(LauncherSettings.KEY_FEED_LANGUAGE, "en-US") ?: "en-US"

    private fun feedRegion(): String = feedLanguageLabel().substringAfter('-', "US")

    private fun xmlEscape(value: String): String = value.replace("&", "&amp;")
        .replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    private fun relativeTime(timestamp: Long): String {
        if (timestamp <= 0L) return "Recently"
        val minutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 60_000L).toInt()
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min. ago"
            minutes < 1_440 -> "${minutes / 60} hr. ago"
            minutes < 10_080 -> "${minutes / 1_440} days ago"
            else -> "${minutes / 10_080} weeks ago"
        }
    }

    private companion object {
        const val LAYOUT_LARGE = "large"
        const val LAYOUT_COMPACT = "compact"
        const val LAYOUT_TEXT = "text"
    }
}
