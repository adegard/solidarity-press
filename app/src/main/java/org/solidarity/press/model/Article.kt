package org.solidarity.press.model

import org.json.JSONObject
import java.util.Locale

data class Article(
    val key: String,
    val sourceId: String,
    val title: String,
    val link: String,
    val summary: String,
    val publishedAt: Long,
) {
    val source: Source? get() = Sources.byId(sourceId)
    val country: Country? get() = source?.country

    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("sourceId", sourceId)
        put("title", title)
        put("link", link)
        put("summary", summary)
        put("publishedAt", publishedAt)
    }

    companion object {
        const val MAX_KEEP = 500

        fun fromJson(o: JSONObject): Article = Article(
            key = o.optString("key"),
            sourceId = o.optString("sourceId"),
            title = o.optString("title"),
            link = o.optString("link"),
            summary = o.optString("summary"),
            publishedAt = o.optLong("publishedAt"),
        )

        /** Stable identity for an article: normalised link first, title as fallback. */
        fun identity(link: String, title: String, sourceId: String): String {
            val normalized = normalizeLink(link)
            val base = if (normalized.isNotBlank()) {
                normalized
            } else {
                "$sourceId|${title.trim().lowercase(Locale.ROOT)}"
            }
            return Integer.toHexString(base.hashCode()) + "-" + Integer.toHexString(sourceId.hashCode())
        }

        private val tracking = setOf(
            "cmp", "ito", "ref", "referrer", "fbclid", "gclid", "mc_cid", "mc_eid",
            "ns_campaign", "ns_mchannel", "ns_source", "at_medium", "at_campaign",
        )

        /**
         * Drops the scheme, www, the fragment and tracking parameters, so the same story
         * picked up from two feeds collapses to one row. Query parameters that carry the
         * identity are kept: some outlets (Politis) route every article through a proxy
         * URL such as /api/proxy/?articleID=1234.
         */
        fun normalizeLink(link: String): String {
            val raw = link.trim()
            if (raw.isEmpty()) return ""
            return runCatching {
                val uri = java.net.URI(raw)
                val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.")
                if (host.isNullOrBlank()) return raw
                val path = (uri.path.orEmpty()).removeSuffix("/")
                val kept = uri.rawQuery
                    ?.split('&')
                    ?.filter { pair -> pair.isNotBlank() }
                    ?.filter { pair -> pair.substringBefore('=').lowercase(Locale.ROOT) !in tracking }
                    ?.map { pair ->
                        val name = pair.substringBefore('=').lowercase(Locale.ROOT)
                        val value = pair.substringAfter('=', "")
                        if (value.isEmpty()) name else "$name=$value"
                    }
                    ?.sorted()
                    ?.joinToString("&")
                    .orEmpty()
                buildString {
                    append(host)
                    append(path)
                    if (kept.isNotEmpty()) {
                        append('?')
                        append(kept)
                    }
                }
            }.getOrDefault(raw)
        }
    }
}
