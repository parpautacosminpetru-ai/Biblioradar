package ro.atlasistoric.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ro.atlasistoric.data.*
import ro.atlasistoric.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class TreeRow(val id: String, val name: String, val depth: Int)

private fun topicRows(items: List<HistoricalTopic>): List<TreeRow> {
    val byParent = items.groupBy { it.parentId }
    val result = mutableListOf<TreeRow>()
    val visited = mutableSetOf<String>()
    fun visit(item: HistoricalTopic, depth: Int) {
        if (!visited.add(item.id)) return
        result += TreeRow(item.id, item.name, depth)
        byParent[item.id].orEmpty().sortedBy { it.name }.forEach { visit(it, depth + 1) }
    }
    items.filter { it.parentId == null || items.none { parent -> parent.id == it.parentId } }
        .sortedBy { it.name }.forEach { visit(it, 0) }
    items.forEach { visit(it, 0) } // cycles and disconnected branches can't hide data
    return result
}

private fun placeRows(items: List<HistoricalEntity>, relations: List<EntityRelation>, from: Int?, until: Int?): List<TreeRow> {
    val ids = items.map { it.id }.toSet()
    val active = relations.filter { it.relationType == "PART_OF" && it.fromEntityId in ids &&
        it.toEntityId in ids && TimeMath.relationActive(it.yearStart, it.yearEnd, from, until) }
    val parents = active.map { it.fromEntityId }.toSet()
    val childrenByParent = active.groupBy { it.toEntityId }
    val visited = mutableSetOf<String>()
    val result = mutableListOf<TreeRow>()
    fun visit(p: HistoricalEntity, depth: Int) {
        if (!visited.add(p.id)) return
        result += TreeRow(p.id, p.name, depth)
        childrenByParent[p.id].orEmpty().mapNotNull { edge -> items.find { it.id == edge.fromEntityId } }
            .sortedBy { it.name }.forEach { visit(it, depth + 1) }
    }
    items.filter { it.id !in parents }.sortedBy { it.name }.forEach { visit(it, 0) }
    items.forEach { visit(it, 0) }
    return result
}

