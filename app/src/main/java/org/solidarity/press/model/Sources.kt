package org.solidarity.press.model

import java.util.Locale

enum class Country(val label: String, val flag: String) {
    IT("Italia", "\uD83C\uDDEE\uD83C\uDDF9"),
    FR("France", "\uD83C\uDDEB\uD83C\uDDF7"),
    UK("United Kingdom", "\uD83C\uDDEC\uD83C\uDDE7");

    val tag: String get() = name
}

data class Source(
    val id: String,
    val name: String,
    val country: Country,
    val feedUrl: String,
    val siteUrl: String,
) {
    val host: String get() = siteUrl.substringAfter("//").substringBefore("/")
    val initials: String
        get() = name.split(' ', '\u2019').filter { it.isNotBlank() }
            .take(2).joinToString("") { it.first().uppercase() }
            .ifBlank { name.take(2).uppercase() }
}

/**
 * Hand curated roster: independent, left-of-centre / social-democratic / green outlets.
 * No party-mouthpieces of extremist tendencies, no aggregators, no paywall-only giants
 * beyond the ones whose RSS stays public.
 */
object Sources {

    val ALL: List<Source> = listOf(
        // ---------------- Italy ----------------
        Source(
            id = "ilfatto",
            name = "il Fatto Quotidiano",
            country = Country.IT,
            feedUrl = "https://www.ilfattoquotidiano.it/feed/",
            siteUrl = "https://www.ilfattoquotidiano.it",
        ),
        Source(
            id = "ilmanifesto",
            name = "il Manifesto",
            country = Country.IT,
            feedUrl = "https://www.ilmanifesto.it/feed/",
            siteUrl = "https://www.ilmanifesto.it",
        ),
        Source(
            id = "micromega",
            name = "Micromega",
            country = Country.IT,
            feedUrl = "https://www.micromega.net/blog/feed/",
            siteUrl = "https://www.micromega.net",
        ),

        // ---------------- France ----------------
        Source(
            id = "lemonde",
            name = "Le Monde",
            country = Country.FR,
            feedUrl = "https://www.lemonde.fr/international/rss_full.xml",
            siteUrl = "https://www.lemonde.fr",
        ),
        Source(
            id = "humanite",
            name = "l'Humanite",
            country = Country.FR,
            feedUrl = "https://www.humanite.fr/rss/",
            siteUrl = "https://www.humanite.fr",
        ),
        Source(
            id = "politis",
            name = "Politis",
            country = Country.FR,
            feedUrl = "https://www.politis.fr/flux-rss/",
            siteUrl = "https://www.politis.fr",
        ),

        // ---------------- United Kingdom ----------------
        Source(
            id = "guardian-uk",
            name = "The Guardian",
            country = Country.UK,
            feedUrl = "https://www.theguardian.com/uk/rss",
            siteUrl = "https://www.theguardian.com/uk",
        ),
        Source(
            id = "guardian-europe",
            name = "The Guardian Europe",
            country = Country.UK,
            feedUrl = "https://www.theguardian.com/europe/rss",
            siteUrl = "https://www.theguardian.com/europe",
        ),
        Source(
            id = "newstatesman",
            name = "New Statesman",
            country = Country.UK,
            feedUrl = "https://www.newstatesman.com/feed/",
            siteUrl = "https://www.newstatesman.com",
        ),
        Source(
            id = "redpepper",
            name = "Red Pepper",
            country = Country.UK,
            feedUrl = "https://www.redpepper.org.uk/feed/",
            siteUrl = "https://www.redpepper.org.uk",
        ),
        Source(
            id = "tribune",
            name = "Tribune",
            country = Country.UK,
            feedUrl = "https://tribunemag.co.uk/feed/",
            siteUrl = "https://tribunemag.co.uk",
        ),
        Source(
            id = "declassified",
            name = "Declassified UK",
            country = Country.UK,
            feedUrl = "https://declassifieduk.org/feed/",
            siteUrl = "https://declassifieduk.org",
        ),
        Source(
            id = "middleeasteye",
            name = "Middle East Eye",
            country = Country.UK,
            feedUrl = "https://www.middleeasteye.net/rss",
            siteUrl = "https://www.middleeasteye.net",
        ),
        Source(
            id = "opendemocracy",
            name = "openDemocracy",
            country = Country.UK,
            feedUrl = "https://www.opendemocracy.net/en/feed/",
            siteUrl = "https://www.opendemocracy.net",
        ),
        Source(
            id = "zeteo",
            name = "Zeteo",
            country = Country.UK,
            feedUrl = "https://zeteo.com/feed/",
            siteUrl = "https://zeteo.com",
        ),
        Source(
            id = "jacobin",
            name = "Jacobin",
            country = Country.UK,
            feedUrl = "https://jacobin.com/feed/",
            siteUrl = "https://jacobin.com",
        ),
    )

    private val byId: Map<String, Source> = ALL.associateBy { it.id }

    fun byId(id: String): Source? = byId[id]
    fun nameOf(id: String): String = byId(id)?.name ?: id

    fun countryOf(id: String): Country? = byId(id)?.country

    fun normalized(text: String): String = text
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\u0300-\\u036f]"), "")
}
