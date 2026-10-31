package cz.rzahr.aicoach.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cz.rzahr.aicoach.R
import cz.rzahr.aicoach.data.db.entity.ChatMessageEntity
import cz.rzahr.aicoach.ui.components.AppHeader
import cz.rzahr.aicoach.ui.components.CircleIconButton
import cz.rzahr.aicoach.ui.components.ConfirmDialog
import cz.rzahr.aicoach.ui.components.Eyebrow
import cz.rzahr.aicoach.util.formatTime
import cz.rzahr.aicoach.util.toLocalDate
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Stav chatu pro bezstavový [ChatContent]. */
data class ChatState(
    val messages: List<ChatMessageEntity>,
    val sending: Boolean,
    val streamingText: String?,
    val hasApiKey: Boolean,
    val error: String?,
    val pendingImagePath: String?,
    val currentModel: String,
    val availableModels: List<String>,
    val modelsLoading: Boolean
)

data class ChatActions(
    val onOpenSettings: () -> Unit = {},
    val onSend: (String) -> Unit = {},
    val onStop: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onRegenerate: () -> Unit = {},
    val onClearChat: () -> Unit = {},
    val onSelectModel: (String) -> Unit = {},
    val onLoadModels: () -> Unit = {},
    val onPickImage: () -> Unit = {},
    val onTakePhoto: () -> Unit = {},
    val onDictate: () -> Unit = {},
    val onRemovePendingImage: () -> Unit = {},
    val onCopy: (String) -> Unit = {}
)

@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    weeklySummaryRequested: Boolean = false,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state = ChatState(
        messages = viewModel.messages.collectAsStateWithLifecycle().value,
        sending = viewModel.sending.collectAsStateWithLifecycle().value,
        streamingText = viewModel.streamingText.collectAsStateWithLifecycle().value,
        hasApiKey = viewModel.hasApiKey.collectAsStateWithLifecycle().value,
        error = viewModel.error.collectAsStateWithLifecycle().value,
        pendingImagePath = viewModel.pendingImagePath.collectAsStateWithLifecycle().value,
        currentModel = viewModel.model.collectAsStateWithLifecycle().value,
        availableModels = viewModel.availableModels.collectAsStateWithLifecycle().value,
        modelsLoading = viewModel.modelsLoading.collectAsStateWithLifecycle().value
    )
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.attachImage(it) }
    }
    // Cesta musí přežít zánik aktivity (otočení ve foťáku, úklid procesu),
    // jinak se vyfocená fotka tiše zahodila.
    var pendingCapturePath by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCapturePath?.let(::File)
        pendingCapturePath = null
        if (success && file != null && file.exists() && file.length() > 0) {
            viewModel.attachCapturedImage(file)
        } else {
            file?.delete()
        }
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) input = (input + " " + spoken).trim()
    }

    LaunchedEffect(Unit) {
        viewModel.toolEvents.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.attachError.collect {
            snackbarHostState.showSnackbar(context.getString(R.string.chat_attach_failed))
        }
    }
    var summaryHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(weeklySummaryRequested) {
        if (weeklySummaryRequested && !summaryHandled) {
            summaryHandled = true
            viewModel.sendWeeklySummary()
        }
    }
    LaunchedEffect(state.hasApiKey) {
        if (state.hasApiKey && state.availableModels.isEmpty()) viewModel.loadModels()
    }

    ChatContent(
        state = state,
        input = input,
        onInputChange = { input = it },
        snackbarHostState = snackbarHostState,
        actions = ChatActions(
            onOpenSettings = onOpenSettings,
            onSend = { text ->
                viewModel.send(text)
                input = ""
            },
            onStop = viewModel::stopGeneration,
            onRetry = viewModel::retryGeneration,
            onDismissError = viewModel::clearError,
            onRegenerate = viewModel::regenerateLastResponse,
            onClearChat = viewModel::clearChat,
            onSelectModel = viewModel::selectModel,
            onLoadModels = viewModel::loadModels,
            onPickImage = {
                pickImage.launch(
                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onTakePhoto = {
                val file = viewModel.newCaptureFile()
                pendingCapturePath = file.absolutePath
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                takePicture.launch(uri)
            },
            onDictate = {
                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(
                        android.speech.RecognizerIntent.EXTRA_LANGUAGE,
                        if (cz.rzahr.aicoach.util.Locales.isCzech()) "cs-CZ" else "en-US"
                    )
                }
                try {
                    speechLauncher.launch(intent)
                } catch (_: android.content.ActivityNotFoundException) {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.chat_voice_unavailable)) }
                }
            },
            onRemovePendingImage = viewModel::clearPendingImage,
            onCopy = { clipboard.setText(AnnotatedString(it)) }
        )
    )
}

private sealed interface ChatRow {
    val key: String

    data class Separator(val date: LocalDate) : ChatRow {
        override val key: String = "sep_$date"
    }

    data class Message(val message: ChatMessageEntity) : ChatRow {
        override val key: String = "msg_${message.id}"
    }
}