@Composable
fun AtlasScreen(
    assertions: List<HistoricalAssertion>, entities: List<HistoricalEntity>,
    topics: List<HistoricalTopic>, sources: List<HistoricalSource>,
    entityLinks: List<AssertionEntityLink>, topicLinks: List<AssertionTopicLink>,
    relations: List<EntityRelation>,
    level: String, onLevel: (String, String?) -> Unit,
    timeKey: String?, onTimeKey: (String?) -> Unit,
    anchorYear: Int, onAnchorYear: (Int) -> Unit,
    anchorMonth: Int, onAnchorMonth: (Int) -> Unit,
    placeIds: Set<String>, onPlaceIds: (Set<String>) -> Unit,
    onCreatePlace: () -> Unit,
    topicIds: Set<String>, onTopicIds: (Set<String>) -> Unit,
    topicMatchAll: Boolean, onTopicMatchAll: (Boolean) -> Unit,
    perspective: String, onPerspective: (String) -> Unit,
    search: String, onSearch: (String) -> Unit,
    onOpen: (String) -> Unit,
    onOverlaps: () -> Unit,
    onBands: () -> Unit,
    onStudy: () -> Unit,
    onDocuments: () -> Unit
) {
    // The temporal ruler exists independently of stored documents: zoom never invents evidence.
    val eras = remember(assertions) { assertions.map { it.periodLabel } }
    val availableSlots = remember(level, anchorYear, anchorMonth, eras) {
        TemporalNavigation.visible(level, anchorYear, anchorMonth, eras)
    }
    val selectedSlot = remember(timeKey) { TemporalNavigation.fromKey(timeKey) }
    val from = selectedSlot?.from
    val until = selectedSlot?.through
    val relevantPlaces = remember(placeIds, relations, from, until) {
        if (placeIds.isEmpty()) null else placeIds.flatMap {
            descendantsOfPlace(it, relations, from, until)
        }.toSet()
    }
    val topicBranches = remember(topicIds, topics) { topicIds.map { descendantsOfTopic(it, topics) } }
    val eByClaim = remember(entityLinks) { entityLinks.groupBy { it.assertionId } }
    val tByClaim = remember(topicLinks) { topicLinks.groupBy { it.assertionId } }
    val sourceById = remember(sources) { sources.associateBy { it.id } }
    val entitiesById = remember(entities) { entities.associateBy { it.id } }
    val topicsById = remember(topics) { topics.associateBy { it.id } }
    val matching = remember(assertions, sources, eByClaim, tByClaim, entitiesById, topicsById,
        relevantPlaces, topicBranches, topicMatchAll, timeKey, search, perspective) {
        val needle = search.trim()
        assertions.asSequence().filter { claim ->
            val linkedEntities = eByClaim[claim.id].orEmpty()
            val linkedTopics = tByClaim[claim.id].orEmpty().map { it.topicId }.toSet()
            (selectedSlot == null || selectedSlot.matches(claim.yearStart, claim.yearEnd,
                claim.month, claim.day, claim.periodLabel)) &&
            (relevantPlaces == null || linkedEntities.any {
                it.role == "LOC" && it.entityId in relevantPlaces
            }) &&
            (topicBranches.isEmpty() || if (topicMatchAll) topicBranches.all { branch ->
                linkedTopics.any { it in branch }
            } else topicBranches.any { branch -> linkedTopics.any { it in branch } }) &&
            (needle.isBlank() || claim.passage.contains(needle, ignoreCase = true) ||
                claim.contextNote.contains(needle, ignoreCase = true) ||
                claim.page.contains(needle, ignoreCase = true) ||
                sourceById[claim.sourceId]?.let { src ->
                    src.title.contains(needle, true) || src.author.contains(needle, true) ||
                        src.identifier.contains(needle, true)
                } == true ||
                linkedEntities.any { link -> entitiesById[link.entityId]?.let {
                    it.name.contains(needle, true) || it.aliases.contains(needle, true)
                } == true } ||
                linkedTopics.any { id -> topicsById[id]?.name?.contains(needle, true) == true })
        }.toList().let { filtered ->
            when (perspective) {
                "SPATIU" -> filtered.sortedWith(compareBy({ claim ->
                    eByClaim[claim.id].orEmpty().firstOrNull { it.role == "LOC" }
                        ?.let { entitiesById[it.entityId]?.name }.orEmpty()
                }, { it.yearStart ?: Int.MAX_VALUE }))
                "TEMA" -> filtered.sortedWith(compareBy({ claim ->
                    tByClaim[claim.id].orEmpty().firstOrNull()
                        ?.let { topicsById[it.topicId]?.name }.orEmpty()
                }, { it.yearStart ?: Int.MAX_VALUE }))
                else -> filtered.sortedWith(compareBy({ it.yearStart ?: Int.MAX_VALUE }, { it.month ?: 0 }, { it.day ?: 0 }))
            }
        }
    }
    val historyPlaces = remember(entities) { entities.filter { it.entityType == "PLACE" } }
    val geographicRows = remember(historyPlaces, relations, from, until) {
        placeRows(historyPlaces, relations, from, until)
    }
    val thematicRows = remember(topics) { topicRows(topics) }
    val linkedActorCounts = remember(matching, eByClaim, entitiesById) {
        matching.flatMap { claim -> eByClaim[claim.id].orEmpty()
            .filter { it.role == "ACTOR" || it.role == "SUBJECT" }.map { it.entityId } }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(40)
            .mapNotNull { (id, count) -> entitiesById[id]?.let { it to count } }
    }
    var yearJump by remember { mutableStateOf(anchorYear.toString()) }
    LaunchedEffect(anchorYear) { yearJump = anchorYear.toString() }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("O singură cunoaștere, mai multe perspective", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text("Timp × Spațiu × Tematică · sursele rămân intacte",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(onClick = onDocuments, modifier = Modifier.weight(1f)) {
                        Text("Fotografiază · OCR")
                    }
                    FilledTonalButton(onClick = onStudy, modifier = Modifier.weight(1f)) {
                        Text("Învățare")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = onBands, modifier = Modifier.weight(1f)) {
                        Text("Benzi pe categorii")
                    }
                    OutlinedButton(onClick = onOverlaps, modifier = Modifier.weight(1f)) {
                        Text("Timp × spațiu")
                    }
                }
            }
        }
        item {
            ThreeAxisAtlas(
                assertions = assertions, entities = entities, topics = topics,
                entityLinks = entityLinks, topicLinks = topicLinks, relations = relations,
                slots = availableSlots, level = level, timeKey = timeKey,
                onTimeKey = { key ->
                    onTimeKey(key)
                    TemporalNavigation.fromKey(key)?.from?.let(onAnchorYear)
                },
                onZoom = { direction ->
                    val levels = listOf("EPOCA", "SECOL", "AN", "LUNA", "ZI")
                    val next = (levels.indexOf(level) + direction).coerceIn(0, levels.lastIndex)
                    selectedSlot?.from?.let(onAnchorYear)
                    onLevel(levels[next], null)
                },
                placeIds = placeIds, onPlaceIds = onPlaceIds,
                topicIds = topicIds, onTopicIds = onTopicIds
            )
        }
        item {
            SectionSurface("01 · TIMP", "Rigla continuă, independentă de surse") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("EPOCA", "SECOL", "AN", "LUNA", "ZI")) { current ->
                        FilterChip(selected = level == current, onClick = {
                            selectedSlot?.from?.let(onAnchorYear)
                            selectedSlot?.month?.let(onAnchorMonth)
                            onLevel(current, timeKey)
                        }, label = { Text(when(current) {
                            "EPOCA" -> "Epocă"; "SECOL" -> "Secol"; "AN" -> "An"
                            "LUNA" -> "Lună"; else -> "Zi"
                        }) })
                    }
                }
                if (level != "EPOCA") {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { onAnchorYear(TemporalNavigation.stepAnchor(level, anchorYear,
                            when (level) { "SECOL" -> -7; "AN" -> -9; else -> -1 })) }) {
                            Icon(Icons.Default.ChevronLeft, "Deplasează rigla spre trecut")
                        }
                        OutlinedTextField(value = yearJump, onValueChange = { yearJump = it },
                            modifier = Modifier.weight(1f), singleLine = true,
                            label = { Text("An focal (− î.Hr.)") })
                        TextButton(onClick = { yearJump.trim().toIntOrNull()?.takeIf { it != 0 }?.let(onAnchorYear) },
                            enabled = yearJump.trim().toIntOrNull()?.let { it != 0 } == true) { Text("Salt") }
                        IconButton(onClick = { onAnchorYear(TemporalNavigation.stepAnchor(level, anchorYear,
                            when (level) { "SECOL" -> 7; "AN" -> 9; else -> 1 })) }) {
                            Icon(Icons.Default.ChevronRight, "Deplasează rigla spre viitor")
                        }
                    }
                    if (level == "ZI") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items((1..12).toList()) { month ->
                                FilterChip(selected = anchorMonth == month, onClick = { onAnchorMonth(month) },
                                    label = { Text("Luna $month") })
                            }
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(selected = timeKey == null, onClick = { onTimeKey(null) }, label = { Text("Tot timpul") }) }
                    items(availableSlots, key = { it.key }) { slot ->
                        FilterChip(selected = timeKey == slot.key, onClick = { onTimeKey(slot.key) },
                            label = { Text(slot.label) })
                    }
                }
                if (selectedSlot != null) {
                    Text("Interval activ: ${selectedSlot.label}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                if (availableSlots.isEmpty()) Hint("Adaugă înregistrări cu etichete de epocă. Nivelurile numerice ale riglei rămân navigabile chiar dacă nu există surse.")
            }
        }
        item {
            SectionSurface("02 · SPAȚIU", "Caută și selectează regiunile tale istorice") {
                RegionPicker(
                    places = historyPlaces,
                    selectedIds = placeIds,
                    onSelected = onPlaceIds,
                    onCreatePlace = onCreatePlace
                )
                if (geographicRows.isEmpty()) {
                    Hint("Catalogul este extensibil. Adaugă un loc și documentează separat relațiile geografice datate.")
                } else {
                    Hint("Ierarhiile istorice sunt evaluate în perioada selectată. Regiunile cu același nume nu sunt unificate automat.")
                }
            }
        }
        item {
            SectionSurface("03 · TEMATICĂ", "Straturi multiple + subteme") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(selected = topicIds.isEmpty(), onClick = { onTopicIds(emptySet()) }, label = { Text("Toate temele") }) }
                    items(thematicRows, key = { it.id }) { row ->
                        FilterChip(selected = row.id in topicIds,
                            onClick = { onTopicIds(if (row.id in topicIds) topicIds - row.id else topicIds + row.id) },
                            label = { Text("${"↳ ".repeat(row.depth.coerceAtMost(4))}${row.name}") })
                    }
                }
                if (topicIds.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Combinarea temelor", style = MaterialTheme.typography.labelSmall)
                        FilterChip(selected = !topicMatchAll, onClick = { onTopicMatchAll(false) }, label = { Text("OR") })
                        FilterChip(selected = topicMatchAll, onClick = { onTopicMatchAll(true) }, label = { Text("AND") })
                    }
                }
            }
        }
        item {
            SectionSurface("PERSPECTIVĂ", "Aceleași date, altă ordonare") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("TIMP", "SPATIU", "TEMA")) { p ->
                        FilterChip(selected = perspective == p, onClick = { onPerspective(p) },
                            label = { Text(when (p) { "TIMP" -> "Cronologic"; "SPATIU" -> "Geografic"; else -> "Tematic" }) })
                    }
                }
            }
        }
        if (linkedActorCounts.isNotEmpty()) {
            item {
                SectionSurface("ACTORI ȘI ENTITĂȚI", "Apariții în afirmațiile filtrate") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(linkedActorCounts, key = { it.first.id }) { (actor, count) ->
                            FilterChip(selected = false, onClick = { onSearch(actor.name) },
                                label = { Text("${actor.name} · $count") })
                        }
                    }
                    Hint("Frecvența este calculată din pasaje. Nu reprezintă automat durata vieții sau a domniei.")
                }
            }
        }
        item {
            OutlinedTextField(value = search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Caută în afirmații, comentarii, actori…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text("Intersecția activă", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${matching.size} afirmații", color = MaterialTheme.colorScheme.primary)
            }
        }
        if (matching.isEmpty()) {
            item { EmptyAtlasCard() }
        } else {
            items(matching, key = { it.id }) { item ->
                ElevatedCard(onClick = { onOpen(item.id) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(onClick = { onOpen(item.id) }, label = { Text(item.kind.lowercase().replaceFirstChar(Char::uppercaseChar)) })
                            Text(item.periodLabel.ifBlank {
                                when {
                                    item.yearStart != null && item.yearEnd != null -> "${item.yearStart} – ${item.yearEnd}"
                                    item.yearStart != null -> TimeMath.yearLabel(item.yearStart)
                                    else -> "Dată neprecizată"
                                }
                            }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                        Text(item.passage, maxLines = 4, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge)
                        val src = sourceById[item.sourceId]
                        Text("${src?.author.orEmpty()} · ${src?.title ?: "Sursă indisponibilă"} · p. ${item.page}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionSurface(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable private fun EmptyAtlasCard() {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Atlasul așteaptă documentarea ta", style = MaterialTheme.typography.titleMedium)
            Text("Înregistrează o sursă, apoi introdu o afirmație sau scanează o pagină prin OCR. Filtrele nu inventează date și nu elimină înregistrările originale.",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable fun Hint(message: String) {
    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}