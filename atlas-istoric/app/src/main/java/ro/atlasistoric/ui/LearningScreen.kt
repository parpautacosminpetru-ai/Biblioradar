package ro.atlasistoric.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ro.atlasistoric.data.*
import ro.atlasistoric.util.*
import java.text.DateFormat
import java.util.Date

/** A recall/review layer over the single historical source-of-truth database. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningScreen(
    cards: List<StudyCard>,
    reviews: List<StudyReview>,
    assertions: List<HistoricalAssertion>,
    sources: List<HistoricalSource>,
    entities: List<HistoricalEntity>,
    topics: List<HistoricalTopic>,
    entityLinks: List<AssertionEntityLink>,
    topicLinks: List<AssertionTopicLink>,
    relations: List<EntityRelation>,
    selectedTimeKey: String?,
    selectedPlaceIds: Set<String>,
    selectedTopicIds: Set<String>,
    topicMatchAll: Boolean,
    openClaimId: String?,
    onConsumedOpenClaim: () -> Unit,
    saveCard: suspend (StudyCard?, StudyCard) -> Unit,
    rateCard: suspend (String, Int) -> Unit,
    onOpenClaim: (String) -> Unit,
    onExploreClaim: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val sourceById = remember(sources) { sources.associateBy { it.id } }
    val claimById = remember(assertions) { assertions.associateBy { it.id } }
    val cardIds = remember(cards, assertions, entityLinks, topicLinks, relations, topics,
        selectedTimeKey, selectedPlaceIds, selectedTopicIds, topicMatchAll) {
        learningFilteredCardIds(cards, assertions, entityLinks, topicLinks, relations, topics,
            selectedTimeKey, selectedPlaceIds, selectedTopicIds, topicMatchAll)
    }

    var useAtlasFilters by remember { mutableStateOf(false) }
    val inSelection = if (useAtlasFilters) cards.filter { it.id in cardIds } else cards
    val now = System.currentTimeMillis()
    val available = inSelection.filter { !it.suspended }
    val due = available.filter { it.dueAt <= now }.sortedWith(compareBy({ it.dueAt }, { it.createdAt }))
    val studiedRecently = reviews.count { it.reviewedAt in (now - 7L * RecallScheduler.DAY_MS)..now }
    val growing = cards.count { it.intervalDays >= 14 && !it.suspended }

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StudyCard?>(null) }
    var newForClaim by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(false) }
    var draftAnswer by remember { mutableStateOf("") }
    val skipped = remember { mutableStateListOf<String>() }
    val current = due.firstOrNull { it.id !in skipped }
    LaunchedEffect(current?.id) {
        reveal = false
        draftAnswer = ""
    }
    LaunchedEffect(openClaimId) {
        if (openClaimId != null) {
            editing = null
            newForClaim = openClaimId
            showEditor = true
            onConsumedOpenClaim()
        }
    }

    if (showEditor) {
        StudyCardEditor(
            old = editing, initialAssertionId = newForClaim,
            assertions = assertions, sources = sources, entities = entities,
            links = entityLinks, onDismiss = {
                showEditor = false
                newForClaim = null
            },
            onSave = { old, card -> saveCard(old, card) },
            onSuccess = {
                showEditor = false
                newForClaim = null
            }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.School, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp))
            Column {
                Text("Învățare din surse", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Text("Aceeași cunoaștere. Exersare separată de cercetare.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Întrebări proprii, recuperare din memorie și repetare la intervale. " +
            "Răspunsurile sunt citite din afirmațiile documentate, nu generate ca fapte noi.",
            style = MaterialTheme.typography.bodySmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                editing = null
                newForClaim = null
                showEditor = true
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Întrebare nouă")
            }
            OutlinedButton(onClick = { useAtlasFilters = !useAtlasFilters }) {
                Text(if (useAtlasFilters) "Filtre: Atlas" else "Toate temele")
            }
        }
        if (useAtlasFilters) {
            Text("Se aplică perioada, regiunile și temele selectate în Atlas. " +
                "Informațiile cu dată incompletă nu primesc artificial o zi precisă.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LearningStat("Întrebări", "${cards.size}", Modifier.weight(1f))
            LearningStat("De repetat", "${due.size}", Modifier.weight(1f))
            LearningStat("Repetări 7 zile", "$studiedRecently", Modifier.weight(1f))
        }
        Text("${growing} fișe au interval de recapitulare de minimum 14 zile.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()

        if (cards.isEmpty()) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Începe dintr-o afirmație documentată",
                        style = MaterialTheme.typography.titleMedium)
                    Text("Nu trebuie să transcrii din nou sursa. Alegi o afirmație din atlas, " +
                        "scrii întrebarea, iar răspunsul este legat permanent de pasaj și de pagină.",
                        style = MaterialTheme.typography.bodyMedium)
                    if (assertions.isEmpty()) {
                        Text("Atlasul nu are încă afirmații. Adaugă mai întâi o sursă și un pasaj.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Text("RECAPITULARE ACTIVĂ", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            if (current == null) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nicio fișă scadentă în această selecție.",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Poți continua să citești, să adaugi întrebări sau să alegi alte teme. " +
                            "Fișele revizuite reapar la intervalul programat.",
                            style = MaterialTheme.typography.bodySmall)
                        if (useAtlasFilters) OutlinedButton(onClick = { useAtlasFilters = false }) {
                            Text("Arată toate fișele")
                        }
                    }
                }
            } else {
                val claim = claimById[current.assertionId]
                val src = claim?.let { sourceById[it.sourceId] }
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("ÎNTREBARE · ${due.size} scadente",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium)
                        Text(current.question, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium)
                        if (current.note.isNotBlank()) {
                            Text("Indiciu personal: ${current.note}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedTextField(
                            value = draftAnswer,
                            onValueChange = { draftAnswer = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2, maxLines = 5,
                            label = { Text("Încearcă să răspunzi din memorie (nu se salvează)") }
                        )
                        if (!reveal) {
                            Button(onClick = { reveal = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Arată răspunsul din sursă")
                            }
                        } else if (claim != null) {
                            HorizontalDivider()
                            Text("RĂSPUNS DOCUMENTAT", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Text(learningAnswer(current.answerMode, claim, entities, entityLinks),
                                style = MaterialTheme.typography.bodyLarge)
                            if (current.answerMode != "PASAJ") {
                                Text("Pasajul: ${claim.passage}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${src?.author.orEmpty()} · ${src?.title ?: "Sursă fără titlu"}" +
                                " · ${if (claim.page.isNotBlank()) "p. " + claim.page else claim.section}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                            if (claim.updatedAt > current.createdAt) {
                                Text("Pasajul a fost modificat după crearea acestei întrebări. " +
                                    "Verifică dacă formularea ei mai este potrivită.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                            OutlinedButton(onClick = { onOpenClaim(claim.id) }) {
                                Icon(Icons.Default.AutoStories, contentDescription = null)
                                Spacer(Modifier.width(5.dp))
                                Text("Vezi sursa și contextul")
                            }
                            Text("Cum ți-ai amintit? Aceasta este autoevaluare, nu verificare automată.",
                                style = MaterialTheme.typography.bodySmall)
                            if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                            listOf(
                                RecallScheduler.AGAIN to "Din nou · 15 min",
                                RecallScheduler.HARD to "Greu",
                                RecallScheduler.GOOD to "Bine",
                                RecallScheduler.EASY to "Ușor"
                            ).chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    pair.forEach { (grade, label) ->
                                        OutlinedButton(
                                            onClick = {
                                                if (working) return@OutlinedButton
                                                working = true
                                                error = null
                                                scope.launch {
                                                    try {
                                                        rateCard(current.id, grade)
                                                        skipped.add(current.id)
                                                        reveal = false
                                                        draftAnswer = ""
                                                    } catch (e: Exception) {
                                                        error = e.localizedMessage ?: "Nu s-a înregistrat recapitularea"
                                                    } finally {
                                                        working = false
                                                    }
                                                }
                                            },
                                            enabled = !working,
                                            modifier = Modifier.weight(1f)
                                        ) { Text(label, maxLines = 2) }
                                    }
                                }
                            }
                        }
                        TextButton(onClick = { skipped.add(current.id) }) {
                            Text("Sari peste această întrebare")
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Text("FIȘELE MELE · ${cards.size}", style = MaterialTheme.typography.titleMedium)
        Text("Poți schimba întrebarea, adăuga observații ori suspenda temporar o fișă. " +
            "Sursa și istoricul încercărilor rămân păstrate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (inSelection.isEmpty() && cards.isNotEmpty()) {
            Text("Nicio fișă corespunde selecției actuale din Atlas.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        inSelection.sortedWith(compareBy({ it.dueAt }, { it.createdAt })).take(30).forEach { card ->
            val claim = claimById[card.assertionId]
            val src = claim?.let { sourceById[it.sourceId] }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(card.question, fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium)
                    Text("${src?.title ?: "Sursă indisponibilă"} · " +
                        "${if (card.suspended) "Suspendată"
                        else if (card.dueAt <= now) "Scadentă"
                        else "Revizuire: " + DateFormat.getDateInstance().format(Date(card.dueAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        TextButton(onClick = {
                            editing = card
                            newForClaim = null
                            showEditor = true
                        }) {
                            Icon(Icons.Default.Edit, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Editează")
                        }
                        if (claim != null) TextButton(onClick = { onExploreClaim(claim.id) }) {
                            Icon(Icons.Default.OpenInNew, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("În Atlas")
                        }
                    }
                }
            }
        }
        if (inSelection.size > 30) {
            Text("Se afișează primele 30 de fișe. Folosește filtrele din Atlas pentru restrângere.",
                style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(85.dp))
    }
}

@Composable
private fun LearningStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyCardEditor(
    old: StudyCard?,
    initialAssertionId: String?,
    assertions: List<HistoricalAssertion>,
    sources: List<HistoricalSource>,
    entities: List<HistoricalEntity>,
    links: List<AssertionEntityLink>,
    onDismiss: () -> Unit,
    onSave: suspend (StudyCard?, StudyCard) -> Unit,
    onSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val bySource = remember(sources) { sources.associateBy { it.id } }
    var assertionId by remember(old?.id, initialAssertionId) {
        mutableStateOf(old?.assertionId ?: initialAssertionId)
    }
    var search by remember { mutableStateOf("") }
    var mode by remember(old?.id) { mutableStateOf(old?.answerMode ?: "PASAJ") }
    var question by remember(old?.id, initialAssertionId) {
        mutableStateOf(old?.question.orEmpty())
    }
    var note by remember(old?.id) { mutableStateOf(old?.note.orEmpty()) }
    var suspended by remember(old?.id) { mutableStateOf(old?.suspended ?: false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val selected = assertions.find { it.id == assertionId }
    fun suggest() {
        if (selected != null) question = learningPrompt(selected, bySource[selected.sourceId],
            mode, entities, links)
    }
    LaunchedEffect(selected?.id) {
        if (old == null && question.isBlank() && selected != null) suggest()
    }
    val dated = selected != null &&
        (selected.yearStart != null || selected.yearEnd != null || selected.periodLabel.isNotBlank())
    val localized = selected != null && links.any {
        it.assertionId == selected.id && it.role == "LOC"
    }
    val allowedModes = listOf("PASAJ") +
        (if (dated) listOf("DATA") else emptyList()) +
        (if (localized) listOf("LOC") else emptyList())
    val displayed = remember(assertions, bySource, search, old?.id) {
        assertions.filter { item ->
            search.isBlank() || item.passage.contains(search, true) ||
                bySource[item.sourceId]?.title?.contains(search, true) == true ||
                bySource[item.sourceId]?.author?.contains(search, true) == true
        }.take(35)
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(if (old == null) "Întrebare de învățare" else "Editează întrebarea") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (old == null) {
                    Text("Alege o afirmație-sursă", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(value = search, onValueChange = { search = it },
                        singleLine = true, label = { Text("Caută pasajul sau cartea") },
                        modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 148.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(displayed, key = { it.id }) { item ->
                            val book = bySource[item.sourceId]
                            Surface(
                                onClick = {
                                    assertionId = item.id
                                    mode = "PASAJ"
                                    question = learningPrompt(item, book, "PASAJ", entities, links)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (item.id == assertionId)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Column(Modifier.padding(9.dp)) {
                                    Text(item.passage, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall)
                                    Text("${book?.title.orEmpty()} · p. ${item.page}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (selected != null) {
                    val source = bySource[selected.sourceId]
                    Text("SURSA: ${source?.title.orEmpty()} · " +
                        (if (selected.page.isNotBlank()) "p. ${selected.page}"
                        else selected.section),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall)
                }
                Text("Exercițiul verifică", style = MaterialTheme.typography.labelMedium)
                ChoiceRow(allowedModes, mode, { selectedMode ->
                    mode = selectedMode
                    if (old == null && selected != null) {
                        question = learningPrompt(selected, bySource[selected.sourceId],
                            mode, entities, links)
                    }
                }) { type ->
                    when (type) {
                        "DATA" -> "Data"
                        "LOC" -> "Locul"
                        else -> "Pasajul"
                    }
                }
                Field("Întrebarea mea *", question, singleLine = false) { question = it }
                TextButton(onClick = { suggest() }, enabled = selected != null) {
                    Text("Propune întrebare din metadatele sursei")
                }
                Field("Indiciu sau notă personală (opțională)", note, singleLine = false) {
                    note = it
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = suspended, onCheckedChange = { suspended = it })
                    Text("Suspendă recapitularea acestei fișe",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (error != null) Text(error.orEmpty(),
                    color = MaterialTheme.colorScheme.error)
                if (selected != null) Text("Răspunsul rămâne legat de pasajul original. " +
                    "O corectură a sursei se reflectă și în învățare.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working && selected != null &&
                    question.trim().length >= 5 && mode in allowedModes,
                onClick = {
                    val claim = selected ?: return@TextButton
                    val card = (old ?: StudyCard(assertionId = claim.id,
                        question = question.trim())).copy(
                        question = question.trim(),
                        answerMode = mode,
                        note = note.trim(),
                        suspended = suspended
                    )
                    working = true
                    error = null
                    scope.launch {
                        try {
                            onSave(old, card)
                            onSuccess()
                        } catch (e: Exception) {
                            error = e.localizedMessage ?: "Nu s-a salvat fișa"
                        } finally {
                            working = false
                        }
                    }
                }
            ) { Text(if (working) "Se salvează..." else "Salvează") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Renunță") } }
    )
}
