package cz.rzahr.aicoach.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cz.rzahr.aicoach.data.db.entity.ChatMessageEntity
import cz.rzahr.aicoach.ui.theme.TextPrimaryDark
import cz.rzahr.aicoach.util.formatTime
import cz.rzahr.aicoach.util.toLocalDate
import kotlinx.coroutines.launch
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private sealed interface ChatRow {
    val key: String

    data class Separator(val label: String, override val key: String) : ChatRow
    data class Message(val message: ChatMessageEntity) : ChatRow {
        override val key: String = "msg_${message.id}"
    }
}

private fun dayLabelFor(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Dnes"
        today.minusDays(1) -> "Včera"
        else -> date.format(DateTimeFormatter.ofPattern("d. M. yyyy"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    weeklySummaryRequested: Boolean = false,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val pendingImagePath by viewModel.pendingImagePath.collectAsStateWithLifecycle()
    val currentModel by viewModel.model.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val modelsLoading by viewModel.modelsLoading.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.attachImage(it) }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCaptureFile
        pendingCaptureFile = null
        if (success && file != null && file.exists()) {
            viewModel.attachCapturedImage(file)
        }
    }

    val snackbarScope = rememberCoroutineScope()
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            input = (input + " " + spoken).trim()
        }
    }
    fun startVoiceInput() {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "cs-CZ")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            snackbarScope.launch { snackbarHostState.showSnackbar("Hlasový vstup není na tomto zařízení dostupný.") }
        }
    }

    val rows = remember(messages) {
        buildList {
            var lastDate: LocalDate? = null
            messages.forEach { message ->
                val date = message.timestamp.toLocalDate()
                if (date != lastDate) {
                    add(ChatRow.Separator(dayLabelFor(date), "sep_$date"))
                    lastDate = date
                }
                add(ChatRow.Message(message))
            }
        }
    }

    val lastModelId = messages.lastOrNull()?.takeIf { it.role == ChatMessageEntity.ROLE_MODEL }?.id
    val showStreamingBubble = !streamingText.isNullOrEmpty()
    val showTypingIndicator = sending && streamingText.isNullOrEmpty()
    val itemCount = rows.size +
        (if (showStreamingBubble) 1 else 0) +
        (if (showTypingIndicator) 1 else 0)

    LaunchedEffect(rows.size, streamingText?.length?.div(80)) {
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.toolEvents.collect { snackbarHostState.showSnackbar(it) }
    }

    var summaryHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(weeklySummaryRequested) {
        if (weeklySummaryRequested && !summaryHandled) {
            summaryHandled = true
            viewModel.sendWeeklySummary()
        }
    }

    LaunchedEffect(hasApiKey) {
        if (hasApiKey && availableModels.isEmpty()) {
            viewModel.loadModels()
        }
    }

    var modelMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chat s trenérem")
                        Text(
                            currentModel.removePrefix("gemini-"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { modelMenuExpanded = true }) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Změnit model")
                        }
                        DropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false }
                        ) {
                            if (modelsLoading) {
                                DropdownMenuItem(
                                    text = { Text("Načítám modely…") },
                                    onClick = {},
                                    enabled = false
                                )
                            } else if (availableModels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Modely se nepodařilo načíst — zkus znovu") },
                                    onClick = { viewModel.loadModels() }
                                )
                            } else {
                                availableModels.forEach { candidate ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                candidate,
                                                color = if (candidate == currentModel) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                fontWeight = if (candidate == currentModel) FontWeight.Bold else null
                                            )
                                        },
                                        trailingIcon = {
                                            if (candidate == currentModel) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectModel(candidate)
                                            modelMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Smazat chat")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            if (!hasApiKey) {
                ApiKeyMissingBanner(onOpenSettings = onOpenSettings)
            }
            error?.let { message ->
                ErrorBanner(
                    message = message,
                    onRetry = viewModel::retryGeneration,
                    onDismiss = viewModel::clearError
                )
            }

            if (rows.isEmpty() && !showStreamingBubble && !showTypingIndicator) {
                EmptyChatHint(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is ChatRow.Separator -> DaySeparator(row.label)
                            is ChatRow.Message -> MessageBubble(
                                message = row.message,
                                canRegenerate = row.message.id == lastModelId && !sending,
                                onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
                                onRegenerate = viewModel::regenerateLastResponse,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                    if (showStreamingBubble) {
                        item(key = "streaming") {
                            StreamingBubble(streamingText.orEmpty())
                        }
                    }
                    if (showTypingIndicator) {
                        item(key = "typing") {
                            Row(verticalAlignment = Alignment.Bottom) {
                                CoachAvatar(Modifier.padding(end = 8.dp, bottom = 4.dp))
                                TypingIndicator()
                            }
                        }
                    }
                }
            }

            pendingImagePath?.let { path ->
                PendingImageChip(
                    path = path,
                    onRemove = viewModel::clearPendingImage
                )
            }

            InputRow(
                input = input,
                onInputChange = { input = it },
                sending = sending,
                enabled = hasApiKey,
                onAttachClick = { pickImage.launch(arrayOf("image/*")) },
                onCameraClick = {
                    val file = viewModel.newCaptureFile()
                    pendingCaptureFile = file
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    takePicture.launch(uri)
                },
                onMicClick = { startVoiceInput() },
                onStopClick = viewModel::stopGeneration,
                onSend = {
                    viewModel.send(input)
                    input = ""
                }
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Smazat historii chatu?") },
            text = { Text("Zprávy se smažou pouze z chatu. Uložená data (váha, jídlo, poznámky) zůstanou.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearChat()
                }) { Text("Smazat") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Zrušit") }
            }
        )
    }
}