@Composable
fun ChatContent(
    state: ChatState,
    input: String,
    onInputChange: (String) -> Unit,
    actions: ChatActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val rows = remember(state.messages) {
        buildList<ChatRow> {
            var lastDate: LocalDate? = null
            state.messages.forEach { message ->
                val date = message.timestamp.toLocalDate()
                if (date != lastDate) {
                    add(ChatRow.Separator(date))
                    lastDate = date
                }
                add(ChatRow.Message(message))
            }
        }
    }
    val lastModelId = state.messages.lastOrNull()?.takeIf { it.role == ChatMessageEntity.ROLE_MODEL }?.id
    val showStreaming = !state.streamingText.isNullOrEmpty()
    val showTyping = state.sending && state.streamingText.isNullOrEmpty()
    val itemCount = rows.size + (if (showStreaming) 1 else 0) + (if (showTyping) 1 else 0)

    LaunchedEffect(rows.size, state.streamingText?.length?.div(80), showTyping) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppHeader(
                title = stringResource(R.string.tab_coach),
                actions = {
                    ModelPicker(
                        currentModel = state.currentModel,
                        models = state.availableModels,
                        loading = state.modelsLoading,
                        onSelect = actions.onSelectModel,
                        onRetry = actions.onLoadModels
                    )
                    if (state.messages.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        CircleIconButton(
                            icon = Icons.Outlined.DeleteSweep,
                            contentDescription = stringResource(R.string.chat_delete_history),
                            onClick = { confirmClear = true },
                            enabled = !state.sending
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            if (!state.hasApiKey) {
                Banner(
                    icon = Icons.Outlined.Key,
                    title = stringResource(
                        if ('/' in state.currentModel) R.string.chat_missing_key_title_or else R.string.chat_missing_key_title
                    ),
                    body = stringResource(
                        if ('/' in state.currentModel) R.string.chat_missing_key_body_or else R.string.chat_missing_key_body
                    ),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    primaryAction = stringResource(R.string.chat_open_settings) to actions.onOpenSettings
                )
            }
            state.error?.let { message ->
                Banner(
                    icon = Icons.Outlined.ErrorOutline,
                    title = message,
                    body = null,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    primaryAction = stringResource(R.string.retry) to actions.onRetry,
                    secondaryAction = stringResource(R.string.close) to actions.onDismissError
                )
            }

            if (rows.isEmpty() && !showStreaming && !showTyping) {
                EmptyChat(
                    enabled = state.hasApiKey,
                    onSuggestion = onInputChange,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is ChatRow.Separator -> DaySeparator(row.date)
                            is ChatRow.Message -> MessageItem(
                                message = row.message,
                                canRegenerate = row.message.id == lastModelId && !state.sending,
                                onCopy = actions.onCopy,
                                onRegenerate = actions.onRegenerate,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                    if (showStreaming) {
                        item(key = "streaming") {
                            CoachMessage(
                                text = state.streamingText.orEmpty(),
                                timestamp = null,
                                streaming = true
                            )
                        }
                    }
                    if (showTyping) {
                        item(key = "typing") { TypingIndicator() }
                    }
                }
            }

            state.pendingImagePath?.let { path ->
                PendingImage(path = path, onRemove = actions.onRemovePendingImage)
            }
            Composer(
                input = input,
                onInputChange = onInputChange,
                sending = state.sending,
                enabled = state.hasApiKey,
                hasPendingImage = state.pendingImagePath != null,
                actions = actions
            )
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.chat_clear_confirm_title),
            text = stringResource(R.string.chat_clear_confirm_text),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                confirmClear = false
                actions.onClearChat()
            },
            onDismiss = { confirmClear = false }
        )
    }
}

@Composable
private fun ModelPicker(
    currentModel: String,
    models: List<String>,
    loading: Boolean,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                Modifier
                    .height(44.dp)
                    .padding(start = 14.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    shortModelName(currentModel),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 132.dp)
                )
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = stringResource(R.string.chat_change_model),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            when {
                loading -> DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_models_loading)) },
                    onClick = {},
                    enabled = false
                )
                models.isEmpty() -> DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_models_failed)) },
                    onClick = onRetry
                )
                else -> models.forEach { candidate ->
                    val selected = candidate == currentModel
                    DropdownMenuItem(
                        text = {
                            Text(
                                candidate,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingIcon = {
                            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        onClick = {
                            onSelect(candidate)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/** "gemini-3.6-flash-lite" → "3.6 flash lite", "qwen/qwen3.8-27b:free" → "qwen3.8-27b". */
internal fun shortModelName(model: String): String =
    model.substringAfterLast('/')
        .substringBefore(':')
        .removePrefix("gemini-")
        .replace('-', ' ')
        .ifBlank { model }

@Composable
private fun Banner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String?,
    container: Color,
    content: Color,
    primaryAction: Pair<String, () -> Unit>,
    secondaryAction: Pair<String, () -> Unit>? = null
) {
    Surface(
        color = container,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = content)
                if (body != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(body, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.85f))
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedButton(
                        onClick = primaryAction.second,
                        border = BorderStroke(1.dp, content.copy(alpha = 0.4f))
                    ) { Text(primaryAction.first, color = content) }
                    if (secondaryAction != null) {
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = secondaryAction.second) { Text(secondaryAction.first, color = content) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChat(enabled: Boolean, onSuggestion: (String) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Eyebrow(stringResource(R.string.chat_empty_eyebrow), color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.chat_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stringArrayResource(R.array.chat_suggestions).forEach { suggestion ->
                Surface(
                    onClick = { onSuggestion(suggestion) },
                    enabled = enabled,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        suggestion,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DaySeparator(date: LocalDate) {
    val label = when (date) {
        LocalDate.now() -> stringResource(R.string.day_today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("d. M. yyyy"))
    }
    Eyebrow(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    message: ChatMessageEntity,
    canRegenerate: Boolean,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isUser = message.role == ChatMessageEntity.ROLE_USER
    Box(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { menuExpanded = true })
    ) {
        if (isUser) {
            UserMessage(message, Modifier.align(Alignment.CenterEnd))
        } else {
            CoachMessage(text = message.content, timestamp = message.timestamp, streaming = false)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_copy)) },
                onClick = {
                    menuExpanded = false
                    onCopy(message.content)
                }
            )
            if (!isUser && canRegenerate) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_regenerate)) },
                    onClick = {
                        menuExpanded = false
                        onRegenerate()
                    }
                )
            }
        }
    }
}

@Composable
private fun UserMessage(message: ChatMessageEntity, modifier: Modifier) {
    Column(modifier.widthIn(max = 320.dp), horizontalAlignment = Alignment.End) {
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp)
        ) {
            Column(Modifier.padding(6.dp)) {
                message.imagePath?.let { path ->
                    AsyncImage(
                        model = File(path),
                        contentDescription = stringResource(R.string.chat_attached_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                }
                val hideText = message.imagePath != null && message.content == PHOTO_ONLY_PLACEHOLDER
                if (!hideText) {
                    Text(
                        message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
        Text(
            message.timestamp.formatTime(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, end = 4.dp)
        )
    }
}

@Composable
private fun CoachMessage(text: String, timestamp: Long?, streaming: Boolean) {
    Column(Modifier.fillMaxWidth().padding(end = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoachMark()
            Spacer(Modifier.width(8.dp))
            Eyebrow(stringResource(R.string.tab_coach), color = MaterialTheme.colorScheme.onSurface)
            if (timestamp != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    timestamp.formatTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            MarkdownText(text, modifier = Modifier.weight(1f, fill = false), color = MaterialTheme.colorScheme.onSurface)
            if (streaming) BlinkingCaret()
        }
    }
}

@Composable
private fun CoachMark() {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimary)
        )
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
            .padding(start = 3.dp, bottom = 4.dp)
            .size(width = 8.dp, height = 16.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
    )
}

@Composable
private fun TypingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CoachMark()
        Spacer(Modifier.width(10.dp))
        repeat(3) { index ->
            val transition = rememberInfiniteTransition(label = "dot$index")
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 150)
                ),
                label = "dotAlpha$index"
            )
            Box(
                Modifier
                    .padding(end = 5.dp)
                    .size(7.dp)
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.onSurface, CircleShape)
            )
        }
    }
}

