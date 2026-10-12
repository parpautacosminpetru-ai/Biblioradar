package ro.atlasistoric.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.atlasistoric.data.*
import ro.atlasistoric.util.*

/** Visual dimensions: time columns, historically nested places in rows, thematic layers in cells.
 * Every count is a DISTINCT assertion, not a duplicate of the same passage. */
internal data class IntersectionCount(val total: Int, val themes: Map<String, Int>)
internal data class AtlasPlaceRow(val id: String, val name: String, val depth: Int, val children: Boolean)

/** Keep this independent of Compose so that historical overlap rules can be tested. */
internal fun threeAxisCounts(
    assertions: List<HistoricalAssertion>,
    links: List<AssertionEntityLink>, topicsLinks: List<AssertionTopicLink>,
    relations: List<EntityRelation>, topics: List<HistoricalTopic>,
    rowIds: List<String>, slots: List<TimeSlice>, displayTopics: List<HistoricalTopic>
): Map<Pair<String, String>, IntersectionCount> {
    val placesByClaim = links.asSequence().filter { it.role == "LOC" }.groupBy { it.assertionId }
        .mapValues { (_, value) -> value.map { it.entityId }.toSet() }
    val themesByClaim = topicsLinks.groupBy { it.assertionId }
        .mapValues { (_, value) -> value.map { it.topicId }.toSet() }
    val themeDescendants = displayTopics.associate { it.id to descendantsOfTopic(it.id, topics) }
    val result = mutableMapOf<Pair<String, String>, IntersectionCount>()
    for (slot in slots) {
        val inPeriod = assertions.filter { slot.matches(it.yearStart, it.yearEnd, it.month, it.day, it.periodLabel) }
        for (rowId in rowIds) {
            val places = descendantsOfPlace(rowId, relations, slot.from, slot.through)
            val claims = inPeriod.filter { claim -> placesByClaim[claim.id].orEmpty().any { it in places } }
            val layers = themeDescendants.mapValues { (_, branch) ->
                claims.count { claim -> themesByClaim[claim.id].orEmpty().any { it in branch } }
            }
            result[rowId to slot.key] = IntersectionCount(claims.size, layers)
        }
    }
    return result
}

/** The geographic outline changes when the selected temporal interval changes. */
internal fun atlasPlaceOutline(
    entities: List<HistoricalEntity>, relations: List<EntityRelation>,
    from: Int?, through: Int?, expanded: Set<String>
): List<AtlasPlaceRow> {
    val places = entities.filter { it.entityType == "PLACE" }
    val byId = places.associateBy { it.id }
    val active = relations.filter { it.relationType == "PART_OF" &&
        it.fromEntityId in byId && it.toEntityId in byId &&
        TimeMath.relationActive(it.yearStart, it.yearEnd, from, through) }
    val childIds = active.map { it.fromEntityId }.toSet()
    val children = active.groupBy { it.toEntityId }
    val visited = mutableSetOf<String>()
    val rows = mutableListOf<AtlasPlaceRow>()
    fun visit(id: String, depth: Int) {
        if (!visited.add(id)) return
        val place = byId[id] ?: return
        val ownChildren = children[id].orEmpty().map { it.fromEntityId }.distinct()
        rows += AtlasPlaceRow(id, place.name, depth, ownChildren.isNotEmpty())
        if (id in expanded) ownChildren.mapNotNull { byId[it] }.sortedBy { it.name }
            .forEach { visit(it.id, depth + 1) }
    }
    val roots = places.filter { it.id !in childIds }.sortedBy { it.name }
    roots.forEach { visit(it.id, 0) }
    // A region with only cyclic parent links still appears; collapsed children do not.
    if (roots.isEmpty()) places.sortedBy { it.name }.forEach { visit(it.id, 0) }
    return rows
}

private val atlasSwatches = listOf(
    Color(0xFF3278BA), Color(0xFFCC7F2B), Color(0xFF4C9485),
    Color(0xFF9B69B4), Color(0xFFB76778), Color(0xFF8B9552),
    Color(0xFF6D83A1), Color(0xFF8B746D)
)

