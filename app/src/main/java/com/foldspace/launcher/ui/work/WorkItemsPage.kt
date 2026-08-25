package com.foldspace.launcher.ui.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.foldspace.launcher.ui.home.ItemEditFrame
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.BoxWithConstraints
import com.foldspace.launcher.widgets.WidgetHostController
import com.foldspace.launcher.ui.home.gridCell
import com.foldspace.launcher.ui.widgets.WidgetCell
import com.foldspace.launcher.ui.home.CellGridLayout
import com.foldspace.launcher.home.HomeLayout
import com.foldspace.launcher.home.HomeItem
import com.foldspace.launcher.home.GridSpec
import com.foldspace.launcher.home.stripRows
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import com.foldspace.launcher.calendar.collapsedLabel
import com.foldspace.launcher.calendar.AgendaEvent
import com.foldspace.launcher.calendar.AgendaSummary
import com.foldspace.launcher.calendar.CalendarAccount
import com.foldspace.launcher.calendar.CalendarAccounts
import com.foldspace.launcher.calendar.OutlookStatus
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
    onResizeWidget: (HomeItem, Int, Int) -> Unit,
    onMoveWidget: (HomeItem, Int, Int) -> Unit,
    agenda: AgendaSummary,
    calendars: List<CalendarAccount>,
    timeLabelFor: (Long) -> String,
    hasCalendarAccess: Boolean,
    onRequestCalendarAccess: () -> Unit,
    outlookInstalled: Boolean,
    onOpenOutlook: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    titlesVisible: Boolean,
    onToggleTitles: (Boolean) -> Unit,
    microsoft: MicrosoftState,
    onJoinMeeting: (String) -> Unit,
    onMicrosoftSignIn: () -> Unit,
    onMicrosoftSignOut: () -> Unit,
    onConfigureMicrosoft: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /**
     * Content above 工項, inside the same scroll.
     *
     * The screen-time card used to ride on top of the news page, where it had
     * nothing to do with the news. It belongs to the summary, and the summary
     * is here.
     */
    header: (@Composable ColumnScope.() -> Unit)? = null,
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

        // Order, top to bottom: what is happening, then what is waiting, then
        // the extras. The calendar leads because it is the one thing here that
        // is true with no account, no setup and no network. The widgets go
        // last because they are the part you arrange once and then stop
        // looking at.
        AgendaSection(
            agenda = agenda,
            calendars = calendars,
            timeLabelFor = timeLabelFor,
            hasAccess = hasCalendarAccess,
            onRequestAccess = onRequestCalendarAccess,
            onJoin = onJoinMeeting,
            titlesVisible = titlesVisible,
            onToggleTitles = onToggleTitles,
            outlookInstalled = outlookInstalled,
            onOpenOutlook = onOpenOutlook,
            onOpenSyncSettings = onOpenSyncSettings,
        )

        Spacer(Modifier.height(16.dp))

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

        // Branches rather than early returns. This section used to end the
        // whole page when it had nothing to show, so an empty inbox took the
        // widgets and the Microsoft card down with it — which is a strange way
        // for good news to behave.
        when {
            !state.listenerConnected -> FoldCard(Modifier.fillMaxWidth()) {
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

            state.groups.isEmpty() -> {
                SourceCaveat()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "目前沒有待處理的工項",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textMuted,
                )
            }

            else -> {
                SourceCaveat()
                Spacer(Modifier.height(12.dp))
                // A plain column, not a LazyColumn: one cannot be nested
                // inside a scrolling parent, and this list is a handful of
                // notifications rather than something worth virtualising.
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
                            WorkItemRow(
                                item = workItem,
                                onClick = { onOpenApp(workItem.packageName) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        header?.invoke(this)

        MicrosoftSection(
            state = microsoft,
            onSignIn = onMicrosoftSignIn,
            onSignOut = onMicrosoftSignOut,
            onConfigure = onConfigureMicrosoft,
        )

        Spacer(Modifier.height(16.dp))

        WorkWidgets(
            layout = widgets,
            widgetHost = widgetHost,
            onAdd = onAddWidget,
            onRemove = onRemoveWidget,
            onResize = onResizeWidget,
            onMove = onMoveWidget,
        )

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * What still works when there is no Azure app registration.
 *
 * Worth saying next to every dead end, because there is one and it is the one
 * this user is actually on: the agenda above reads the device's own calendar
 * with no account at all, and Outlook's and Teams' own widgets show their real
 * data with their own sign-in. Leaving that unsaid would make a card that
 * cannot work look like the only way in.
 */
@Composable
private fun NoRegistrationRoute() {
    Text(
        text = "不做註冊也可以：上面的「今天」讀的是這台裝置的行事曆（Outlook 只要開啟" +
            "「同步日曆」就會寫進去），而 Outlook 和 Teams 自己的小工具會用它們自己的" +
            "登入顯示真實資料。這一張卡片只是額外的路。",
        style = MaterialTheme.typography.labelSmall,
        color = FoldSpaceTheme.tokens.textMuted,
    )
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
                is MicrosoftState.Failed -> Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextAction(text = "設定", onClick = onConfigure, color = tokens.textSecondary)
                    TextAction(text = "再試一次", onClick = onSignIn)
                }
                else -> Unit
            }
        }

        when (state) {
            MicrosoftState.NotConfigured -> Column {
                Text(
                    // The one thing I cannot do for the user, said plainly.
                    text = "還沒有用戶端 ID。連到 Microsoft 需要一組 Azure 應用程式註冊，" +
                        "而那要用你自己的 Microsoft 帳戶到 Azure 入口網站建立 —— FoldSpace " +
                        "沒有辦法代勞。在設定裡貼上 ID 之後這裡就會運作。",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                NoRegistrationRoute()
            }

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

            is MicrosoftState.Failed -> Column {
                Text(
                    text = state.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
                NoRegistrationRoute()
            }

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
    calendars: List<CalendarAccount>,
    timeLabelFor: (Long) -> String,
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
    onJoin: (String) -> Unit,
    titlesVisible: Boolean,
    onToggleTitles: (Boolean) -> Unit,
    outlookInstalled: Boolean,
    onOpenOutlook: () -> Unit,
    onOpenSyncSettings: () -> Unit,
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasAccess && agenda.remaining > 0) {
                    Pill(text = "還有 ${agenda.remaining} 個", color = tokens.accent)
                }
                if (hasAccess && !agenda.isEmpty) {
                    TextAction(
                        text = if (titlesVisible) "隱藏內容" else "顯示內容",
                        onClick = { onToggleTitles(!titlesVisible) },
                        color = tokens.textSecondary,
                    )
                }
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
                text = "今天和明天都沒有行程。",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted,
            )
            Spacer(Modifier.height(8.dp))
            CalendarSources(
                calendars = calendars,
                outlookInstalled = outlookInstalled,
                onOpenOutlook = onOpenOutlook,
                onOpenSyncSettings = onOpenSyncSettings,
            )
            return@FoldCard
        }

        Spacer(Modifier.height(8.dp))

        if (agenda.events.isEmpty()) {
            Text(
                text = "今天沒有行程了",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted,
            )
        }

        agenda.events.forEachIndexed { index, event ->
            AgendaRow(
                event = event,
                isNext = index == agenda.nextIndex,
                timeLabelFor = timeLabelFor,
                titlesVisible = titlesVisible,
                revealed = revealed,
                onJoin = onJoin,
            )
        }

        if (agenda.tomorrow.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "明天",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
            )
            Spacer(Modifier.height(4.dp))
            agenda.tomorrow.forEach { event ->
                AgendaRow(
                    event = event,
                    isNext = false,
                    timeLabelFor = timeLabelFor,
                    titlesVisible = titlesVisible,
                    revealed = revealed,
                    onJoin = onJoin,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        CalendarSources(
            calendars = calendars,
            outlookInstalled = outlookInstalled,
            onOpenOutlook = onOpenOutlook,
            onOpenSyncSettings = onOpenSyncSettings,
        )
    }
}

/**
 * Which calendars this is reading, and where Outlook's stands.
 *
 * "Can it get my Outlook calendar?" has one honest answer: yes, once Outlook
 * syncs it into the device provider, because that is the only interface
 * Outlook exposes on the device. What was missing was any way to tell from the
 * phone whether that had happened — an empty day meant "nothing on" or "your
 * work calendar was never here" and the page could not say which. It can now.
 *
 * Note it lists what is *read*, not what is visible: no `VISIBLE = 1` filter
 * has ever been applied, so a calendar hidden inside Samsung Calendar is still
 * on this list and its events are still on this page.
 */
@Composable
private fun CalendarSources(
    calendars: List<CalendarAccount>,
    outlookInstalled: Boolean,
    onOpenOutlook: () -> Unit,
    onOpenSyncSettings: () -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    val status = remember(calendars) { CalendarAccounts.outlookStatus(calendars) }
    val labels = remember(calendars) { CalendarAccounts.labels(calendars) }

    if (labels.isNotEmpty()) {
        Text(
            text = "來源：" + labels.joinToString("、"),
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted,
        )
    }

    val advice = when (status) {
        OutlookStatus.Present -> null

        OutlookStatus.NoCalendars ->
            "這台裝置上沒有任何行事曆帳號。要看到 Outlook 的會議，先在 Outlook 裡登入，" +
                "再把「同步處理行事曆」打開。"

        OutlookStatus.Missing ->
            "沒有看到 Outlook 的行事曆。Outlook 不會直接開放給其他 App 讀 —— 它要先把" +
                "行事曆同步進系統：Outlook →「設定」→ 選你的帳號 →「同步處理行事曆」。" +
                "打開之後這裡就會自己出現，不需要任何登入。"

        OutlookStatus.NotSyncing ->
            "Outlook 的行事曆在這台裝置上，但同步是關掉的，所以內容可能是舊的。" +
                "到「帳號與同步」把它打開。"
    } ?: return

    Spacer(Modifier.height(6.dp))
    Text(
        text = advice,
        style = MaterialTheme.typography.labelSmall,
        color = tokens.textSecondary,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (outlookInstalled && status != OutlookStatus.NotSyncing) {
            TextAction(text = "開啟 Outlook", onClick = onOpenOutlook)
        }
        TextAction(
            text = "帳號與同步",
            onClick = onOpenSyncSettings,
            color = if (status == OutlookStatus.NotSyncing) tokens.accent else tokens.textSecondary,
        )
    }
}

/**
 * One entry.
 *
 * [titlesVisible] is the preference; [revealed] is a per-row override for the
 * times it is off and you want to see one anyway. Neither is persisted beyond
 * the preference itself — opening a single title is a decision about now.
 */
@Composable
private fun AgendaRow(
    event: AgendaEvent,
    isNext: Boolean,
    timeLabelFor: (Long) -> String,
    titlesVisible: Boolean,
    revealed: MutableList<Long>,
    onJoin: (String) -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    val open = titlesVisible || event.id in revealed

    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                if (event.id in revealed) revealed.remove(event.id) else revealed.add(event.id)
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
            }
        }
        // A Teams meeting is a calendar event with a join link in it, so this
        // needs no account and no permission beyond the one already granted.
        event.joinUrl?.let { url ->
            TextAction(text = "加入", onClick = { onJoin(url) })
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
    onResize: (HomeItem, Int, Int) -> Unit,
    onMove: (HomeItem, Int, Int) -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    val density = LocalDensity.current
    var editing by remember { mutableStateOf(false) }
    val items = layout.pages.firstOrNull()?.items.orEmpty()

    // The span a handle is being dragged to, so the widget grows under the
    // finger instead of jumping when the drag ends. Same shape as the grid's.
    var pendingResize by remember { mutableStateOf<Pair<Long, Pair<Int, Int>>?>(null) }
    LaunchedEffect(items, pendingResize) {
        val (id, span) = pendingResize ?: return@LaunchedEffect
        val current = items.firstOrNull { it.id == id }
        if (current == null || current.spanX to current.spanY == span) pendingResize = null
    }

    fun spanOf(item: HomeItem): Pair<Int, Int> =
        pendingResize?.takeIf { it.first == item.id }?.second
            ?: (item.spanX.coerceAtLeast(1) to item.spanY.coerceAtLeast(1))

    Column(Modifier.fillMaxWidth()) {
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
            return@Column
        }

        Spacer(Modifier.height(8.dp))

        // No card around the widgets. Wrapped in a FoldCard they were inset by
        // the card's padding on top of the page's own, and a widget drawn two
        // margins in is a widget rendering itself smaller than it needs to be.
        //
        // The strip is measured on the rows actually occupied, and — this is
        // the part that was wrong — it is *laid out* on those same rows. It
        // used to be given a box one row tall and a grid three rows tall, so
        // CellGridLayout divided that box into three and a one-row widget was
        // drawn at a third of its height. That is the "被限制在框框裡、畫面
        // 更小" this page has had since the strip existed; removing the card
        // did not touch it.
        val rowsInUse = stripRows(
            bottoms = items.map { it.cellY + spanOf(it).second },
            maxRows = layout.grid.rows,
        )
        val stripGrid = GridSpec(columns = layout.grid.columns, rows = rowsInUse)

        BoxWithConstraints(Modifier.fillMaxWidth().height((STRIP_ROW_DP * rowsInUse).dp)) {
            val cellWidthPx = with(density) { maxWidth.toPx() } / stripGrid.columns
            val cellHeightPx = with(density) { STRIP_ROW_DP.dp.toPx() }
            val cellWidthDp = (maxWidth.value / stripGrid.columns).toInt()

            CellGridLayout(grid = stripGrid, modifier = Modifier.fillMaxSize()) {
                items.forEach { item ->
                    val (spanX, spanY) = spanOf(item)
                    Box(
                        Modifier
                            .gridCell(item.cellX, item.cellY, spanX, spanY)
                            .padding(2.dp),
                    ) {
                        val id = item.appWidgetId
                        if (widgetHost != null && id != null) {
                            WidgetCell(
                                controller = widgetHost,
                                appWidgetId = id,
                                widthDp = cellWidthDp * spanX,
                                heightDp = STRIP_ROW_DP * spanY,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        if (editing) {
                            // The same frame the desktop uses, body drag and
                            // all: a widget swallows touches, so the frame is
                            // the only way to reach one at all.
                            ItemEditFrame(
                                spanX = spanX,
                                spanY = spanY,
                                cellWidthPx = cellWidthPx,
                                cellHeightPx = cellHeightPx,
                                onProposeSpan = { wantX, wantY ->
                                    val fits = layout.rectFits(
                                        pageIndex = 0,
                                        item = item,
                                        cellX = item.cellX,
                                        cellY = item.cellY,
                                        spanX = wantX,
                                        spanY = wantY,
                                    )
                                    if (fits) pendingResize = item.id to (wantX to wantY)
                                },
                                onCommitSpan = {
                                    val (x, y) = spanOf(item)
                                    if (x != item.spanX || y != item.spanY) onResize(item, x, y)
                                },
                                onMoveBy = { stepX, stepY ->
                                    val targetX = (item.cellX + stepX)
                                        .coerceIn(0, layout.grid.columns - spanX)
                                    // Downwards it may add one row at a time.
                                    // Letting it jump to the grid's last row
                                    // would stretch the strip to eight rows
                                    // with seven of them empty above it.
                                    val lowest = rowsInUse
                                        .coerceAtMost(layout.grid.rows - spanY)
                                        .coerceAtLeast(0)
                                    val targetY = (item.cellY + stepY)
                                        .coerceIn(0, lowest)
                                    val moved = targetX != item.cellX || targetY != item.cellY
                                    val fits = layout.rectFits(
                                        pageIndex = 0,
                                        item = item,
                                        cellX = targetX,
                                        cellY = targetY,
                                        spanX = spanX,
                                        spanY = spanY,
                                    )
                                    if (moved && fits) onMove(item, targetX, targetY)
                                },
                                onRemove = { onRemove(item) },
                                bodyDraggable = true,
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


/** Enough to read as deliberate emptiness rather than a truncated page. */
