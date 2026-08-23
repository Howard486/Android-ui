package com.foldspace.launcher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * A text button that can actually be hit.
 *
 * The launcher is full of bare `Text` with `.clickable().padding(4.dp)` at
 * 11sp, which is about a 19dp target — well under half the 48dp minimum, and
 * with no pressed state at all. This is the same thing with room around it and
 * a ripple, so the padding is a touch target rather than decoration.
 */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
    enabled: Boolean = true,
) {
    val tokens = FoldSpaceTheme.tokens
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minWidth = MIN_TARGET, minHeight = MIN_TARGET)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                !enabled -> tokens.textMuted
                color != null -> color
                else -> tokens.accent
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Android's own minimum, which none of the old text buttons met. */
private val MIN_TARGET = 48.dp
