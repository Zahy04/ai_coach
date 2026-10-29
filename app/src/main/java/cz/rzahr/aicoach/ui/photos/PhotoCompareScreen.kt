package cz.rzahr.aicoach.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.ui.components.AppHeader
import cz.rzahr.aicoach.ui.components.EmptyState
import cz.rzahr.aicoach.ui.components.Eyebrow
import cz.rzahr.aicoach.util.formatDate
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun PhotoCompareScreen(
    firstId: Long,
    secondId: Long,
    onBack: () -> Unit,
    viewModel: PhotosViewModel = hiltViewModel()
) {
    val first by remember(firstId) { viewModel.photoById(firstId) }.collectAsStateWithLifecycle()
    val second by remember(secondId) { viewModel.photoById(secondId) }.collectAsStateWithLifecycle()
    // Krátce po otevření jsou fotky ještě null (načítají se) – „nenalezeno" ukázat až potom.
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1500)
        waited = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppHeader(title = stringResource(R.string.compare_title), onBack = onBack) }
    ) { padding ->
        val before = first
        val after = second
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when {
                before != null && after != null -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow(stringResource(R.string.compare_before_cd))
                            Text(before.timestamp.formatDate(), style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Eyebrow(stringResource(R.string.compare_after_cd), color = MaterialTheme.colorScheme.primary)
                            Text(after.timestamp.formatDate(), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    CompareSlider(File(before.filePath), File(after.filePath))
                    Text(
                        stringResource(R.string.compare_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                !waited -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> EmptyState(
                    icon = Icons.Outlined.ImageNotSupported,
                    title = stringResource(R.string.compare_missing_title),
                    description = stringResource(R.string.compare_missing_desc)
                )
            }
        }
    }
}

@Composable
private fun CompareSlider(beforeFile: File, afterFile: File) {
    var fraction by remember { mutableFloatStateOf(0.5f) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val fullWidth = maxWidth
        AsyncImage(
            model = afterFile,
            contentDescription = stringResource(R.string.compare_after_cd),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .width(fullWidth * fraction)
                .fillMaxHeight()
                .clipToBounds()
        ) {
            AsyncImage(
                model = beforeFile,
                contentDescription = stringResource(R.string.compare_before_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(fullWidth)
                    .fillMaxHeight()
            )
        }
        Box(
            Modifier
                .offset(x = fullWidth * fraction - 1.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = fullWidth * fraction - 22.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, tint = Color(0xFF1B1A16))
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        fraction = (change.position.x / size.width).coerceIn(0.02f, 0.98f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset -> fraction = (offset.x / size.width).coerceIn(0.02f, 0.98f) }
                }
        )
    }
}
