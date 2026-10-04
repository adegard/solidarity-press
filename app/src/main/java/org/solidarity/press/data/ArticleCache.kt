package org.solidarity.press.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.solidarity.press.model.Article
import java.io.File

/** Last good snapshot of the aggregated feed, so the app opens instantly and works offline. */
class ArticleCache(context: Context) {

    private val file = File(context.filesDir, "feed_cache.json")

    data class Snapshot(val fetchedAt: Long, val articles: List<Article>)

    fun load(): Snapshot? {
        if (!file.exists()) return null
        return runCatching {
            val root = JSONObject(file.readText())
            val array: JSONArray = root.getJSONArray("articles")
            val list = ArrayList<Article>(array.length())
            for (i in 0 until array.length()) {
                list.add(Article.fromJson(array.getJSONObject(i)))
            }
            Snapshot(root.optLong("fetchedAt"), list)
        }.onFailure { Log.w(TAG, "cache unreadable: ${it.message}") }.getOrNull()
    }

    fun save(articles: List<Article>, fetchedAt: Long) {
        runCatching {
            val array = JSONArray()
            articles.take(Article.MAX_KEEP).forEach { array.put(it.toJson()) }
            val root = JSONObject().apply {
                put("fetchedAt", fetchedAt)
                put("articles", array)
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(file)
        }.onFailure { Log.w(TAG, "cache not saved: ${it.message}") }
    }

    fun clear() {
        runCatching { file.delete() }
    }

    private companion object {
        const val TAG = "ArticleCache"
    }
}
