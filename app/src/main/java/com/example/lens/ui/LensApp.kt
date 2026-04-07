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
import android.media.ExifInterface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.lens.audio.AudioCaptureService
import com.example.lens.data.Config
import com.example.lens.data.TranslationProvider
import java.io.File
import java.io.InputStream

data class GeminiModelOption(val value: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LensApp(viewModel: LensViewModel) {
    val config by viewModel.config.collectAsState()
    val translationResult by viewModel.translationResult.collectAsState()
    val explanation by viewModel.explanation.collectAsState()

    var showConfig by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val audioFile = remember { File(context.cacheDir, "audio_record.wav") }

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
            context.unregisterReceiver(receiver)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Lens", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = { showConfig = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Enter text to translate (${config.preferredProvider.name})") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.translateText(inputText) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("Translate", maxLines = 1)
                }
                
                Button(
                    onClick = { showCamera = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("Camera", maxLines = 1)
                }
                
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("Gallery", maxLines = 1)
                }
                
                Button(
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
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (isRecording) ButtonDefaults.buttonColors(containerColor = Color.Red) else ButtonDefaults.buttonColors(),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(if (isRecording) "Stop" else "Audio", maxLines = 1)
                }
            }

            if (translationResult.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }

            translationResult.error?.let {
                Text("Error: $it", color = MaterialTheme.colorScheme.error)
            }

            if (translationResult.translatedText.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Translation:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val annotatedText = buildAnnotatedString {
                            val words = translationResult.translatedText.split(Regex("\\s+"))
                            words.forEachIndexed { index, word ->
                                val cleanWord = word.filter { it.isLetterOrDigit() }
                                pushStringAnnotation(tag = "WORD", annotation = cleanWord)
                                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)) {
                                    append(word)
                                }
                                pop()
                                if (index < words.size - 1) append(" ")
                            }
                        }

                        ClickableText(
                            text = annotatedText,
                            onClick = { offset ->
                                annotatedText.getStringAnnotations(tag = "WORD", start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        viewModel.explainWord(annotation.item)
                                    }
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                        )
                    }
                }
            }
        }

        if (showConfig) {
            ConfigDialog(
                config = config,
                onDismiss = { showConfig = false },
                onSave = {
                    viewModel.updateConfig(it)
                    showConfig = false
                }
            )
        }

        explanation?.let {
            AlertDialog(
                onDismissRequest = { viewModel.clearExplanation() },
                title = { Text("Word Explanation") },
                text = { Text(it) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearExplanation() }) {
                        Text("Close")
                    }
                }
            )
        }
    }
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
    } catch (e: Exception) {
        return 0f
    } finally {
        inputStream?.close()
    }
}

@Composable
fun ConfigDialog(
    config: Config,
    onDismiss: () -> Unit,
    onSave: (Config) -> Unit
) {
    var geminiKey by remember { mutableStateOf(config.geminiApiKey) }
    var groqKey by remember { mutableStateOf(config.groqApiKey) }
    var geminiModel by remember { mutableStateOf(config.geminiModel) }
    var groqModel by remember { mutableStateOf(config.groqModel) }
    var targetLang by remember { mutableStateOf(config.targetLanguage) }
    var explainLang by remember { mutableStateOf(config.explanationLanguage) }
    var preferredProvider by remember { mutableStateOf(config.preferredProvider) }

    var geminiKeyVisible by remember { mutableStateOf(false) }
    var groqKeyVisible by remember { mutableStateOf(false) }

    val geminiModels = listOf(
        GeminiModelOption("gemini-2.0-flash", "Gemini 2.0 Flash"),
        GeminiModelOption("gemini-2.0-flash-lite", "Gemini 2.0 Flash Lite"),
        GeminiModelOption("gemini-1.5-pro", "Gemini 1.5 Pro"),
        GeminiModelOption("gemini-1.5-flash", "Gemini 1.5 Flash")
    )
    val groqModels = listOf("llama-3.3-70b-versatile", "llama3-8b-8192", "mixtral-8x7b-32768")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ModelDropdown(
                    label = "Preferred Provider",
                    selectedModel = preferredProvider.name,
                    models = TranslationProvider.values().map { it.name }
                ) { preferredProvider = TranslationProvider.valueOf(it) }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    label = { Text("Gemini API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (geminiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val icon = if (geminiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { geminiKeyVisible = !geminiKeyVisible }) {
                            Icon(icon, contentDescription = "Toggle visibility")
                        }
                    }
                )
                ModelOptionDropdown(label = "Gemini Model", selectedModelValue = geminiModel, options = geminiModels) { geminiModel = it }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = groqKey,
                    onValueChange = { groqKey = it },
                    label = { Text("Groq API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (groqKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val icon = if (groqKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { groqKeyVisible = !groqKeyVisible }) {
                            Icon(icon, contentDescription = "Toggle visibility")
                        }
                    }
                )
                ModelDropdown(label = "Groq Model", selectedModel = groqModel, models = groqModels) { groqModel = it }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = targetLang, onValueChange = { targetLang = it }, label = { Text("Target Language") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = explainLang, onValueChange = { explainLang = it }, label = { Text("Explanation Language (3rd)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(Config(geminiKey, groqKey, geminiModel, groqModel, targetLang, explainLang, preferredProvider))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", modifier = Modifier.clickable { expanded = true })
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
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", modifier = Modifier.clickable { expanded = true })
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
