package ro.atlasistoric.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.atlasistoric.data.*

/** Linked source references are generated on export, not pasted into editable assertions. */
fun exportDraftMarkdown(
    draft: ResearchDraft, assertionIds: Set<String>,
    assertions: List<HistoricalAssertion>, sources: List<HistoricalSource>
): String {
    val bySource = sources.associateBy { it.id }
    val linked = assertions.filter { it.id in assertionIds }
    return buildString {
        append("# ").append(draft.title).append("\n\n")
        append(draft.content.trim()).append("\n\n")
        append("---\n\n## Aparat documentar asociat\n\n")
        if (linked.isEmpty()) append("Nicio afirmație atașată.\n")
        linked.forEachIndexed { index, assertion ->
            val source = bySource[assertion.sourceId]
            append(index + 1).append(". ")
            append(source?.author.orEmpty()).append(", ")
            append(source?.title ?: "[Sursă lipsă]")
            source?.edition?.takeIf { it.isNotBlank() }?.let { append(", ").append(it) }
            source?.year?.takeIf { it.isNotBlank() }?.let { append(" (").append(it).append(")") }
            append(", ").append(if (assertion.page.isNotBlank()) "p. ${assertion.page}" else "§ ${assertion.section}")
            append(" [").append(assertion.kind).append("]")
            append(" — ID: ").append(assertion.id).append("\n")
        }
    }
}

@Composable
fun DraftsScreen(
    drafts: List<ResearchDraft>, links: List<DraftClaimLink>,
    assertions: List<HistoricalAssertion>, sources: List<HistoricalSource>,
    revisions: List<RevisionEntry>,
    onSave: (ResearchDraft?, ResearchDraft, Set<String>) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<ResearchDraft?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf<ResearchDraft?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val draft = exporting
        exporting = null
        if (uri != null && draft != null) scope.launch {
            try {
                val attached = links.filter { it.draftId == draft.id }.map { it.assertionId }.toSet()
                val text = exportDraftMarkdown(draft, attached, assertions, sources)
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                        ?: error("Nu se poate salva fișierul")
                }
                snackbar.showSnackbar("Textul a fost exportat separat de arhiva surselor")
            } catch (e: Exception) { snackbar.showSnackbar("Export eșuat: ${e.localizedMessage}") }
        }
    }
    if (showEditor) DraftForm(
        old = editing, links = links, assertions = assertions, sources = sources,
        onDismiss = { showEditor = false },
        onSave = { item, claims -> onSave(editing, item, claims); showEditor = false }
    )
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Atelier de sinteză", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary)
            Text("Aici scrii interpretările și lucrările proprii. Sursele brute rămân nemodificate; sinteza păstrează legături către afirmațiile citate.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Sinteză nouă")
            }
            if (drafts.isEmpty()) Hint("Atelierul este gol. Poți construi oricâte lucrări diferite din aceeași bază de afirmații.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 60.dp)) {
                items(drafts, key = { it.id }) { draft ->
                    ElevatedCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(draft.title, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text(draft.content.ifBlank { "Sinteză fără text încă" }.take(250),
                                style = MaterialTheme.typography.bodySmall)
                            val count = links.count { it.draftId == draft.id }
                            val changed = revisions.count { it.recordType == "DRAFT" && it.recordId == draft.id }
                            Hint("$count afirmații asociate · $changed versiuni păstrate")
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Button(onClick = { editing = draft; showEditor = true }) { Text("Editează") }
                                OutlinedButton(onClick = {
                                    exporting = draft
                                    exporter.launch("atlas-sinteza-${draft.id.take(8)}.md")
                                }) { Text("Export Markdown") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftForm(
    old: ResearchDraft?, links: List<DraftClaimLink>,
    assertions: List<HistoricalAssertion>, sources: List<HistoricalSource>,
    onDismiss: () -> Unit, onSave: (ResearchDraft, Set<String>) -> Unit
) {
    var title by remember(old?.id) { mutableStateOf(old?.title.orEmpty()) }
    var content by remember(old?.id) { mutableStateOf(old?.content.orEmpty()) }
    var query by remember { mutableStateOf("") }
    val selected = remember(old?.id, links) {
        mutableStateListOf<String>().apply {
            addAll(links.filter { it.draftId == old?.id }.map { it.assertionId })
        }
    }
    val sourceById = remember(sources) { sources.associateBy { it.id } }
    val listed = remember(assertions, sourceById, query) {
        assertions.filter { query.isBlank() || it.passage.contains(query, true) ||
            sourceById[it.sourceId]?.title?.contains(query, true) == true }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (old == null) "Sinteză nouă" else "Editează sinteza") },
        text = {
            Column(Modifier.heightIn(max = 550.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Titlu *", title) { title = it }
                Field("Textul și interpretarea ta", content, singleLine = false) { content = it }
                HorizontalDivider()
                Text("Alege afirmațiile documentare care susțin această lucrare",
                    style = MaterialTheme.typography.labelLarge)
                Field("Caută pasaj sau titlul sursei", query) { query = it }
                Hint("${selected.size} afirmații selectate. Nu se creează copii ale surselor.")
                listed.take(120).forEach { claim ->
                    val checked = claim.id in selected
                    val bibliographic = sourceById[claim.sourceId]
                    FilterChip(
                        selected = checked,
                        onClick = { if (checked) selected.remove(claim.id) else selected.add(claim.id) },
                        label = { Text("${bibliographic?.title ?: "Sursă"} · ${claim.page} · ${claim.passage.take(90)}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (listed.size > 120) Hint("Afișate primele 120 rezultate. Restrânge căutarea pentru a găsi alte pasaje.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val draft = (old ?: ResearchDraft(title = title.trim())).copy(
                    title = title.trim(), content = content.trim(), updatedAt = nowMillis())
                onSave(draft, selected.toSet())
            }, enabled = title.isNotBlank()) { Text("Salvează sinteza") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Renunță") } }
    )
}
