package com.foldspace.launcher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.ui.components.FoldCard
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * The list of widgets the device offers.
 *
 * Grouped by the app that provides them, because that is how people look for
 * one — nobody remembers a widget's own name, they remember whose it is.
 */
@Composable
fun WidgetPicker(
    providers: List<AppWidgetProviderInfo>,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens
    val context = LocalContext.current
    val packageManager = context.packageManager

    val grouped = remember(providers) {
        providers
            .groupBy { it.provider.packageName }
            .toList()
            .sortedBy { (packageName, _) ->
                runCatching {
                    packageManager
                        .getApplicationInfo(packageName, 0)
                        .loadLabel(packageManager)
                        .toString()
                        .lowercase()
                }.getOrDefault(packageName)
            }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(tokens.scrim.copy(alpha = 0.96f))
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
                text = "新增小工具",
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.textPrimary,
            )
            Text(
                text = "取消",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.accent,
                modifier = Modifier.clickable(onClick = onDismiss).padding(6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        if (grouped.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "這台裝置沒有可用的小工具",
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
            grouped.forEach { (packageName, infos) ->
                item(key = "header-$packageName") {
                    val appLabel = remember(packageName) {
                        runCatching {
                            packageManager
                                .getApplicationInfo(packageName, 0)
                                .loadLabel(packageManager)
                                .toString()
                        }.getOrDefault(packageName)
                    }
                    Text(
                        text = appLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(infos, key = { it.provider.flattenToString() }) { info ->
                    val label = remember(info) {
                        runCatching { info.loadLabel(packageManager) }
                            .getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?: info.provider.shortClassName
                    }
                    FoldCard(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(info) },
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Widgets have a minimum size and the grid has fixed
                        // cells, so saying how much room one needs up front
                        // avoids adding one and finding it does not fit.
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "最小 ${info.minWidth} × ${info.minHeight} px",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted,
                        )
                    }
                }
            }
        }
    }
}
