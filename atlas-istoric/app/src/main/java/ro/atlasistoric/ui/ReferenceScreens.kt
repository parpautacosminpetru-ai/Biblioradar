package ro.atlasistoric.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ro.atlasistoric.data.*
import ro.atlasistoric.util.TimeMath

@Composable
fun SourcesScreen(sources: List<HistoricalSource>, assertions: List<HistoricalAssertion>, onSave: (HistoricalSource?, HistoricalSource) -> Unit) {
    var editing by remember { mutableStateOf<HistoricalSource?>(null) }
    var dialog by remember { mutableStateOf(false) }
    if (dialog) SourceForm(editing, onDismiss = { dialog = false }) { result ->
        onSave(editing, result); dialog = false
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenLead("Catalog bibliografic", "Păstrează ediția, identificatorul și contextul bibliografic. O sursă poate susține oricâte afirmații.")
        Button(onClick = { editing = null; dialog = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Sursă nouă") }
        if (sources.isEmpty()) Hint("Nu există încă surse. Începe prin a introduce lucrarea pe care o citești.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sources, key = { it.id }) { source ->
                OutlinedCard(onClick = { editing = source; dialog = true }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(source.title, fontWeight = FontWeight.SemiBold)
                        Text("${source.author} · ${source.year}", style = MaterialTheme.typography.bodySmall)
                        val extracted = assertions.filter { it.sourceId == source.id }
                        Hint("${extracted.size} afirmații · ${extracted.map { it.page }.filter { it.isNotBlank() }.distinct().size} pagini cu extrageri · ${when(source.readingStatus) {
                            "IN_CURS" -> "În curs"; "FINALIZATA" -> "Parcursă integral"; else -> "Neîncepută" }}")
                        if (source.lastPage.isNotBlank()) Hint("Ultima pagină parcursă: ${source.lastPage}")
                        Text(listOf(source.edition, source.publication, source.identifier).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable fun SourceForm(old: HistoricalSource?, onDismiss: () -> Unit, onSave: (HistoricalSource) -> Unit) {
    var author by remember(old?.id) { mutableStateOf(old?.author.orEmpty()) }
    var title by remember(old?.id) { mutableStateOf(old?.title.orEmpty()) }
    var edition by remember(old?.id) { mutableStateOf(old?.edition.orEmpty()) }
    var publication by remember(old?.id) { mutableStateOf(old?.publication.orEmpty()) }
    var year by remember(old?.id) { mutableStateOf(old?.year.orEmpty()) }
    var identifier by remember(old?.id) { mutableStateOf(old?.identifier.orEmpty()) }
    var notes by remember(old?.id) { mutableStateOf(old?.notes.orEmpty()) }
    var readingStatus by remember(old?.id) { mutableStateOf(old?.readingStatus ?: "NEINCEPUTA") }
    var lastPage by remember(old?.id) { mutableStateOf(old?.lastPage.orEmpty()) }
    FormDialog(
        title = if (old == null) "Sursă bibliografică" else "Editează sursa",
        saveEnabled = title.isNotBlank(),
        onDismiss = onDismiss,
        onSave = { onSave((old ?: HistoricalSource(author = author.trim(), title = title.trim())).copy(
            author = author.trim(), title = title.trim(), edition = edition.trim(), publication = publication.trim(),
            year = year.trim(), identifier = identifier.trim(), notes = notes.trim(),
            readingStatus = readingStatus, lastPage = lastPage.trim()
        )) }
    ) {
        Field("Autor / editor", author) { author = it }
        Field("Titlul exact *", title) { title = it }
        Field("Ediție / volum", edition) { edition = it }
        Field("Editură și loc", publication) { publication = it }
        Field("Anul publicării", year) { year = it }
        Field("ISBN / DOI / cotă", identifier) { identifier = it }
        Field("Observații bibliografice", notes, singleLine = false) { notes = it }
        Text("Stadiul lecturii", style = MaterialTheme.typography.labelLarge)
        ChoiceRow(listOf("NEINCEPUTA", "IN_CURS", "FINALIZATA"), readingStatus, { readingStatus = it }) {
            when(it) { "IN_CURS" -> "În curs"; "FINALIZATA" -> "Epuizată / finalizată"; else -> "Neîncepută" }
        }
        Field("Ultima pagină parcursă (opțional)", lastPage) { lastPage = it }
    }
}

@Composable
fun EntitiesScreen(
    entities: List<HistoricalEntity>, relations: List<EntityRelation>,
    onSave: (HistoricalEntity?, HistoricalEntity) -> Unit, onRelations: () -> Unit
) {
    var editing by remember { mutableStateOf<HistoricalEntity?>(null) }
    var dialog by remember { mutableStateOf(false) }
    if (dialog) EntityForm(editing, entities, onDismiss = { dialog = false }) { item ->
        onSave(editing, item); dialog = false
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenLead("Identități, nu copii", "Fiecare persoană, loc, instituție ori eveniment primește un identificator stabil. Homonimele rămân distincte.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editing = null; dialog = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Identitate") }
            OutlinedButton(onClick = onRelations) { Text("Relații (${relations.size})") }
        }
        if (entities.isEmpty()) Hint("Adaugă primul actor, eveniment sau loc istoric.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entities, key = { it.id }) { item ->
                OutlinedCard(onClick = { editing = item; dialog = true }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(typeLabel(item.entityType), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary)
                        }
                        if (item.aliases.isNotBlank()) Hint("Variante: ${item.aliases}")
                        if (item.description.isNotBlank()) Hint(item.description)
                    }
                }
            }
        }
    }
}

private fun typeLabel(type: String): String = when (type) {
    "PERSON" -> "Persoană"; "PLACE" -> "Loc"; "INSTITUTION" -> "Instituție"
    "EVENT" -> "Eveniment"; else -> "Altă entitate"
}

@Composable fun EntityForm(old: HistoricalEntity?, all: List<HistoricalEntity>, onDismiss: () -> Unit, onSave: (HistoricalEntity) -> Unit) {
    var name by remember(old?.id) { mutableStateOf(old?.name.orEmpty()) }
    var entityType by remember(old?.id) { mutableStateOf(old?.entityType ?: "PERSON") }
    var aliases by remember(old?.id) { mutableStateOf(old?.aliases.orEmpty()) }
    var description by remember(old?.id) { mutableStateOf(old?.description.orEmpty()) }
    val knownForms = (listOf(name) + aliases.split(';', ',')).map(TimeMath::normalize)
        .filter { it.isNotBlank() }.toSet()
    val doubles = all.filter { item ->
        item.id != old?.id && item.entityType == entityType &&
            (listOf(item.name) + item.aliases.split(';', ',')).map(TimeMath::normalize)
                .any { it.isNotBlank() && it in knownForms }
    }
    FormDialog(
        if (old == null) "Identitate nouă" else "Editează identitatea",
        onDismiss, name.isNotBlank(),
        onSave = { onSave((old ?: HistoricalEntity(name = name.trim(), entityType = entityType)).copy(
            name = name.trim(), entityType = entityType, aliases = aliases.trim(),
            description = description.trim(), updatedAt = nowMillis())) }
    ) {
        Text("Tip de entitate", style = MaterialTheme.typography.labelLarge)
        ChoiceRow(listOf("PERSON", "PLACE", "INSTITUTION", "EVENT", "OTHER"), entityType, { entityType = it }, ::typeLabel)
        Field("Nume / denumire *", name) { name = it }
        if (doubles.isNotEmpty()) Text("Posibilă dublură: ${doubles.joinToString { it.name }}. Verifică identitatea înainte de a salva.", color = MaterialTheme.colorScheme.error)
        Field("Alte forme de nume", aliases) { aliases = it }
        Field("Descriere pentru identificare", description, singleLine = false) { description = it }
        Hint("Nu unificăm automat persoane omonime. Relațiile temporale și spațiale se adaugă separat.")
    }
}

@Composable
fun TopicsScreen(topics: List<HistoricalTopic>, onSave: (HistoricalTopic?, HistoricalTopic) -> Unit) {
    var editing by remember { mutableStateOf<HistoricalTopic?>(null) }
    var dialog by remember { mutableStateOf(false) }
    if (dialog) TopicForm(editing, topics, onDismiss = { dialog = false }) { item ->
        onSave(editing, item); dialog = false
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenLead("Teme imbricate", "O subtemă poate fi explorată împreună cu toate afirmațiile din ramurile ei descendente.")
        Button(onClick = { editing = null; dialog = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Temă sau subtemă") }
        val rows = remember(topics) {
            val res = mutableListOf<Pair<HistoricalTopic, Int>>()
            val found = mutableSetOf<String>()
            fun walk(p: HistoricalTopic, depth: Int) {
                if (!found.add(p.id)) return
                res.add(p to depth)
                topics.filter { it.parentId == p.id }.forEach { walk(it, depth + 1) }
            }
            topics.filter { it.parentId == null }.forEach { walk(it, 0) }
            topics.forEach { walk(it, 0) }
            res
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows, key = { it.first.id }) { (t, depth) ->
                Surface(onClick = { editing = t; dialog = true }, tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width((depth * 18).coerceAtMost(110).dp))
                        Text(if (depth > 0) "↳  " else "◉  ", color = MaterialTheme.colorScheme.primary)
                        Text(t.name)
                    }
                }
            }
        }
    }
}

