package ro.atlasistoric.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import kotlin.math.roundToInt

/** Visible photo zoom is INDEPENDENT from chronology and historical map layers.
 * Original file pixels are never modified by dragging or zooming.
 */
@Composable
fun ZoomableDocumentImage(
    bitmap: ImageBitmap,
    modifier: Modifier = Modifier
) {
    var factor by remember(bitmap) { mutableFloatStateOf(1f) }
    var shiftX by remember(bitmap) { mutableFloatStateOf(0f) }
    var shiftY by remember(bitmap) { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    fun scaleTo(next: Float) {
        factor = next.coerceIn(1f, 5f)
        if (factor <= 1.001f) {
            shiftX = 0f
            shiftY = 0f
        } else {
            val maxX = viewport.width * (factor - 1f) / 2f
            val maxY = viewport.height * (factor - 1f) / 2f
            shiftX = shiftX.coerceIn(-maxX, maxX)
            shiftY = shiftY.coerceIn(-maxY, maxY)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ZOOM FOTOGRAFIE",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = { scaleTo(factor / 1.4f) }, enabled = factor > 1.01f) {
                Icon(Icons.Default.ZoomOut, "Micșorează fotografia")
            }
            Text(
                "${(factor * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = { scaleTo(factor * 1.4f) }, enabled = factor < 4.99f) {
                Icon(Icons.Default.ZoomIn, "Mărește fotografia")
            }
            TextButton(onClick = { scaleTo(1f) }) { Text("100%") }
        }
        Box(
            Modifier.fillMaxWidth().height(310.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .onSizeChanged { viewport = it }
                .pointerInput(bitmap) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (factor * zoom).coerceIn(1f, 5f)
                        val xBound = size.width * (next - 1f) / 2f
                        val yBound = size.height * (next - 1f) / 2f
                        factor = next
                        shiftX = if (next <= 1f) 0f
                            else (shiftX + pan.x).coerceIn(-xBound, xBound)
                        shiftY = if (next <= 1f) 0f
                            else (shiftY + pan.y).coerceIn(-yBound, yBound)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = "Fotografia originală, cu zoom până la 500%",
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = factor, scaleY = factor,
                    translationX = shiftX, translationY = shiftY
                ),
                contentScale = ContentScale.Fit
            )
        }
        Text(
            "Mărește cu două degete sau folosește + / −. Trage imaginea mărită pentru detalii.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
