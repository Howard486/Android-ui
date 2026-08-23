package com.foldspace.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.ai.NanoUnavailableReason
import com.foldspace.launcher.context.SwitchMode
import com.foldspace.launcher.settings.GridChoice
import com.foldspace.launcher.settings.DockShape
import com.foldspace.launcher.settings.BadgeStyle
import com.foldspace.launcher.settings.PowerMode
import com.foldspace.launcher.settings.ThemeId
import com.foldspace.launcher.ui.LauncherUiState
import com.foldspace.launcher.ui.components.FoldCard
import com.foldspace.launcher.ui.components.TextAction
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * §16 — every special access is presented with its purpose and its data
 * policy, and every one of them is optional. Nothing here silently degrades:
 * a permission that is off says what stops working.
 */
@Composable
fun SettingsScreen(
    state: LauncherUiState,
    nanoAvailability: NanoUnavailableReason,
    onSetTheme: (ThemeId) -> Unit,
    onSetGridChoice: (GridChoice) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onEditRules: () -> Unit,
    onPickSimpleApps: () -> Unit,
    onEditPairs: () -> Unit,
    onPickIconPack: () -> Unit,
    onPickHiddenApps: () -> Unit,
    onSetBadgeStyle: (BadgeStyle) -> Unit,
    onSetDockShape: (DockShape) -> Unit,
    onSetDesktopModeOnUnfold: (Boolean) -> Unit,
    onSetMicrosoftClientId: (String?) -> Unit,
    onExportLayout: () -> Unit,
    onImportLayout: () -> Unit,
    onRequestCalendarAccess: () -> Unit,
    onSetPowerMode: (PowerMode) -> Unit,
    onSetSwitchMode: (SwitchMode) -> Unit,
    onSetWalletCompatibility: (Boolean) -> Unit,
    onSetContentAnalysis: (Boolean) -> Unit,
    onRequestDefaultHome: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onOrganiseApps: () -> Unit,
    onAddWidgetPage: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.overlayScrim())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "設定",
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.textPrimary,
                )
                Text(
                    text = "關閉",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.accent,
                    modifier = Modifier.clickable(onClick = onClose).padding(6.dp),
                )
            }
        }

        // §5.1 — the single most important setup step; first on the list until done.
        item {
            PermissionCard(
                title = "預設 Home",
                granted = state.isDefaultHome,
                purpose = "FoldSpace 需要成為預設 Home App 才能取代原生桌面。" +
                    "Private Space 等功能也以此為前提。",
                actionLabel = if (state.isDefaultHome) "已設定" else "設為預設 Home",
                onAction = onRequestDefaultHome,
            )
        }

        item {
            PermissionCard(
                title = "通知存取",
                granted = state.hasNotificationAccess,
                purpose = "把通知依 Space 與重要性重新整理。內容只在裝置端處理，" +
                    "不會上傳，通知消失後即刪除。未授權時僅停用通知整理，不影響其他功能。",
                actionLabel = if (state.hasNotificationAccess) "已授權" else "前往系統設定",
                onAction = onRequestNotificationAccess,
            )
        }

        item {
            PermissionCard(
                title = "使用情形存取",
                granted = state.hasUsageAccess,
                purpose = "讓 Smart Dock 推薦常用 App。只保留聚合分數，" +
                    "不保留完整使用紀錄。未授權時 Dock 僅顯示你自己釘選的 App。",
                actionLabel = if (state.hasUsageAccess) "已授權" else "前往系統設定",
                onAction = onRequestUsageAccess,
            )
        }

        // Home-screen actions.
        item {
            FoldCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "整理桌面",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "把所有 App 依類別收進資料夾。分類來源是系統類別與內建對照表；" +
                        "判斷不出來的會放進「其他」。整理後可以一鍵還原。",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "一鍵整理分類",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.accent,
                    modifier = Modifier.clickable(onClick = onOrganiseApps).padding(vertical = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "新增小工具頁",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.accent,
                    modifier = Modifier.clickable(onClick = onAddWidgetPage).padding(vertical = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "選擇簡易模式的 App",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.accent,
                    modifier = Modifier.clickable(onClick = onPickSimpleApps).padding(vertical = 4.dp),
                )
            }
        }

        // Backup. Prompted by this project's own history: two destructive
        // schema migrations have already taken the user's arrangement with
        // them, and there was no way back.
        item {
            FoldCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "備份桌面",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "把排列存成檔案，換機或升級後再匯回來。小工具不包含在內 —— " +
                        "它的編號只對這台裝置有意義，還原時要重新新增。",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(
                        text = "匯出",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.accent,
                        modifier = Modifier.clickable(onClick = onExportLayout).padding(vertical = 4.dp),
                    )
                    Text(
                        text = "匯入",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.accent,
                        modifier = Modifier.clickable(onClick = onImportLayout).padding(vertical = 4.dp),
                    )
                }
            }
        }

        // §16 — READ_CALENDAR was declared from V0.1 and never requested, so
        // the calendar condition in the rule editor could never match.
        item {
            FoldCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "行事曆訊號",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.hasCalendarAccess) {
                        "已授權。FoldSpace 只讀目前時段有沒有行程，以及標題裡的關鍵字，" +
                            "轉成「會議／專注／交通／忙碌」四種標籤之一；標題本身不會被保存或顯示。"
                    } else {
                        "尚未授權。授權後，規則可以用「行事曆」當條件。" +
                            "FoldSpace 只讀目前時段的忙碌狀態，不讀與會者、地點或內容。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                if (!state.hasCalendarAccess) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "授權行事曆",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.accent,
                        modifier = Modifier
                            .clickable(onClick = onRequestCalendarAccess)
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }

        // §7.1 level 2. Reachable at last.
        item {
            FoldCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "自動切換規則",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.settings.automationRules.isEmpty()) {
                        "還沒有規則。規則是唯一可以直接切換情境的東西，其他訊號只會提示。"
                    } else {
                        "已設定 " + state.settings.automationRules.size + " 條規則。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "編輯規則",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.accent,
                    modifier = Modifier.clickable(onClick = onEditRules).padding(vertical = 4.dp),
                )
            }
        }

        // §4 — the foldable feature this launcher exists for.
        item {
            FoldCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "App 配對",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "一次開兩個 App。能不能真的左右並排由系統決定 —— " +
                        "Android 沒有給第三方 Launcher 開分割畫面的公開 API，" +
                        "已在分割模式時會並排，否則第二個會蓋住第一個。",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "編輯配對",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.accent,
                    modifier = Modifier.clickable(onClick = onEditPairs).padding(vertical = 4.dp),
                )
            }
        }

        item {
            FoldCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "圖示包",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = (state.settings.iconPackPackage?.let { "已套用：" + it } ?: "使用系統圖示") +
                        "。圖示包只提供圖案，形狀仍由 FoldSpace 統一。",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "選擇圖示包",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.accent,
                    modifier = Modifier.clickable(onClick = onPickIconPack).padding(vertical = 4.dp),
                )
            }
        }

        item {
            FoldCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "隱藏 App",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.settings.hiddenApps.isEmpty()) {
                        "沒有隱藏任何 App。桌面會跟著已安裝清單走，所以每個 App 都會拿到一格；" +
                            "這裡是唯一可以說「這個我不想看到」的地方。"
                    } else {
                        "已隱藏 ${state.settings.hiddenApps.size} 個。隱藏只是拿掉圖示，" +
                            "App 仍然安裝著也仍然可以從別的地方打開。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                Spacer(Modifier.height(14.dp))
                TextAction(text = "選擇要隱藏的 App", onClick = onPickHiddenApps)
            }
        }

        item {
            ToggleCard(
                title = "展開時進入桌面模式",
                description = "展開內螢幕時換成工作列版面，App 以視窗開啟。" +
                    "視窗要真的開成視窗，需要在「開發人員選項 → 啟用自由形式視窗」打開並重新開機；" +
                    "沒開的話 App 會照常全螢幕，桌面模式裡會直接說明。" +
                    "FoldSpace 只能決定視窗開在哪、開多大 —— 移動、縮放和標題列是系統的，不歸 Launcher 管。",
                checked = state.settings.desktopModeOnUnfold,
                onCheckedChange = onSetDesktopModeOnUnfold,
            )
        }

        item {
            var clientId by remember(state.settings.microsoftClientId) {
                mutableStateOf(state.settings.microsoftClientId.orEmpty())
            }
            FoldCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "Microsoft 帳戶",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    // The honest version of "not implemented": everything above
                    // the credential is built and tested; the credential is the
                    // user's to create and nothing here can do it for them.
                    text = "工項頁可以顯示你的 Microsoft 行事曆與待辦，但需要一組 Azure 應用程式" +
                        "註冊的用戶端 ID。到 Azure 入口網站新增一個應用程式，平台選「行動與" +
                        "桌面應用程式」，重新導向 URI 填 foldspace://auth/microsoft，" +
                        "並把「允許公用用戶端流程」開啟，然後把用戶端 ID 貼在下面。",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                SettingsSearchField(
                    query = clientId,
                    onQueryChange = { clientId = it },
                    placeholder = "用戶端 ID",
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextAction(
                        text = "儲存",
                        onClick = { onSetMicrosoftClientId(clientId.trim().takeIf { it.isNotBlank() }) },
                    )
                    if (!state.settings.microsoftClientId.isNullOrBlank()) {
                        TextAction(
                            text = "清除",
                            onClick = {
                                clientId = ""
                                onSetMicrosoftClientId(null)
                            },
                            color = tokens.textMuted,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "只要求讀取權限（行事曆、待辦、基本資料），不會修改任何東西。" +
                        "權杖用裝置金鑰庫加密後存在本機，行事曆內容不會被儲存。",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                )
            }
        }

        item {
            ChoiceCard(
                title = "通知標記",
                description = "數字說明有多少在等，圓點只說明有東西在等 —— 滿版一頁時安靜得多。",
                options = BadgeStyle.entries.map { it to it.label },
                selected = state.settings.badgeStyle,
                onSelect = onSetBadgeStyle,
            )
        }

        item {
            ChoiceCard(
                title = "Dock 列數",
                description = "一列五格是為外螢幕設計的。內螢幕寬度是它的兩倍以上，" +
                    "同一個托盤放在上面會顯得空。第三列開始會吃到頁面，所以上限是兩列。",
                options = (DockShape.MIN_ROWS..DockShape.MAX_ROWS).map { it to "$it 列" },
                selected = state.settings.dockShape.rows,
                onSelect = { onSetDockShape(state.settings.dockShape.copy(rows = it)) },
            )
        }

        item {
            ChoiceCard(
                title = "Dock 每列格數",
                description = "格數越多每個圖示越小。在外螢幕上超過五個就開始不好按。",
                options = (DockShape.MIN_COLUMNS..DockShape.MAX_COLUMNS).map { it to "$it 格" },
                selected = state.settings.dockShape.columns,
                onSelect = { onSetDockShape(state.settings.dockShape.copy(columns = it)) },
            )
        }

        item {
            ToggleCard(
                title = "觸覺回饋",
                description = "拿起圖示、調整小工具大小、進出編輯模式時的細微震動。",
                checked = state.settings.hapticsEnabled,
                onCheckedChange = onSetHaptics,
            )
        }

        // §6.1 Samsung Compatibility Mode.
        item {
            ToggleCard(
                title = "保留 Samsung Wallet 快速存取",
                description = "開啟時，畫面最底部保留給 Samsung Wallet 的向上滑手勢，" +
                    "FoldSpace 不會在該區域放置元件或攔截手勢。",
                checked = state.settings.samsungWalletCompatibility,
                onCheckedChange = onSetWalletCompatibility,
            )
        }

        // §10.4 privacy control.
        item {
            ToggleCard(
                title = "分析通知內容",
                description = "關閉後，FoldSpace 只讀取通知的來源 App 與數量，" +
                    "不讀取標題與內文，通知分級改以結構判斷。",
                checked = state.settings.notificationContentAnalysis,
                onCheckedChange = onSetContentAnalysis,
            )
        }

        // §7.2 switch mode.
        item {
            ChoiceCard(
                title = "情境切換",
                description = "AI 與規則能否自動切換 Space。",
                options = SwitchMode.entries.map { it to it.label() },
                selected = state.settings.switchMode,
                onSelect = onSetSwitchMode,
            )
        }

        // §12.3 power mode.
        item {
            ChoiceCard(
                title = "效能模式",
                description = "決定動畫、背景工作與 AI 推論的積極程度。",
                options = PowerMode.entries.map { it to it.label() },
                selected = state.settings.powerMode,
                onSelect = onSetPowerMode,
            )
        }

        // The iOS home doc: the grid is the user's call, 4x6 by default.
        item {
            ChoiceCard(
                title = "桌面格線",
                description = "4 × 6 是 iPhone 的間距。變更會依現有順序重新排列桌面 —— " +
                    "第一個圖示仍然是第一個，但位置會重排。",
                options = GridChoice.entries.map { it to it.displayName },
                selected = state.settings.gridChoice,
                onSelect = onSetGridChoice,
            )
        }

        // §15.1 themes.
        item {
            ChoiceCard(
                title = "主題",
                description = "主題會同時改變顏色、圓角、圖示與動畫等級。",
                options = ThemeId.entries.map { it to it.displayName },
                selected = state.settings.themeId,
                onSelect = onSetTheme,
            )
        }

        // §8 / §22 — Nano availability is reported, never silently absent.
        item {
            FoldCard(Modifier.fillMaxWidth()) {
                Text(
                    text = "裝置端 AI (Gemini Nano)",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = nanoAvailability.explain(),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
            }
        }

        item {
            Text(
                text = "FoldSpace 0.1.0 · MVP V0.1",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    purpose: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    FoldCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary,
            )
            com.foldspace.launcher.ui.components.Pill(
                text = if (granted) "已授權" else "未授權",
                color = if (granted) tokens.accent else tokens.textMuted,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = purpose,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary,
        )
        if (!granted) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.accent,
                modifier = Modifier.clickable(onClick = onAction).padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    FoldCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = tokens.textPrimary,
                    checkedTrackColor = tokens.accent,
                    uncheckedThumbColor = tokens.textMuted,
                    uncheckedTrackColor = tokens.surfaceElevated,
                    uncheckedBorderColor = tokens.outline,
                ),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary,
        )
    }
}

