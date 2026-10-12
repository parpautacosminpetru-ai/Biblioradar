package ro.atlasistoric.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.atlasistoric.data.*
import ro.atlasistoric.ocr.CameraOcrScreen

private enum class Screen { ATLAS, SOURCES, ENTITIES, TOPICS, RELATIONS, CAMERA, ASSERTION_EDITOR, ASSERTION_DETAIL, BACKUP, DRAFTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtlasApp() {
    val context = LocalContext.current
    val database = remember { AtlasDatabase.get(context) }
    val repo = remember { AtlasRepository(database) }
    val dao = repo.dao
    val entities by dao.entities().collectAsState(initial = emptyList())
    val sources by dao.sources().collectAsState(initial = emptyList())
    val assertions by dao.assertions().collectAsState(initial = emptyList())
    val topics by dao.topics().collectAsState(initial = emptyList())
    val eLinks by dao.assertionEntities().collectAsState(initial = emptyList())
    val tLinks by dao.assertionTopics().collectAsState(initial = emptyList())
    val relations by dao.relations().collectAsState(initial = emptyList())
    val revisions by dao.revisions().collectAsState(initial = emptyList())
    val drafts by dao.drafts().collectAsState(initial = emptyList())
    val draftLinks by dao.draftClaimLinks().collectAsState(initial = emptyList())

    var current by rememberSaveable { mutableStateOf(Screen.ATLAS.name) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var seedText by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf("SECOL") }
    var selectedTimeKey by rememberSaveable { mutableStateOf<String?>(null) }
    var anchorYear by rememberSaveable { mutableIntStateOf(java.time.Year.now().value) }
    var anchorMonth by rememberSaveable { mutableIntStateOf(java.time.LocalDate.now().monthValue) }
    // UUIDs do not include semicolons. Strings are Saveable across process recreation.
    var selectedPlaces by rememberSaveable { mutableStateOf("") }
    var selectedTopics by rememberSaveable { mutableStateOf("") }
    val placeIds = remember(selectedPlaces) { selectedPlaces.split(';').filter { it.isNotBlank() }.toSet() }
    val topicIds = remember(selectedTopics) { selectedTopics.split(';').filter { it.isNotBlank() }.toSet() }
    var topicMatchAll by rememberSaveable { mutableStateOf(false) }
    var perspective by rememberSaveable { mutableStateOf("TIMP") }
    var search by rememberSaveable { mutableStateOf("") }
    var quickSource by remember { mutableStateOf(false) }
    var quickEntity by remember { mutableStateOf(false) }
    var quickTopic by remember { mutableStateOf(false) }
    var savingAssertion by remember { mutableStateOf(false) }
    var preferredSourceId by remember { mutableStateOf<String?>(null) }
    var preferredEntityId by remember { mutableStateOf<String?>(null) }
    var preferredTopicId by remember { mutableStateOf<String?>(null) }

