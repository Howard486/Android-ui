package com.foldspace.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.home.HomeItem
import com.foldspace.launcher.spaces.SpaceDensity
import com.foldspace.launcher.ui.components.AppTile
import com.foldspace.launcher.ui.components.FoldCard
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * An open folder.
 *
 * The title is editable in place rather than behind a rename menu: a folder
 * created by one-tap organise arrives with a guessed name, and the moment
 * someone wants to change it is the moment they are looking at its contents.
 */
@Composable
fun FolderSheet(
    folder: HomeItem,
    density: SpaceDensity,
    onLaunch: (HomeItem) -> Unit,
    onRename: (String) -> Unit,
    onRemoveFromFolder: (HomeItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens
    var title by remember(folder.id) { mutableStateOf(folder.folderTitle.orEmpty()) }

    Box(
        modifier
            .fillMaxSize()
            .background(tokens.overlayScrim())
            .clickable(onClick = onDismiss)
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Swallows the dismiss click so tapping inside the card does not close it.
        FoldCard(Modifier.fillMaxWidth().clickable(enabled = false) {}) {
            BasicTextField(
                value = title,
                onValueChange = {
                    title = it
                    onRename(it)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = tokens.textPrimary),
                cursorBrush = SolidColor(tokens.accent),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = "${folder.folderContents.size} 個 App · 長按可移出",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
            )
            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(density.drawerCellDp.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(folder.folderContents, key = { it.id }) { member ->
                    val app = member.app
                    if (app != null) {
                        AppTile(
                            entry = app,
                            onClick = { onLaunch(member) },
                            onLongClick = { onRemoveFromFolder(member) },
                            iconSize = density.iconSizeDp.dp,
                        )
                    }
                }
            }
        }
    }
}
