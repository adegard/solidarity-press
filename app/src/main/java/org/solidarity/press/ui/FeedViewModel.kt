package org.solidarity.press.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.solidarity.press.data.ArticleCache
import org.solidarity.press.data.FeedFetcher
import org.solidarity.press.data.Prefs
import org.solidarity.press.model.Article
import org.solidarity.press.model.Country
import org.solidarity.press.model.Source
import org.solidarity.press.model.Sources

data class SourceStatus(
    val sourceId: String,
    val count: Int = 0,
    val error: String? = null,
)

data class UiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val articles: List<Article> = emptyList(),
    val statuses: Map<String, SourceStatus> = emptyMap(),
    val disabled: Set<String> = emptySet(),
    val read: Set<String> = emptySet(),
    val country: Country? = null,
    val query: String = "",
    val darkMode: Boolean = false,
    val lastUpdated: Long = 0L,
    val fromCache: Boolean = false,
) {
    val visible: List<Article>
        get() {
            val q = Sources.normalized(query)
            return articles.filter { a ->
                val okCountry = country == null || a.country == country
                val okQuery = q.isEmpty() ||
                    Sources.normalized(a.title).contains(q) ||
                    Sources.normalized(a.summary).contains(q) ||
                    Sources.normalized(Sources.nameOf(a.sourceId)).contains(q)
                okCountry && okQuery
            }
        }

    val unreadCount: Int get() = visible.count { it.key !in read }
    val activeSources: List<Source> get() = Sources.ALL.filter { it.id !in disabled }
}

class FeedViewModel(app: Application) : AndroidViewModel(app) {

    private val fetcher = FeedFetcher()
    private val cache = ArticleCache(app)
    private val prefs = Prefs(app)

    private val _state = MutableStateFlow(
        UiState(
            disabled = prefs.disabledSources,
            read = prefs.readArticles,
            darkMode = prefs.darkMode,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val gateway = Semaphore(MAX_PARALLEL)

    init {
        val snapshot = cache.load()
        if (snapshot != null && snapshot.articles.isNotEmpty()) {
            _state.value = _state.value.copy(
                loading = false,
                articles = snapshot.articles.sortedByDescending { it.publishedAt },
                lastUpdated = snapshot.fetchedAt,
                fromCache = true,
            )
            if (prefs.shouldAutoRefresh(snapshot.fetchedAt)) refresh()
        } else {
            refresh()
        }
    }

    fun refresh() {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            val startedAt = System.currentTimeMillis()
            val enabled = _state.value.activeSources
            val results = coroutineScope {
                enabled.map { source ->
                    async(Dispatchers.IO) {
                        val status = runCatching {
                            gateway.withPermit { fetcher.fetch(source) }
                        }
                        source to status
                    }
                }.awaitAll()
            }

            val statuses = LinkedHashMap<String, SourceStatus>()
            val fresh = ArrayList<Article>()
            var anyOk = false
            results.forEach { (source, result) ->
                result.onSuccess { items ->
                    anyOk = true
                    statuses[source.id] = SourceStatus(source.id, items.size)
                    fresh.addAll(items)
                    Log.i(TAG, "${source.id}: ${items.size} items")
                }
                result.onFailure { e ->
                    val message = e.message?.take(80) ?: "error"
                    statuses[source.id] = SourceStatus(source.id, 0, message)
                    Log.w(TAG, "${source.id} FAILED: $message")
                }
            }
            Log.i(
                TAG,
                "refresh done in ${System.currentTimeMillis() - startedAt}ms: " +
                    "${fresh.size} fresh items from ${statuses.count { it.value.error == null }}/${enabled.size} sources",
            )

            if (!anyOk && _state.value.articles.isEmpty()) {
                _state.value = _state.value.copy(
                    refreshing = false,
                    loading = false,
                    statuses = statuses,
                )
                return@launch
            }

            val merged = merge(fresh, _state.value.articles)
            val now = System.currentTimeMillis()
            _state.value = _state.value.copy(
                refreshing = false,
                loading = false,
                articles = merged,
                statuses = statuses,
                lastUpdated = now,
                fromCache = false,
            )
            withContext(Dispatchers.IO) { cache.save(merged, now) }
        }
    }

    /** Fresh articles win; older cache entries survive so slow feeds still show something. */
    private fun merge(fresh: List<Article>, previous: List<Article>): List<Article> {
        val seen = HashSet<String>(fresh.size * 2)
        val out = ArrayList<Article>(fresh.size)
        fresh.sortedByDescending { it.publishedAt }.forEach {
            if (seen.add(it.key)) out.add(it)
        }
        previous.sortedByDescending { it.publishedAt }.forEach {
            if (seen.add(it.key)) out.add(it)
        }
        return out.sortedByDescending { it.publishedAt }.take(Article.MAX_KEEP)
    }

    fun setCountry(country: Country?) {
        _state.value = _state.value.copy(country = country)
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    fun toggleSource(id: String) {
        val next = HashSet(_state.value.disabled)
        if (!next.remove(id)) next.add(id)
        prefs.disabledSources = next
        _state.value = _state.value.copy(disabled = next)
    }

    fun enableAll() {
        prefs.disabledSources = emptySet()
        _state.value = _state.value.copy(disabled = emptySet())
        refresh()
    }

    fun markRead(key: String) {
        if (key in _state.value.read) return
        val next = HashSet(_state.value.read)
        next.add(key)
        persistRead(next)
    }

    fun markAllRead() {
        val next = HashSet(_state.value.read)
        _state.value.visible.forEach { next.add(it.key) }
        persistRead(next)
    }

    /** Keeps the persisted set from growing forever: 3000 keys is ~2 years of daily reading. */
    private fun persistRead(keys: Set<String>) {
        val trimmed = if (keys.size > MAX_READ) {
            val recent = _state.value.visible.map { it.key }
            LinkedHashSet<String>(recent).apply { keys.forEach { add(it) } }.take(MAX_READ).toSet()
        } else {
            keys
        }
        prefs.readArticles = trimmed
        _state.value = _state.value.copy(read = trimmed)
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.darkMode = enabled
        _state.value = _state.value.copy(darkMode = enabled)
    }

    private companion object {
        const val TAG = "SolidarityFeed"
        const val MAX_PARALLEL = 3
        const val MAX_READ = 3000
    }
}
