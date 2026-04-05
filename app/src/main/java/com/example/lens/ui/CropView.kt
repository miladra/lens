package com.example.lens.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
            if (cropRect != null) {
                // Draw the semi-transparent scrim over the whole area
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    size = size
                )
                
                // "Punch" a transparent hole for the selected area
                drawRect(
                    color = Color.Transparent,
                    topLeft = cropRect.topLeft,
                    size = cropRect.size,
                    blendMode = BlendMode.Clear
                )

                // Draw a white border around the selection
                drawRect(
                    color = Color.White,
                    topLeft = cropRect.topLeft,
                    size = cropRect.size,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onCancel) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    cropRect?.let { rect ->
                        val cropped = cropBitmap(bitmap, rect, containerSize)
                        onCropped(cropped)
                    }
                },
                enabled = cropRect != null && cropRect.width > 10 && cropRect.height > 10
            ) {
                Text("Crop & Translate")
            }
        }
    }
}

private fun cropBitmap(bitmap: Bitmap, rect: Rect, containerSize: Size): Bitmap {
    val bitmapWidth = bitmap.width.toFloat()
    val bitmapHeight = bitmap.height.toFloat()
    
    // ContentScale.Fit logic to find the actual image bounds on screen
    val scale = minOf(containerSize.width / bitmapWidth, containerSize.height / bitmapHeight)
    val actualWidth = bitmapWidth * scale
    val actualHeight = bitmapHeight * scale
    
    val leftOffset = (containerSize.width - actualWidth) / 2
    val topOffset = (containerSize.height - actualHeight) / 2
    
    // Map screen coordinates to bitmap coordinates
    val mappedLeft = ((rect.left - leftOffset) / scale).coerceIn(0f, bitmapWidth)
    val mappedTop = ((rect.top - topOffset) / scale).coerceIn(0f, bitmapHeight)
    val mappedRight = ((rect.right - leftOffset) / scale).coerceIn(0f, bitmapWidth)
    val mappedBottom = ((rect.bottom - topOffset) / scale).coerceIn(0f, bitmapHeight)
    
    val finalWidth = (mappedRight - mappedLeft).toInt().coerceAtLeast(1)
    val finalHeight = (mappedBottom - mappedTop).toInt().coerceAtLeast(1)
    
    // Ensure width and height don't exceed bitmap dimensions
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
