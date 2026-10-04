package org.solidarity.press.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.solidarity.press.model.Article
import org.solidarity.press.model.Country
import org.solidarity.press.model.Sources

@Composable
fun SolidarityApp(viewModel: FeedViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var selected by remember { mutableStateOf<Article?>(null) }

    SolidarityTheme(dark = state.darkMode) {
        Surface(color = MaterialTheme.colorScheme.background) {
            AnimatedContent(targetState = selected, label = "root") { target ->
                if (target == null) {
                    FeedScreen(state = state, viewModel = viewModel, onOpen = { selected = it })
                } else {
                    ArticleScreen(article = target, onBack = { selected = null })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedScreen(state: UiState, viewModel: FeedViewModel, onOpen: (Article) -> Unit) {
    var sourcesOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val unread = state.unreadCount

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                title = {
                    Column {
                        Text("Solidarity", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = headerLine(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) viewModel.setQuery("")
                    }) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = if (searchOpen) "Close search" else "Search",
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh feeds")
                    }
                    IconButton(onClick = { sourcesOpen = true }) {
                        Icon(Icons.Outlined.RssFeed, contentDescription = "Sources")
                    }
                    IconButton(onClick = { viewModel.setDarkMode(!state.darkMode) }) {
                        Icon(
                            imageVector = if (state.darkMode) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = "Toggle theme",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (unread > 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.markAllRead()
                        Toast.makeText(context, "All articles marked as read", Toast.LENGTH_SHORT).show()
                    },
                    icon = { Icon(Icons.Outlined.DoneAll, contentDescription = null) },
                    text = { Text("$unread unread") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (searchOpen) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    singleLine = true,
                    placeholder = { Text("Search headlines, sources, topics") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    shape = RoundedCornerShape(14.dp),
                )
            }

            CountryFilters(
                selected = state.country,
                articles = state.articles,
                enabledSources = state.activeSources.map { it.id }.toSet(),
                onSelect = viewModel::setCountry,
            )

            val failures = state.statuses.values.count { it.error != null }
            if (failures > 0 && state.articles.isNotEmpty()) {
                FailureNotice(failures = failures, onClick = { sourcesOpen = true })
            }

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.loading && state.articles.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.visible.isEmpty()) {
                    EmptyState(state = state, onRetry = viewModel::refresh)
                } else {
                    ArticleList(
                        articles = state.visible,
                        read = state.read,
                        onOpen = onOpen,
                    )
                }
            }
        }
    }

    if (sourcesOpen) {
        SourcesSheet(state = state, viewModel = viewModel, onDismiss = { sourcesOpen = false })
    }
}

private fun headerLine(state: UiState): String {
    val count = state.articles.size
    val sources = state.activeSources.size
    val stamp = when {
        state.refreshing -> "refreshing..."
        state.lastUpdated <= 0L -> "no data yet"
        else -> TimeText.relative(state.lastUpdated)
    }
    return "$count articles - $sources/${Sources.ALL.size} sources - $stamp"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryFilters(
    selected: Country?,
    articles: List<Article>,
    enabledSources: Set<String>,
    onSelect: (Country?) -> Unit,
) {
    val counts = remember(articles, enabledSources) {
        articles.filter { it.sourceId in enabledSources }
            .groupingBy { it.country }
            .eachCount()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("All ${counts.values.sum()}") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
        Country.entries.forEach { country ->
            val n = counts[country] ?: 0
            FilterChip(
                selected = selected == country,
                onClick = { onSelect(if (selected == country) null else country) },
                label = { Text("${country.flag} $n") },
                enabled = n > 0 || selected == country,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = countryColor(country).copy(alpha = 0.18f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

@Composable
private fun FailureNotice(failures: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "$failures source(s) unreachable - tap for details",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(state: UiState, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.Newspaper,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (state.query.isNotBlank()) "Nothing matches \"${state.query}\""
                else if (state.activeSources.isEmpty()) "All sources are switched off"
                else "No articles loaded yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (state.query.isNotBlank()) "Try a different word or clear the country filter."
                else "Pull down to fetch the latest headlines.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.query.isEmpty() && state.activeSources.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ArticleList(articles: List<Article>, read: Set<String>, onOpen: (Article) -> Unit) {
    val grouped = remember(articles) { articles.groupBy { TimeText.dayHeader(it.publishedAt) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        grouped.forEach { (day, itemsInDay) ->
            stickyHeader(key = "header-$day") { DayHeader(day, itemsInDay.size) }
            items(itemsInDay, key = { it.key }) { article ->
                ArticleRow(
                    article = article,
                    isRead = article.key in read,
                    onClick = { onOpen(article) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun DayHeader(day: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun ArticleRow(article: Article, isRead: Boolean, onClick: () -> Unit) {
    val accent = countryColor(article.country)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isRead) accent.copy(alpha = 0.35f) else accent),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${article.country?.flag.orEmpty()} ${Sources.nameOf(article.sourceId)}".trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = TimeText.relative(article.publishedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (!isRead) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            if (article.summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = article.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcesSheet(state: UiState, viewModel: FeedViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text("Sources", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${state.activeSources.size} of ${Sources.ALL.size} active - independent, left-of-centre press",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Sources.ALL.forEach { source ->
                val status = state.statuses[source.id]
                val enabled = source.id !in state.disabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(countryColor(source.country).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = source.country.flag,
                            fontSize = 16.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = source.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = when {
                                !enabled -> "switched off"
                                status?.error != null -> "unreachable: ${status.error}"
                                status != null -> "${status.count} articles - ${source.host}"
                                else -> source.host
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                !enabled -> MaterialTheme.colorScheme.outline
                                status?.error != null -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { viewModel.toggleSource(source.id) },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { viewModel.enableAll() }) { Text("Enable every source") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleScreen(article: Article, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val accent = countryColor(article.country)
    val toolbarColor = MaterialTheme.colorScheme.primary.toArgbInt()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                title = {
                    Text(
                        text = Sources.nameOf(article.sourceId),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { shareArticle(context, article) }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { openArticle(context, article, toolbarColor) }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open in browser")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${article.country?.flag.orEmpty()} ${article.country?.label.orEmpty()}".trim(),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Outlined.Language,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = article.source?.host.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(article.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                text = TimeText.full(article.publishedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))

            if (article.summary.isNotBlank()) {
                Text(
                    text = article.summary,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp, fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = "This source does not ship a summary with its feed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { openArticle(context, article, toolbarColor) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Read the full article")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Headline and summary come from the ${article.source?.name ?: "source"} RSS feed. " +
                    "The full story stays on ${article.source?.host ?: "their site"}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
