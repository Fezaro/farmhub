package com.farm_tech.farmhub.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

/** A viewer only: opening/closing an attachment never changes the conversation. */
@Composable
fun ChatImageViewer(imageUri: String, onClose: () -> Unit) {
    var scale by remember(imageUri) { mutableFloatStateOf(1f) }
    var offset by remember(imageUri) { mutableStateOf(Offset.Zero) }
    var attempt by remember(imageUri) { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            val context = LocalContext.current
            val density = androidx.compose.ui.platform.LocalDensity.current
            val width = with(density) { maxWidth.toPx() }
            val height = with(density) { maxHeight.toPx() }
            val transform = rememberTransformableState { zoom, pan, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                val next = offset + pan
                offset = if (scale == 1f) Offset.Zero else Offset(
                    next.x.coerceIn(-width * (scale - 1f) / 2f, width * (scale - 1f) / 2f),
                    next.y.coerceIn(-height * (scale - 1f) / 2f, height * (scale - 1f) / 2f)
                )
            }
            key(imageUri, attempt) {
                val painter = rememberAsyncImagePainter(ImageRequest.Builder(context)
                    .data(imageUri).size(3072).build())
                Image(
                    painter = painter, contentDescription = "Full-screen message image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(imageUri) {
                            detectTapGestures(onDoubleTap = {
                                scale = if (scale > 1f) 1f else 2.5f
                                offset = Offset.Zero
                            })
                        }
                        .transformable(transform)
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationX = offset.x; translationY = offset.y
                        }
                )
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is AsyncImagePainter.State.Error -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Couldn't load this image.", color = Color.White)
                        TextButton(onClick = { attempt++ }) { Text("Retry image", color = Color.White) }
                    }
                    else -> Unit
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.65f))) {
                Icon(Icons.Default.Close, "Close image", tint = Color.White)
            }
            Text("Pinch or double-tap to zoom", color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.65f)).padding(12.dp))
        }
    }
}
