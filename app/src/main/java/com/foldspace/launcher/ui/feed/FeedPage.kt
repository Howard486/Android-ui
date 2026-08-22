package com.foldspace.launcher.ui.feed

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.feed.FeedItem
import com.foldspace.launcher.feed.FeedState
import com.foldspace.launcher.ui.components.FoldCard
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * The leftmost page.
 *
 * It is FoldSpace's own page with a swappable source rather than a Google
 * surface we host, because the Google overlay is not something a third-party
 * launcher can count on. Whichever source answered is named at the top, so
 * "this isn't Discover" is visible rather than a silent substitution.
 */
@Composable
fun FeedPage(
    state: FeedState,
    onRefresh: () -> Unit,
    onOpen: (FeedItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens

    // Demand-driven: the fetch happens because the page came into view, never
    // on a timer (§12.1). The repository throttles repeats.
    LaunchedEffect(Unit) { onRefresh() }

    Column(
        modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        when (state) {
            FeedState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = tokens.accent)
            }

            is FeedState.Unavailable -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                FoldCard(Modifier.fillMaxWidth()) {
                    Text(
                        text = "新聞暫時無法顯示",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "重新整理",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.accent,
                        modifier = Modifier.clickable(onClick = onRefresh).padding(4.dp),
                    )
                }
            }

            is FeedState.Ready -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.providerName,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted,
                    )
                    Text(
                        text = "重新整理",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.accent,
                        modifier = Modifier.clickable(onClick = onRefresh).padding(4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.items, key = { it.title }) { item ->
                        FeedRow(item = item, onClick = { onOpen(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRow(item: FeedItem, onClick: () -> Unit) {
    val tokens = FoldSpaceTheme.tokens
    FoldCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = tokens.textPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        item.source?.takeIf { it.isNotBlank() }?.let { source ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = source,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
            )
        }
    }
}
