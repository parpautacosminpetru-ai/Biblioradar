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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
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

private val indexBandColors = listOf(
    Color(0xFF2A8177), Color(0xFFB47A2D), Color(0xFF7057B3),
    Color(0xFFBA596C), Color(0xFF397BB8), Color(0xFF839038),
    Color(0xFFAE689C), Color(0xFF997C53), Color(0xFF4C8C9C),
    Color(0xFF91729E), Color(0xFFBB825C), Color(0xFF437F6B)
)

private fun indexColor(rootId: String): Color =
    if (rootId == UNCLASSIFIED_TOPIC_ID) Color(0xFF858B93)
    else indexBandColors[((rootId.hashCode().toLong() and 0x7fffffffL) %
        indexBandColors.size).toInt()]

/**
 * Parallel category lanes are a live projection over the SAME documented claims.
 * Roots are editable user-created indices; descendants are categories/subcategories.
 * Geographic ancestry is reevaluated per time slot, not assumed to be timeless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBandsScreen(
    topics: List<HistoricalTopic>,
    assertions: List<HistoricalAssertion>,
    sources: List<HistoricalSource>,
    entities: List<HistoricalEntity>,
    topicLinks: List<AssertionTopicLink>,
    entityLinks: List<AssertionEntityLink>,
    relations: List<EntityRelation>,
    level: String,
    onLevel: (String, String?) -> Unit,
    anchorYear: Int,
    onAnchorYear: (Int) -> Unit,
    anchorMonth: Int,
    onAnchorMonth: (Int) -> Unit,
    placeIds: Set<String>,
    onPlaceIds: (Set<String>) -> Unit,
    onCreatePlace: () -> Unit,
    onSaveTopic: (HistoricalTopic?, HistoricalTopic) -> Unit,
    onManageTopics: () -> Unit,
    onOpenAssertion: (String) -> Unit
) {
    val levels = remember { listOf("EPOCA", "SECOL", "AN", "LUNA", "ZI") }
    val periodNames = remember(assertions) { assertions.map { it.periodLabel } }
    val slots = remember(level, anchorYear, anchorMonth, periodNames) {
        TemporalNavigation.visible(level, anchorYear, anchorMonth, periodNames) +
            TimeSlice(UNKNOWN_TIME_KEY, "Fără dată")
    }

    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    var expandedLimit by remember { mutableIntStateOf(45) }
    val rows = remember(topics, collapsed) {
        categoryBandRows(topics, collapsed) +
            CategoryBandRow(UNCLASSIFIED_TOPIC_ID, "Fără categorie", UNCLASSIFIED_TOPIC_ID, 0, false)
    }
    val shownRows = rows.take(expandedLimit)
    val model by produceState(CategoryBandIndex(emptyMap(), emptySet()),
        topics, assertions, topicLinks, entityLinks, relations, slots, placeIds) {
        value = withContext(Dispatchers.Default) {
            indexCategoryBands(topics, assertions, topicLinks, entityLinks,
                relations, slots, placeIds)
        }
    }
    val biggestCell = remember(model) { (model.cells.values.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1) }
    val sourceById = remember(sources) { sources.associateBy { it.id } }
    val claimById = remember(assertions) { assertions.associateBy { it.id } }
    val topicById = remember(topics) { topics.associateBy { it.id } }

    var selectedCell by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HistoricalTopic?>(null) }
    var parentForNew by remember { mutableStateOf<String?>(null) }
    fun newTopic(parent: String?) {
        editing = null
        parentForNew = parent
        showEditor = true
    }
    if (showEditor) {
        TopicForm(old = editing, topics = topics,
            initialParentId = parentForNew,
            onDismiss = { showEditor = false }) { item ->
            onSaveTopic(editing, item)
            showEditor = false
        }
    }

    var yearText by remember { mutableStateOf(anchorYear.toString()) }
    LaunchedEffect(anchorYear) { yearText = anchorYear.toString() }
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("Indici și categorii · benzi paralele",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text("Fiecare rând este un indice sau o categorie editabilă. Toate benzile folosesc aceeași axă temporală.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { newTopic(null) }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Indice nou")
            }
            OutlinedButton(onClick = onManageTopics) { Text("Editează arborele") }
        }

        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = {
                val i = levels.indexOf(level)
                if (i > 0) onLevel(levels[i - 1], null)
            }, enabled = levels.indexOf(level) > 0) {
                Icon(Icons.Default.ZoomOut, "Micșorează nivelul temporal")
            }
            Text(when (level) {
                "EPOCA" -> "Epoci"; "SECOL" -> "Secole"; "AN" -> "Ani"
                "LUNA" -> "Luni"; else -> "Zile"
            }, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val i = levels.indexOf(level)
                if (i in 0 until levels.lastIndex) onLevel(levels[i + 1], null)
            }, enabled = levels.indexOf(level) in 0 until levels.lastIndex) {
                Icon(Icons.Default.ZoomIn, "Mărește nivelul temporal")
            }
            IconButton(onClick = {
                onAnchorYear(TemporalNavigation.stepAnchor(level, anchorYear,
                    if (level == "SECOL") -7 else -5))
            }) {
                Icon(Icons.Default.ChevronLeft, "Spre trecut")
            }
            IconButton(onClick = {
                onAnchorYear(TemporalNavigation.stepAnchor(level, anchorYear,
                    if (level == "SECOL") 7 else 5))
            }) {
                Icon(Icons.Default.ChevronRight, "Spre viitor")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(
                value = yearText, onValueChange = { yearText = it },
                singleLine = true, label = { Text("An focal") },
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = {
                yearText.toIntOrNull()?.takeIf { it != 0 }?.let(onAnchorYear)
            }, enabled = yearText.toIntOrNull()?.let { it != 0 } == true) {
                Text("Salt")
            }
            if (level == "ZI") {
                TextButton(onClick = {
                    onAnchorMonth(if (anchorMonth == 12) 1 else anchorMonth + 1)
                }) { Text("Luna $anchorMonth →") }
            }
        }

        Text("SPAȚIU · FILTREAZĂ TOATE REGIUNILE INDEXATE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
        RegionPicker(
            places = entities,
            selectedIds = placeIds,
            onSelected = onPlaceIds,
            onCreatePlace = onCreatePlace,
            compact = true
        )

        Text("BENZI ACTIVE · ${model.visibleAssertionIds.size} afirmații distincte",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
        if (topics.isEmpty()) {
            Text("Adaugă primul indice. Îl poți extinde apoi cu categorii și subcategorii.",
                style = MaterialTheme.typography.bodyMedium)
        }
        if (assertions.isEmpty()) {
            Text("Benzile există independent de surse. Se colorează pe măsură ce indexezi afirmații reale.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // The same vertical scroller holds both fixed labels and the scrollable timeline.
        Row(
            Modifier.fillMaxWidth().weight(1f)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                .verticalScroll(vertical)
        ) {
            Column(Modifier.width(168.dp)) {
                Box(Modifier.fillMaxWidth().height(57.dp).padding(7.dp),
                    contentAlignment = Alignment.CenterStart) {
                    Text("CATEGORIE ↓ / TIMP →",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                shownRows.forEach { band ->
                    val isUnclassified = band.id == UNCLASSIFIED_TOPIC_ID
                    val color = indexColor(band.rootId)
                    Row(
                        modifier = Modifier.fillMaxWidth().height(68.dp)
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color.copy(alpha = if (band.depth == 0) 0.16f else 0.08f)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
                        Spacer(Modifier.width((band.depth * 9).coerceAtMost(36).dp))
                        if (band.hasChildren) {
                            IconButton(modifier = Modifier.size(24.dp), onClick = {
                                collapsed = if (band.id in collapsed) collapsed - band.id
                                    else collapsed + band.id
                            }) {
                                Icon(if (band.id in collapsed) Icons.Default.KeyboardArrowRight
                                    else Icons.Default.KeyboardArrowDown,
                                    "Deschide / închide " + band.label,
                                    modifier = Modifier.size(18.dp))
                            }
                        } else {
                            Spacer(Modifier.width(24.dp))
                        }
                        Text(
                            band.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (band.depth == 0) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).clickable(enabled = !isUnclassified) {
                                editing = topicById[band.id]
                                parentForNew = null
                                showEditor = true
                            }
                        )
                        if (!isUnclassified) {
                            IconButton(modifier = Modifier.size(26.dp),
                                onClick = { newTopic(band.id) }) {
                                Icon(Icons.Default.Add, "Adaugă subcategorie la " + band.label,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).horizontalScroll(horizontal)) {
                Row(Modifier.height(57.dp)) {
                    slots.forEach { slot ->
                        Box(Modifier.width(101.dp).fillMaxHeight().padding(2.dp)
                            .background(MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center) {
                            Text(slot.label, style = MaterialTheme.typography.labelSmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                shownRows.forEach { band ->
                    val color = indexColor(band.rootId)
                    Row {
                        slots.forEach { slot ->
                            val number = model.count(band.id, slot.key)
                            Surface(
                                onClick = { selectedCell = band.id to slot.key },
                                modifier = Modifier.width(101.dp).height(68.dp).padding(2.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = if (number == 0) MaterialTheme.colorScheme.surface
                                    else color.copy(alpha = 0.11f),
                                border = BorderStroke(0.6.dp,
                                    if (number > 0) color.copy(alpha = 0.65f)
                                    else MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(Modifier.fillMaxSize().padding(7.dp),
                                    verticalArrangement = Arrangement.Center) {
                                    Text(if (number == 0) "·" else "$number",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (number > 0) FontWeight.Bold else FontWeight.Normal,
                                        color = if (number > 0) color
                                            else MaterialTheme.colorScheme.outline)
                                    Box(Modifier.fillMaxWidth().height(8.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(color.copy(alpha = 0.12f))) {
                                        if (number > 0) {
                                            Box(Modifier.fillMaxWidth(
                                                (number.toFloat() / biggestCell).coerceIn(0.08f, 1f))
                                                .fillMaxHeight().background(color))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (rows.size > expandedLimit) {
            TextButton(onClick = { expandedLimit += 40 }) {
                Text("Încarcă încă 40 de benzi (${rows.size - expandedLimit} rămase)")
            }
        }
        Text("Rândul-părinte include ramurile sale, fără a duplica afirmațiile în aceeași celulă. " +
            "O afirmație poate exista în mai multe categorii distincte. Atinge o bandă pentru sursele exacte.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val selected = selectedCell
    if (selected != null) {
        val topicName = if (selected.first == UNCLASSIFIED_TOPIC_ID) "Fără categorie"
            else topicById[selected.first]?.name ?: "Indice"
        val timeName = slots.find { it.key == selected.second }?.label ?: "Interval"
        val claims = model.ids(selected.first, selected.second).mapNotNull { claimById[it] }
        ModalBottomSheet(onDismissRequest = { selectedCell = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                .padding(bottom = 26.dp).heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(topicName, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text("$timeName · ${claims.size} afirmații",
                    color = MaterialTheme.colorScheme.primary)
                if (claims.isEmpty()) {
                    Text("Nicio afirmație indexată aici. Poți adăuga ulterior surse și categorii.")
                }
                claims.take(60).forEach { claim ->
                    ElevatedCard(
                        onClick = {
                            selectedCell = null
                            onOpenAssertion(claim.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(claim.passage, maxLines = 5,
                                overflow = TextOverflow.Ellipsis)
                            val source = sourceById[claim.sourceId]
                            Text(
                                (source?.author.orEmpty()) + " · " +
                                    (source?.title ?: "Sursă necunoscută") + " · " +
                                    (if (claim.page.isNotBlank()) "p. " + claim.page
                                    else claim.section),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Deschide pasajul și proveniența →",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (claims.size > 60) Text("Se văd primele 60 de pasaje; apropie zoomul temporal.")
            }
        }
    }
}
