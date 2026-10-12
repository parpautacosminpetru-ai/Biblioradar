package ro.atlasistoric.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ro.atlasistoric.data.*
import ro.atlasistoric.util.TimeMath
import ro.atlasistoric.ocr.suggestOcrLinks

@Composable
fun AssertionEditor(
    old: HistoricalAssertion?, initialOcr: String,
    preferredSourceId: String?, preferredEntityId: String?, preferredTopicId: String?,
    sources: List<HistoricalSource>, entities: List<HistoricalEntity>, topics: List<HistoricalTopic>,
    oldEntityLinks: List<AssertionEntityLink>, oldTopicLinks: List<AssertionTopicLink>,
    onNeedsSource: () -> Unit,
    onCreateEntity: () -> Unit,
    onCreateTopic: () -> Unit,
    saving: Boolean,
    onSave: (HistoricalAssertion, Map<String, String>, Set<String>, Boolean) -> Unit
) {
    var sourceId by remember(old?.id) { mutableStateOf(old?.sourceId ?: preferredSourceId ?: sources.firstOrNull()?.id) }
    LaunchedEffect(sources, preferredSourceId) {
        when {
            old == null && preferredSourceId != null && sources.any { it.id == preferredSourceId } -> sourceId = preferredSourceId
            sourceId == null && sources.isNotEmpty() -> sourceId = sources.first().id
        }
    }
    var kind by remember(old?.id) { mutableStateOf(old?.kind ?: "CITAT") }
    var sourceSearch by remember { mutableStateOf("") }
    var entitySearch by remember { mutableStateOf("") }
    var topicSearch by remember { mutableStateOf("") }
    var passage by remember(old?.id, initialOcr) { mutableStateOf(old?.passage ?: initialOcr) }
    var page by remember(old?.id) { mutableStateOf(old?.page.orEmpty()) }
    var section by remember(old?.id) { mutableStateOf(old?.section.orEmpty()) }
    var periodLabel by remember(old?.id) { mutableStateOf(old?.periodLabel.orEmpty()) }
    var start by remember(old?.id) { mutableStateOf(old?.yearStart?.toString().orEmpty()) }
    var end by remember(old?.id) { mutableStateOf(old?.yearEnd?.toString().orEmpty()) }
    var month by remember(old?.id) { mutableStateOf(old?.month?.toString().orEmpty()) }
    var day by remember(old?.id) { mutableStateOf(old?.day?.toString().orEmpty()) }
    var precision by remember(old?.id) { mutableStateOf(old?.precision ?: "NECUNOSCUTA") }
    var contextNote by remember(old?.id) { mutableStateOf(old?.contextNote.orEmpty()) }
    val selectedEntityRoles = remember(old?.id, oldEntityLinks) {
        mutableStateMapOf<String, String>().apply {
            oldEntityLinks.filter { it.assertionId == old?.id }.forEach { put(it.entityId, it.role) }
        }
    }
    val selectedTopics = remember(old?.id, oldTopicLinks) {
        mutableStateListOf<String>().apply { addAll(oldTopicLinks.filter { it.assertionId == old?.id }.map { it.topicId }) }
    }
    LaunchedEffect(preferredEntityId, entities) {
        val entity = entities.find { it.id == preferredEntityId }
        if (old == null && entity != null && entity.id !in selectedEntityRoles) {
            selectedEntityRoles[entity.id] = when (entity.entityType) {
                "PLACE" -> "LOC"; "PERSON", "INSTITUTION" -> "ACTOR"; else -> "SUBJECT"
            }
        }
    }
    LaunchedEffect(preferredTopicId, topics) {
        if (old == null) preferredTopicId?.let { id ->
            if (topics.any { it.id == id } && id !in selectedTopics) selectedTopics.add(id)
        }
    }
    val ocrHints = remember(initialOcr, entities, topics) {
        if (initialOcr.isBlank()) null else suggestOcrLinks(initialOcr, entities, topics)
    }
    val from = start.trim().toIntOrNull()
    val to = end.trim().toIntOrNull()
    val mm = month.trim().toIntOrNull()
    val dd = day.trim().toIntOrNull()
    val validTime = (start.isBlank() || (from != null && from != 0)) &&
        (end.isBlank() || (to != null && to != 0)) &&
        (from == null || to == null || from <= to) &&
        (month.isBlank() || (mm != null && mm in 1..12 && from != null && (to == null || from == to))) &&
        (day.isBlank() || (dd != null && dd in 1..31 && mm != null)) &&
        (precision != "ZI" || (from != null && mm != null && dd != null)) &&
        (precision != "LUNA" || (from != null && mm != null))
    val canSave = sourceId != null && passage.isNotBlank() && (page.isNotBlank() || section.isNotBlank()) && validTime

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Păstrează fidelitatea față de sursă", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text("Aici se înregistrează ce apare în document. Comentariul tău este separat. Conexiunile reutilizează aceleași identități.",
            style = MaterialTheme.typography.bodySmall)
        if (sources.isEmpty()) {
            Text("Mai întâi înregistrează sursa bibliografică.", color = MaterialTheme.colorScheme.error)
            Button(onClick = onNeedsSource) { Text("Adaugă sursa fără să pierzi pasajul") }
        } else {
            Text("Sursa *", fontWeight = FontWeight.SemiBold)
            Field("Filtrează biblioteca", sourceSearch) { sourceSearch = it }
            SelectIdChips(sources.filter { sourceSearch.isBlank() || it.title.contains(sourceSearch, true) ||
                it.author.contains(sourceSearch, true) }.map { it.id to "${it.author} · ${it.title}" }, sourceId, { sourceId = it })
            Hint("Sursa aleasă: ${sources.find { it.id == sourceId }?.title ?: "Neselectată"}")
            OutlinedButton(onClick = onNeedsSource) { Text("+ Creează o sursă nouă fără să pierzi pasajul") }
        }
        HorizontalDivider()
        Text("Tipul redării", fontWeight = FontWeight.SemiBold)
        ChoiceRow(listOf("CITAT", "PARAFRAZA", "EXTRAGERE"), kind, { kind = it }) {
            when (it) { "CITAT" -> "Citat direct"; "PARAFRAZA" -> "Parafrază"; else -> "Extragere brută" }
        }
        Field("Textul afirmației / pasajul OCR *", passage, singleLine = false) { passage = it }
        if (initialOcr.isNotBlank()) {
            Hint("Text preluat din cameră: corectează manual eventualele erori OCR.")
            if (ocrHints != null) {
                Text("Propuneri OCR locale — verifică înainte de aplicare", color = MaterialTheme.colorScheme.primary)
                val candidates = entities.filter { it.id in ocrHints.entityIds }
                if (candidates.isNotEmpty()) {
                    Text("Entități identificate după nume exact", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(candidates) { candidate ->
                            FilterChip(selected = candidate.id in selectedEntityRoles, onClick = {
                                if (candidate.id in selectedEntityRoles) selectedEntityRoles.remove(candidate.id)
                                else selectedEntityRoles[candidate.id] = if (candidate.entityType == "PLACE") "LOC" else "ACTOR"
                            }, label = { Text(candidate.name) })
                        }
                    }
                }
                if (ocrHints.candidateYears.isNotEmpty()) {
                    Text("Numere care ar putea fi ani (pot fi și pagini!)", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(ocrHints.candidateYears) { candidateYear ->
                            AssistChip(onClick = { start = candidateYear.toString(); end = candidateYear.toString(); precision = "AN" },
                                label = { Text("Verifică ${candidateYear}") })
                        }
                    }
                }
                val candidateTopics = topics.filter { it.id in ocrHints.topicIds }
                if (candidateTopics.isNotEmpty()) {
                    Text("Teme sugerate", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(candidateTopics) { t ->
                            FilterChip(selected = t.id in selectedTopics,
                                onClick = { if (t.id in selectedTopics) selectedTopics.remove(t.id) else selectedTopics.add(t.id) },
                                label = { Text(t.name) })
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Field("Pagină / file", page) { page = it } }
            Box(Modifier.weight(1f)) { Field("Capitol / folio / §", section) { section = it } }
        }
        HorizontalDivider()
        Text("Încadrare temporală", fontWeight = FontWeight.SemiBold)
        ChoiceRow(listOf("NECUNOSCUTA", "EPOCA", "SECOL", "INTERVAL", "AN", "LUNA", "ZI"), precision,
            { precision = it }) { when(it) {
                "NECUNOSCUTA" -> "Necunoscută"; "EPOCA" -> "Epocă"; "SECOL" -> "Secol"
                "INTERVAL" -> "Interval"; "AN" -> "An"; "LUNA" -> "Lună"; else -> "Zi"
            } }
        Field("Denumirea epocii / perioada din sursă", periodLabel) { periodLabel = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Field("An început", start) { start = it } }
            Box(Modifier.weight(1f)) { Field("An sfârșit", end) { end = it } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Field("Lună 1–12", month) { month = it } }
            Box(Modifier.weight(1f)) { Field("Zi 1–31", day) { day = it } }
        }
        Hint("Folosește ani negativi pentru î.Hr. (ex.: -44), pozitivi pentru d.Hr.; anul zero nu există. Nu preciza artificial o dată necunoscută.")
        if (!validTime) Text("Interval sau dată invalidă.", color = MaterialTheme.colorScheme.error)
        HorizontalDivider()
        Text("Entități asociate", fontWeight = FontWeight.SemiBold)
        Hint("Selectează mai multe persoane, locuri, instituții sau evenimente. Fiecare este reutilizat după ID.")
        OutlinedButton(onClick = onCreateEntity) { Text("+ Creează o identitate nouă") }
        if (entities.isEmpty()) Hint("Creează un actor, loc, eveniment sau instituție și apoi asociază identitatea cu afirmația.")
        Field("Caută identități existente", entitySearch) { entitySearch = it }
        val grouped = entities.filter { entitySearch.isBlank() || it.name.contains(entitySearch, true) ||
            it.aliases.contains(entitySearch, true) }.groupBy { it.entityType }
        listOf("PLACE", "PERSON", "INSTITUTION", "EVENT", "OTHER").forEach { type ->
            val available = grouped[type].orEmpty()
            if (available.isNotEmpty()) {
                Text(when(type) { "PLACE" -> "Spațiu"; "PERSON" -> "Actori"; "INSTITUTION" -> "Instituții"
                    "EVENT" -> "Evenimente"; else -> "Altele" }, style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(available, key = { it.id }) { entity ->
                        FilterChip(selected = entity.id in selectedEntityRoles,
                            onClick = { if (entity.id in selectedEntityRoles) selectedEntityRoles.remove(entity.id)
                            else selectedEntityRoles[entity.id] = when(type) { "PLACE" -> "LOC"; "PERSON", "INSTITUTION" -> "ACTOR"; else -> "SUBJECT" } },
                            label = { Text(entity.name) })
                    }
                }
            }
        }
        // A place may be cited as a SUBJECT without being the event's location.
        selectedEntityRoles.keys.toList().forEach { id ->
            val name = entities.find { it.id == id }?.name ?: return@forEach
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                listOf("LOC", "ACTOR", "SUBJECT").filter { role ->
                    role != "LOC" || entities.find { it.id == id }?.entityType == "PLACE"
                }.forEach { role ->
                    FilterChip(selected = selectedEntityRoles[id] == role,
                        onClick = { selectedEntityRoles[id] = role }, label = { Text(role) })
                }
            }
        }
        HorizontalDivider()
        Text("Teme și subteme", fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = onCreateTopic) { Text("+ Creează o temă nouă") }
        Field("Filtrează teme", topicSearch) { topicSearch = it }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(topics.filter { topicSearch.isBlank() || it.name.contains(topicSearch, true) }, key = { it.id }) { t ->
                FilterChip(selected = t.id in selectedTopics,
                    onClick = { if (t.id in selectedTopics) selectedTopics.remove(t.id) else selectedTopics.add(t.id) },
                    label = { Text(t.name) })
            }
        }
        HorizontalDivider()
        Text("Comentariul cercetătorului (separat)", fontWeight = FontWeight.SemiBold)
        Field("De ce ai extras informația / legături / interpretare", contextNote, singleLine = false) { contextNote = it }
        Button(onClick = {
            val item = (old ?: HistoricalAssertion(
                sourceId = sourceId!!, passage = passage.trim(), page = page.trim()
            )).copy(
                sourceId = sourceId!!, kind = kind, passage = passage.trim(), page = page.trim(),
                section = section.trim(), contextNote = contextNote.trim(),
                periodLabel = periodLabel.trim(), yearStart = from, yearEnd = to ?: from,
                month = mm, day = dd, precision = precision
            )
            onSave(item, selectedEntityRoles.toMap(), selectedTopics.toSet(), old != null)
        }, enabled = canSave && !saving, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(if (saving) "Se salvează…" else if (old == null) "Integrează în atlas" else "Salvează modificările")
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun AssertionDetail(
    item: HistoricalAssertion, sources: List<HistoricalSource>, entities: List<HistoricalEntity>,
    topics: List<HistoricalTopic>, entityLinks: List<AssertionEntityLink>, topicLinks: List<AssertionTopicLink>,
    revisions: List<RevisionEntry>,
    onEdit: () -> Unit
) {
    val src = sources.find { it.id == item.sourceId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AssistChip(onClick = onEdit, label = { Text(item.kind) })
        Text("Afirmație documentată", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        ElevatedCard {
            Text(item.passage, modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge)
        }
        Text("REFERINȚA EXACTĂ", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(listOfNotNull(src?.author, src?.title, src?.edition?.ifBlank { null }, src?.year?.ifBlank { null })
            .filter { it.isNotBlank() }.joinToString(" · "))
        Text("Pagina/file: ${item.page} ${item.section}")
        HorizontalDivider()
        Text("TIMP", fontWeight = FontWeight.Bold)
        Text("${item.precision} · ${item.periodLabel.ifBlank { "Fără etichetă de epocă" }} · ${item.yearStart ?: "?"} – ${item.yearEnd ?: "?"}")
        Text("SPAȚIU ȘI ACTORI", fontWeight = FontWeight.Bold)
        val linked = entityLinks.filter { it.assertionId == item.id }
        if (linked.isEmpty()) Hint("Fără entități legate deocamdată") else linked.forEach { link ->
            Text("${link.role}: ${entities.find { it.id == link.entityId }?.name ?: "?"}")
        }
        Text("TEMATICĂ", fontWeight = FontWeight.Bold)
        val themes = topicLinks.filter { it.assertionId == item.id }
        if (themes.isEmpty()) Hint("Fără teme alocate") else themes.forEach { link ->
            Text(topics.find { it.id == link.topicId }?.name ?: "?")
        }
        HorizontalDivider()
        Text("COMENTARIUL TĂU (NECONFUNDAT CU SURSA)", fontWeight = FontWeight.Bold)
        Text(item.contextNote.ifBlank { "Niciun comentariu adăugat." })
        Text("ISTORICUL MODIFICĂRILOR", fontWeight = FontWeight.Bold)
        var expandedRevision by remember { mutableStateOf<String?>(null) }
        if (revisions.isEmpty()) Hint("Înregistrarea nu a fost încă modificată.")
        else revisions.forEach { revision ->
            OutlinedButton(onClick = { expandedRevision = if (expandedRevision == revision.id) null else revision.id }) {
                Text("Versiune anterioară · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(revision.changedAt))}")
            }
            if (expandedRevision == revision.id) {
                Text(revision.previousJson, style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = onEdit) { Text("Editează înregistrarea") }
    }
}
