package org.solidarity.press.data

import org.solidarity.press.model.Article
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.Reader
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dependency-free RSS 2.0 / RDF / Atom parser on top of the platform XmlPullParser.
 *
 * Namespace processing is left off, so tags arrive as written; every name is therefore
 * normalised by dropping any prefix ("dc:date" -> "date", "content:encoded" -> "encoded").
 */
object FeedParser {

    fun newParser(): XmlPullParser = XmlPullParserFactory.newInstance().newPullParser()

    fun parse(input: InputStream, sourceId: String): List<Article> {
        val parser = newParser()
        parser.setInput(input, null)
        return readAll(parser, sourceId)
    }

    fun parse(reader: Reader, sourceId: String): List<Article> {
        val parser = newParser()
        parser.setInput(reader)
        return readAll(parser, sourceId)
    }

    private fun readAll(parser: XmlPullParser, sourceId: String): List<Article> {
        val out = ArrayList<Article>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = if (event == XmlPullParser.START_TAG) tagOf(parser) else null
            if (tag == "item" || tag == "entry") {
                parseEntry(parser, sourceId)?.let(out::add)
            } else {
                parser.next()
            }
            event = parser.eventType
        }
        return out
    }

    private fun tagOf(parser: XmlPullParser): String =
        parser.name?.substringAfterLast(':')?.lowercase(Locale.ROOT).orEmpty()

    private fun parseEntry(parser: XmlPullParser, sourceId: String): Article? {
        var title = ""
        var link = ""
        var summary = ""
        var date = ""
        var depth = 1 // we are positioned on <item>/<entry>, so we are already inside it

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = tagOf(parser)
            val entryTag = tag == "entry" || tag == "item"
            if (entryTag && event == XmlPullParser.START_TAG) {
                depth++
            } else if (entryTag && event == XmlPullParser.END_TAG) {
                depth--
                if (depth == 0) break
            }
            if (depth == 1 && event == XmlPullParser.START_TAG) {
                when (tag) {
                    "title" -> title = cleanText(readInnerText(parser))
                    // Atom: <link rel="alternate" href="..."/>; RSS: <link>url</link>
                    "link" -> {
                        val rel = parser.getAttributeValue(null, "rel")
                        val href = parser.getAttributeValue(null, "href")?.trim()
                        val candidate = href ?: readInnerText(parser).trim()
                        if (candidate.startsWith("http") && (rel == null || rel == "alternate")) {
                            if (link.isEmpty()) link = candidate
                        }
                    }
                    "guid", "id" -> {
                        val candidate = readInnerText(parser).trim()
                        if (link.isEmpty() && candidate.startsWith("http")) link = candidate
                    }
                    "description", "summary", "encoded", "content" -> {
                        val text = htmlToText(readInnerText(parser))
                        if (text.length > summary.length) summary = text
                    }
                    "pubdate", "published", "updated", "date", "modified" -> {
                        if (date.isEmpty()) date = readInnerText(parser)
                    }
                }
            }
            event = parser.next()
        }

        if (title.isBlank() && summary.isBlank()) return null
        if (title.isBlank()) title = summary.take(80)
        return Article(
            key = Article.identity(link, title, sourceId),
            sourceId = sourceId,
            title = title,
            link = link.ifBlank { "https://invalid.local/${'$'}{sourceId}" },
            summary = summary.take(1200),
            publishedAt = parseDate(date),
        )
    }

    /** Reads everything up to the matching END_TAG, tolerating CDATA and nested markup. */
    private fun readInnerText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (true) {
            when (parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> sb.append(parser.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> {
                    depth--
                    if (depth == 0) return sb.toString()
                }
                else -> if (parser.eventType == XmlPullParser.END_DOCUMENT) return sb.toString()
            }
        }
    }

    private val tags = Regex("<[^>]*>")

    private val entities = mapOf(
        "&nbsp;" to "\u00A0", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
        "&quot;" to "\"", "&apos;" to "'", "&laquo;" to "\u00AB", "&raquo;" to "\u00BB",
        "&hellip;" to "\u2026", "&mdash;" to "\u2014", "&ndash;" to "\u2013",
        "&lsquo;" to "\u2018", "&rsquo;" to "\u2019", "&ldquo;" to "\u201C",
        "&rdquo;" to "\u201D", "&eacute;" to "\u00E9", "&egrave;" to "\u00E8",
        "&agrave;" to "\u00E0", "&ccedil;" to "\u00E7", "&ugrave;" to "\u00F9",
        "&uuml;" to "\u00FC", "&ouml;" to "\u00F6", "&auml;" to "\u00E4",
        "&szlig;" to "\u00DF", "&euro;" to "\u20AC", "&deg;" to "\u00B0",
        "&middot;" to "\u00B7", "&bull;" to "\u2022", "&copy;" to "\u00A9",
        "&euro" to "\u20AC", "&trade;" to "\u2122", "&agrave" to "\u00E0",
    )

    private val numericEntity = Regex("&#(x?)([0-9a-fA-F]+);")

    fun htmlToText(raw: String): String {
        if (raw.isBlank()) return ""
        var s = raw
            .replace(Regex("(?is)<(script|style|figure|figcaption|noscript)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), " ")
            .replace(Regex("(?i)</(p|div|li|h[1-6]|tr|blockquote)>"), " ")
            .replace(Regex("(?i)<!--.*?-->"), " ")
        s = tags.replace(s, " ")
        s = decodeEntities(s)
        return s.replace('\u00A0', ' ').replace(Regex("[ \\t\\r\\n\\u000B\\f]+"), " ").trim()
    }

    private fun decodeEntities(input: String): String {
        var s = input
        for ((entity, char) in entities) s = s.replace(entity, char, ignoreCase = false)
        s = numericEntity.replace(s) { m ->
            val radix = if (m.groupValues[1].isEmpty()) 10 else 16
            val code = m.groupValues[2].toIntOrNull(radix) ?: return@replace ""
            if (code in 1..0x10FFFF) String(Character.toChars(code)) else ""
        }
        return s
    }

    private fun cleanText(raw: String): String = raw.replace(Regex("\\s+"), " ").trim()

    private val offsetPatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm zzz",
        "dd MMM yyyy HH:mm zzz",
    )

    private val localPatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "EEE, dd MMM yyyy HH:mm:ss",
        "EEE, d MMM yyyy HH:mm:ss",
        "dd MMM yyyy HH:mm:ss",
        "yyyy-MM-dd",
        "EEE, dd MMM yyyy",
        "dd MMM yyyy",
        "yyyy/MM/dd",
    )

    private val namedZoneFix = Regex("(?i)\\s+(GMT|UT|UTC|Z)$")
    private val dowPrefix = Regex("^[A-Za-z]{2,9},\\s*")

    fun parseDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val s = raw.trim()
        val withOffset = s.replace(namedZoneFix, " +0000")
        val candidates = listOf(s, withOffset, withOffset.replace(dowPrefix, ""), s.replace(dowPrefix, ""))

        for (candidate in candidates) {
            val v = candidate.trim()
            if (v.isEmpty()) continue
            runCatching { return OffsetDateTime.parse(v, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }
            runCatching { return Instant.parse(v).toEpochMilli() }
            runCatching { return ZonedDateTime.parse(v, DateTimeFormatter.ISO_ZONED_DATE_TIME).toInstant().toEpochMilli() }
            for (p in offsetPatterns) {
                val f = DateTimeFormatter.ofPattern(p, Locale.ENGLISH)
                runCatching { return OffsetDateTime.parse(v, f).toInstant().toEpochMilli() }
                runCatching { return ZonedDateTime.parse(v, f).toInstant().toEpochMilli() }
            }
            for (p in localPatterns) {
                val f = DateTimeFormatter.ofPattern(p, Locale.ENGLISH)
                runCatching { return LocalDateTime.parse(v, f).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                runCatching { return LocalDate.parse(v, f).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            }
        }
        return 0L
    }
}
