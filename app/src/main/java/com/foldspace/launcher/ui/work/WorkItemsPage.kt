package com.foldspace.launcher.ui.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.foldspace.launcher.widgets.WidgetHostController
import com.foldspace.launcher.ui.home.gridCell
import com.foldspace.launcher.ui.widgets.WidgetCell
import com.foldspace.launcher.ui.home.CellGridLayout
import com.foldspace.launcher.home.HomeLayout
import com.foldspace.launcher.home.HomeItem
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import com.foldspace.launcher.calendar.collapsedLabel
import com.foldspace.launcher.calendar.AgendaSummary
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
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
    widgets: HomeLayout,
    widgetHost: WidgetHostController?,
    onAddWidget: () -> Unit,
    onRemoveWidget: (HomeItem) -> Unit,
    agenda: AgendaSummary,
    timeLabelFor: (Long) -> String,
    hasCalendarAccess: Boolean,
    onRequestCalendarAccess: () -> Unit,
    microsoft: MicrosoftState,
    onJoinMeeting: (String) -> Unit,
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
            // The page scrolls as one. It used to be a fixed column with a
            // LazyColumn at the bottom, so everything above that list — the
            // widgets, today, Microsoft — was simply cut off wherever the
            // screen ended.
            .verticalScroll(rememberScrollState())
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

        WorkWidgets(
            layout = widgets,
            widgetHost = widgetHost,
            onAdd = onAddWidget,
            onRemove = onRemoveWidget,
        )

        Spacer(Modifier.height(12.dp))

        AgendaSection(
            agenda = agenda,
            timeLabelFor = timeLabelFor,
            hasAccess = hasCalendarAccess,
            onRequestAccess = onRequestCalendarAccess,
            onJoin = onJoinMeeting,
        )

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
            Box(
                Modifier.fillMaxWidth().height(EMPTY_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "目前沒有待處理的工項",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textMuted,
                )
            }
            return@Column
        }

        // A plain column, not a LazyColumn: one cannot be nested inside a
        // scrolling parent, and this list is a handful of notifications rather
        // than something worth virtualising.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.groups.forEach { group ->
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
                group.items.forEach { workItem ->
                    WorkItemRow(item = workItem, onClick = { onOpenApp(workItem.packageName) })
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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

/**
 * Today, read from the device's own calendar provider.
 *
 * The route that needs no account and no app registration: whatever Outlook,
 * Samsung Calendar or Google Calendar syncs into the provider is already
 * here, offline included. Microsoft Graph, above, is the upgrade — and the
 * only way to reach To Do.
 *
 * Titles are collapsed by default. A launcher's home screen is the one
 * surface visible to whoever is standing next to you, and "Q3 budget review
 * with Acme" is not something to put there without being asked. The time and
 * a coarse category are enough to plan around; the title is one tap away.
 */
@Composable
private fun AgendaSection(
    agenda: AgendaSummary,
    timeLabelFor: (Long) -> String,
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
    onJoin: (String) -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    // Which rows the user has opened, this composition only. Deliberately not
    // persisted: revealing a title is a decision about right now, not a
    // setting that should quietly stay on tomorrow.
    val revealed = remember { mutableStateListOf<Long>() }

    FoldCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "今天",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary,
            )
            if (hasAccess && agenda.remaining > 0) {
                Pill(text = "還有 ${agenda.remaining} 個", color = tokens.accent)
            }
        }

        if (!hasAccess) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "需要行事曆權限才能顯示。讀的是這台裝置上的行事曆，不會連線、" +
                    "不需要帳號，也不會存下任何內容。",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            TextAction(text = "授予權限", onClick = onRequestAccess)
            return@FoldCard
        }

        if (agenda.isEmpty) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "今天沒有行程。如果 Outlook 的行事曆沒有出現，請到 Outlook 的" +
                    "帳號設定把「同步日曆」打開 —— 它預設不一定會寫進系統行事曆。",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted,
            )
            return@FoldCard
        }

        Spacer(Modifier.height(8.dp))
        agenda.events.forEachIndexed { index, event ->
            val open = event.id in revealed
            val isNext = index == agenda.nextIndex

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (open) revealed.remove(event.id) else revealed.add(event.id)
                    }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (event.allDay) "全天" else timeLabelFor(event.startMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isNext) tokens.accent else tokens.textMuted,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (open) event.title else event.collapsedLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (open) tokens.textPrimary else tokens.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (open) {
                        event.location?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        event.calendarName?.let {
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
                // A Teams meeting is a calendar event with a join link in
                // it, so this needs no account and no permission beyond the
                // one already granted.
                event.joinUrl?.let { url ->
                    TextAction(text = "加入", onClick = { onJoin(url) })
                }
                Text(
                    text = if (open) "隱藏" else "顯示",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.accent,
                )
            }
        }
    }
}

