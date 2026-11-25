package com.arcturus.haven.launcher.core.data

import com.arcturus.haven.launcher.core.model.NewsFeedArticle
import com.arcturus.haven.launcher.core.model.NewsFeedSource
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

object RssFeedParser {
    private val HTML_TAG_REGEX = Regex("<[^>]*>")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val DATE_TAGS = listOf("pubDate", "published", "updated", "date")
    private val DESCRIPTION_TAGS = listOf("description", "summary", "encoded", "content")

    fun parse(xml: String, source: NewsFeedSource): List<NewsFeedArticle> {
        require(xml.isNotBlank()) { "The feed is empty." }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
        val document = builder.parse(InputSource(StringReader(xml)))
        val elements = document.getElementsByTagName("item").asSequence().toList().ifEmpty {
            document.getElementsByTagNameNS("*", "entry").asSequence().toList()
        }

        return elements.mapNotNull { node ->
            val element = node as? Element ?: return@mapNotNull null
            val title = element.childText("title").cleanMarkup()
            val link = element.childElements().firstOrNull { it.localTagName() == "link" }?.let { linkNode ->
                linkNode.getAttribute("href").takeIf(String::isNotBlank) ?: linkNode.textContent?.trim().orEmpty()
            }.orEmpty().ifBlank { element.childText("guid") }
            if (title.isBlank() || (!link.startsWith("http://") && !link.startsWith("https://"))) {
                return@mapNotNull null
            }
            val date = DATE_TAGS.firstNotNullOfOrNull { element.childText(it).parseTimestamp() } ?: 0L
            val description = DESCRIPTION_TAGS.asSequence().map { element.childText(it) }
                .firstOrNull(String::isNotBlank).orEmpty().cleanMarkup()
            NewsFeedArticle(source.id, source.title, title, link, date, description)
        }.distinctBy(NewsFeedArticle::id)
    }

    private fun Element.childText(tag: String): String = childElements()
        .firstOrNull { it.localTagName() == tag || it.nodeName.substringAfter(':') == tag }
        ?.textContent?.trim().orEmpty()

    private fun Element.childElements(): Sequence<Element> = childNodes.asSequence().filterIsInstance<Element>()

    private fun Element.localTagName(): String = (localName ?: nodeName).substringAfter(':').lowercase(Locale.ROOT)

    private fun String.cleanMarkup(): String = replace(HTML_TAG_REGEX, " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(WHITESPACE_REGEX, " ")
        .trim()

    private fun String.parseTimestamp(): Long? = if (isBlank()) {
        null
    } else {
        runCatching { ZonedDateTime.parse(this, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(this, DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
    }

    private fun NodeList.asSequence(): Sequence<Node> = sequence {
        for (index in 0 until length) yield(item(index))
    }
}