@Composable
fun ThreeAxisAtlas(
    assertions: List<HistoricalAssertion>, entities: List<HistoricalEntity>,
    topics: List<HistoricalTopic>, entityLinks: List<AssertionEntityLink>,
    topicLinks: List<AssertionTopicLink>, relations: List<EntityRelation>,
    slots: List<TimeSlice>, level: String, timeKey: String?,
    onTimeKey: (String?) -> Unit, onZoom: (Int) -> Unit,
    placeIds: Set<String>, onPlaceIds: (Set<String>) -> Unit,
    topicIds: Set<String>, onTopicIds: (Set<String>) -> Unit
) {
    val locClaims = remember(entityLinks) { entityLinks.filter { it.role == "LOC" }.map { it.assertionId }.toSet() }
    val hasRecords = remember(assertions, locClaims) { assertions.any { it.id in locClaims } }
    // Presentation-only demo. Never saved, exported, or returned among documented assertions.
    var demoOverride by remember { mutableStateOf<Boolean?>(null) }
    val demo = demoOverride ?: !hasRecords
    val selectedSlot = remember(timeKey) { TemporalNavigation.fromKey(timeKey) }
    val realRootThemes = remember(topics) {
        topics.filter { topic -> topic.parentId == null || topics.none { it.id == topic.parentId } }
            .sortedBy { it.name }
    }
    var themeWindow by remember { mutableIntStateOf(0) }
    val visibleThemes = remember(realRootThemes, themeWindow) {
        realRootThemes.drop(themeWindow.coerceIn(0, (realRootThemes.size - 6).coerceAtLeast(0))).take(6)
    }
    val examplePlaces = remember { listOf(
        HistoricalEntity(id = "example:europa", name = "Europa (exemplu)", entityType = "PLACE"),
        HistoricalEntity(id = "example:iberia", name = "Peninsula Iberică", entityType = "PLACE"),
        HistoricalEntity(id = "example:castilia", name = "Castilia", entityType = "PLACE"),
        HistoricalEntity(id = "example:italia", name = "Italia", entityType = "PLACE"),
        HistoricalEntity(id = "example:toledo", name = "Toledo", entityType = "PLACE")
    ) }
    val exampleRelations = remember { listOf(
        EntityRelation(fromEntityId = "example:iberia", toEntityId = "example:europa", relationType = "PART_OF"),
        EntityRelation(fromEntityId = "example:italia", toEntityId = "example:europa", relationType = "PART_OF"),
        EntityRelation(fromEntityId = "example:castilia", toEntityId = "example:iberia", relationType = "PART_OF"),
        EntityRelation(fromEntityId = "example:toledo", toEntityId = "example:castilia", relationType = "PART_OF")
    ) }
    val demoThemes = remember { listOf(
        HistoricalTopic(id = "example:politic", name = "Politic"),
        HistoricalTopic(id = "example:religios", name = "Religios"),
        HistoricalTopic(id = "example:economic", name = "Economic")
    ) }
    val selectedThemes = if (demo) demoThemes else visibleThemes
    val usedPlaces = if (demo) examplePlaces else entities
    val usedRelations = if (demo) exampleRelations else relations
    val allRoots = remember(usedPlaces, usedRelations, selectedSlot) {
        atlasPlaceOutline(usedPlaces, usedRelations, selectedSlot?.from, selectedSlot?.through, emptySet())
    }
    var expanded by remember(demo) { mutableStateOf(if (demo) setOf("example:europa", "example:iberia", "example:castilia") else allRoots.take(3).map { it.id }.toSet()) }
    val outline = remember(usedPlaces, usedRelations, selectedSlot, expanded) {
        atlasPlaceOutline(usedPlaces, usedRelations, selectedSlot?.from, selectedSlot?.through, expanded)
    }
    var displayed by remember { mutableIntStateOf(9) }
    val rows = outline.take(displayed)
    val matrix by produceState<Map<Pair<String, String>, IntersectionCount>>(
        emptyMap(), demo, assertions, entityLinks, topicLinks, relations, topics, rows, slots, selectedThemes
    ) {
        value = if (demo) emptyMap() else withContext(Dispatchers.Default) {
            threeAxisCounts(assertions, entityLinks, topicLinks, relations, topics,
                rows.map { it.id }, slots, selectedThemes)
        }
    }
    var demoFocus by remember { mutableStateOf<Pair<String, String>?>(null) }
    var demoTheme by remember { mutableStateOf<String?>(null) }
    val axisScroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(level, slots) {
        val center = (slots.size / 2 - 1).coerceAtLeast(0)
        axisScroll.scrollTo(with(density) { (center * 135).dp.roundToPx() })
    }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("INTERSECȚII LIVE · TIMP × SPAȚIU × TEMĂ", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Coloane = timp, rânduri = teritorii imbricate, culori = straturi tematice. O afirmație nu este duplicată.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !demo, onClick = { demoOverride = false }, label = { Text("Datele mele") })
                FilterChip(selected = demo, onClick = { demoOverride = true }, label = { Text("Exemplu vizual") })
            }
            if (demo) {
                Text("SIMULARE — culorile și numerele sunt ilustrative; nimic nu este salvat în cercetare.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ZOOM TEMPORAL · ${when(level) { "EPOCA" -> "epocă"; "SECOL" -> "secol"; "AN" -> "an"; "LUNA" -> "lună"; else -> "zi" }}",
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = { onZoom(-1) }) { Icon(Icons.Default.Remove, "Depărtează timpul") }
                Spacer(Modifier.width(6.dp))
                FilledTonalIconButton(onClick = { onZoom(1) }) { Icon(Icons.Default.Add, "Apropie timpul") }
            }
            Text("STRATURI TEMATICE — atinge o culoare dintr-o celulă pentru filtrare",
                style = MaterialTheme.typography.labelSmall)
            if (selectedThemes.isEmpty()) {
                Text("Adaugă teme pentru a suprapune straturile tematice.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    selectedThemes.chunked(3).forEach { group ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            group.forEach { t ->
                                val index = if (demo) demoThemes.indexOf(t) else realRootThemes.indexOf(t)
                                val swatch = atlasSwatches[index.coerceAtLeast(0) % atlasSwatches.size]
                                Surface(
                                    onClick = { if (!demo) onTopicIds(if (t.id in topicIds) topicIds - t.id else topicIds + t.id)
                                        else demoTheme = t.id },
                                    shape = RoundedCornerShape(7.dp),
                                    border = BorderStroke(if ((if (demo) demoTheme == t.id else t.id in topicIds)) 2.dp else 1.dp,
                                        if ((if (demo) demoTheme == t.id else t.id in topicIds)) swatch else MaterialTheme.colorScheme.outlineVariant),
                                    color = swatch.copy(alpha = 0.12f), modifier = Modifier.weight(1f)
                                ) {
                                    Row(Modifier.padding(horizontal = 6.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Box(Modifier.size(10.dp).background(swatch, CircleShape))
                                        Text(t.name, style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!demo && realRootThemes.size > 6) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { themeWindow = (themeWindow - 6).coerceAtLeast(0) }, enabled = themeWindow > 0) {
                        Text("Teme anterioare")
                    }
                    TextButton(onClick = { themeWindow = (themeWindow + 6).coerceAtMost(realRootThemes.size - 6) },
                        enabled = themeWindow + 6 < realRootThemes.size) { Text("Alte teme") }
                }
            }
            if (slots.isEmpty()) {
                Text("Selectează un nivel temporal sau adaugă epoci pentru a afișa coloanele.",
                    style = MaterialTheme.typography.bodySmall)
            } else if (rows.isEmpty()) {
                Text("Nu există încă regiuni. Adaugă un loc în fila Actori, apoi o afirmație legată de el.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Row {
                    Column(Modifier.width(128.dp)) {
                        Box(Modifier.fillMaxWidth().height(53.dp), contentAlignment = Alignment.CenterStart) {
                            Text("SPAȚIU ↓ / TIMP →", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold)
                        }
                        rows.forEach { row ->
                            val selected = if (demo) demoFocus?.first == row.id else row.id in placeIds
                            Row(Modifier.fillMaxWidth().height(78.dp)
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else Color.Transparent, RoundedCornerShape(7.dp))
                                .clickable {
                                    if (row.children) expanded = if (row.id in expanded) expanded - row.id else expanded + row.id
                                    else if (!demo) onPlaceIds(setOf(row.id))
                                    else demoFocus = row.id to (demoFocus?.second ?: slots[slots.size / 2].key)
                                }, verticalAlignment = Alignment.CenterVertically) {
                                Text(if (row.children) { if (row.id in expanded) "▾" else "▸" } else "·",
                                    modifier = Modifier.width(17.dp), style = MaterialTheme.typography.bodyMedium)
                                Column(Modifier.padding(start = (row.depth * 7).coerceAtMost(21).dp)) {
                                    Text(row.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium)
                                    if (row.children) Text("deschide / închide", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f).horizontalScroll(axisScroll)) {
                        Row(Modifier.height(53.dp)) {
                            slots.forEach { slot ->
                                Box(Modifier.width(135.dp).fillMaxHeight().padding(2.dp)
                                    .background(if (slot.key == timeKey) MaterialTheme.colorScheme.primaryContainer else
                                        MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                                    .clickable { if (!demo) onTimeKey(slot.key) else demoFocus =
                                        (demoFocus?.first ?: rows.first().id) to slot.key },
                                    contentAlignment = Alignment.Center) {
                                    Text(slot.label, style = MaterialTheme.typography.labelMedium,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        rows.forEachIndexed { rowIndex, row ->
                            Row {
                                slots.forEachIndexed { columnIndex, slot ->
                                    val cell = if (demo) {
                                        val counts = selectedThemes.mapIndexed { index, t ->
                                            t.id to (if ((rowIndex + columnIndex + index) % 4 == 0) 0 else
                                                ((rowIndex + columnIndex * 2 + index) % 3 + 1))
                                        }.toMap()
                                        IntersectionCount(counts.values.maxOrNull() ?: 0, counts)
                                    } else matrix[row.id to slot.key] ?: IntersectionCount(0, emptyMap())
                                    val focused = if (demo) demoFocus == (row.id to slot.key)
                                        else (row.id in placeIds && timeKey == slot.key)
                                    val hasValue = cell.total > 0
                                    Surface(
                                        onClick = { if (demo) demoFocus = row.id to slot.key else {
                                            onTimeKey(slot.key); onPlaceIds(setOf(row.id))
                                        } },
                                        color = when { focused -> MaterialTheme.colorScheme.primaryContainer
                                            hasValue -> MaterialTheme.colorScheme.surface
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .38f) },
                                        border = BorderStroke(if (focused) 2.dp else 1.dp,
                                            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.width(135.dp).height(78.dp).padding(3.dp)
                                            .semantics { contentDescription =
                                                "${row.name}, ${slot.label}: ${cell.total} afirmații distincte" }
                                    ) {
                                        Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text(if (cell.total == 0) "·" else "${cell.total} afirmații",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold)
                                            selectedThemes.chunked(3).forEach { group ->
                                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    group.forEach { topic ->
                                                        val count = cell.themes[topic.id] ?: 0
                                                        val paletteIndex = if (demo) demoThemes.indexOf(topic) else realRootThemes.indexOf(topic)
                                                        val color = atlasSwatches[paletteIndex.coerceAtLeast(0) % atlasSwatches.size]
                                                        Surface(onClick = {
                                                            if (demo) { demoFocus = row.id to slot.key; demoTheme = topic.id }
                                                            else { onTimeKey(slot.key); onPlaceIds(setOf(row.id)); onTopicIds(setOf(topic.id)) }
                                                        }, modifier = Modifier.weight(1f).height(19.dp),
                                                            color = if (count > 0) color else color.copy(alpha = .10f),
                                                            shape = RoundedCornerShape(4.dp)) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Text(if (count == 0) "" else count.toString(),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = if (count > 0) Color.White else color)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (outline.size > displayed) {
                    TextButton(onClick = { displayed += 10 }) {
                        Text("Mai multe regiuni (${outline.size - displayed} rămase)")
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            if (demo) {
                Text("Exemplul arată doar cum interacționează cele trei axe. Alege «Datele mele» ca să vezi exclusiv documentarea ta.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Totalul din celulă numără afirmațiile distincte. Sumele pe culori pot depăși totalul: o afirmație poate avea mai multe teme.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Atinge celula pentru perioada și regiunea ei; atinge o bandă colorată pentru a activa și tema. Afirmațiile și sursele filtrate apar mai jos.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}