/**
 * The widgets the user puts on this page.
 *
 * The reason this exists: Outlook and Teams expose nothing a launcher can
 * read, and every password-only route into Microsoft's services — IMAP, POP,
 * EWS, ActiveSync — has been closed. But both ship widgets of their own, and
 * those show real data using the app's own sign-in. So the honest way to put
 * work information on this page is not to fetch it, but to hold the frame and
 * let the app draw into it.
 *
 * Stored on its own surface, so nothing here moves when the desktop is
 * reflowed to a different grid, and no newly installed app is ever placed
 * among them.
 */
@Composable
private fun WorkWidgets(
    layout: HomeLayout,
    widgetHost: WidgetHostController?,
    onAdd: () -> Unit,
    onRemove: (HomeItem) -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    var editing by remember { mutableStateOf(false) }
    val items = layout.pages.firstOrNull()?.items.orEmpty()

    FoldCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "小工具",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (items.isNotEmpty()) {
                    TextAction(
                        text = if (editing) "完成" else "編輯",
                        onClick = { editing = !editing },
                        color = if (editing) tokens.accent else tokens.textSecondary,
                    )
                }
                TextAction(text = "新增", onClick = onAdd)
            }
        }

        if (items.isEmpty()) {
            Text(
                text = "放上 Outlook 或 Teams 自己的小工具，它們會用自己的登入顯示真實資料 —— " +
                    "FoldSpace 不會讀取你的帳戶。",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted,
            )
            return@FoldCard
        }

        Spacer(Modifier.height(8.dp))
        // Tall enough for the tallest widget on it, and no taller. A fixed
        // 240dp cropped a three-row inbox widget halfway through a message;
        // a strip that grows without limit would push the work items off the
        // page. The page scrolls now, so the ceiling is the grid's own rows.
        val rows = items
            .maxOfOrNull { it.spanY.coerceAtLeast(1) }
            ?.coerceIn(1, layout.grid.rows)
            ?: 1
        CellGridLayout(
            grid = layout.grid,
            modifier = Modifier.fillMaxWidth().height((STRIP_ROW_DP * rows).dp),
        ) {
            items.forEach { item ->
                Box(
                    Modifier
                        .gridCell(
                            item.cellX,
                            item.cellY,
                            item.spanX.coerceAtLeast(1),
                            item.spanY.coerceAtLeast(1),
                        )
                        .padding(2.dp),
                ) {
                    val id = item.appWidgetId
                    if (widgetHost != null && id != null) {
                        WidgetCell(
                            controller = widgetHost,
                            appWidgetId = id,
                            widthDp = STRIP_CELL_DP * item.spanX.coerceAtLeast(1),
                            heightDp = STRIP_ROW_DP * item.spanY.coerceAtLeast(1),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (editing) {
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(tokens.surfaceElevated)
                                .clickable { onRemove(item) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One row of the work strip, in dp.
 *
 * Taller than a desktop cell on purpose: the widgets that belong here are list
 * widgets — an inbox, an agenda — and a row of one has to be tall enough to
 * show a message rather than a sliver of one.
 */
private const val STRIP_ROW_DP = 104

/** Nominal cell width handed to the widget host for its size hint. */
private const val STRIP_CELL_DP = 80

/** Enough to read as deliberate emptiness rather than a truncated page. */
private val EMPTY_HEIGHT = 160.dp
