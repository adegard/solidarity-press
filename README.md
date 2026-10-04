# Solidarity

An Android news reader that merges the RSS/Atom feeds of **independent, left-of-centre**
newspapers from Italy, France and the UK** into a single chronological stream.

Kotlin + Jetpack Compose (Material 3). No news API, no scraping, no tracking: it talks
directly to the ~16 publishers that make up the roster.

---

## Sources

All outlets are independent of their owners' political sponsors, left-of-centre or further
left, and none of them is an extremist or party-mouthpiece organ.

| Country | Outlet | Site |
|---|---|---|
| Italy | il Fatto Quotidiano | <https://www.ilfattoquotidiano.it> |
| Italy | il Manifesto | <https://www.ilmanifesto.it> |
| Italy | Micromega | <https://www.micromega.net> |
| France | Le Monde | <https://www.lemonde.fr> |
| France | l'Humanité | <https://www.humanite.fr> |
| France | Politis | <https://www.politis.fr> |
| UK | The Guardian (UK) | <https://www.theguardian.com/uk> |
| UK | The Guardian (Europe) | <https://www.theguardian.com/europe> |
| UK | New Statesman | <https://www.newstatesman.com> |
| UK | Red Pepper | <https://www.redpepper.org.uk> |
| UK | Tribune | <https://tribunemag.co.uk> |
| UK | Declassified UK | <https://declassifieduk.org> |
| UK | Middle East Eye | <https://www.middleeasteye.net> |
| UK | openDemocracy | <https://www.opendemocracy.net> |
| UK | Zeteo | <https://zeteo.com> |
| UK | Jacobin | <https://jacobin.com> |

The list lives in one place: [`model/Sources.kt`](app/src/main/java/org/solidarity/press/model/Sources.kt).

---

## Features

- One merged feed, grouped by day, newest first, with per-story source, country colour and
  reading-time-relative timestamp.
- **Country filter** (All / Italia / France / UK) with live article counts.
- **Search** across headlines, summaries and source names.
- **Per-source switches** and live status: item count, or the exact reason a feed failed.
- **Reader view** with the headline, source, timestamp and the summary shipped in the feed,
  plus *Read the full article* (opens in a Custom Tab, falls back to any installed browser)
  and share.
- **Read/unread** tracking, persisted, with a *mark all read* action.
- **Offline**: the last successful refresh is cached in internal storage, so the app opens
  instantly and shows yesterday's headlines with no connection.
- **Dark mode** toggle, edge-to-edge, pull-to-refresh, adaptive launcher icon.

---

## Build

Requirements: **JDK 17**, **Gradle 8.7**, **Android SDK** with platform 34 and build-tools 35.
No Gradle wrapper is committed yet — either use your own `gradle 8.7`, or generate one with
`gradle wrapper --gradle-version 8.7` and commit the result.

`local.properties` is gitignored, so point Gradle at your SDK either with a `local.properties`
containing `sdk.dir=/path/to/Android/Sdk`, or by exporting `ANDROID_HOME`.

```bash
# assemble
gradle assembleDebug

# run the parser tests against the checked-in feed snapshots
gradle testDebugUnitTest
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Install on a device

```bash
./install.sh                # single connected device
./install.sh <serial>       # pick explicitly when several are attached
```

The script builds, refuses to install on TV boxes (`FORCE=1` overrides), installs with
`adb install -r -t` and launches `org.solidarity.press/.MainActivity`.

```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.solidarity.press/.MainActivity
```

**Manifest:** `minSdk 26` (Android 8) · `targetSdk 34` · permissions: `INTERNET`,
`ACCESS_NETWORK_STATE`.

---

## Tests

[`FeedParserTest`](app/src/test/java/org/solidarity/press/data/FeedParserTest.kt) parses a real
snapshot of **every** feed in the roster — the XML files are committed under
`app/src/test/resources/feeds/` — and asserts that each one yields:

- non-empty results, titles longer than 3 characters, links starting with `http`
- dates parsed to sane timestamps (and not in the future)
- no two different articles collapsing to the same identity key
- at least 90% distinct articles per feed

plus standalone tests for HTML/entity stripping, Atom vs RSS shapes, and URL normalisation.

This suite is the reason two real bugs never reached a phone: a depth counter that made *every*
feed parse to zero items, and identity normalisation that collapsed Politis' 200 articles into
one because that site routes every story through `/api/proxy/?articleID=…`.

---

## How it works

```
model/Sources.kt     the roster (id, name, country, feed URL, site URL)
model/Article.kt     article model + stable identity + link normalisation
data/FeedParser.kt   RSS 2.0 / RDF / Atom on the platform XmlPullParser
data/FeedFetcher.kt  HttpURLConnection: gzip, redirects, charset sniffing, timeouts, retry
data/ArticleCache.kt JSON snapshot in internal storage
data/Prefs.kt        read/unread, source switches, theme
ui/FeedViewModel.kt  StateFlow state, concurrent fetching, merge with previous snapshot
ui/FeedScreen.kt     Compose UI: feed, filters, sources sheet, reader
```

Notable details:

- **No third-party networking or XML libraries.** `HttpURLConnection` plus
  `org.xmlpull.v1.XmlPullParser` covers everything; the parser is shared with the JVM unit
  tests via `XmlPullParserFactory` (kxml2 in `testImplementation`).
- **Namespace handling is off**, so tags arrive as written and every name is normalised by
  dropping the prefix — `dc:date` → `date`, `content:encoded` → `encoded`.
- **Dates**: ISO-8601, RFC-822/1123 with numeric *and* named zones (`GMT`, `UT`, `UTC`, `Z`),
  single- and double-digit days, with and without seconds.
- **Charsets** are taken from the `Content-Type` header, then from the XML declaration, then
  UTF-8.
- **Identity** drops scheme, `www`, the fragment and tracking parameters (`utm_*`, `fbclid`,
  `gclid`, …) so the same story from two feeds shows once — but it *keeps* query parameters
  that carry the story id.
- **Partial failure is expected.** Sources are fetched with a bounded concurrency and one
  silent retry; a source that fails shows its error in the sources sheet and the rest of the
  feed still renders. The cached snapshot is merged with the fresh results, so a source that
  timed out keeps showing its last known articles.

---

## Adding or removing a source

Append a `Source(...)` to `Sources.ALL`, then refresh the fixture so the tests cover it:

```bash
curl -sSL -A 'Mozilla/5.0' "<feed-url>" -o app/src/test/resources/feeds/<id>.xml
gradle testDebugUnitTest      # prints item counts and a sample headline per feed
```

The test output prints one sample headline per source, which is the quickest way to notice that
a feed has been hijacked. (This is not hypothetical: `greenleft.org.uk` used to be in the
roster until its domain expired and started serving Vietnamese gambling ads — the fixture
caught it.)

## Known limitations

- Fetch concurrency is capped at 3 (`MAX_PARALLEL` in `FeedViewModel`). Under higher
  concurrency several publishers rate-limited the device and only ~6 of 16 feeds came back in
  one refresh; this value has not been re-tuned since.
- Publisher feeds change without notice; a source can break between releases. Errors are shown
  per source rather than swallowed.
- Some feeds only carry the first paragraph (Le Monde, the Guardian) or no summary at all
  (Politis audio items), so previews vary in length.
- No image support, no offline article archiving, no notifications.

## License

No license file yet — treat the code as all rights reserved until one is added.