@Composable fun TopicForm(old: HistoricalTopic?, topics: List<HistoricalTopic>, onDismiss: () -> Unit,
    initialParentId: String? = null, onSave: (HistoricalTopic) -> Unit) {
    var name by remember(old?.id) { mutableStateOf(old?.name.orEmpty()) }
    var description by remember(old?.id) { mutableStateOf(old?.description.orEmpty()) }
    var parentId by remember(old?.id, initialParentId) { mutableStateOf(old?.parentId ?: initialParentId) }
    // Avoid parent cycles even when changing an existing branch.
    val invalidParents = remember(topics, old?.id) {
        old?.let { ro.atlasistoric.util.descendantsOfTopic(it.id, topics) }.orEmpty()
    }
    FormDialog(if (old == null) "Temă nouă" else "Editează tema", onDismiss, name.isNotBlank(), onSave = {
        onSave((old ?: HistoricalTopic(name = name.trim())).copy(name = name.trim(), parentId = parentId, description = description.trim()))
    }) {
        Field("Numele temei *", name) { name = it }
        Field("Descriere", description, singleLine = false) { description = it }
        Text("Părinte (opțional)", style = MaterialTheme.typography.labelLarge)
        SelectIdChips(choices = listOf(null to "Rădăcină") + topics.filter { it.id !in invalidParents }.map { it.id to it.name },
            selected = parentId, onSelected = { parentId = it })
    }
}