@Composable
private fun DaySeparator(label: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CoachAvatar(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.FitnessCenter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun ApiKeyMissingBanner(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Chybí Gemini API klíč",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.size(4.dp))
            Text(
                "Bez klíče chat nefunguje. Zdarma ho získáš na aistudio.google.com.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.size(8.dp))
            OutlinedButton(onClick = onOpenSettings) { Text("Otevřít nastavení") }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.size(8.dp))
            Row {
                OutlinedButton(onClick = onRetry) { Text("Zkusit znovu") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("Zavřít") }
            }
        }
    }
}

@Composable
private fun EmptyChatHint(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            "Napiš mi, co jsi dnes jedl nebo jak jsi cvičil.\nTřeba: „K snídani jsem měl ovesnou kaši a vážím 78 kg.“",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessageEntity,
    canRegenerate: Boolean,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isUser = message.role == ChatMessageEntity.ROLE_USER

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box {
            if (isUser) {
                Surface(
                    shape = RoundedCornerShape(20.dp, 6.dp, 20.dp, 20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { menuExpanded = true }
                    )
                ) {
                    BubbleContent(message)
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    CoachAvatar(Modifier.padding(end = 8.dp, bottom = 4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { menuExpanded = true }
                        )
                    ) {
                        BubbleContent(message, forceTextColor = TextPrimaryDark)
                    }
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Kopírovat") },
                    onClick = {
                        menuExpanded = false
                        onCopy(message.content)
                    }
                )
                if (!isUser && canRegenerate) {
                    DropdownMenuItem(
                        text = { Text("Regenerovat odpověď") },
                        onClick = {
                            menuExpanded = false
                            onRegenerate()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleContent(message: ChatMessageEntity, forceTextColor: Color? = null) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        message.imagePath?.let { path ->
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth(0.75f)
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        if (message.role == ChatMessageEntity.ROLE_USER) {
            Text(message.content, style = MaterialTheme.typography.bodyLarge)
        } else {
            MarkdownText(message.content, color = forceTextColor ?: Color.Unspecified)
        }
        val timeColor = forceTextColor?.copy(alpha = 0.6f) ?: MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            message.timestamp.formatTime(),
            style = MaterialTheme.typography.labelSmall,
            color = timeColor,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        CoachAvatar(Modifier.padding(end = 8.dp, bottom = 4.dp))
        Surface(
            shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                MarkdownText(text, color = TextPrimaryDark)
                BlinkingCaret()
            }
        }
    }
}

@Composable
private fun BlinkingCaret() {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "caretAlpha"
    )
    Box(
        Modifier
            .padding(start = 2.dp, bottom = 2.dp)
            .size(width = 3.dp, height = 16.dp)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                RoundedCornerShape(1.dp)
            )
    )
}

@Composable
private fun TypingIndicator() {
    Surface(
        shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                TypingDot(delayMillis = index * 200)
                if (index < 2) Spacer(Modifier.width(5.dp))
            }
        }
    }
}

@Composable
private fun TypingDot(delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "dot")
    val bounce by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(450),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMillis)
        ),
        label = "dotAlpha"
    )
    Box(
        Modifier
            .size(9.dp)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = bounce),
                CircleShape
            )
    )
}

@Composable
private fun PendingImageChip(path: String, onRemove: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Fotka připravena k odeslání",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Odebrat fotku")
        }
    }
}

@Composable
private fun InputRow(
    input: String,
    onInputChange: (String) -> Unit,
    sending: Boolean,
    enabled: Boolean,
    onAttachClick: () -> Unit,
    onCameraClick: () -> Unit,
    onMicClick: () -> Unit,
    onStopClick: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = onMicClick, enabled = enabled && !sending) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Diktovat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCameraClick, enabled = enabled && !sending) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = "Vyfotit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onAttachClick, enabled = enabled && !sending) {
                Icon(
                    Icons.Filled.AddPhotoAlternate,
                    contentDescription = "Připojit fotku",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Napiš zprávu…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                maxLines = 5,
                enabled = enabled && !sending,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark
                )
            )
            FilledIconButton(
                onClick = if (sending) onStopClick else onSend,
                enabled = if (sending) true else enabled && input.isNotBlank(),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(
                    if (sending) Icons.Filled.Close else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (sending) "Zastavit generování" else "Odeslat"
                )
            }
        }
    }
}
