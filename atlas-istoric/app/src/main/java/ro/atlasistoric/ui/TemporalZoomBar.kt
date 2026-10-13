package ro.atlasistoric.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ro.atlasistoric.util.TemporalNavigation
import kotlin.math.roundToInt

private val chronologicalZoomLevels = listOf("EPOCA", "SECOL", "AN", "LUNA", "ZI")
private val chronologicalZoomLabels = listOf("Epocă", "Secol", "An", "Lună", "Zi")

/**
 * Always visible above the content on Atlas, overlaps and category bands.
 * This is TEMPORAL zoom, not a geographic map scale.
 */
@Composable
fun TemporalZoomBar(
    level: String,
    selectedTimeKey: String?,
    anchorYear: Int,
    onLevel: (String, String?) -> Unit,
    onAnchorYear: (Int) -> Unit
) {
    val idx = chronologicalZoomLevels.indexOf(level).coerceAtLeast(0)
    val chosen = chronologicalZoomLabels[idx]
    Surface(
        shape = RoundedCornerShape(0.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ZOOM TEMPORAL",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Nivel: $chosen",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (idx > 0) {
                            TemporalNavigation.fromKey(selectedTimeKey)?.from?.let(onAnchorYear)
                            onLevel(chronologicalZoomLevels[idx - 1], null)
                        }
                    },
                    enabled = idx > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, "Micșorează zoomul temporal")
                }
                Slider(
                    value = idx.toFloat(),
                    onValueChange = { value ->
                        val next = value.roundToInt().coerceIn(0, chronologicalZoomLevels.lastIndex)
                        if (next != idx) {
                            TemporalNavigation.fromKey(selectedTimeKey)?.from?.let(onAnchorYear)
                            onLevel(chronologicalZoomLevels[next], null)
                        }
                    },
                    valueRange = 0f..4f,
                    steps = 3,
                    modifier = Modifier.weight(1f).height(39.dp)
                )
                IconButton(
                    onClick = {
                        if (idx < chronologicalZoomLevels.lastIndex) {
                            TemporalNavigation.fromKey(selectedTimeKey)?.from?.let(onAnchorYear)
                            onLevel(chronologicalZoomLevels[idx + 1], null)
                        }
                    },
                    enabled = idx < chronologicalZoomLevels.lastIndex,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, "Mărește zoomul temporal")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                chronologicalZoomLabels.forEachIndexed { position, label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (idx == position) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (idx == position) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
