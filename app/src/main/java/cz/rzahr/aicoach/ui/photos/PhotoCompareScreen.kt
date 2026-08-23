package cz.rzahr.aicoach.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cz.rzahr.aicoach.util.formatDate
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCompareScreen(
    firstId: Long,
    secondId: Long,
    onBack: () -> Unit,
    viewModel: PhotosViewModel = hiltViewModel()
) {
    val first by remember(firstId) { viewModel.photoById(firstId) }.collectAsStateWithLifecycle()
    val second by remember(secondId) { viewModel.photoById(secondId) }.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Před / po") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val before = first
            val after = second
            if (before != null && after != null) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "Před · ${before.timestamp.formatDate()}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Po · ${after.timestamp.formatDate()}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                CompareSlider(
                    beforeFile = File(before.filePath),
                    afterFile = File(after.filePath)
                )
                Text(
                    "Táhni prstem doleva a doprava.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Text("Fotky se nepodařilo načíst.", style = MaterialTheme.typography.bodyLarge)
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
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        val fullWidth = maxWidth

        AsyncImage(
            model = afterFile,
            contentDescription = "Po",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .width(fullWidth * fraction)
                .clipToBounds()
        ) {
            AsyncImage(
                model = beforeFile,
                contentDescription = "Před",
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(fullWidth)
            )
        }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = fullWidth * fraction)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.9f))
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(x = fullWidth * fraction - 20.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.CompareArrows,
                contentDescription = null,
                tint = Color(0xFF1A2000)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        fraction = (change.position.x / size.width).coerceIn(0.02f, 0.98f)
                    }
                }
        )
    }
}
