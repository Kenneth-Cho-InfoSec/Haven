package com.arcturus.haven.launcher.core.data

import com.arcturus.haven.launcher.core.model.NewsFeedSource

data class NewsFeedCatalogEntry(
    val source: NewsFeedSource,
    val tags: List<String> = emptyList(),
    val popular: Boolean = false,
)

object NewsFeedCatalog {
    val categories = listOf("Technology", "Games", "Entertainment", "News", "Sports", "Science")

    private fun googleNews(topic: String) =
        "https://news.google.com/rss/headlines/section/topic/$topic?hl=en-US&gl=US&ceid=US:en"

    val entries = listOf(
        NewsFeedCatalogEntry(
            NewsFeedSource("Technology - Latest - Google News", googleNews("TECHNOLOGY"), "Technology", "Recent technology news, devices, software, and industry updates.", 0xFF4285F4.toInt()),
            listOf("technology", "consumer electronics", "apple", "google", "smartphones"), popular = true,
        ),
        NewsFeedCatalogEntry(
            NewsFeedSource("Android Authority", "https://www.androidauthority.com/feed/", "Technology", "Android devices, apps, accessories, and guides.", 0xFF2C653B.toInt()),
            listOf("android", "smartphones", "apps", "gadgets", "reviews"), popular = true,
        ),
        NewsFeedCatalogEntry(
            NewsFeedSource("The Verge", "https://www.theverge.com/rss/index.xml", "Technology", "Technology, science, art, and culture.", 0xFFE11D48.toInt()),
            listOf("technology", "science", "culture", "reviews"),
        ),
        NewsFeedCatalogEntry(
            NewsFeedSource("PC Gamer", "https://www.pcgamer.com/rss/", "Games", "PC gaming news, reviews, and features.", 0xFFD52A37.toInt()),
            listOf("pc", "games", "reviews", "hardware"), popular = true,
        ),
        NewsFeedCatalogEntry(
            NewsFeedSource("Nintendo Life", "https://www.nintendolife.com/feeds/latest", "Games", "Nintendo news, reviews, and features.", 0xFFE60012.toInt()),
            listOf("nintendo", "switch", "games"),
        ),
        NewsFeedCatalogEntry(
            NewsFeedSource("Entertainment - Latest - Google News", googleNews("ENTERTAINMENT"), "Entertainment", "Film, television, music, and culture news.", 0xFF7E57C2.toInt()),
            listOf("film", "music", "television", "culture"),
        ),
        NewsFeedCatalogEntry(
            NewsFeedSource("BBC News", "https://feeds.bbci.co.uk/news/rss.xml", "News", "The latest headlines from BBC News.", 0xFFB80000.toInt()),
            listOf("world", "uk", "headlines"), popular = true,
        ),
        NewsFeedCatalogEntry(
            NewsFeedSource("World News - Google News", googleNews("WORLD"), "News", "Recent international news coverage.", 0xFF4285F4.toInt()),
            listOf("world", "international", "headlines"),
        ),
        NewsFeedCatalogEntry(
            NewsFeedSource("BBC Sport", "https://feeds.bbci.co.uk/sport/rss.xml", "Sports", "The latest sports news and results.", 0xFFB80000.toInt()),
            listOf("football", "sport", "results"),
        ),
        NewsFeedCatalogEntry(
            NewsFeedSource("Sports - Latest - Google News", googleNews("SPORTS"), "Sports", "Recent sports news from around the world.", 0xFF4285F4.toInt()),
            listOf("sports", "football", "basketball"),
        ),
        NewsFeedCatalogEntry(
            NewsFeedSource("NASA JPL News", "https://www.jpl.nasa.gov/feeds/news/", "Science", "News and feature stories from NASA's Jet Propulsion Laboratory.", 0xFF1261A0.toInt()),
            listOf("space", "NASA", "science", "missions"), popular = true,
        ),
        NewsFeedCatalogEntry(
            NewsFeedSource("Science - Latest - Google News", googleNews("SCIENCE"), "Science", "Recent science and research news.", 0xFF4285F4.toInt()),
            listOf("science", "research", "space"),
        ),
    )

    val popular: List<NewsFeedCatalogEntry> = entries.filter { it.popular }

    fun inCategory(category: String, query: String = ""): List<NewsFeedCatalogEntry> =
        entries.filter { it.source.category == category && it.matches(query) }

    fun search(query: String): List<NewsFeedCatalogEntry> = entries.filter { it.matches(query) }

    private fun NewsFeedCatalogEntry.matches(query: String): Boolean {
        val normalized = query.trim().lowercase().ifEmpty { return true }
        return sequenceOf(source.title, source.category, source.description).plus(tags)
            .any { it.lowercase().contains(normalized) }
    }
}
