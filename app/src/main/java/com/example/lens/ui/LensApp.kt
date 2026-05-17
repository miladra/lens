package com.example.lens.ui

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.example.lens.audio.AudioCaptureService
import com.example.lens.data.Config
import com.example.lens.data.HistoryItem
import com.example.lens.data.TranslationProvider
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GeminiModelOption(val value: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiLensApp(viewModel: AiLensViewModel) {
    val config by viewModel.config.collectAsState()
    val translationResult by viewModel.translationResult.collectAsState()
    val explanation by viewModel.explanation.collectAsState()
    val history by viewModel.history.collectAsState()

    var showConfig by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val audioFile = remember { File(context.cacheDir, "audio_record.wav") }

    val sheetState = rememberModalBottomSheetState()

    // Animation for recording state
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val recordingAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioCaptureService.ACTION_FINISHED) {
                    viewModel.translateAudio(audioFile)
                    isRecording = false
                }
            }
        }
        val filter = IntentFilter(AudioCaptureService.ACTION_FINISHED)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream: InputStream? = context.contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val rotatedBitmap = bitmap?.let { b ->
                val rotation = getUriOrientation(context, it)
                if (rotation != 0f) {
                    val matrix = Matrix().apply { postRotate(rotation) }
                    Bitmap.createBitmap(b, 0, 0, b.width, b.height, matrix, true)
                } else b
            }
            capturedBitmap = rotatedBitmap
        }
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(context, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START
                putExtra(AudioCaptureService.EXTRA_RESULT_DATA, result.data)
                putExtra(AudioCaptureService.EXTRA_FILE_PATH, audioFile.absolutePath)
            }
            context.startForegroundService(serviceIntent)
            isRecording = true
        } else {
            Toast.makeText(context, "System audio permission denied. Recording cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else true

        if (micGranted && notificationGranted) {
            val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
        } else {
            val missing = mutableListOf<String>()
            if (!micGranted) missing.add("Microphone")
            if (!notificationGranted) missing.add("Notifications")
            Toast.makeText(context, "Missing permissions: ${missing.joinToString(", ")}", Toast.LENGTH_LONG).show()
        }
    }

    if (showCamera) {
        CameraView(
            onImageCaptured = { bitmap ->
                capturedBitmap = bitmap
                showCamera = false
            },
            onClose = { showCamera = false }
        )
        return
    }

    capturedBitmap?.let { bitmap ->
        CropView(
            bitmap = bitmap,
            onCropped = { cropped ->
                viewModel.translateImage(cropped)
                capturedBitmap = null
            },
            onCancel = { capturedBitmap = null }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.translateText(inputText) },
                icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                text = { Text("Translate") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.imePadding()
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = null
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Filled.Language, 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Auto Detect → ${config.targetLanguage}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            Spacer(modifier = Modifier.weight(1f))

                            var providerExpanded by remember { mutableStateOf(false) }
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { providerExpanded = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        config.preferredProvider.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = providerExpanded,
                                    onDismissRequest = { providerExpanded = false }
                                ) {
                                    TranslationProvider.entries.forEach { provider ->
                                        DropdownMenuItem(
                                            text = { Text(provider.name) },
                                            onClick = {
                                                viewModel.updateConfig(config.copy(preferredProvider = provider))
                                                providerExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            IconButton(onClick = { showHistory = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.History, contentDescription = "History", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { showConfig = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
                            }
                        }
                        
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Enter text to translate...", style = MaterialTheme.typography.bodyLarge) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                errorContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                errorIndicatorColor = Color.Transparent
                            ),
                            trailingIcon = {
                                Row {
                                    if (inputText.isNotEmpty()) {
                                        IconButton(onClick = {
                                            viewModel.setTranslatedText(inputText)
                                            inputText = ""
                                        }) {
                                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move to Result")
                                        }
                                        IconButton(onClick = { inputText = "" }) {
                                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                                        }
                                    }
                                }
                            },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            minLines = 4
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickAction(
                        icon = Icons.Filled.CameraAlt,
                        label = "Camera",
                        onClick = { showCamera = true }
                    )
                    QuickAction(
                        icon = Icons.Filled.PhotoLibrary,
                        label = "Gallery",
                        onClick = { imagePickerLauncher.launch("image/*") }
                    )
                    QuickAction(
                        icon = if (isRecording) Icons.Filled.StopCircle else Icons.Filled.Mic,
                        label = if (isRecording) "Stop" else "Audio",
                        iconColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = if (isRecording) Modifier.alpha(recordingAlpha) else Modifier,
                        onClick = {
                            if (isRecording) {
                                val serviceIntent = Intent(context, AudioCaptureService::class.java).apply {
                                    action = AudioCaptureService.ACTION_STOP
                                }
                                context.startService(serviceIntent)
                            } else {
                                val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                
                                val allGranted = permissions.all {
                                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                                }

                                if (allGranted) {
                                    val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                    mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                                } else {
                                    permissionLauncher.launch(permissions.toTypedArray())
                                }
                            }
                        }
                    )
                }

                if (translationResult.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .clip(CircleShape)
                    )
                }

                AnimatedVisibility(
                    visible = translationResult.error != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    translationResult.error?.let {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Error: $it",
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.retry() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = translationResult.translatedText.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 80.dp), // Spacing for FAB
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Translation",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Row {
                                    IconButton(
                                        onClick = { viewModel.retry() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            contentDescription = "Retry",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { 
                                            clipboardManager.setText(AnnotatedString(translationResult.translatedText))
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.ContentCopy,
                                            contentDescription = "Copy",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            SelectionContainer {
                                val primaryColor = MaterialTheme.colorScheme.primary
                                val annotatedText = buildAnnotatedString {
                                    val words = translationResult.translatedText.split(Regex("\\s+"))
                                    words.forEachIndexed { index, word ->
                                        val cleanWord = word.filter { it.isLetterOrDigit() }
                                        
                                        val link = LinkAnnotation.Clickable(
                                            tag = "WORD_EXPLAIN",
                                            styles = TextLinkStyles(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)),
                                            linkInteractionListener = { 
                                                viewModel.explainWord(cleanWord)
                                            }
                                        )
                                        
                                        withLink(link) {
                                            append(word)
                                        }

                                        if (index < words.size - 1) append(" ")
                                    }
                                }

                                Text(
                                    text = annotatedText,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        lineHeight = 28.sp,
                                        fontSize = 18.sp
                                    )
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tap a word for explanation",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }

        if (showConfig) {
            ModalBottomSheet(
                onDismissRequest = { showConfig = false },
                sheetState = sheetState
            ) {
                ConfigSheetContent(
                    config = config,
                    onSave = {
                        viewModel.updateConfig(it)
                        showConfig = false
                    }
                )
            }
        }

        if (showHistory) {
            ModalBottomSheet(
                onDismissRequest = { showHistory = false },
                sheetState = sheetState
            ) {
                HistorySheetContent(
                    history = history,
                    onSelectItem = {
                        viewModel.selectHistoryItem(it)
                        if (it.originalText != "[Image]" && it.originalText != "[Audio]") {
                            inputText = it.originalText
                        }
                        showHistory = false
                    },
                    onDeleteItem = { viewModel.deleteHistoryItem(it.id) },
                    onClearHistory = { viewModel.clearHistory() }
                )
            }
        }

        explanation?.let {
            AlertDialog(
                onDismissRequest = { viewModel.clearExplanation() },
                text = { Text(it) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearExplanation() }) {
                        Text("Got it")
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}

@Composable
fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconColor: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = iconColor.copy(alpha = 0.1f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = iconColor)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun HistorySheetContent(
    history: List<HistoryItem>,
    onSelectItem: (HistoryItem) -> Unit,
    onDeleteItem: (HistoryItem) -> Unit,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recent Translations",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearHistory) {
                    Text("Clear all", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "No history yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn {
                items(history, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelectItem(item) },
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.originalText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.translatedText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val date = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                            IconButton(onClick = { onDeleteItem(item) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigSheetContent(
    config: Config,
    onSave: (Config) -> Unit
) {
    var geminiKey by remember { mutableStateOf(config.geminiApiKey) }
    var groqKey by remember { mutableStateOf(config.groqApiKey) }
    var openRouterKey by remember { mutableStateOf(config.openRouterApiKey) }
    
    var geminiModel by remember { mutableStateOf(config.geminiModel) }
    var groqModel by remember { mutableStateOf(config.groqModel) }
    var openRouterModel by remember { mutableStateOf(config.openRouterModel) }
    
    var targetLang by remember { mutableStateOf(config.targetLanguage) }
    var explainLang by remember { mutableStateOf(config.explanationLanguage) }

    var geminiKeyVisible by remember { mutableStateOf(false) }
    var groqKeyVisible by remember { mutableStateOf(false) }
    var openRouterKeyVisible by remember { mutableStateOf(false) }

    val geminiModels = listOf(
        GeminiModelOption("gemma-4-31b-it", "Gemma 4 31B"),
        GeminiModelOption("gemma-4-26b-a4b-it", "Gemma 4 26B"),

        GeminiModelOption("gemini-3.1-pro-preview", "Gemini 3 Pro"),
        GeminiModelOption("gemini-3-flash-preview", "Gemini 3.1 Flash"),
        GeminiModelOption("gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash-Lite"),

        GeminiModelOption("gemini-2.5-pro", "Gemini 2.5 Pro"),
        GeminiModelOption("gemini-2.5-flash", "Gemini 2.5 Flash"),
        GeminiModelOption("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite"),

        GeminiModelOption("gemini-2.0-flash", "Gemini 2.0 Flash"),
        GeminiModelOption("gemini-2.0-flash-lite-preview-02-05", "Gemini 2.0 Flash-Lite"),
        GeminiModelOption("gemini-2.0-pro-exp-02-05", "Gemini 2.0 Pro (Exp)"),

        GeminiModelOption("gemini-1.5-pro", "Gemini 1.5 Pro"),
        GeminiModelOption("gemini-1.5-flash", "Gemini 1.5 Flash")
    )
    val groqModels = listOf(
        "llama-3.3-70b-versatile", 
        "llama-3.2-11b-vision-preview",
        "llama-3.2-3b-preview",
        "llama-3.2-1b-preview",
        "llama-3.1-8b-instant",
        "llama3-70b-8192",
        "llama3-8b-8192",
        "mixtral-8x7b-32768",
        "gemma2-9b-it",
        "deepseek-r1-distill-llama-70b",
        "deepseek-r1-distill-qwen-32b"
    )
    val openRouterModels = listOf(
        "openrouter/auto",
        "openrouter/free",
        "google/gemma-4-31b-it:free",
        "google/gemma-4-26b-a4b-it:free",
        "openai/gpt-oss-20b:free",
        "google/gemini-2.0-pro-exp-02-05:free",
        "google/gemma-3-27b-it:free",
        "google/gemma-7b-it:free",
        "google/gemma-2-9b-it:free",
        "meta-llama/llama-3.3-70b-instruct",
        "meta-llama/llama-3.1-8b-instruct:free",
        "meta-llama/llama-3.2-1b-instruct:free",
        "meta-llama/llama-3.2-3b-instruct:free",
        "mistralai/mistral-7b-instruct:free",
        "mistralai/pixtral-12b:free",
        "qwen/qwen-2-7b-instruct:free",
        "qwen/qwen-2.5-72b-instruct:free",
        "qwen/qwen-2-vl-7b-instruct:free",
        "gryphe/mythomist-7b:free"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        Text("General", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = targetLang, 
            onValueChange = { targetLang = it }, 
            label = { Text("Target Language") }, 
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = explainLang, 
            onValueChange = { explainLang = it }, 
            label = { Text("Explanation Language") }, 
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text("API Credentials", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ApiKeyField(
            label = "Gemini API Key",
            value = geminiKey,
            onValueChange = { geminiKey = it },
            visible = geminiKeyVisible,
            onToggleVisibility = { geminiKeyVisible = !geminiKeyVisible }
        )
        ModelOptionDropdown(label = "Gemini Model", selectedModelValue = geminiModel, options = geminiModels) { geminiModel = it }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ApiKeyField(
            label = "Groq API Key",
            value = groqKey,
            onValueChange = { groqKey = it },
            visible = groqKeyVisible,
            onToggleVisibility = { groqKeyVisible = !groqKeyVisible }
        )
        ModelDropdown(label = "Groq Model", selectedModel = groqModel, models = groqModels) { groqModel = it }

        Spacer(modifier = Modifier.height(16.dp))

        ApiKeyField(
            label = "OpenRouter API Key",
            value = openRouterKey,
            onValueChange = { openRouterKey = it },
            visible = openRouterKeyVisible,
            onToggleVisibility = { openRouterKeyVisible = !openRouterKeyVisible }
        )
        ModelDropdown(label = "OpenRouter Model", selectedModel = openRouterModel, models = openRouterModels) { openRouterModel = it }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                onSave(Config(
                    geminiApiKey = geminiKey,
                    groqApiKey = groqKey,
                    openRouterApiKey = openRouterKey,
                    geminiModel = geminiModel,
                    groqModel = groqModel,
                    openRouterModel = openRouterModel,
                    targetLanguage = targetLang,
                    explanationLanguage = explainLang,
                    preferredProvider = config.preferredProvider
                ))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Text("Save Settings")
        }
    }
}

@Composable
fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisibility: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            val icon = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
            IconButton(onClick = onToggleVisibility) {
                Icon(icon, contentDescription = "Toggle visibility")
            }
        }
    )
}

fun getUriOrientation(context: Context, uri: Uri): Float {
    var inputStream: InputStream? = null
    try {
        inputStream = context.contentResolver.openInputStream(uri)
        val exifInterface = inputStream?.let { ExifInterface(it) }
        val orientation = exifInterface?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        ) ?: ExifInterface.ORIENTATION_NORMAL
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (_: Exception) {
        return 0f
    } finally {
        try { inputStream?.close() } catch (_: Exception) {}
    }
}

@Composable
fun ModelOptionDropdown(label: String, selectedModelValue: String, options: List<GeminiModelOption>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.value == selectedModelValue }?.label ?: selectedModelValue

    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Dropdown")
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ModelDropdown(label: String, selectedModel: String, models: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        OutlinedTextField(
            value = selectedModel,
            onValueChange = {},
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Dropdown")
                }
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onSelect(model)
                        expanded = false
                    }
                )
            }
        }
    }
}
