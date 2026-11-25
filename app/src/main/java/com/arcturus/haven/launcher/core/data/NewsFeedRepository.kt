package com.arcturus.haven.launcher.core.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import com.arcturus.haven.launcher.core.model.NewsFeedArticle
import com.arcturus.haven.launcher.core.model.NewsFeedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors

/** Owns the user's RSS subscriptions, cached stories, and off-main-thread feed requests. */
class NewsFeedRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    fun sources(): List<NewsFeedSource> = readJsonArray(KEY_SOURCES) { item ->
        val title = item.optString("title").trim()
        val url = item.optString("url").trim()
        if (title.isEmpty() || !isValidFeedUrl(url)) return@readJsonArray null
        NewsFeedSource(
            title = title,
            url = url,
            category = item.optString("category", "Other").ifBlank { "Other" },
            description = item.optString("description"),
            accentColor = item.optInt("accentColor", 0xFF5267C9.toInt()),
        )
    }.distinctBy(NewsFeedSource::id)

    @Synchronized
    fun addSource(source: NewsFeedSource): Boolean {
        if (source.title.isBlank() || !isValidFeedUrl(source.url)) return false
        val current = sources()
        if (current.any { it.id == source.id }) return false
        saveSources(current + source)
        return true
    }

    @Synchronized
    fun removeSource(sourceId: String) {
        saveSources(sources().filterNot { it.id == sourceId })
        saveArticles(cachedArticles().filterNot { it.sourceId == sourceId })
    }

    @Synchronized
    fun cachedArticles(): List<NewsFeedArticle> = readJsonArray(KEY_ARTICLES) { item ->
        val sourceId = item.optString("sourceId")
        val title = item.optString("title")
        val link = item.optString("link")
        if (sourceId.isBlank() || title.isBlank() || !isValidArticleUrl(link)) return@readJsonArray null
        NewsFeedArticle(
            sourceId = sourceId,
            sourceTitle = item.optString("sourceTitle"),
            title = title,
            link = link,
            publishedAtMillis = item.optLong("publishedAtMillis"),
            description = item.optString("description"),
        )
    }.distinctBy(NewsFeedArticle::id)

    fun lastUpdatedAtMillis(): Long = preferences.getLong(KEY_LAST_UPDATED, 0L)

    fun fetch(source: NewsFeedSource, callback: (Result<List<NewsFeedArticle>>) -> Unit) {
        ioExecutor.execute {
            val result = runCatching { request(source) }.onSuccess { articles ->
                synchronized(this) {
                    val retained = cachedArticles().filterNot { it.sourceId == source.id }
                    saveArticles(
                        (retained + articles).distinctBy(NewsFeedArticle::id)
                            .sortedByDescending(NewsFeedArticle::publishedAtMillis)
                            .take(MAX_CACHED_ARTICLES),
                    )
                    preferences.edit { putLong(KEY_LAST_UPDATED, System.currentTimeMillis()) }
                }
            }
            mainHandler.post { callback(result) }
        }
    }

    private inline fun <T> readJsonArray(key: String, parse: (JSONObject) -> T?): List<T> {
        val json = runCatching { JSONArray(preferences.getString(key, "[]")) }.getOrNull() ?: return emptyList()
        return List(json.length()) { index -> json.optJSONObject(index)?.let(parse) }.filterNotNull()
    }

    private inline fun <T> saveJsonArray(key: String, items: List<T>, encode: (T) -> JSONObject) {
        val json = JSONArray().apply { items.forEach { put(encode(it)) } }
        preferences.edit { putString(key, json.toString()) }
    }

    @Synchronized
    private fun saveSources(sources: List<NewsFeedSource>) = saveJsonArray(KEY_SOURCES, sources) { source ->
        JSONObject()
            .put("title", source.title)
            .put("url", source.url)
            .put("category", source.category)
            .put("description", source.description)
            .put("accentColor", source.accentColor)
    }

    @Synchronized
    private fun saveArticles(articles: List<NewsFeedArticle>) = saveJsonArray(KEY_ARTICLES, articles) { article ->
        JSONObject()
            .put("sourceId", article.sourceId)
            .put("sourceTitle", article.sourceTitle)
            .put("title", article.title)
            .put("link", article.link)
            .put("publishedAtMillis", article.publishedAtMillis)
            .put("description", article.description)
    }

    private fun request(source: NewsFeedSource): List<NewsFeedArticle> {
        if (!isValidFeedUrl(source.url)) throw IOException("Only secure HTTPS feed URLs are supported.")
        val connection = (URL(source.url).openConnection() as? HttpURLConnection)
            ?: throw IOException("Could not open this feed.")
        try {
            connection.apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/atom+xml, application/rss+xml, application/xml, text/xml, */*")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("The feed returned HTTP $status.")
            val xml = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(8_192)
                var count: Int
                while (reader.read(buffer).also { count = it } >= 0) {
                    if (result.length + count > MAX_FEED_CHARACTERS) throw IOException("This feed is too large to read.")
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
            return RssFeedParser.parse(xml, source).take(MAX_STORIES_PER_SOURCE)
                .ifEmpty { throw IOException("No readable stories were found in this feed.") }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val PREFERENCES = "news_feed"
        private const val KEY_SOURCES = "sources"
        private const val KEY_ARTICLES = "articles"
        private const val KEY_LAST_UPDATED = "last_updated"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val MAX_FEED_CHARACTERS = 2_000_000
        private const val MAX_STORIES_PER_SOURCE = 50
        private const val MAX_CACHED_ARTICLES = 250
        private const val USER_AGENT = "TeresLauncher/1.0 RSS Reader"
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1")
        private val ARTICLE_SCHEMES = setOf("http", "https")
        private val ioExecutor = Executors.newFixedThreadPool(3) { task ->
            Thread(task, "Teres-news-feed").apply { isDaemon = true }
        }
        private val mainHandler = Handler(Looper.getMainLooper())

        fun isValidFeedUrl(value: String): Boolean = runCatching {
            val uri = URI(value.trim())
            val host = uri.host?.takeIf(String::isNotBlank)?.lowercase() ?: return@runCatching false
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.userInfo.isNullOrBlank() &&
                host !in LOCAL_HOSTS &&
                !host.endsWith(".local")
        }.getOrDefault(false)

        private fun isValidArticleUrl(value: String): Boolean = runCatching {
            val uri = URI(value.trim())
            uri.scheme?.lowercase() in ARTICLE_SCHEMES &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo.isNullOrBlank()
        }.getOrDefault(false)
    }
}
