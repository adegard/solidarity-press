package org.solidarity.press.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.solidarity.press.model.Article
import org.solidarity.press.model.Source
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class FeedFetchException(message: String) : Exception(message)

class FeedFetcher {

    /**
     * Some hosts drop the first TLS handshake under load, so one silent retry is cheaper
     * than showing the reader an "unreachable" badge.
     */
    suspend fun fetch(source: Source): List<Article> {
        var lastError: FeedFetchException? = null
        repeat(2) { attempt ->
            try {
                return fetchOnce(source)
            } catch (e: FeedFetchException) {
                lastError = e
                Log.w(TAG, "${source.id} attempt ${attempt + 1} failed: ${e.message}")
                if (attempt == 0) delay(600)
            }
        }
        throw lastError ?: FeedFetchException("unknown error")
    }

    private suspend fun fetchOnce(source: Source): List<Article> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(source.feedUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 25_000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) SolidarityPress/1.0 (RSS reader)",
                )
                setRequestProperty(
                    "Accept",
                    "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, */*;q=0.5",
                )
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("Accept-Language", "it,it;q=0.9,en;q=0.8,fr;q=0.7")
            }
            val code = conn.responseCode
            if (code !in 200..299) throw FeedFetchException("HTTP $code")
            var stream: InputStream = BufferedInputStream(conn.inputStream)
            val encoding = conn.contentEncoding
            if (encoding != null && encoding.contains("gzip", ignoreCase = true)) {
                stream = GZIPInputStream(stream)
            }
            val bytes = stream.readLimited(MAX_BYTES)
            val charset = detectCharset(conn.contentType, bytes)
            val parsed = FeedParser.parse(
                InputStreamReader(ByteArrayInputStream(bytes), charset),
                source.id,
            )
            if (parsed.isEmpty()) throw FeedFetchException("no items")
            parsed
        } catch (e: FeedFetchException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message?.take(120) ?: e.javaClass.simpleName
            throw FeedFetchException(msg)
        } finally {
            conn?.disconnect()
        }
    }

    private fun detectCharset(contentType: String?, bytes: ByteArray): java.nio.charset.Charset {
        contentType?.let {
            Regex("charset=[\"']?([A-Za-z0-9_\\-]+)", RegexOption.IGNORE_CASE)
                .find(it)?.groupValues?.get(1)?.let { name ->
                    runCatching { return java.nio.charset.Charset.forName(name) }
                }
        }
        val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1)
        Regex("encoding=[\"']([A-Za-z0-9_\\-]+)[\"']", RegexOption.IGNORE_CASE)
            .find(head)?.groupValues?.get(1)?.let { name ->
                runCatching { return java.nio.charset.Charset.forName(name) }
            }
        return Charsets.UTF_8
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(64 * 1024)
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw FeedFetchException("feed too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val TAG = "FeedFetcher"
        const val MAX_BYTES = 6 * 1024 * 1024
    }
}
