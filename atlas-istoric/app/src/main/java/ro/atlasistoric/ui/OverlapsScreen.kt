package ro.atlasistoric.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.atlasistoric.data.*
import ro.atlasistoric.util.*

private val layerColors = listOf(
    Color(0xFF368C81), Color(0xFFCE9B4E), Color(0xFF8467B2),
    Color(0xFFCE6F77), Color(0xFF5A86BC), Color(0xFF899D52),
    Color(0xFFB9789C), Color(0xFF8B8064)
)
private val unknownLayer = Color(0xFF9A9FA5)
private val zoomLevels = listOf("EPOCA", "SECOL", "AN", "LUNA", "ZI")

private fun colorForRoot(root: String, rootOrder: List<String>): Color {
    val index = rootOrder.indexOf(root)
    return layerColors[if (index >= 0) index % layerColors.size else
        ((root.hashCode().toLong() and 0x7fffffffL) % layerColors.size).toInt()]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlapsScreen(
    assertions: List<HistoricalAssertion>,
    entities: List<HistoricalEntity>,
    topics: List<HistoricalTopic>,
    sources: List<HistoricalSource>,
    entityLinks: List<AssertionEntityLink>,
    topicLinks: List<AssertionTopicLink>,
    relations: List<EntityRelation>,
    level: String,
    onLevel: (String, String?) -> Unit,
    anchorYear: Int,
    onAnchorYear: (Int) -> Unit,
    anchorMonth: Int,
    onAnchorMonth: (Int) -> Unit,
    timeKey: String?,
    onTimeKey: (String?) -> Unit,
    placeIds: Set<String>,
    onPlaceIds: (Set<String>) -> Unit,
    topicIds: Set<String>,
    onTopicIds: (Set<String>) -> Unit,
    topicMatchAll: Boolean,
    onTopicMatchAll: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onBackToAtlas: () -> Unit
) {
    val periods = remember(assertions) { assertions.map { it.periodLabel } }
    val slots = remember(level, anchorYear, anchorMonth, periods) {
        TemporalNavigation.visible(level, anchorYear, anchorMonth, periods) +
            TimeSlice(UNKNOWN_TIME_KEY, "Fără dată")
    }
    val focusYear = remember(anchorYear, timeKey) {
        TemporalNavigation.fromKey(timeKey)?.from ?: anchorYear
    }
    val baseRows = remember(entities, relations, focusYear) {
        buildPlaceBranches(entities.filter { it.entityType == "PLACE" },
            relations, focusYear, focusYear)
    }
    val rootTopics = remember(topics) {
        val byId = topics.associateBy { it.id }
        topics.filter { rootTopic(it.id, byId) == it.id }.sortedBy { it.name }
    }
    val rootOrder = remember(rootTopics) { rootTopics.map { it.id } }
    // Rows are an onscreen projection, while cell content is computed against EACH time column.
    val allRows = remember(baseRows) {
        baseRows + PlaceBranch(UNKNOWN_PLACE_ID, "Fără loc explicit", 0,
            listOf(UNKNOWN_PLACE_ID), false)
    }
    val overlap by produceState(
        initialValue = OverlapIndex(emptyMap(), emptySet(), 0, 0),
        slots, allRows, assertions, entityLinks, topicLinks, relations, topics, topicIds, topicMatchAll
    ) {
        value = withContext(Dispatchers.Default) {
            buildOverlapIndex(slots, allRows, assertions, entityLinks, topicLinks,
                relations, topics, topicIds, topicMatchAll)
        }
    }

    var closed by remember { mutableStateOf(emptySet<String>()) }
    var allBranchesVisible by remember { mutableStateOf(false) }
    var selectedCell by remember { mutableStateOf<Pair<String, String>?>(null) }
    var jumpYear by remember { mutableStateOf(anchorYear.toString()) }
    LaunchedEffect(anchorYear) { jumpYear = anchorYear.toString() }

    val subsetRows = remember(allRows, closed, placeIds, relations, focusYear) {
        val allowed = if (placeIds.isEmpty()) null else placeIds.flatMap {
            descendantsOfPlace(it, relations, focusYear, focusYear)
        }.toSet()
        allRows.filter { row ->
            row.path.dropLast(1).none { it in closed } &&
                (allowed == null || row.id in allowed ||
                    row.path.any { it in placeIds } || row.id == UNKNOWN_PLACE_ID)
        }
    }
    val shownRows = if (allBranchesVisible) subsetRows else subsetRows.take(28)
    val sourceById = remember(sources) { sources.associateBy { it.id } }
    val claimById = remember(assertions) { assertions.associateBy { it.id } }
    val themesByClaim = remember(topicLinks) { topicLinks.groupBy { it.assertionId } }
    val topicById = remember(topics) { topics.associateBy { it.id } }
    val maxCount = remember(overlap) { overlap.cells.values.maxOfOrNull { it.count } ?: 0 }
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Intersecțiile cunoașterii", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text("Timp → pe orizontală · Spațiu → pe verticală · Temele → benzi colorate",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val i = zoomLevels.indexOf(level)
                if (i > 0) onLevel(zoomLevels[i - 1], null)
            }, enabled = zoomLevels.indexOf(level) > 0) {
                Icon(Icons.Default.ZoomOut, "Micșorează nivelul temporal")
            }
            Text(when (level) { "EPOCA" -> "Epoci"; "SECOL" -> "Secole"; "AN" -> "Ani";
                "LUNA" -> "Luni"; else -> "Zile" },
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = {
                val i = zoomLevels.indexOf(level)
                if (i in 0 until zoomLevels.lastIndex) onLevel(zoomLevels[i + 1], null)
            }, enabled = zoomLevels.indexOf(level) in 0 until zoomLevels.lastIndex) {
                Icon(Icons.Default.ZoomIn, "Mărește nivelul temporal")
            }
            IconButton(onClick = { onAnchorYear(TemporalNavigation.stepAnchor(level, anchorYear,
                if (level == "SECOL") -7 else -5)) }) {
                Icon(Icons.Default.ChevronLeft, "Rigla spre trecut")
            }
            IconButton(onClick = { onAnchorYear(TemporalNavigation.stepAnchor(level, anchorYear,
                if (level == "SECOL") 7 else 5)) }) {
                Icon(Icons.Default.ChevronRight, "Rigla spre viitor")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = jumpYear, onValueChange = { jumpYear = it },
                modifier = Modifier.weight(1f), label = { Text("An focal") },
                singleLine = true)
            OutlinedButton(onClick = {
                jumpYear.toIntOrNull()?.takeIf { it != 0 }?.let {
                    onAnchorYear(it); onTimeKey(null)
                }
            }, enabled = jumpYear.toIntOrNull()?.let { it != 0 } == true) {
                Text("Salt")
            }
            if (level == "ZI") {
                TextButton(onClick = { onAnchorMonth(if (anchorMonth == 12) 1 else anchorMonth + 1) }) {
                    Text("Luna $anchorMonth ›")
                }
            }
        }
        if (rootTopics.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("STRATURI TEMATICE", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { onTopicIds(emptySet()) }) {
                    Text("Toate")
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rootTopics.forEach { topic ->
                    FilterChip(selected = topic.id in topicIds,
                        onClick = {
                            onTopicIds(if (topic.id in topicIds) topicIds - topic.id
                                else topicIds + topic.id)
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(9.dp).clip(RoundedCornerShape(4.dp))
                                    .background(colorForRoot(topic.id, rootOrder)))
                                Text(topic.name, maxLines = 1)
                            }
                        })
                }
                FilterChip(selected = topicMatchAll,
                    onClick = { onTopicMatchAll(!topicMatchAll) },
                    label = { Text(if (topicMatchAll) "Teme: ȘI" else "Teme: SAU") })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("${overlap.visibleAssertionIds.size} afirmații afișate",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text("${overlap.undatedCount} fără dată",
                style = MaterialTheme.typography.labelSmall)
            Text("${overlap.unlocatedCount} fără loc",
                style = MaterialTheme.typography.labelSmall)
        }
        if (assertions.isEmpty()) {
            Surface(shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer) {
                Text("Atlasul este gol. Creează o sursă și o afirmație cu loc, timp și temă. " +
                    "Aici vei vedea suprapunerile reale, fără evenimente inventate.",
                    modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Atinge o celulă pentru pasajele și paginile surselor. Atinge săgețile regiunilor pentru nesting.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // A single vertical scroller keeps location labels aligned with time cells.
        // Only the time area scrolls horizontally: location names remain visible.
        Row(Modifier.fillMaxWidth().weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .verticalScroll(vertical)) {
            Column(Modifier.width(150.dp)) {
                Box(Modifier.height(59.dp).fillMaxWidth().padding(9.dp),
                    contentAlignment = Alignment.CenterStart) {
                    Text("SPAȚIU ↓ / TIMP →", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                shownRows.forEach { branch ->
                    val selected = branch.id in placeIds
                    Row(Modifier.fillMaxWidth().height(65.dp)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                        .clickable {
                            if (branch.id == UNKNOWN_PLACE_ID) onPlaceIds(emptySet())
                            else onPlaceIds(if (selected) emptySet() else setOf(branch.id))
                        }
                        .padding(start = (2 + 7 * branch.depth.coerceAtMost(6)).dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (branch.expandable) {
                            IconButton(modifier = Modifier.size(27.dp),
                                onClick = {
                                    closed = if (branch.id in closed) closed - branch.id
                                    else closed + branch.id
                                }) {
                                Icon(if (branch.id in closed) Icons.Default.KeyboardArrowRight
                                    else Icons.Default.KeyboardArrowDown,
                                    "Extinde sau restrânge ${branch.name}",
                                    modifier = Modifier.size(17.dp))
                            }
                        } else {
                            Spacer(Modifier.width(27.dp))
                        }
                        Text(branch.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    HorizontalDivider(thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Column(Modifier.weight(1f).horizontalScroll(horizontal)) {
                Row {
                    slots.forEach { slot ->
                        Box(Modifier.width(88.dp).height(59.dp).padding(3.dp),
                            contentAlignment = Alignment.Center) {
                            Text(slot.label, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
                shownRows.forEach { branch ->
                    Row {
                        slots.forEach { slot ->
                            val cell = overlap.cell(branch.id, slot.key)
                            val intensity = if (maxCount <= 0) 0f
                                else cell.count.toFloat() / maxCount.toFloat()
                            val active = selectedCell == (branch.id to slot.key)
                            Surface(
                                onClick = { selectedCell = branch.id to slot.key },
                                modifier = Modifier.width(88.dp).height(65.dp).padding(3.dp),
                                border = BorderStroke(if (active) 2.dp else 0.6.dp,
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant),
                                color = if (cell.count == 0) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.primaryContainer
                                        .copy(alpha = 0.25f + 0.70f * intensity),
                                shape = RoundedCornerShape(9.dp)
                            ) {
                                Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (cell.count == 0) "·" else cell.count.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (cell.count > 0) FontWeight.Bold else FontWeight.Normal,
                                        color = if (cell.count > 0) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.outline)
                                    if (cell.count > 0) {
                                        Row(Modifier.fillMaxWidth().height(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            cell.themes.take(5).forEach { band ->
                                                Box(Modifier.weight(band.assertionCount.toFloat())
                                                    .fillMaxHeight().clip(RoundedCornerShape(2.dp))
                                                    .background(colorForRoot(band.rootTopicId, rootOrder)))
                                            }
                                            if (cell.withoutTopic > 0) {
                                                Box(Modifier.weight(cell.withoutTopic.toFloat())
                                                    .fillMaxHeight().clip(RoundedCornerShape(2.dp))
                                                    .background(unknownLayer))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        if (subsetRows.size > 28) {
            TextButton(onClick = { allBranchesVisible = !allBranchesVisible }) {
                Text(if (allBranchesVisible) "Arată mai puține regiuni"
                    else "Arată toate cele ${subsetRows.size} ramuri")
            }
        }
        Text("O afirmație poate apărea în mai multe straturi sau ierarhii, " +
            "dar se numără o singură dată în fiecare celulă. Culorile indică temele, " +
            "nu certitudinea informației. Nestingul afișat ține de anul focal.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp))
    }

    val selected = selectedCell
    if (selected != null) {
        val place = allRows.firstOrNull { it.id == selected.first }
        val slot = slots.firstOrNull { it.key == selected.second }
        val cell = overlap.cell(selected.first, selected.second)
        val shown = cell.assertionIds.mapNotNull { claimById[it] }
        ModalBottomSheet(onDismissRequest = { selectedCell = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 26.dp)
                .heightIn(max = 570.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Intersecția documentară", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text("${place?.name.orEmpty()} · ${slot?.label.orEmpty()}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium)
                Text("${cell.count} afirmații distincte · ${cell.themes.size} straturi tematice",
                    style = MaterialTheme.typography.bodySmall)
                if (cell.count == 0) {
                    Text("Nu există încă afirmații indexate la această intersecție. " +
                        "Celula goală nu înseamnă că nu s-a petrecut nimic în istorie.")
                }
                cell.themes.forEach { band ->
                    val name = topicById[band.rootTopicId]?.name ?: "Temă fără denumire"
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp))
                            .background(colorForRoot(band.rootTopicId, rootOrder)))
                        Text("$name: ${band.assertionCount}", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (cell.withoutTopic > 0) {
                    Text("Fără clasificare tematică: ${cell.withoutTopic}",
                        style = MaterialTheme.typography.labelMedium)
                }
                shown.take(50).forEach { claim ->
                    ElevatedCard(onClick = { selectedCell = null; onOpen(claim.id) },
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(claim.passage, maxLines = 5, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium)
                            val src = sourceById[claim.sourceId]
                            Text("${src?.author.orEmpty()} · ${src?.title ?: "Sursă necunoscută"} " +
                                "· ${if (claim.page.isNotBlank()) "p. ${claim.page}" else claim.section}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                            val names = themesByClaim[claim.id].orEmpty()
                                .mapNotNull { topicById[it.topicId]?.name }.distinct()
                            if (names.isNotEmpty()) Text(names.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Deschide pasajul și proveniența →",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (shown.size > 50) {
                    Text("Afișate primele 50 de afirmații; restrânge intervalul pentru detalii.")
                }
                if (selected.first != UNKNOWN_PLACE_ID && selected.second != UNKNOWN_TIME_KEY) {
                    OutlinedButton(onClick = {
                        onPlaceIds(setOf(selected.first))
                        onTimeKey(selected.second)
                        selectedCell = null
                        onBackToAtlas()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Explorează intersecția în Atlas")
                    }
                }
            }
        }
    }
}
