package com.arcturus.haven.launcher.core.data

import com.arcturus.haven.launcher.core.model.NewsFeedSource
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssFeedParserTest {
    private val source = NewsFeedSource(title = "Example News", url = "https://example.org/feed.xml", category = "Technology")

    @Test
    fun parsesRssItemsAndCleansDescriptions() {
        val xml = """
            <rss version="2.0"><channel>
              <item>
                <title>New &amp; improved</title>
                <link>https://example.org/story</link>
                <pubDate>Mon, 12 Oct 2026 10:00:00 GMT</pubDate>
                <description><![CDATA[<p>Simple &amp; clear <b>report</b>.</p>]]></description>
              </item>
              <item><title>Without a link</title></item>
            </channel></rss>
        """.trimIndent()

        val article = RssFeedParser.parse(xml, source).single()

        assertEquals("New & improved", article.title)
        assertEquals("https://example.org/story", article.link)
        assertEquals("Simple & clear report .", article.description)
        assertEquals(Instant.parse("2026-10-12T10:00:00Z").toEpochMilli(), article.publishedAtMillis)
        assertEquals(source.id, article.sourceId)
    }

    @Test
    fun parsesNamespacedAtomEntries() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <title>Android update</title>
                <link rel="alternate" href="https://example.org/android-update" />
                <updated>2026-10-12T10:00:00Z</updated>
                <summary>Stable release notes</summary>
              </entry>
            </feed>
        """.trimIndent()

        val article = RssFeedParser.parse(xml, source).single()

        assertEquals("Android update", article.title)
        assertEquals("https://example.org/android-update", article.link)
        assertEquals("Stable release notes", article.description)
        assertEquals(Instant.parse("2026-10-12T10:00:00Z").toEpochMilli(), article.publishedAtMillis)
    }

    @Test
    fun skipsEntriesWithNonWebLinksOrBlankTitles() {
        val xml = """
            <rss><channel>
              <item><title>Unsafe</title><link>javascript:alert(1)</link></item>
              <item><title> </title><link>https://example.org/blank</link></item>
            </channel></rss>
        """.trimIndent()

        assertTrue(RssFeedParser.parse(xml, source).isEmpty())
    }
}