    val coroutine = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    fun execute(message: String = "Salvat pe dispozitiv", onSuccess: () -> Unit = {}, job: suspend () -> Unit) {
        coroutine.launch {
            try {
                withContext(Dispatchers.IO) { job() }
                onSuccess()
                snackbar.showSnackbar(message)
            } catch (e: Exception) {
                snackbar.showSnackbar("Nu s-a salvat: ${e.localizedMessage ?: "eroare"}")
            }
        }
    }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { repo.bootstrapTopics() } }
    val currentScreen = Screen.valueOf(current)
    val detailAssertion = assertions.find { it.id == editId }
    val navigatingEditor = currentScreen == Screen.ASSERTION_EDITOR || currentScreen == Screen.ASSERTION_DETAIL

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(when (currentScreen) {
                    Screen.ATLAS -> "ATLAS ISTORIC"
                    Screen.SOURCES -> "Biblioteca de surse"
                    Screen.ENTITIES -> "Identități istorice"
                    Screen.TOPICS -> "Arbore tematic"
                    Screen.RELATIONS -> "Relații documentate"
                    Screen.CAMERA -> "OCR Live · Offline"
                    Screen.ASSERTION_EDITOR -> if (editId != null) "Editează afirmația" else "Afirmație nouă"
                    Screen.ASSERTION_DETAIL -> "Proveniență și context"
                    Screen.BACKUP -> "Arhivare și restaurare"
                    Screen.DRAFTS -> "Atelier de sinteză"
                }, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    if (navigatingEditor || currentScreen == Screen.BACKUP || currentScreen == Screen.RELATIONS || currentScreen == Screen.DRAFTS) {
                        IconButton(onClick = { current = when (currentScreen) {
                            Screen.RELATIONS -> Screen.ENTITIES.name
                            else -> Screen.ATLAS.name
                        } }) { Icon(Icons.Default.ArrowBack, contentDescription = "Înapoi") }
                    }
                },
                actions = {
                    if (currentScreen == Screen.ATLAS) {
                        IconButton(onClick = { current = Screen.DRAFTS.name }) {
                            Icon(Icons.Default.EditNote, contentDescription = "Atelierul de sinteze")
                        }
                        IconButton(onClick = { current = Screen.BACKUP.name }) {
                            Icon(Icons.Default.SaveAlt, contentDescription = "Copie de siguranță")
                        }
                    }
                    if (currentScreen == Screen.ENTITIES) {
                        IconButton(onClick = { current = Screen.RELATIONS.name }) {
                            Icon(Icons.Default.AccountTree, contentDescription = "Relații între entități")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!navigatingEditor && currentScreen != Screen.BACKUP && currentScreen != Screen.RELATIONS && currentScreen != Screen.DRAFTS) {
                NavigationBar {
                    listOf(
                        Triple(Screen.ATLAS, Icons.Default.Timeline, "Atlas"),
                        Triple(Screen.SOURCES, Icons.Default.MenuBook, "Surse"),
                        Triple(Screen.ENTITIES, Icons.Default.Hub, "Actori"),
                        Triple(Screen.TOPICS, Icons.Default.Layers, "Teme"),
                        Triple(Screen.CAMERA, Icons.Default.CameraAlt, "OCR")
                    ).forEach { (screen, icon, title) ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { current = screen.name },
                            icon = { Icon(icon, contentDescription = title) },
                            label = { Text(title) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentScreen == Screen.ATLAS) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editId = null; seedText = ""
                        preferredEntityId = null; preferredTopicId = null
                        current = Screen.ASSERTION_EDITOR.name
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Afirmație") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            when (currentScreen) {
                Screen.ATLAS -> AtlasScreen(
                    assertions = assertions, entities = entities, topics = topics, sources = sources,
                    entityLinks = eLinks, topicLinks = tLinks, relations = relations,
                    level = level, onLevel = { nextLevel, nextKey -> level = nextLevel; selectedTimeKey = nextKey },
                    timeKey = selectedTimeKey, onTimeKey = { selectedTimeKey = it },
                    anchorYear = anchorYear, onAnchorYear = { anchorYear = it },
                    anchorMonth = anchorMonth, onAnchorMonth = { anchorMonth = it },
                    placeIds = placeIds, onPlaceIds = { selectedPlaces = it.joinToString(";") },
                    topicIds = topicIds, onTopicIds = { selectedTopics = it.joinToString(";") },
                    topicMatchAll = topicMatchAll, onTopicMatchAll = { topicMatchAll = it },
                    perspective = perspective, onPerspective = { perspective = it },
                    search = search, onSearch = { search = it },
                    onOpen = { editId = it; current = Screen.ASSERTION_DETAIL.name }
                )
                Screen.SOURCES -> SourcesScreen(
                    sources = sources, assertions = assertions,
                    onSave = { old, new -> execute {
                        repo.saveSource(old, new)
                    } }
                )
                Screen.ENTITIES -> EntitiesScreen(
                    entities = entities, relations = relations,
                    onSave = { old, new -> execute {
                        repo.saveEntity(old, new)
                    } },
                    onRelations = { current = Screen.RELATIONS.name }
                )
                Screen.TOPICS -> TopicsScreen(
                    topics = topics,
                    onSave = { old, new -> execute {
                        repo.saveTopic(old, new)
                    } }
                )
                Screen.RELATIONS -> RelationsScreen(
                    entities = entities, relations = relations, assertions = assertions,
                    onSave = { old, r -> execute { repo.saveRelation(old, r) } }
                )
                Screen.CAMERA -> CameraOcrScreen(onUseText = { recognized ->
                    seedText = recognized
                    preferredEntityId = null
                    preferredTopicId = null
                    editId = null
                    current = Screen.ASSERTION_EDITOR.name
                })
                Screen.ASSERTION_EDITOR -> AssertionEditor(
                    old = assertions.find { it.id == editId },
                    initialOcr = seedText,
                    preferredSourceId = preferredSourceId,
                    preferredEntityId = preferredEntityId,
                    preferredTopicId = preferredTopicId,
                    sources = sources, entities = entities, topics = topics,
                    oldEntityLinks = eLinks, oldTopicLinks = tLinks,
                    onNeedsSource = { quickSource = true },
                    onCreateEntity = { quickEntity = true },
                    onCreateTopic = { quickTopic = true },
                    saving = savingAssertion,
                    onSave = { item, roles, selectedTopics, isUpdate ->
                        if (!savingAssertion) {
                            savingAssertion = true
                            execute(onSuccess = {
                                savingAssertion = false
                                seedText = ""
                                preferredSourceId = item.sourceId
                                preferredEntityId = null
                                preferredTopicId = null
                                current = Screen.ATLAS.name
                            }) {
                                try { repo.saveAssertion(item, roles, selectedTopics, isUpdate) }
                                finally { withContext(Dispatchers.Main) { savingAssertion = false } }
                            }
                        }
                    }
                )
                Screen.ASSERTION_DETAIL -> if (detailAssertion != null) {
                    AssertionDetail(
                        item = detailAssertion, sources = sources, entities = entities, topics = topics,
                        entityLinks = eLinks, topicLinks = tLinks,
                        revisions = revisions.filter { it.recordType == "ASSERTION" && it.recordId == detailAssertion.id },
                        onEdit = { current = Screen.ASSERTION_EDITOR.name }
                    )
                }
                Screen.DRAFTS -> DraftsScreen(
                    drafts = drafts, links = draftLinks, assertions = assertions, sources = sources,
                    revisions = revisions,
                    onSave = { old, draft, claimIds -> execute { repo.saveDraft(old, draft, claimIds) } }
                )
                Screen.BACKUP -> BackupScreen(database = database, onNotice = {
                    coroutine.launch { snackbar.showSnackbar(it) }
                })
            }
        }
    }
    // Quick-add overlays keep the unsaved research/OCR draft on screen.
    if (quickSource) SourceForm(null, onDismiss = { quickSource = false }) { item ->
        execute(onSuccess = { preferredSourceId = item.id }) { repo.saveSource(null, item) }
        quickSource = false
    }
    if (quickEntity) EntityForm(null, entities, onDismiss = { quickEntity = false }) { item ->
        execute(onSuccess = { preferredEntityId = item.id }) { repo.saveEntity(null, item) }
        quickEntity = false
    }
    if (quickTopic) TopicForm(null, topics, onDismiss = { quickTopic = false }) { item ->
        execute(onSuccess = { preferredTopicId = item.id }) { repo.saveTopic(null, item) }
        quickTopic = false
    }

}
