package ro.atlasistoric.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.atlasistoric.data.*
import ro.atlasistoric.util.AnchorStatus
import ro.atlasistoric.util.resolveTextAnchor

/**
 * The original image is always above the OCR; selecting a text range creates
 * a scholarly annotation anchored by quote and context, not by a guessed "fact".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentLabScreen(
    repository: DocumentRepository,
    sources: List<HistoricalSource>,
    pages: List<DocumentPage>,
    annotations: List<PageAnnotation>,
    links: List<AnnotationClaimLink>,
    initialPageId: String?,
    onInitialPageConsumed: () -> Unit,
    onNewSource: () -> Unit,
    onExtract: (DocumentPage, PageAnnotation) -> Unit,
    onNotice: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSourceId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sources) {
        if (selectedSourceId !in sources.map { it.id }) selectedSourceId = sources.firstOrNull()?.id
    }
    var pageLabel by remember { mutableStateOf("") }
    var selectedPageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initialPageId, pages) {
        if (initialPageId != null && pages.any { it.id == initialPageId }) {
            selectedPageId = initialPageId
            selectedSourceId = pages.first { it.id == initialPageId }.sourceId
            onInitialPageConsumed()
        }
    }
    val selected = pages.firstOrNull { it.id == selectedPageId }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var cameraOpen by remember { mutableStateOf(false) }
    var processingStage by remember { mutableStateOf<String?>(null) }

    // The captured original is stored BEFORE OCR begins; a failed OCR cannot lose the photo.
    suspend fun importAndRead(uri: Uri) {
        try {
            err = null
            processingStage = "Se arhivează fotografia originală..."
            val sourceId = selectedSourceId ?: error("Alege o sursă bibliografică")
            val label = pageLabel.trim()
            val outcome = ro.atlasistoric.util.storeThenRecognize(
                saveOriginal = { repository.importImage(sourceId, label, uri) },
                onOriginalSaved = { page ->
                    selectedPageId = page.id
                    processingStage = "Fotografia a fost păstrată. Se extrage textul OCR..."
                },
                readText = { page ->
                    if (page.originalOcrText.isNotBlank()) page.originalOcrText
                    else repository.recognizePage(page.id).originalOcrText
                }
            )
            when {
                outcome.ocrProblem != null -> {
                    err = "Fotografia este salvată. OCR a eșuat: " +
                        (outcome.ocrProblem.localizedMessage ?: "eroare") +
                        ". Îl poți relua fără să refaci fotografia."
                    onNotice("Fotografia este salvată; OCR poate fi reluat")
                }
                !outcome.hasRecognizedText -> {
                    err = "Fotografia este salvată, dar OCR nu a identificat text. " +
                        "Poți relua recunoașterea sau transcrie manual."
                    onNotice("Fotografia este păstrată; OCR nu a găsit text lizibil")
                }
                else -> onNotice("Fotografie salvată și OCR extras. Verifică transcrierea.")
            }
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            err = e.localizedMessage ?: "Fotografia nu a putut fi salvată"
        } finally {
            processingStage = null
            busy = false
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch { importAndRead(uri) }
        }
    }
    val sourceById = remember(sources) { sources.associateBy { it.id } }
    val available = pages.filter { it.sourceId == selectedSourceId }

    if (cameraOpen && selected == null) {
        DocumentCameraCapture(
            onCaptured = { photo ->
                cameraOpen = false
                busy = true
                scope.launch {
                    try {
                        importAndRead(Uri.fromFile(photo))
                    } finally {
                        photo.delete()
                    }
                }
            },
            onCancel = { cameraOpen = false }
        )
        return
    }

    if (selected == null) {
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Laboratorul Sursei", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Original păstrat · OCR brut · transcriere corectată · adnotări · afirmații documentate",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sources.isEmpty()) {
                Text("Adaugă mai întâi o sursă bibliografică.")
                Button(onClick = onNewSource) { Text("+ Sursă nouă") }
            } else {
                Text("1. Sursa", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sources, key = { it.id }) { source ->
                        FilterChip(selected = selectedSourceId == source.id,
                            onClick = { selectedSourceId = source.id },
                            label = { Text(source.title, maxLines = 1) })
                    }
                }
                OutlinedButton(onClick = onNewSource) { Text("+ Adaugă sursă") }
                OutlinedTextField(value = pageLabel, onValueChange = { pageLabel = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("2. Pagină / filă exactă *") })
                Button(
                    onClick = { err = null; cameraOpen = true },
                    enabled = !busy && selectedSourceId != null && pageLabel.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(9.dp))
                    Text("Deschide camera → fotografie → OCR automat")
                }
                OutlinedButton(
                    onClick = { pickPhoto.launch(arrayOf("image/*")) },
                    enabled = !busy && pageLabel.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null,
                        modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Sau importă o fotografie existentă")
                }
                Text("Imaginea originală se arhivează înaintea recunoașterii " +
                    "textului. OCR funcționează pe telefon, fără internet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                processingStage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            if (err != null) Text(err.orEmpty(), color = MaterialTheme.colorScheme.error)
            HorizontalDivider()
            Text("Pagini arhivate din sursa aleasă (${available.size})",
                style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 80.dp)) {
                items(available, key = { it.id }) { page ->
                    ElevatedCard(onClick = { selectedPageId = page.id }) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("Pagina ${page.pageLabel}", fontWeight = FontWeight.SemiBold)
                            Text("${page.status} · ${annotations.count { it.pageId == page.id }} adnotări",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(page.verifiedText.ifBlank { "OCR în așteptare" }.take(145),
                                style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        }
                    }
                }
            }
        }
        return
    }

    val ownAnnotations = annotations.filter { it.pageId == selected.id }
    var editor by remember(selected.id, selected.updatedAt) {
        mutableStateOf(TextFieldValue(selected.verifiedText))
    }
    var annotationType by remember { mutableStateOf("AFIRMATIE") }
    var annotationNote by remember { mutableStateOf("") }
    var showRaw by remember { mutableStateOf(false) }

    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null, selected.storagePath
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = repository.fileFor(selected)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, options)
                val maxSide = maxOf(options.outHeight, options.outWidth).coerceAtLeast(1)
                val sample = (maxSide / 1800).coerceAtLeast(1)
                val bmp = BitmapFactory.decodeFile(f.path,
                    BitmapFactory.Options().apply { inSampleSize = sample })
                bmp?.asImageBitmap()
            }.getOrNull()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { selectedPageId = null }) {
                Icon(Icons.Default.ArrowBack, "Înapoi la pagini")
            }
            Column {
                Text("Pagina ${selected.pageLabel}", style = MaterialTheme.typography.titleLarge)
                Text(sourceById[selected.sourceId]?.title.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("ORIGINAL · conservat pe dispozitiv",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
        if (image != null) ZoomableDocumentImage(
            bitmap = image!!,
            modifier = Modifier.fillMaxWidth()
        )
        else Text("Imagine indisponibilă. Verifică arhiva ZIP înainte de continuare.",
            color = MaterialTheme.colorScheme.error)

        if (busy && processingStage != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(processingStage.orEmpty(), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall)
        }
        if (selected.originalOcrText.isBlank() && !busy) {
            Text("Fotografia este păstrată. Nu există încă un text OCR recunoscut. " +
                "Poți relua recunoașterea sau introduce manual transcrierea.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TRANSCRIERE OCR", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { showRaw = !showRaw }) {
                Text(if (showRaw) "Ascunde OCR brut" else "Vezi OCR brut")
            }
        }
        if (showRaw) SelectionContainer {
            Text(selected.originalOcrText.ifBlank { "Textul OCR nu a fost încă recunoscut." },
                style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = editor, onValueChange = { editor = it },
            modifier = Modifier.fillMaxWidth(), minLines = 6, maxLines = 22,
            label = { Text("Text corectabil: selectează pasajul pentru adnotare") }
        )
        if (editor.text != selected.verifiedText) {
            Text("Corecturile nu sunt încă salvate; adnotările existente vor fi reverificate.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                busy = true
                scope.launch {
                    try {
                        repository.saveVerifiedText(selected.id, editor.text, confirmed = false)
                        onNotice("Transcriere salvată; OCR brut conservat")
                    } catch (e: Exception) { err = e.localizedMessage } finally { busy = false }
                }
            }, enabled = !busy && editor.text != selected.verifiedText, modifier = Modifier.weight(1f)) {
                Text("Salvează textul")
            }
            Button(onClick = {
                busy = true
                scope.launch {
                    try {
                        repository.saveVerifiedText(selected.id, editor.text, confirmed = true)
                        onNotice("Transcriere verificată manual")
                    } catch (e: Exception) { err = e.localizedMessage } finally { busy = false }
                }
            }, enabled = !busy && editor.text.isNotBlank(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(17.dp))
                Spacer(Modifier.width(3.dp))
                Text("Verificată")
            }
        }
        TextButton(onClick = {
            busy = true
            scope.launch {
                try {
                    repository.recognizePage(selected.id)
                    onNotice("OCR reexecutat; originalul și corecturile au rămas păstrate")
                } catch (e: Exception) { err = e.localizedMessage } finally { busy = false }
            }
        }, enabled = !busy) {
            Icon(Icons.Default.FindInPage, null, Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text(if (selected.originalOcrText.isBlank()) "Extrage textul prin OCR"
                 else "Reexecută OCR fără a șterge corecturile")
        }

        HorizontalDivider()
        Text("ADNOTARE ANCORATĂ", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary)
        val from = editor.selection.min
        val until = editor.selection.max
        val hasSelection = from >= 0 && until <= editor.text.length && from < until
        Text(if (hasSelection) "Fragment selectat: ${editor.text.substring(from, until).take(160)}"
            else "Selectează cu degetul un fragment din textul de mai sus.",
            style = MaterialTheme.typography.bodySmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(listOf("AFIRMATIE", "PERSOANA", "LOC", "DATA", "NOTA", "INCERT")) { kind ->
                FilterChip(selected = annotationType == kind,
                    onClick = { annotationType = kind }, label = { Text(kind) })
            }
        }
        OutlinedTextField(value = annotationNote, onValueChange = { annotationNote = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Comentariu documentar (opțional)") }, minLines = 2)
        Button(onClick = {
            busy = true
            scope.launch {
                try {
                    repository.annotate(selected.id, from, until, annotationType, annotationNote)
                    annotationNote = ""
                    onNotice("Adnotare legată de pasajul exact")
                } catch (e: Exception) { err = e.localizedMessage } finally { busy = false }
            }
        }, enabled = !busy && hasSelection && editor.text == selected.verifiedText) {
            Icon(Icons.Default.NoteAdd, null)
            Spacer(Modifier.width(6.dp))
            Text("Adnotează fragmentul")
        }

        HorizontalDivider()
        SourceCoveragePanel(
            page = selected,
            annotations = ownAnnotations,
            busy = busy || editor.text != selected.verifiedText,
            onSelectText = { window ->
                editor = editor.copy(selection = TextRange(window.start, window.end))
            },
            onVerifySegment = { window ->
                busy = true
                scope.launch {
                    try {
                        repository.annotate(
                            selected.id, window.start, window.end,
                            "SEGMENT_VERIFICAT", "Fragment confruntat manual cu pagina originală")
                        onNotice("Fragment verificat; registrul de acoperire a fost actualizat")
                    } catch (e: Exception) {
                        err = e.localizedMessage ?: "Nu s-a putut confirma segmentul"
                    } finally { busy = false }
                }
            }
        )
        HorizontalDivider()
        val scholarlyNotes = ownAnnotations.filter { it.category != "SEGMENT_VERIFICAT" }
        Text("ADNOTĂRI · ${scholarlyNotes.size}", style = MaterialTheme.typography.titleMedium)
        if (scholarlyNotes.isEmpty()) Text("Selectează un fragment și adaugă prima adnotare.")
        scholarlyNotes.forEach { a ->
            val resolved = resolveTextAnchor(selected.verifiedText, a)
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(a.category, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text("„${a.exactQuote}”", style = MaterialTheme.typography.bodyMedium,
                        maxLines = 6, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (a.note.isNotBlank()) Text(a.note, style = MaterialTheme.typography.bodySmall)
                    if (resolved.status == AnchorStatus.AMBIGUOUS ||
                        resolved.status == AnchorStatus.MISSING) {
                        Text("Ancoră de reverificat — pasajul s-a schimbat sau este ambiguu.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall)
                    } else {
                        Text(if (resolved.status == AnchorStatus.MOVED)
                            "Ancora a fost regăsită după corecturi." else "Ancoră validă",
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Text("${links.count { it.annotationId == a.id }} afirmații indexate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { onExtract(selected, a) },
                        enabled = resolved.resolved) {
                        Text("Transformă în afirmație documentară →")
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (err != null) Text(err.orEmpty(), color = MaterialTheme.colorScheme.error)
        Text("Originalul nu se rescrie. OCR-ul brut, corecturile, pasajele și referințele au istorii separate.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(85.dp))
    }
}