@Composable
private fun <T> ChoiceCard(
    title: String,
    description: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    FoldCard(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) tokens.accent.copy(alpha = 0.16f)
                            else tokens.surfaceElevated,
                        )
                        .border(
                            1.dp,
                            if (isSelected) tokens.accent.copy(alpha = 0.45f) else tokens.outline,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelect(value) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) tokens.accent else tokens.textSecondary,
                    )
                }
            }
        }
    }
}

private fun SwitchMode.label(): String = when (this) {
    SwitchMode.ManualOnly -> "只手動切換"
    SwitchMode.SuggestFirst -> "先建議再切換（預設）"
    SwitchMode.Automatic -> "依我建立的規則自動切換"
}

private fun PowerMode.label(): String = when (this) {
    PowerMode.Smart -> "Smart（預設）"
    PowerMode.Performance -> "Performance"
    PowerMode.BatterySaver -> "Battery Saver"
}

private fun NanoUnavailableReason.explain(): String = when (this) {
    NanoUnavailableReason.Available -> "可用。僅在 FoldSpace 位於前景且情境改變時推論。"
    NanoUnavailableReason.DeviceNotSupported ->
        "此裝置或此版本尚未提供裝置端模型。所有情境判斷改以規則引擎處理，功能不受影響。"
    NanoUnavailableReason.ModelNotDownloaded -> "模型尚未下載完成。"
    NanoUnavailableReason.NotForeground -> "僅能在 FoldSpace 位於前景時推論。"
    NanoUnavailableReason.QuotaExceeded -> "已達到系統的每日推論配額，稍後恢復。"
    NanoUnavailableReason.Busy -> "系統忙碌中，稍後重試。"
    NanoUnavailableReason.FeatureDisabled -> "已在設定中關閉。"
}
