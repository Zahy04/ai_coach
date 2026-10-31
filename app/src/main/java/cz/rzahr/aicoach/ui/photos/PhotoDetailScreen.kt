package cz.rzahr.aicoach.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.ui.components.AppHeader
import cz.rzahr.aicoach.ui.components.CircleIconButton
import cz.rzahr.aicoach.ui.components.ConfirmDialog
import cz.rzahr.aicoach.util.formatDate
import java.io.File

@Composable
fun PhotoDetailScreen(
    photoId: Long,
    onBack: () -> Unit,
    viewModel: PhotosViewModel = hiltViewModel()
) {
    val photo by remember(photoId) { viewModel.photoById(photoId) }.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppHeader(
                title = photo?.timestamp?.formatDate() ?: stringResource(R.string.photo_detail_default),
                eyebrow = photo?.pose?.let { poseLabel(it) },
                onBack = onBack,
                actions = {
                    if (photo != null) {
                        CircleIconButton(
                            icon = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.delete),
                            onClick = { confirmDelete = true },
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            photo?.let {
                AsyncImage(
                    model = File(it.filePath),
                    contentDescription = it.timestamp.formatDate(),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    val target = photo
    if (confirmDelete && target != null) {
        ConfirmDialog(
            title = stringResource(R.string.photo_delete_confirm_title),
            text = stringResource(R.string.photos_delete_confirm_text),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete(target)
                onBack()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
