package cz.rzahr.aicoach.ui.photos

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.data.db.entity.ProgressPhotoEntity
import cz.rzahr.aicoach.ui.components.AppHeader
import cz.rzahr.aicoach.ui.components.CircleIconButton
import cz.rzahr.aicoach.ui.components.ConfirmDialog
import cz.rzahr.aicoach.ui.components.EmptyState
import cz.rzahr.aicoach.util.formatDate
import java.io.File
import kotlinx.coroutines.launch

data class PhotosFolderActions(
    val onBack: () -> Unit = {},
    val onOpenDetail: (Long) -> Unit = {},
    val onOpenCompare: (Long, Long) -> Unit = { _, _ -> },
    val onOpenSlideshow: (String) -> Unit = {},
    val onPickPhotos: () -> Unit = {},
    val onTakePhoto: () -> Unit = {},
    val onDelete: (List<ProgressPhotoEntity>) -> Unit = {},
    val onMove: (List<Long>, String) -> Unit = { _, _ -> }
)

@Composable
fun PhotosFolderScreen(
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenCompare: (Long, Long) -> Unit,
    onOpenSlideshow: (String) -> Unit,
    viewModel: PhotosFolderViewModel = hiltViewModel()
) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val allPoses by viewModel.allPoses.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(context.getString(R.string.photos_imported, it)) }
    }
    val pickMultiple = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) viewModel.addPhotos(uris)
    }
    // Cesta přežije zánik aktivity během focení (otočení, úklid procesu).
    var pendingCapturePath by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCapturePath?.let(::File)
        pendingCapturePath = null
        if (ok && file != null && file.exists() && file.length() > 0) viewModel.addCaptured(file) else file?.delete()
    }

    PhotosFolderContent(
        pose = viewModel.pose,
        photos = photos,
        allPoses = allPoses,
        snackbarHostState = snackbarHostState,
        actions = PhotosFolderActions(
            onBack = onBack,
            onOpenDetail = onOpenDetail,
            onOpenCompare = onOpenCompare,
            onOpenSlideshow = onOpenSlideshow,
            onPickPhotos = {
                pickMultiple.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onTakePhoto = {
                scope.launch {
                    val file = viewModel.createCaptureFile()
                    pendingCapturePath = file.absolutePath
                    takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                }
            },
            onDelete = viewModel::deleteAll,
            onMove = { ids, target -> ids.forEach { viewModel.moveToPose(it, target) } }
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosFolderContent(
    pose: String,
    photos: List<ProgressPhotoEntity>,
    allPoses: List<String>,
    actions: PhotosFolderActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var selectMode by rememberSaveable { mutableStateOf(false) }
    var rawSelection by rememberSaveable { mutableStateOf(listOf<Long>()) }
    // Výběr se vždy průnikem s aktuálními fotkami – smazaná fotka v něm nesmí zůstat
    // (dřív druhé ťuknutí na Smazat spadlo na NoSuchElementException).
    val photoIds = remember(photos) { photos.map { it.id }.toSet() }
    val selection = rawSelection.filter { it in photoIds }
    var confirmDelete by remember { mutableStateOf(false) }
    var moveMenu by remember { mutableStateOf(false) }

    fun exitSelection() {
        selectMode = false
        rawSelection = emptyList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectMode) {
                AppHeader(
                    title = pluralStringResource(R.plurals.photos_selected, selection.size, selection.size),
                    onBack = ::exitSelection,
                    actions = {
                        CircleIconButton(
                            icon = Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = stringResource(R.string.photos_compare_action),
                            onClick = {
                                val ordered = photos.filter { it.id in selection }.sortedBy { it.timestamp }
                                actions.onOpenCompare(ordered.first().id, ordered.last().id)
                                exitSelection()
                            },
                            enabled = selection.size == 2
                        )
                        Box(Modifier.padding(start = 8.dp)) {
                            CircleIconButton(
                                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                                contentDescription = stringResource(R.string.photos_move_to),
                                onClick = { moveMenu = true },
                                enabled = selection.isNotEmpty()
                            )
                            DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                                allPoses.filter { it != pose }.forEach { target ->
                                    DropdownMenuItem(
                                        text = { Text(poseLabel(target)) },
                                        onClick = {
                                            moveMenu = false
                                            actions.onMove(selection, target)
                                            exitSelection()
                                        }
                                    )
                                }
                            }
                        }
                        CircleIconButton(
                            icon = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.delete),
                            onClick = { confirmDelete = true },
                            enabled = selection.isNotEmpty(),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                )
            } else {
                AppHeader(
                    title = poseLabel(pose),
                    eyebrow = pluralStringResource(R.plurals.photo_count, photos.size, photos.size),
                    onBack = actions.onBack,
                    actions = {
                        if (photos.size >= 2) {
                            CircleIconButton(
                                icon = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.slideshow_title),
                                onClick = { actions.onOpenSlideshow(pose) }
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        CircleIconButton(
                            icon = Icons.Filled.PhotoLibrary,
                            contentDescription = stringResource(R.string.photos_add),
                            onClick = actions.onPickPhotos
                        )
                        Spacer(Modifier.width(8.dp))
                        CircleIconButton(
                            icon = Icons.Filled.PhotoCamera,
                            contentDescription = stringResource(R.string.photos_take),
                            onClick = actions.onTakePhoto,
                            container = MaterialTheme.colorScheme.inverseSurface,
                            tint = MaterialTheme.colorScheme.inverseOnSurface
                        )
                    }
                )
            }
        }
    ) { padding ->
        if (photos.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.PhotoCamera,
                title = stringResource(R.string.photos_folder_empty_title),
                description = stringResource(R.string.photos_folder_empty),
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    val selected = photo.id in selection
                    PhotoTile(
                        photo = photo,
                        selectMode = selectMode,
                        selected = selected,
                        onClick = {
                            if (selectMode) {
                                rawSelection = if (selected) selection - photo.id else selection + photo.id
                            } else {
                                actions.onOpenDetail(photo.id)
                            }
                        },
                        onLongClick = {
                            selectMode = true
                            if (!selected) rawSelection = selection + photo.id
                        }
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = pluralStringResource(R.plurals.photos_delete_confirm, selection.size, selection.size),
            text = stringResource(R.string.photos_delete_confirm_text),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                actions.onDelete(photos.filter { it.id in selection })
                exitSelection()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    photo: ProgressPhotoEntity,
    selectMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape) else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = File(photo.filePath),
            contentDescription = photo.timestamp.formatDate(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Text(
            photo.timestamp.formatDate(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        )
        if (selectMode) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f))
                    .border(BorderStroke(1.5.dp, Color.White), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
