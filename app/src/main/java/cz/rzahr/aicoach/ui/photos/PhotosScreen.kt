package cz.rzahr.aicoach.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.ui.components.AppHeader
import cz.rzahr.aicoach.ui.components.CircleIconButton
import cz.rzahr.aicoach.ui.components.bounceClick
import java.io.File

@Composable
fun PhotosScreen(
    onOpenFolder: (String) -> Unit,
    viewModel: PhotosViewModel = hiltViewModel()
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    PhotosContent(folders = folders, onOpenFolder = onOpenFolder, onCreateFolder = viewModel::createFolder)
}

@Composable
fun PhotosContent(
    folders: List<PhotosViewModel.Folder>,
    onOpenFolder: (String) -> Unit,
    onCreateFolder: (String) -> Unit
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var newFolderName by rememberSaveable { mutableStateOf("") }
    val total = folders.sumOf { it.count }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppHeader(
                title = stringResource(R.string.photos_title),
                eyebrow = pluralStringResource(R.plurals.photo_count, total, total),
                actions = {
                    CircleIconButton(
                        icon = Icons.Filled.CreateNewFolder,
                        contentDescription = stringResource(R.string.photos_folder_create),
                        onClick = { showCreateDialog = true }
                    )
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(folders, key = { it.key }) { folder ->
                FolderTile(folder = folder, onClick = { onOpenFolder(folder.key) })
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.photos_folder_create)) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text(stringResource(R.string.photos_folder_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateFolder(newFolderName)
                        newFolderName = ""
                        showCreateDialog = false
                    },
                    enabled = newFolderName.isNotBlank()
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun FolderTile(folder: PhotosViewModel.Folder, onClick: () -> Unit) {
    val cover = folder.latestPhoto
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .bounceClick(onClick)
    ) {
        if (cover != null) {
            AsyncImage(
                model = File(cover.filePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f)))
            )
        } else {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
            )
        }
        val textColor = if (cover != null) Color.White else MaterialTheme.colorScheme.onSurface
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(
                poseLabel(folder.key),
                style = MaterialTheme.typography.titleSmall,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                pluralStringResource(R.plurals.photo_count, folder.count, folder.count),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.75f)
            )
        }
    }
}