@Composable
fun RelationsScreen(
    entities: List<HistoricalEntity>, relations: List<EntityRelation>, assertions: List<HistoricalAssertion>,
    onSave: (EntityRelation?, EntityRelation) -> Unit
) {
    var dialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EntityRelation?>(null) }
    if (dialog) RelationForm(editing, entities, assertions, onDismiss = { dialog = false }) { onSave(editing, it); dialog = false }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenLead("Legături istorice", "PART_OF stabilește nestingul geografic într-un interval; alte relații exprimă conexiuni documentate ori ipoteze.")
        Button(onClick = { editing = null; dialog = true }, enabled = entities.size >= 2) { Text("+ Relație nouă") }
        if (entities.size < 2) Hint("Creează cel puțin două identități istorice.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(relations, key = { it.id }) { r ->
                val a = entities.find { it.id == r.fromEntityId }?.name.orEmpty()
                val b = entities.find { it.id == r.toEntityId }?.name.orEmpty()
                OutlinedCard(onClick = { editing = r; dialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("$a   → ${r.relationType} →   $b", fontWeight = FontWeight.SemiBold)
                        Hint("${r.yearStart?.toString() ?: "?"} – ${r.yearEnd?.toString() ?: "?"} · ${if (r.supportingAssertionId == null) "Provizorie, fără afirmație-sursă" else "Cu afirmație de susținere"}")
                        if (r.comment.isNotBlank()) Text(r.comment, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable private fun RelationForm(old: EntityRelation?, entities: List<HistoricalEntity>, assertions: List<HistoricalAssertion>, onDismiss: () -> Unit, onSave: (EntityRelation) -> Unit) {
    var from by remember(old?.id) { mutableStateOf(old?.fromEntityId) }
    var to by remember(old?.id) { mutableStateOf(old?.toEntityId) }
    var type by remember(old?.id) { mutableStateOf(old?.relationType ?: "PART_OF") }
    var start by remember(old?.id) { mutableStateOf(old?.yearStart?.toString().orEmpty()) }
    var end by remember(old?.id) { mutableStateOf(old?.yearEnd?.toString().orEmpty()) }
    var supporting by remember(old?.id) { mutableStateOf(old?.supportingAssertionId) }
    var comment by remember(old?.id) { mutableStateOf(old?.comment.orEmpty()) }
    val startY = start.trim().toIntOrNull()
    val endY = end.trim().toIntOrNull()
    val datesValid = (start.isBlank() || (startY != null && startY != 0)) &&
        (end.isBlank() || (endY != null && endY != 0)) && (startY == null || endY == null || startY <= endY)
    val valid = from != null && to != null && from != to && datesValid &&
        (type != "PART_OF" || entities.firstOrNull { it.id == from }?.entityType == "PLACE" &&
            entities.firstOrNull { it.id == to }?.entityType == "PLACE")
    FormDialog(if (old == null) "Relație temporală" else "Editează relația", onDismiss, valid, onSave = {
        onSave((old ?: EntityRelation(fromEntityId = from!!, toEntityId = to!!, relationType = type)).copy(
            fromEntityId = from!!, toEntityId = to!!, relationType = type,
            yearStart = startY, yearEnd = endY, supportingAssertionId = supporting, comment = comment.trim()))
    }) {
        Text("Tipul relației", style = MaterialTheme.typography.labelLarge)
        ChoiceRow(listOf("PART_OF", "RULES", "CONFLICT", "INFLUENCES", "RELATED"), type, { type = it }) { it }
        Text("De la (entitatea copil / inițiatorul)")
        SelectIdChips(entities.map { it.id to "${it.name} · ${typeLabel(it.entityType)}" }, from, { from = it })
        Text("La (părintele / destinatarul)")
        SelectIdChips(entities.filter { it.id != from }.map { it.id to it.name }, to, { to = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Field("Din anul", start) { start = it } }
            Box(Modifier.weight(1f)) { Field("Până în", end) { end = it } }
        }
        Text("Afirmație care susține această legătură (opțional)")
        SelectIdChips(listOf(null to "Fără dovadă atașată") + assertions.map { it.id to it.passage.take(50) }, supporting, { supporting = it })
        Field("Comentariu / statutul ipotezei", comment, singleLine = false) { comment = it }
        Hint("PART_OF este rezervată nestingului geografic dintre două locuri și nu poate crea cicluri. O relație fără afirmație de susținere este provizorie.")
    }
}

@Composable private fun ScreenLead(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun Field(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = singleLine,
        minLines = if (singleLine) 1 else 3)
}

@Composable fun ChoiceRow(values: List<String>, selected: String, onSelected: (String) -> Unit, label: (String) -> String = { it }) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(values) { item -> FilterChip(selected = selected == item, onClick = { onSelected(item) }, label = { Text(label(item)) }) }
    }
}

@Composable fun SelectIdChips(choices: List<Pair<String?, String>>, selected: String?, onSelected: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(choices) { (key, name) ->
            FilterChip(selected = selected == key, onClick = { onSelected(key) }, label = { Text(name, maxLines = 1) })
        }
    }
}

@Composable fun FormDialog(title: String, onDismiss: () -> Unit, saveEnabled: Boolean, onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = saveEnabled) { Text("Salvează") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Renunță") } }
    )
}