@Composable
private fun PendingImage(path: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = stringResource(R.string.chat_pending_photo),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.chat_pending_photo),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_remove_photo))
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onInputChange: (String) -> Unit,
    sending: Boolean,
    enabled: Boolean,
    hasPendingImage: Boolean,
    actions: ChatActions
) {
    var attachMenu by remember { mutableStateOf(false) }
    val canSend = enabled && !sending && (input.isNotBlank() || hasPendingImage)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
    ) {
        Row(
            Modifier.padding(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box {
                IconButton(onClick = { attachMenu = true }, enabled = enabled && !sending) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_attach_photo))
                }
                DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_take_photo)) },
                        leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                        onClick = {
                            attachMenu = false
                            actions.onTakePhoto()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_attach_photo)) },
                        leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                        onClick = {
                            attachMenu = false
                            actions.onPickImage()
                        }
                    )
                }
            }
            TextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        stringResource(R.string.chat_input_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                maxLines = 5,
                enabled = enabled && !sending,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            when {
                sending -> RoundAction(
                    icon = Icons.Filled.Stop,
                    contentDescription = stringResource(R.string.chat_stop_generation),
                    onClick = actions.onStop,
                    container = MaterialTheme.colorScheme.inverseSurface,
                    tint = MaterialTheme.colorScheme.inverseOnSurface
                )
                input.isBlank() && !hasPendingImage -> IconButton(onClick = actions.onDictate, enabled = enabled) {
                    Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.chat_dictate))
                }
                else -> RoundAction(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.chat_send),
                    onClick = { actions.onSend(input) },
                    enabled = canSend,
                    container = MaterialTheme.colorScheme.primary,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    container: Color,
    tint: Color,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) container else container.copy(alpha = 0.4f),
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

/** Text, který ViewModel uloží za zprávu jen s fotkou – v bublině se neukazuje. */
private const val PHOTO_ONLY_PLACEHOLDER = "(foto)"
