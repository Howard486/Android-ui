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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.ai.NanoUnavailableReason
import com.foldspace.launcher.context.SwitchMode
import com.foldspace.launcher.settings.PowerMode
import com.foldspace.launcher.settings.ThemeId
import com.foldspace.launcher.ui.LauncherUiState
import com.foldspace.launcher.ui.components.FoldCard
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
            .background(tokens.scrim)
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
            }
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
