package com.foldspace.launcher.ui.work

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
import com.foldspace.launcher.spaces.NotificationTier
import com.foldspace.launcher.ui.components.FoldCard
import com.foldspace.launcher.ui.components.TextAction
import com.foldspace.launcher.microsoft.MicrosoftState
import com.foldspace.launcher.microsoft.GraphModels
import com.foldspace.launcher.ui.components.Pill
import com.foldspace.launcher.ui.theme.FoldSpaceTheme
import com.foldspace.launcher.ui.theme.ThemeTokens
import com.foldspace.launcher.work.WorkItem
import com.foldspace.launcher.work.WorkItemsState

/**
 * 工作 mode's work-item list.
 *
 * The limitation banner is not boilerplate. This list is built from
 * notifications because Outlook exposes no readable interface on the device,
 * so an empty list can mean "nothing waiting" *or* "notifications are off" —
 * two very different things for someone deciding whether they can stop
 * checking their inbox. Saying which is the difference between a useful
 * screen and a misleading one.
 */
@Composable
fun WorkItemsPage(
    state: WorkItemsState,
    onOpenApp: (String) -> Unit,
    onRequestNotificationAccess: () -> Unit,
    microsoft: MicrosoftState,
    onMicrosoftSignIn: () -> Unit,
    onMicrosoftSignOut: () -> Unit,
    onConfigureMicrosoft: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens

    Column(
        modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "工項",
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.textPrimary,
            )
            if (state.needsAction > 0) {
                Pill(text = "${state.needsAction} 項待處理", color = tokens.accent)
            }
        }

        Spacer(Modifier.height(12.dp))

        MicrosoftSection(
            state = microsoft,
            onSignIn = onMicrosoftSignIn,
            onSignOut = onMicrosoftSignOut,
            onConfigure = onConfigureMicrosoft,
        )

        Spacer(Modifier.height(12.dp))

        if (!state.listenerConnected) {
            FoldCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "尚未授予通知存取權",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "工項清單是從 Outlook、Teams 等 App 的通知整理出來的，" +
                        "需要通知存取權才能運作。",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "前往系統設定開啟",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.accent,
                    modifier = Modifier
                        .clickable(onClick = onRequestNotificationAccess)
                        .padding(4.dp),
                )
            }
            return@Column
        }

        SourceCaveat()
        Spacer(Modifier.height(12.dp))

        if (state.groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "目前沒有待處理的工項",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textMuted,
                )
            }
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            state.groups.forEach { group ->
                item(key = "header-${group.packageName}") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenApp(group.packageName) }
                            .padding(top = 6.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = group.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.textPrimary,
                        )
                        Text(
                            text = "開啟",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.accent,
                        )
                    }
                }
                items(group.items, key = { it.key }) { workItem ->
                    WorkItemRow(item = workItem, onClick = { onOpenApp(workItem.packageName) })
                }
            }
        }
    }
}

/**
 * States the source's limits once, at the top, rather than leaving the user to
 * infer them from an empty list.
 */
@Composable
private fun SourceCaveat() {
    val tokens = FoldSpaceTheme.tokens
    Text(
        text = "來源為通知。已關閉通知或已被清除的項目不會出現在這裡。",
        style = MaterialTheme.typography.labelSmall,
        color = tokens.textMuted,
    )
}

@Composable
private fun WorkItemRow(item: WorkItem, onClick: () -> Unit) {
    val tokens = FoldSpaceTheme.tokens
    FoldCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(text = item.tier.displayName, color = item.tier.tint(tokens))
            if (item.actionable) {
                Text(
                    text = "可直接回覆",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = tokens.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.detail?.let { detail ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun NotificationTier.tint(tokens: ThemeTokens): Color = when (this) {
    NotificationTier.Now -> tokens.accent
    NotificationTier.Action -> tokens.accentSecondary
    NotificationTier.Info -> tokens.textSecondary
    NotificationTier.Noise -> tokens.textMuted
}

/**
 * Real calendar and tasks, when there is an account behind them.
 *
 * Everything below the notification-derived list is a guess about what an app
 * meant by a notification. This is the part that is not: it comes from the
 * account itself. When it is signed in the guesses go below it, still labelled
 * as guesses.
 */
@Composable
private fun MicrosoftSection(
    state: MicrosoftState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onConfigure: () -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens

    FoldCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Microsoft",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary,
            )
            when (state) {
                is MicrosoftState.Ready -> TextAction(
                    text = "登出",
                    onClick = onSignOut,
                    color = tokens.textMuted,
                )
                MicrosoftState.SignedOut -> TextAction(text = "連結帳戶", onClick = onSignIn)
                MicrosoftState.NotConfigured -> TextAction(text = "設定", onClick = onConfigure)
                else -> Unit
            }
        }

        when (state) {
            MicrosoftState.NotConfigured -> Text(
                // The one thing I cannot do for the user, said plainly.
                text = "還沒有用戶端 ID。連到 Microsoft 需要一組 Azure 應用程式註冊，" +
                    "而那要用你自己的 Microsoft 帳戶到 Azure 入口網站建立 —— FoldSpace " +
                    "沒有辦法代勞。在設定裡貼上 ID 之後這裡就會運作。",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
            )

            MicrosoftState.SignedOut -> Text(
                text = "連結後會顯示今天的行事曆與未完成的待辦。只讀取，不會修改任何東西，" +
                    "也不會存到裝置上。",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
            )

            MicrosoftState.Loading -> Text(
                text = "讀取中…",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted,
            )

            is MicrosoftState.Failed -> Text(
                text = state.reason,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted,
            )

            is MicrosoftState.Ready -> {
                Spacer(Modifier.height(8.dp))
                if (state.events.isEmpty() && state.tasks.isEmpty()) {
                    Text(
                        text = "今天沒有行程，也沒有未完成的待辦",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textMuted,
                    )
                }

                state.events.forEach { event ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (event.isAllDay) "全天" else GraphModels.timeOf(event.start) ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.accent,
                        )
                        Text(
                            text = event.subject,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        event.location?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (state.tasks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    state.tasks.forEach { task ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "☐",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textMuted,
                            )
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = task.listName,
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textMuted,
                            )
                        }
                    }
                }
            }
        }

        // Said once, here, rather than left as two features quietly missing
        // from something modelled on Microsoft Launcher's feed.
        Spacer(Modifier.height(8.dp))
        Text(
            text = "沒有 Sticky Notes 和 Copilot：便條沒有公開的 Graph API（舊的 Outlook REST " +
                "路徑已停用且沒有替代），Copilot 也沒有可供第三方 Launcher 使用的介面。",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted,
        )
    }
}
