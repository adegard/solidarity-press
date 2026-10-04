package org.solidarity.press.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.solidarity.press.model.Article
import org.solidarity.press.model.Sources
import org.solidarity.press.ui.TimeText
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.time.LocalDate

/**
 * Parses real snapshots of every feed in the roster (app/src/test/resources/feeds/<id>.xml),
 * so a change to the parser cannot silently ship an empty or garbled feed.
 */
class FeedParserTest {

    private fun fixture(id: String): ByteArray {
        val stream = javaClass.classLoader!!.getResourceAsStream("feeds/$id.xml")
            ?: error("missing fixture for $id")
        return stream.use { it.readBytes() }
    }

    private fun charsetOf(bytes: ByteArray): Charset {
        val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1)
        val m = Regex("encoding=[\"']([A-Za-z0-9_\\-]+)[\"']", RegexOption.IGNORE_CASE).find(head)
        return m?.groupValues?.get(1)?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
    }

    @Test
    fun `every source feed parses into usable articles`() {
        for (source in Sources.ALL) {
            val bytes = fixture(source.id)
            val articles = FeedParser.parse(
                InputStreamReader(ByteArrayInputStream(bytes), charsetOf(bytes)),
                source.id,
            )
            assertTrue("${source.id}: no items parsed", articles.isNotEmpty())
            println("${source.id}: ${articles.size} items, charset=${charsetOf(bytes)}")

            assertTrue("${source.id}: titles too short", articles.all { it.title.length > 3 })
            assertTrue("${source.id}: bad link", articles.all { it.link.startsWith("http") })

            articles.groupBy { it.key }.filterValues { it.size > 1 }.forEach { (key, dupes) ->
                assertEquals(
                    "${source.id}: colliding key $key for different articles",
                    1,
                    dupes.map { it.link }.toSet().size,
                )
            }
            // Identity must keep query parameters that carry the article, otherwise every
            // story of a proxy-based feed (Politis /api/proxy/?articleID=) merges into one.
            assertTrue(
                "${source.id}: only ${articles.map { it.key }.toSet().size} distinct articles out of ${articles.size}",
                articles.map { it.key }.toSet().size >= (articles.size * 0.9).toInt(),
            )

            val dated = articles.count { it.publishedAt > 0 }
            assertTrue("${source.id}: no dates parsed ($dated/${articles.size})", dated >= articles.size / 2)

            val fresh = articles.filter { it.publishedAt > 0 }.map { it.publishedAt }
            // "Dates are sane", not a freshness SLA: some blogs publish once a year.
            val newest = fresh.maxOrNull() ?: 0L
            assertTrue(
                "${source.id}: newest item dated ${TimeText.full(newest)}",
                newest > System.currentTimeMillis() - 1825L * 24 * 3600 * 1000,
            )
            assertTrue(
                "${source.id}: implausible dates",
                fresh.all {
                    it < System.currentTimeMillis() + 2L * 24 * 3600 * 1000 &&
                        it > (LocalDate.of(2005, 1, 1).toEpochDay() * 86_400_000L)
                },
            )

            val withSummary = articles.count { it.summary.isNotBlank() }
            println("    ${source.id}: summaries $withSummary/${articles.size}, sample: ${articles.first().title.take(70)}")
        }
    }

    @Test
    fun `entities inside a feed survive the xml layer exactly once`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>t</title>
            <item>
              <title>Garzia &amp; company &#39;quoted&#39;</title>
              <link>https://example.org/a?b=1&amp;c=2</link>
              <description><![CDATA[<p>Città &amp; caff&egrave; &#8212; 50% <b>off</b></p>]]></description>
              <pubDate>Tue, 04 Mar 2025 07:15:00 +0000</pubDate>
            </item>
            </channel></rss>
        """.trimIndent()

        val articles = FeedParser.parse(xml.byteInputStream(), "test")
        assertEquals(1, articles.size)
        val article = articles.single()
        assertEquals("Garzia & company 'quoted'", article.title)
        assertEquals("Città & caffè \u2014 50% off", article.summary)
        assertEquals("https://example.org/a?b=1&c=2", article.link)
        assertTrue(article.publishedAt > 0)
    }

    @Test
    fun `atom entries are parsed like rss items`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Example</title>
              <entry>
                <title type="text">Atom headline</title>
                <link rel="self" href="https://example.org/self"/>
                <link rel="alternate" type="text/html" href="https://example.org/post-1"/>
                <summary>Atom summary text</summary>
                <published>2025-03-04T07:15:00Z</published>
              </entry>
            </feed>
        """.trimIndent()

        val articles = FeedParser.parse(xml.byteInputStream(), "test")
        assertEquals(1, articles.size)
        assertEquals("Atom headline", articles.single().title)
        assertEquals("https://example.org/post-1", articles.single().link)
        assertEquals("Atom summary text", articles.single().summary)
        assertTrue(articles.single().publishedAt > 0)
    }

    @Test
    fun `link normalisation keeps identity but drops tracking noise`() {
        // Same story via two feeds -> one row.
        assertEquals(
            Article.identity("https://www.example.org/story/", "A", "s1"),
            Article.identity("http://example.org/story", "B", "s1"),
        )
        // Tracking parameters must not create a second copy.
        assertEquals(
            Article.identity("https://example.org/story?utm_source=rss&utm_medium=feed", "A", "s1"),
            Article.identity("https://example.org/story", "A", "s1"),
        )
        // Query parameters that carry the identity must be preserved (Politis proxy pattern).
        val a = Article.identity("https://www.politis.fr/api/proxy/?articleID=1", "A", "politis")
        val b = Article.identity("https://www.politis.fr/api/proxy/?articleID=2", "B", "politis")
        assertNotEquals(a, b)
        // Same URL on different sources stays distinct.
        assertNotEquals(
            Article.identity("https://example.org/story", "A", "s1"),
            Article.identity("https://example.org/story", "A", "s2"),
        )
    }

    @Test
    fun `summaries are stripped of markup and entities`() {
        val text = FeedParser.htmlToText(
            "<p>Hello&nbsp;&amp; <b>welcome</b></p><br/><div>Caf&#233; &#8217;2024&#8217;</div>" +
                "<script>bad()</script><ul><li>One</li><li>Two</li></ul>"
        )
        assertEquals("Hello & welcome Caf\u00E9 \u20192024\u2019 One Two", text)
    }

    @Test
    fun `dates understand the formats real feeds emit`() {
        assertNotEquals(0L, FeedParser.parseDate("Tue, 04 Mar 2025 08:15:00 +0100"))
        assertNotEquals(0L, FeedParser.parseDate("Tue, 04 Mar 2025 08:15:00 GMT"))
        assertNotEquals(0L, FeedParser.parseDate("Tue, 4 Mar 2025 08:15:00 +0000"))
        assertNotEquals(0L, FeedParser.parseDate("2025-03-04T08:15:00+01:00"))
        assertNotEquals(0L, FeedParser.parseDate("2025-03-04T07:15:00Z"))
        assertNotEquals(0L, FeedParser.parseDate("2025-03-04T06:15:00.123Z"))
        assertNotEquals(0L, FeedParser.parseDate("2025-03-04"))
        assertEquals(0L, FeedParser.parseDate(""))
        assertEquals(0L, FeedParser.parseDate(null))
        assertEquals(0L, FeedParser.parseDate("sometime last week"))

        // Same instant expressed two ways must resolve to the same millisecond.
        assertEquals(
            FeedParser.parseDate("Tue, 04 Mar 2025 07:15:00 +0000"),
            FeedParser.parseDate("2025-03-04T07:15:00Z"),
        )
        // GMT is an alias for UTC, not the local zone.
        assertEquals(
            FeedParser.parseDate("2025-03-04T07:15:00Z"),
            FeedParser.parseDate("Tue, 04 Mar 2025 07:15:00 GMT"),
        )
    }
}
