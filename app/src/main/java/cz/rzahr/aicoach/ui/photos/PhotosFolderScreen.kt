package cz.rzahr.aicoach.ui.photos

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.data.db.entity.ProgressPhotoEntity
import cz.rzahr.aicoach.util.formatDate
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotosFolderScreen(
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenCompare: (Long, Long) -> Unit,
    onOpenSlideshow: (String) -> Unit,
    viewModel: PhotosFolderViewModel = hiltViewModel()
) {
    val pose = viewModel.pose
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val allPoses by viewModel.allPoses.collectAsStateWithLifecycle()
    var selection by remember { mutableStateOf(setOf<Long>()) }
    var selectMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect {
            snackbarHostState.showSnackbar(context.getString(R.string.photos_imported, it))
        }
    }

    val pickMultiple = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) viewModel.addPhotos(uris)
    }

    val scope = rememberCoroutineScope()
    var pendingCapture by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCapture
        pendingCapture = null
        if (ok && file != null && file.exists()) viewModel.addCaptured(file)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(poseLabel(pose)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!selectMode) {
                        IconButton(onClick = { onOpenSlideshow(pose) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.slideshow_title))
                        }
                        IconButton(onClick = { pickMultiple.launch("image/*") }) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.photos_add))
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val file = viewModel.createCaptureFile()
                                pendingCapture = file
                                takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                            }
                        }) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.photos_take))
                        }
                    }
                    if (selection.isNotEmpty() && selection.size <= 2) {
                        if (selection.size == 2) {
                            val ids = selection.sorted()
                            IconButton(onClick = {
                                onOpenCompare(ids.first(), ids.last())
                                selection = emptySet()
                                selectMode = false
                            }) {
                                Icon(Icons.Filled.Compare, contentDescription = stringResource(R.string.photos_compare_action))
                            }
                        }
                    }
                    IconButton(onClick = {
                        if (selectMode) {
                            selection = emptySet()
                            selectMode = false
                        } else {
                            selectMode = true
                        }
                    }) {
                        Icon(
                            if (selectMode) Icons.Filled.Check else Icons.Filled.HighlightOff,
                            contentDescription = stringResource(
                                if (selectMode) R.string.photos_select_done else R.string.photos_select_mode
                            )
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selection.size == 1) {
                IconButton(
                    onClick = { viewModel.delete(photos.first { it.id == selection.first() }) }
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    ) { padding ->
        if (photos.isEmpty()) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.photos_folder_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    if (selectMode) {
                        PhotoSelectTile(
                            photo = photo,
                            selected = photo.id in selection,
                            onClick = {
                                selection = if (photo.id in selection) selection - photo.id else selection + photo.id
                            }
                        )
                    } else {
                        PhotoFolderTile(
                            photo = photo,
                            allPoses = allPoses,
                            onTap = { onOpenDetail(photo.id) },
                            onMove = { target -> viewModel.moveToPose(photo.id, target) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoFolderTile(
    photo: ProgressPhotoEntity,
    allPoses: List<String>,
    onTap: () -> Unit,
    onMove: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        Modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onTap, onLongClick = { menuExpanded = true })
    ) {
        AsyncImage(
            model = File(photo.filePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                photo.timestamp.formatDate(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            Text(
                stringResource(R.string.photos_move_to),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            allPoses.forEach { target ->
                if (target != photo.pose) {
                    DropdownMenuItem(
                        text = { Text(poseLabel(target)) },
                        onClick = {
                            menuExpanded = false
                            onMove(target)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoSelectTile(
    photo: ProgressPhotoEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onClick)
    ) {
        AsyncImage(
            model = File(photo.filePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (selected) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}