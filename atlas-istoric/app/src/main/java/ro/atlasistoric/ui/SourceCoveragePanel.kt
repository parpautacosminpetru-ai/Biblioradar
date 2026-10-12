package ro.atlasistoric.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.atlasistoric.data.DocumentPage
import ro.atlasistoric.data.PageAnnotation
import ro.atlasistoric.util.*

/**
 * A document-process audit, NOT a guarantee of complete semantic extraction.
 * Whole verified text is partitioned without loss. Each segment requires manual
 * acknowledgement before the visible "reviewed" counter increases.
 */
@Composable
fun SourceCoveragePanel(
    page: DocumentPage,
    annotations: List<PageAnnotation>,
    busy: Boolean,
    onSelectText: (SourceTextWindow) -> Unit,
    onVerifySegment: (SourceTextWindow) -> Unit
) {
    data class Model(val segments: List<SourceTextWindow>, val audit: SourceCoverage)
    val model by produceState<Model?>(
        initialValue = null, page.id, page.verifiedText, annotations
    ) {
        value = withContext(Dispatchers.Default) {
            val windows = segmentEntireTranscription(page.verifiedText)
            Model(windows, documentCoverage(page.verifiedText, windows, annotations))
        }
    }
    var shown by remember(page.id) { mutableIntStateOf(10) }
    val m = model
    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        Text("REGISTRU DE ACOPERIRE DOCUMENTARĂ",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text("Toate caracterele transcrierii sunt repartizate în segmente consecutive. " +
            "Bifezi manual ce ai confruntat cu originalul; cele neparcurse nu dispar.",
            style = MaterialTheme.typography.bodySmall)
        if (m == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            val audit = m.audit
            Text("${audit.reviewedSegments} din ${audit.segmentCount} segmente verificate · " +
                "${audit.pendingCharacters} caractere nealbe în segmente neconfirmate",
                style = MaterialTheme.typography.labelMedium)
            if (audit.segmentCount > 0) {
                LinearProgressIndicator(
                    progress = { audit.reviewedSegments.toFloat() / audit.segmentCount },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${audit.annotatedSegments} segmente au cel puțin o adnotare. " +
                    "Verificarea textului NU dovedește extragerea exhaustivă a sensului.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Transcrierea este încă goală.")
            }
            m.segments.take(shown).forEachIndexed { i, w ->
                val reviewed = annotations.any { a ->
                    if (a.category != "SEGMENT_VERIFICAT") false
                    else {
                        val anchor = resolveTextAnchor(page.verifiedText, a)
                        anchor.resolved && anchor.start <= w.start && anchor.end >= w.end
                    }
                }
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Segment ${i+1} · pozițiile ${w.start}–${w.end} · " +
                            if (reviewed) "verificat" else "neparcurs",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (reviewed) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(w.text, maxLines = 4, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedButton(onClick = { onSelectText(w) },
                                modifier = Modifier.weight(1f)) {
                                Text("Selectează")
                            }
                            Button(onClick = { onVerifySegment(w) },
                                modifier = Modifier.weight(1f),
                                enabled = page.status == "VERIFIED" &&
                                    !reviewed && !busy) {
                                Text(if (reviewed) "Verificat" else "Confirmă")
                            }
                        }
                    }
                }
            }
            if (shown < m.segments.size) {
                OutlinedButton(onClick = { shown += 20 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Arată alte segmente (${m.segments.size-shown} rămase)")
                }
            }
            if (page.status != "VERIFIED") {
                Text("Pentru confirmarea segmentelor, corectează textul și apasă mai întâi «Verificată».",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
