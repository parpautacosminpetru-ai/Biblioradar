package ro.atlasistoric.data

import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class AtlasSnapshot(
    val format: String = "atlas-istoric-json",
    val schemaVersion: Int = 4,
    val entities: List<HistoricalEntity>,
    val sources: List<HistoricalSource>,
    val assertions: List<HistoricalAssertion>,
    val topics: List<HistoricalTopic>,
    val assertionEntities: List<AssertionEntityLink>,
    val assertionTopics: List<AssertionTopicLink>,
    val relations: List<EntityRelation>,
    val revisions: List<RevisionEntry> = emptyList(),
    val drafts: List<ResearchDraft> = emptyList(),
    val draftClaims: List<DraftClaimLink> = emptyList(),
    val studyCards: List<StudyCard> = emptyList(),
    val studyReviews: List<StudyReview> = emptyList(),
    val documentPages: List<DocumentPage> = emptyList(),
    val pageAnnotations: List<PageAnnotation> = emptyList(),
    val annotationClaims: List<AnnotationClaimLink> = emptyList()
)

class BackupService(private val db: AtlasDatabase) {
    private val dao = db.dao()
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun exportSnapshot(): AtlasSnapshot = db.withTransaction {
        AtlasSnapshot(
            entities = dao.allEntities(),
            sources = dao.allSources(),
            assertions = dao.allAssertions(),
            topics = dao.allTopics(),
            assertionEntities = dao.allAssertionEntities(),
            assertionTopics = dao.allAssertionTopics(),
            relations = dao.allRelations(),
            revisions = dao.allRevisions(),
            drafts = dao.allDrafts(),
            draftClaims = dao.allDraftClaimLinks(),
            studyCards = dao.allStudyCards(),
            studyReviews = dao.allStudyReviews(),
            documentPages = dao.allDocumentPages(),
            pageAnnotations = dao.allPageAnnotations(),
            annotationClaims = dao.allAnnotationClaimLinks()
        )
    }

    fun encodeSnapshot(data: AtlasSnapshot): String = json.encodeToString(data)
    fun decodeSnapshot(raw: String): AtlasSnapshot = json.decodeFromString(raw)

    /** Plain JSON deliberately cannot pretend to contain image binaries. */
    suspend fun exportJson(): String {
        require(dao.documentPageCount() == 0) {
            "Există fotografii. Pentru arhiva completă folosește Exportă ZIP."
        }
        return encodeSnapshot(exportSnapshot())
    }

    /** Explicit *replacement*, never implicit merge. Validates referential integrity before clearing.
     *  A failed transaction rolls back every delete. */
    suspend fun importReplaceJson(raw: String) {
        val data = decodeSnapshot(raw)
        require(data.documentPages.isEmpty() && dao.documentPageCount() == 0) {
            "Nu poți înlocui fotografii originale cu o arhivă JSON. Folosește ZIP complet."
        }
        importReplaceSnapshot(data, withMedia = false)
    }

    /** Used only by a ZIP archive after its image bytes and SHA-256 have been verified. */
    suspend fun importReplaceSnapshot(data: AtlasSnapshot, withMedia: Boolean) {
        require(data.format == "atlas-istoric-json" && data.schemaVersion in 1..4) {
            "Format sau versiune necunoscută"
        }
        require(withMedia || data.documentPages.isEmpty()) {
            "Fotografiile nu sunt incluse în importul JSON"
        }
        validate(data)
        db.withTransaction {
            dao.clearAnnotationClaimLinks()
            dao.clearPageAnnotations()
            dao.clearDocumentPages()
            dao.clearAllStudyReviews()
            dao.clearAllStudyCards()
            dao.clearRevisions()
            dao.clearAllDraftClaimLinks()
            dao.clearDrafts()
            dao.clearEntityLinks()
            dao.clearTopicLinks()
            dao.clearRelations()
            dao.clearAssertions()
            dao.clearTopics()
            dao.clearEntities()
            dao.clearSources()
            data.entities.forEach { dao.insertEntity(it) }
            data.sources.forEach { dao.insertSource(it) }
            // Parent topics before nested topics (foreign keys are enforced).
            val unresolved = data.topics.toMutableList()
            val written = mutableSetOf<String>()
            while (unresolved.isNotEmpty()) {
                val ready = unresolved.filter { it.parentId == null || it.parentId in written }
                require(ready.isNotEmpty()) { "Nesting tematic ciclic în arhivă" }
                ready.forEach { dao.insertTopic(it); written.add(it.id) }
                unresolved.removeAll(ready)
            }
            data.assertions.forEach { dao.insertAssertion(it) }
            data.assertionEntities.forEach { dao.addAssertionEntityLink(it) }
            data.assertionTopics.forEach { dao.addAssertionTopicLink(it) }
            data.studyCards.forEach { dao.insertStudyCard(it) }
            data.studyReviews.forEach { dao.insertStudyReview(it) }
            data.documentPages.forEach { dao.insertDocumentPage(it) }
            data.pageAnnotations.forEach { dao.insertPageAnnotation(it) }
            data.annotationClaims.forEach { dao.insertAnnotationClaimLink(it) }
            data.relations.forEach { dao.insertRelation(it) }
            data.drafts.forEach { dao.insertDraft(it) }
            data.draftClaims.forEach { dao.insertDraftClaimLink(it) }
            data.revisions.forEach { dao.insertRevision(it) }
        }
    }

