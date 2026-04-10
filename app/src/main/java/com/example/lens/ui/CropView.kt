package com.example.lens.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

@Composable
fun CropView(
    bitmap: Bitmap,
    onCropped: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var startOffset by remember { mutableStateOf(Offset.Zero) }
    var currentOffset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(Size.Zero) }

    val cropRect = remember(startOffset, currentOffset) {
        if (startOffset == Offset.Zero && currentOffset == Offset.Zero) {
            null
        } else {
            Rect(
                left = minOf(startOffset.x, currentOffset.x),
                top = minOf(startOffset.y, currentOffset.y),
                right = maxOf(startOffset.x, currentOffset.x),
                bottom = maxOf(startOffset.y, currentOffset.y)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { containerSize = it.size.toSize() },
            contentScale = ContentScale.Fit
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            startOffset = offset
                            currentOffset = offset
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            currentOffset += dragAmount
                        }
                    )
                }
        ) {
            // Draw the semi-transparent scrim over the whole area
            drawRect(
                color = Color.Black.copy(alpha = 0.7f),
                size = size
            )

            if (cropRect != null) {
                // "Punch" a transparent hole for the selected area
                drawRect(
                    color = Color.Transparent,
                    topLeft = cropRect.topLeft,
                    size = cropRect.size,
                    blendMode = BlendMode.Clear
                )

                // Draw a primary color border around the selection with a slight glow or thickness
                drawRect(
                    color = Color.White,
                    topLeft = cropRect.topLeft,
                    size = cropRect.size,
                    style = Stroke(width = 2.dp.toPx())
                )
                
                // Corner marks for better UI feel
                val cornerSize = 20.dp.toPx()
                val cornerStroke = 4.dp.toPx()
                
                // Top-left
                drawPath(
                    Path().apply {
                        moveTo(cropRect.left, cropRect.top + cornerSize)
                        lineTo(cropRect.left, cropRect.top)
                        lineTo(cropRect.left + cornerSize, cropRect.top)
                    },
                    color = Color.White,
                    style = Stroke(width = cornerStroke)
                )
                // Top-right
                drawPath(
                    Path().apply {
                        moveTo(cropRect.right - cornerSize, cropRect.top)
                        lineTo(cropRect.right, cropRect.top)
                        lineTo(cropRect.right, cropRect.top + cornerSize)
                    },
                    color = Color.White,
                    style = Stroke(width = cornerStroke)
                )
                // Bottom-left
                drawPath(
                    Path().apply {
                        moveTo(cropRect.left, cropRect.bottom - cornerSize)
                        lineTo(cropRect.left, cropRect.bottom)
                        lineTo(cropRect.left + cornerSize, cropRect.bottom)
                    },
                    color = Color.White,
                    style = Stroke(width = cornerStroke)
                )
                // Bottom-right
                drawPath(
                    Path().apply {
                        moveTo(cropRect.right - cornerSize, cropRect.bottom)
                        lineTo(cropRect.right, cropRect.bottom)
                        lineTo(cropRect.right, cropRect.bottom - cornerSize)
                    },
                    color = Color.White,
                    style = Stroke(width = cornerStroke)
                )
            }
        }

        // Top Header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCancel,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "Crop Text",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom Action
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(32.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    cropRect?.let { rect ->
                        val cropped = cropBitmap(bitmap, rect, containerSize)
                        onCropped(cropped)
                    }
                },
                expanded = cropRect != null && cropRect.width > 20 && cropRect.height > 20,
                icon = { Icon(Icons.Default.Check, contentDescription = null) },
                text = { Text("Translate Selection") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
        
        if (cropRect == null) {
            Text(
                "Drag to select text",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 100.dp)
            )
        }
    }
}

private fun cropBitmap(bitmap: Bitmap, rect: Rect, containerSize: Size): Bitmap {
    val bitmapWidth = bitmap.width.toFloat()
    val bitmapHeight = bitmap.height.toFloat()
    
    val scale = minOf(containerSize.width / bitmapWidth, containerSize.height / bitmapHeight)
    val actualWidth = bitmapWidth * scale
    val actualHeight = bitmapHeight * scale
    
    val leftOffset = (containerSize.width - actualWidth) / 2
    val topOffset = (containerSize.height - actualHeight) / 2
    
    val mappedLeft = ((rect.left - leftOffset) / scale).coerceIn(0f, bitmapWidth)
    val mappedTop = ((rect.top - topOffset) / scale).coerceIn(0f, bitmapHeight)
    val mappedRight = ((rect.right - leftOffset) / scale).coerceIn(0f, bitmapWidth)
    val mappedBottom = ((rect.bottom - topOffset) / scale).coerceIn(0f, bitmapHeight)
    
    val finalWidth = (mappedRight - mappedLeft).toInt().coerceAtLeast(1)
    val finalHeight = (mappedBottom - mappedTop).toInt().coerceAtLeast(1)
    
    val safeWidth = if (mappedLeft.toInt() + finalWidth > bitmap.width) bitmap.width - mappedLeft.toInt() else finalWidth
    val safeHeight = if (mappedTop.toInt() + finalHeight > bitmap.height) bitmap.height - mappedTop.toInt() else finalHeight

    return Bitmap.createBitmap(
        bitmap,
        mappedLeft.toInt(),
        mappedTop.toInt(),
        safeWidth.coerceAtLeast(1),
        safeHeight.coerceAtLeast(1)
    )
}
