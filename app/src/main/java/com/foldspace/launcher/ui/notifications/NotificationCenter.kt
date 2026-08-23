package com.foldspace.launcher.ui.notifications

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.notifications.NotificationItem
import com.foldspace.launcher.notifications.NotificationSummary
import com.foldspace.launcher.spaces.NotificationPolicy
import com.foldspace.launcher.spaces.NotificationTier
import com.foldspace.launcher.ui.components.FoldCard
import com.foldspace.launcher.ui.components.Pill
import com.foldspace.launcher.ui.theme.FoldSpaceTheme
import com.foldspace.launcher.ui.theme.ThemeTokens

/**
 * §10.1 Notification Center — grouped by the four-level model and filtered by
 * the current Space's policy, which is what makes Focus actually focused
 * rather than merely differently decorated.
 */
@Composable
fun NotificationCenter(
    summary: NotificationSummary,
    policy: NotificationPolicy,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens

    Column(
        modifier
            .fillMaxSize()
            .background(tokens.overlayScrim())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "通知",
            style = MaterialTheme.typography.headlineSmall,
            color = tokens.textPrimary,
        )
        Spacer(Modifier.height(12.dp))

        if (!summary.listenerConnected) {
            AccessPrompt(onRequestAccess)
            return@Column
        }

        val visible = summary.items.filter { policy.admits(it.tier) }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (summary.items.isEmpty()) "目前沒有通知"
                    else "此 Space 只顯示「${policy.minimumTier.displayName}」以上的通知",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textMuted,
                )
            }
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            NotificationTier.entries.forEach { tier ->
                val items = visible.filter { it.tier == tier }
                if (items.isEmpty()) return@forEach

                item(key = "header-${tier.name}") {
                    Text(
                        text = "${tier.displayName} · ${items.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tier.tint(tokens),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(items, key = { it.key }) { item ->
                    NotificationRow(item)
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: NotificationItem) {
    val tokens = FoldSpaceTheme.tokens
    FoldCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.packageName.substringAfterLast('.'),
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
            )
            Pill(text = item.tier.displayName, color = item.tier.tint(tokens))
        }

        Spacer(Modifier.height(8.dp))

        // §10.4 — an app the user excluded from analysis reaches here with no
        // title or body at all, so the row says so rather than showing blanks.
        val title = item.title
        if (title.isNullOrBlank()) {
            Text(
                text = "內容未分析",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textMuted,
            )
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.body?.takeIf { it.isNotBlank() }?.let { body ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AccessPrompt(onRequestAccess: () -> Unit) {
    val tokens = FoldSpaceTheme.tokens
    FoldCard(Modifier.fillMaxWidth()) {
        Text(
            text = "尚未授予通知存取權",
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        // §16 — the purpose is stated before the user is sent to Settings.
        Text(
            text = "FoldSpace 需要通知存取權，才能把通知依 Space 與重要性重新整理。" +
                "通知內容只在裝置端處理，不會上傳，通知消失後即刪除。",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "前往系統設定開啟",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.accent,
            modifier = Modifier
                .clickable(onClick = onRequestAccess)
                .padding(vertical = 4.dp),
        )
    }
}

private fun NotificationTier.tint(tokens: ThemeTokens): Color = when (this) {
    NotificationTier.Now -> tokens.accent
    NotificationTier.Action -> tokens.accentSecondary
    NotificationTier.Info -> tokens.textSecondary
    NotificationTier.Noise -> tokens.textMuted
}
