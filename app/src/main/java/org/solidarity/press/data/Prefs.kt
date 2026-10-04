package org.solidarity.press.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("solidarity", Context.MODE_PRIVATE)

    var disabledSources: Set<String>
        get() = sp.getStringSet(KEY_DISABLED, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_DISABLED, value).apply()

    var readArticles: Set<String>
        get() = sp.getStringSet(KEY_READ, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_READ, value).apply()

    var darkMode: Boolean
        get() = sp.getBoolean(KEY_DARK, false)
        set(value) = sp.edit().putBoolean(KEY_DARK, value).apply()

    /** Auto-refresh on launch, at most once every [minIntervalMs]. */
    fun shouldAutoRefresh(lastRefresh: Long, minIntervalMs: Long = 15 * 60_000L): Boolean =
        System.currentTimeMillis() - lastRefresh > minIntervalMs

    private companion object {
        const val KEY_DISABLED = "disabled_sources"
        const val KEY_READ = "read_articles"
        const val KEY_DARK = "dark_mode"
    }
}
