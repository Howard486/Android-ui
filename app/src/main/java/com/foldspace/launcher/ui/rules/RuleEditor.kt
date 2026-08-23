package com.foldspace.launcher.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.context.AutomationRule
import com.foldspace.launcher.context.BluetoothClass
import com.foldspace.launcher.context.RuleMatcher
import com.foldspace.launcher.context.SwitchMode
import com.foldspace.launcher.context.TimeBucket
import com.foldspace.launcher.context.label
import com.foldspace.launcher.spaces.SpaceId
import com.foldspace.launcher.ui.components.FoldCard
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * Where the user writes the rules that may switch a 情境 outright.
 *
 * §7.1 puts user-authored rules at level 2, above every automatic signal, and
 * §7.2 says only they may apply a switch rather than suggest one. All of that
 * was implemented and none of it was reachable: the engine was built with an
 * empty rule list and there was no way to add one, so "自動" mode did almost
 * nothing.
 *
 * The conditions offered are exactly the ones [RuleMatcher] can actually
 * evaluate. Offering a condition the engine ignores would be worse than
 * offering none.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RuleEditor(
    rules: List<AutomationRule>,
    switchMode: SwitchMode,
    onSave: (AutomationRule) -> Unit,
    onDelete: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens
    var draft by remember { mutableStateOf<AutomationRule?>(null) }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(tokens.overlayScrim())
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "自動切換規則",
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.textPrimary,
                )
                Text(
                    text = "完成",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.accent,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (switchMode != SwitchMode.Automatic) {
                    item {
                        // A rule that cannot apply is worth saying out loud, or the
                        // user writes one and concludes the feature is broken.
                        FoldCard(Modifier.fillMaxWidth()) {
                            Text(
                                text = "目前是「" + switchMode.shortLabel() + "」",
                                style = MaterialTheme.typography.titleSmall,
                                color = tokens.textPrimary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "規則仍會比對，但只會提示、不會直接切換。要讓規則自己切換，" +
                                    "請把「情境切換」改成自動。",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary,
                            )
                        }
                    }
                }

                rules.forEach { rule ->
                    item(key = rule.id) {
                        RuleRow(
                            rule = rule,
                            onToggle = { onToggle(rule.id, it) },
                            onDelete = { onDelete(rule.id) },
                            onEdit = { draft = rule },
                        )
                    }
                }

                item {
                    if (rules.isEmpty()) {
                        Text(
                            text = "還沒有規則。加一條，例如「平日上午 → 工作」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textMuted,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    Text(
                        text = "＋ 新增規則",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.accent,
                        modifier = Modifier
                            .clickable { draft = newRule() }
                            .padding(vertical = 10.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
    }

    draft?.let { editing ->
        RuleSheet(
            rule = editing,
            onSave = {
                onSave(it)
                draft = null
            },
            onDismiss = { draft = null },
            contentPadding = contentPadding,
        )
    }
    }
}

@Composable
private fun RuleRow(
    rule: AutomationRule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    FoldCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
                Text(
                    text = rule.matcher.describe() + " → " + rule.target.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.textPrimary,
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "編輯",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.accent,
                modifier = Modifier.clickable(onClick = onEdit).padding(4.dp),
            )
            Text(
                text = "刪除",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                modifier = Modifier.clickable(onClick = onDelete).padding(4.dp),
            )
        }
    }
}

/** The condition builder. Every control here maps to a real matcher field. */
@Composable
private fun RuleSheet(
    rule: AutomationRule,
    onSave: (AutomationRule) -> Unit,
    onDismiss: () -> Unit,
    contentPadding: PaddingValues,
) {
    val tokens = FoldSpaceTheme.tokens
    var target by remember(rule.id) { mutableStateOf(rule.target) }
    var buckets by remember(rule.id) { mutableStateOf(rule.matcher.timeBuckets) }
    var weekdayOnly by remember(rule.id) { mutableStateOf(rule.matcher.weekdayOnly) }
    var charging by remember(rule.id) { mutableStateOf(rule.matcher.charging) }
    var bluetooth by remember(rule.id) { mutableStateOf(rule.matcher.bluetoothClass) }

    val matcher = RuleMatcher(
        timeBuckets = buckets,
        weekdayOnly = weekdayOnly,
        charging = charging,
        bluetoothClass = bluetooth,
        calendarCategory = rule.matcher.calendarCategory,
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(tokens.overlayScrim())
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "規則條件",
            style = MaterialTheme.typography.headlineSmall,
            color = tokens.textPrimary,
        )
        Spacer(Modifier.height(14.dp))

        SectionLabel("切換到")
        ChipRow(
            options = SpaceId.entries.map { it to it.displayName },
            selected = setOf(target),
            onToggle = { target = it },
        )

        SectionLabel("時段")
        ChipRow(
            options = TimeBucket.entries.map { it to it.label() },
            selected = buckets,
            onToggle = { bucket ->
                buckets = if (bucket in buckets) buckets - bucket else buckets + bucket
            },
        )

        SectionLabel("藍牙")
        ChipRow(
            options = BluetoothClass.entries.map { it to it.label() },
            selected = setOfNotNull(bluetooth),
            onToggle = { value -> bluetooth = if (bluetooth == value) null else value },
        )

        SectionLabel("其他")
        ChipRow(
            options = listOf(
                Condition.Weekday to "只在平日",
                Condition.Charging to "充電中",
                Condition.NotCharging to "未充電",
            ),
            selected = buildSet {
                if (weekdayOnly) add(Condition.Weekday)
                when (charging) {
                    true -> add(Condition.Charging)
                    false -> add(Condition.NotCharging)
                    null -> Unit
                }
            },
            onToggle = { condition ->
                when (condition) {
                    Condition.Weekday -> weekdayOnly = !weekdayOnly
                    Condition.Charging -> charging = if (charging == true) null else true
                    Condition.NotCharging -> charging = if (charging == false) null else false
                }
            },
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = if (matcher.hasAnyCondition()) {
                matcher.describe() + " → " + target.displayName
            } else {
                // Saving this would give the user a rule that silently never
                // fires, so the button says why instead.
                "至少要選一個時段、藍牙或充電條件"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (matcher.hasAnyCondition()) tokens.textSecondary else tokens.textMuted,
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (matcher.hasAnyCondition()) {
                Text(
                    text = "儲存",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.accent,
                    modifier = Modifier
                        .clickable {
                            onSave(rule.copy(target = target, matcher = matcher))
                        }
                        .padding(6.dp),
                )
            }
            Text(
                text = "取消",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                modifier = Modifier.clickable(onClick = onDismiss).padding(6.dp),
            )
        }
    }
}

private enum class Condition { Weekday, Charging, NotCharging }

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = FoldSpaceTheme.tokens.textMuted,
    )
    Spacer(Modifier.height(6.dp))
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val isOn = value in selected
            Box(
                Modifier
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isOn) tokens.accent.copy(alpha = 0.22f) else tokens.surfaceElevated,
                    )
                    .clickable { onToggle(value) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOn) tokens.accent else tokens.textSecondary,
                )
            }
        }
    }
}

private fun SwitchMode.shortLabel(): String = when (this) {
    SwitchMode.ManualOnly -> "只手動"
    SwitchMode.SuggestFirst -> "先詢問"
    SwitchMode.Automatic -> "自動"
}

/** Ids are only ever compared, so any stable unique string will do. */
private fun newRule() = AutomationRule(
    id = "rule-" + System.currentTimeMillis(),
    target = SpaceId.Work,
    matcher = RuleMatcher(),
)