    fun validate(data: AtlasSnapshot) {
        val eIds = data.entities.map { it.id }.toSet()
        val sIds = data.sources.map { it.id }.toSet()
        val aIds = data.assertions.map { it.id }.toSet()
        val tIds = data.topics.map { it.id }.toSet()
        val dIds = data.drafts.map { it.id }.toSet()
        val cIds = data.studyCards.map { it.id }.toSet()
        val rIds = data.studyReviews.map { it.id }.toSet()
        require(data.studyCards.size == cIds.size && data.studyReviews.size == rIds.size) {
            "Identificatori de învățare duplicați"
        }
        require(data.studyCards.all { it.assertionId in aIds && it.answerMode in setOf("PASAJ", "DATA", "LOC") }) {
            "Fișă de învățare fără afirmație ori mod recunoscut"
        }
        require(data.studyReviews.all { it.cardId in cIds && it.grade in 0..3 }) {
            "Recapitulare fără fișă sau evaluare invalidă"
        }
        val pages = data.documentPages.associateBy { it.id }
        val annotations = data.pageAnnotations.associateBy { it.id }
        require(pages.size == data.documentPages.size && annotations.size == data.pageAnnotations.size) {
            "Pagini sau adnotări cu ID duplicat"
        }
        require(data.documentPages.all { p ->
            p.sourceId in sIds && p.sha256.matches(Regex("[0-9a-f]{64}")) &&
                p.storagePath.startsWith("atlas_pages/") &&
                !p.storagePath.contains("..") &&
                !p.storagePath.contains('\\') &&
                p.status in setOf("CAPTURED", "TRANSCRIBED", "VERIFIED", "ANNOTATED")
        }) { "Proveniență sau fișier original invalid" }
        require(data.pageAnnotations.all { a ->
            a.pageId in pages && a.startOffset >= 0 && a.endOffset > a.startOffset &&
                a.exactQuote.isNotEmpty()
        }) { "Ancoră de adnotare invalidă" }
        require(data.annotationClaims.all { link ->
            link.annotationId in annotations && link.assertionId in aIds &&
                data.assertions.first { it.id == link.assertionId }.sourceId ==
                pages.getValue(annotations.getValue(link.annotationId).pageId).sourceId
        }) { "Legături documentare între surse diferite" }
        require(data.drafts.size == dIds.size) { "ID-uri duplicate în atelier" }
        require(data.draftClaims.all { it.draftId in dIds && it.assertionId in aIds }) { "Referință invalidă în atelier" }
        require(listOf(data.entities.size == eIds.size, data.sources.size == sIds.size,
            data.assertions.size == aIds.size, data.topics.size == tIds.size).all { it }) { "ID-uri duplicate" }
        require(data.assertions.all { it.sourceId in sIds }) { "Referințe la surse inexistente" }
        require(data.topics.all { it.parentId == null || it.parentId in tIds }) { "Tema părinte lipsește" }
        require(data.assertionEntities.all { it.assertionId in aIds && it.entityId in eIds }) { "Legătură entitate lipsă" }
        require(data.assertionTopics.all { it.assertionId in aIds && it.topicId in tIds }) { "Legătură tematică lipsă" }
        require(data.relations.all { it.fromEntityId in eIds && it.toEntityId in eIds &&
            (it.supportingAssertionId == null || it.supportingAssertionId in aIds) }) { "Relație incompletă" }
    }
}
