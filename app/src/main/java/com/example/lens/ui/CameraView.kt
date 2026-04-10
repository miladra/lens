package com.example.lens.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraView(
    onImageCaptured: (Bitmap) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(cameraProviderFuture) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (exc: Exception) {
            Log.e("CameraView", "Use case binding failed", exc)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Top Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            
            IconButton(
                onClick = { /* Toggle Flash */ },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = "Flash", tint = Color.White)
            }
        }

        // Bottom Shutter
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp)
        ) {
            ShutterButton(
                onClick = {
                    imageCapture.takePicture(
                        cameraExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bitmap = image.toBitmap().rotate(image.imageInfo.rotationDegrees.toFloat())
                                onImageCaptured(bitmap)
                                image.close()
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e("CameraView", "Photo capture failed: ${exception.message}", exception)
                            }
                        }
                    )
                }
            )
        }
        
        Text(
            "Point at text to translate",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        )
    }
}

@Composable
fun ShutterButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick)
    )
}

private fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
