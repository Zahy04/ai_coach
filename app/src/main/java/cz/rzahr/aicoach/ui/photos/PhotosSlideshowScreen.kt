package cz.rzahr.aicoach.ui.photos

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.ui.components.AppHeader
import cz.rzahr.aicoach.ui.components.CircleIconButton
import cz.rzahr.aicoach.ui.components.LinearMeter
import cz.rzahr.aicoach.util.formatDate
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun PhotosSlideshowScreen(
    onBack: () -> Unit,
    viewModel: PhotosFolderViewModel = hiltViewModel()
) {
    // Ve složce jsou fotky od nejnovější – časosběr ale dává smysl od nejstarší.
    val photos = viewModel.photos.collectAsStateWithLifecycle().value.sortedBy { it.timestamp }
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val safeIndex = if (photos.isEmpty()) 0 else index.coerceIn(0, photos.lastIndex)

    LaunchedEffect(photos.size, safeIndex, paused) {
        if (photos.size < 2 || paused) return@LaunchedEffect
        delay(2400)
        index = (safeIndex + 1) % photos.size
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppHeader(
                title = photos.getOrNull(safeIndex)?.timestamp?.formatDate() ?: poseLabel(viewModel.pose),
                eyebrow = if (photos.isNotEmpty()) "${safeIndex + 1} / ${photos.size}" else null,
                onBack = onBack,
                actions = {
                    CircleIconButton(
                        icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = stringResource(if (paused) R.string.slideshow_play else R.string.slideshow_pause),
                        onClick = { paused = !paused }
                    )
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { paused = !paused },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = photos.getOrNull(safeIndex),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "slideshow"
                ) { photo ->
                    if (photo != null) {
                        AsyncImage(
                            model = File(photo.filePath),
                            contentDescription = photo.timestamp.formatDate(),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (photos.isEmpty()) {
                    Text(
                        stringResource(R.string.photos_folder_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (photos.size >= 2) {
                LinearMeter(
                    progress = (safeIndex + 1) / photos.size.toFloat(),
                    color = MaterialTheme.colorScheme.primary,
                    height = 4.dp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
        }
    }
}
