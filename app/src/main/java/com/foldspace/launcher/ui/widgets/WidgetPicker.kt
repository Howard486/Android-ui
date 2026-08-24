package com.foldspace.launcher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

    var query by remember { mutableStateOf("") }
    val trimmed = query.trim().lowercase()

    // App label resolved once per package, not once per row: this list is
    // every widget on the device, and a PackageManager lookup per frame while
    // typing is the sort of thing that makes a search field feel broken.
    val labels = remember(providers) {
        providers.map { it.provider.packageName }.distinct().associateWith { packageName ->
            runCatching {
                packageManager.getApplicationInfo(packageName, 0)
                    .loadLabel(packageManager)
                    .toString()
            }.getOrDefault(packageName)
        }
    }

    val widgetLabels = remember(providers) {
        providers.associateBy({ it.provider.flattenToString() }) { info ->
            runCatching { info.loadLabel(packageManager) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: info.provider.shortClassName
        }
    }

    val grouped = remember(providers, labels, widgetLabels, trimmed) {
        providers
            // Matching on both the app's name and the widget's: people look
            // for "Outlook" and for "行事曆", and which one they remember
            // depends on the widget.
            .filter { info ->
                if (trimmed.isEmpty()) return@filter true
                val app = labels[info.provider.packageName].orEmpty().lowercase()
                val widget = widgetLabels[info.provider.flattenToString()].orEmpty().lowercase()
                trimmed in app || trimmed in widget ||
                    trimmed in info.provider.packageName.lowercase()
            }
            .groupBy { it.provider.packageName }
            .toList()
            .sortedBy { (packageName, _) -> labels[packageName].orEmpty().lowercase() }
    }

    // Which app sections are open. Everything starts closed when there are
    // many, because a device has dozens of widgets and an open list of all of
    // them is a wall — but a search that matched a handful should show them.
    val collapsedByDefault = trimmed.isEmpty() && grouped.size > AUTO_EXPAND_LIMIT
    val opened = remember { mutableStateListOf<String>() }

    Column(
        modifier
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
        PickerSearchField(query = query, onQueryChange = { query = it })
        Spacer(Modifier.height(12.dp))

        if (grouped.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (trimmed.isEmpty()) "這台裝置沒有可用的小工具"
                    else "找不到「$query」",
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
                val open = if (collapsedByDefault) packageName in opened else true

                item(key = "header-$packageName") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = collapsedByDefault) {
                                if (packageName in opened) opened.remove(packageName)
                                else opened.add(packageName)
                            }
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = labels[packageName].orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted,
                        )
                        Text(
                            text = if (collapsedByDefault && !open) "${infos.size} 個" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.accent,
                        )
                    }
                }

                if (!open) return@forEach

                items(infos, key = { it.provider.flattenToString() }) { info ->
                    val label = widgetLabels[info.provider.flattenToString()].orEmpty()
                    // A provider's own preview, falling back to its icon. A
                    // list of names alone was unreadable: nobody picks a widget
                    // by its class name, they pick it by what it looks like.
                    val preview = remember(info) {
                        val drawable = runCatching { info.loadPreviewImage(context, 0) }.getOrNull()
                            ?: runCatching { info.loadIcon(context, 0) }.getOrNull()
                        drawable?.toImageBitmapOrNull(PREVIEW_MAX_PX)
                    }
                    val density = LocalDensity.current

                    FoldCard(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(info) },
                    ) {
                        if (preview != null) {
                            Image(
                                bitmap = preview,
                                contentDescription = label,
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Widgets have a minimum size and the grid has fixed
                        // cells, so saying how much room one needs up front
                        // avoids adding one and finding it does not fit. The
                        // provider reports pixels; dp is what the grid is in.
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = with(density) {
                                "最小 ${info.minWidth.toDp().value.toInt()} × " +
                                    "${info.minHeight.toDp().value.toInt()} dp"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A provider's preview drawable as something Compose can draw.
 *
 * Bounded on the long edge: a preview is authored at whatever size its app
 * chose, and some ship full-screen artwork that would be a waste to decode at
 * source size for a row 120dp tall.
 */
private fun Drawable.toImageBitmapOrNull(maxPx: Int): ImageBitmap? = runCatching {
    // A bitmap-backed drawable can be handed over as it is.
    (this as? BitmapDrawable)?.bitmap?.let { return@runCatching it.asImageBitmap() }

    val sourceWidth = intrinsicWidth.takeIf { it > 0 } ?: maxPx
    val sourceHeight = intrinsicHeight.takeIf { it > 0 } ?: maxPx
    val scale = minOf(1f, maxPx.toFloat() / maxOf(sourceWidth, sourceHeight))
    val width = (sourceWidth * scale).toInt().coerceAtLeast(1)
    val height = (sourceHeight * scale).toInt().coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, width, height)
    draw(Canvas(bitmap))
    bitmap.asImageBitmap()
}.getOrNull()

/** Long-edge cap for a decoded preview. */
private const val PREVIEW_MAX_PX = 640

@Composable
private fun PickerSearchField(query: String, onQueryChange: (String) -> Unit) {
    val tokens = FoldSpaceTheme.tokens
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.cardRadius))
            .background(tokens.surfaceElevated)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.textPrimary),
            cursorBrush = SolidColor(tokens.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = "搜尋 App 或小工具名稱",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textMuted,
                    )
                }
                inner()
            },
        )
    }
}

/** Above this many apps the list opens closed, or it is a wall of names. */
private const val AUTO_EXPAND_LIMIT = 6
