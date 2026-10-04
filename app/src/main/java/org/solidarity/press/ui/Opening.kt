package org.solidarity.press.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import org.solidarity.press.model.Article

fun openArticle(context: Context, article: Article, toolbarColor: Int) {
    val uri = runCatching { Uri.parse(article.link) }.getOrNull() ?: return
    val params = CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build()
    val customTab = CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(params)
        .setShowTitle(true)
        .setUrlBarHidingEnabled(true)
        .build()
    val launched = runCatching { customTab.launchUrl(context, uri) }.isSuccess
    if (!launched) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}

fun shareArticle(context: Context, article: Article) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, article.title)
        putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.link}")
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share article")) }
}
