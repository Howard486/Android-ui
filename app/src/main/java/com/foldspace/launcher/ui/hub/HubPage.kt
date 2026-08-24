package com.foldspace.launcher.ui.hub

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/** Which half of the hub is showing. */
enum class HubTab(val key: String, val title: String) {
    Summary("summary", "摘要"),
    News("news", "新聞"),
    ;

    companion object {
        fun fromKey(key: String?): HubTab = entries.firstOrNull { it.key == key } ?: Summary
    }
}

/**
 * The leftmost page: Microsoft Launcher's glance page and Google News, behind
 * two tabs.
 *
 * They used to be two different pages that could not both exist — 通用 got the
 * news, 工作 got the summary, 簡易 got neither — so whichever one you were not
 * in was simply gone. Sharing one page costs a tap and makes both reachable
 * from every Space, which is the trade Microsoft Launcher makes on its own
 * feed and the reason it works.
 *
 * The chosen tab is remembered, because a page that resets to 摘要 every time
 * you swipe to it is a page that argues with whoever prefers the news.
 */
@Composable
fun HubPage(
    tab: HubTab,
    onSelectTab: (HubTab) -> Unit,
    summary: @Composable () -> Unit,
    news: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        HubTabs(
            selected = tab,
            onSelect = onSelectTab,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Weighted, not fillMaxSize: the tab row is above it inside the same
        // column, and a child asking for the full height would push its own
        // bottom off the screen.
        Box(Modifier.fillMaxWidth().weight(1f)) {
            // Only the selected half is composed. Keeping both alive would
            // mean the news list refreshing behind a summary nobody is looking
            // at, on a page that is one swipe from every home screen.
            when (tab) {
                HubTab.Summary -> summary()
                HubTab.News -> news()
            }
        }
    }
}

@Composable
private fun HubTabs(
    selected: HubTab,
    onSelect: (HubTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = FoldSpaceTheme.tokens
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.surfaceAlpha())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HubTab.entries.forEach { entry ->
            val active = entry == selected
            val background by animateColorAsState(
                targetValue = if (active) tokens.accent else tokens.surfaceElevated.copy(alpha = 0f),
                label = "hubTab",
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(background)
                    .clickable { onSelect(entry) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) tokens.onAccent else tokens.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
