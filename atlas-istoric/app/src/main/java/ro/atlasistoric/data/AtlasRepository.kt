package ro.atlasistoric.data

import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ro.atlasistoric.util.descendantsOfTopic
import ro.atlasistoric.util.descendantsOfPlace

@Serializable
private data class AssertionBeforeEdit(
    val assertion: HistoricalAssertion,
    val entities: List<AssertionEntityLink>,
    val topics: List<AssertionTopicLink>
)

@Serializable
private data class DraftBeforeEdit(val draft: ResearchDraft, val claims: List<DraftClaimLink>)

class AtlasRepository(private val db: AtlasDatabase) {
    val dao = db.dao()
    private val json = Json { encodeDefaults = true }

    suspend fun saveSource(old: HistoricalSource?, item: HistoricalSource) = db.withTransaction {
        if (old == null) dao.insertSource(item)
        else {
            dao.insertRevision(RevisionEntry(recordType = "SOURCE", recordId = old.id, previousJson = json.encodeToString(old)))
            dao.updateSource(item)
        }
    }
    suspend fun saveEntity(old: HistoricalEntity?, item: HistoricalEntity) = db.withTransaction {
        if (old == null) dao.insertEntity(item)
        else {
            dao.insertRevision(RevisionEntry(recordType = "ENTITY", recordId = old.id, previousJson = json.encodeToString(old)))
            dao.updateEntity(item)
        }
    }
    suspend fun saveTopic(old: HistoricalTopic?, item: HistoricalTopic) = db.withTransaction {
        val topics = dao.allTopics()
        require(item.name.isNotBlank()) { "Numele categoriei nu poate fi gol" }
        val normalized = ro.atlasistoric.util.TimeMath.normalize(item.name)
        require(topics.none { previous ->
            previous.id != item.id && previous.parentId == item.parentId &&
                ro.atlasistoric.util.TimeMath.normalize(previous.name) == normalized
        }) { "Există deja această categorie în același indice sau părinte" }
        require(item.parentId == null || topics.any { it.id == item.parentId }) { "Tema părinte nu există" }
        require(item.parentId !in descendantsOfTopic(item.id, topics)) { "Nesting tematic ciclic" }
        if (old == null) dao.insertTopic(item)
        else {
            dao.insertRevision(RevisionEntry(recordType = "TOPIC", recordId = old.id, previousJson = json.encodeToString(old)))
            dao.updateTopic(item)
        }
    }

    suspend fun saveRelation(old: EntityRelation?, item: EntityRelation) = db.withTransaction {
        require(item.fromEntityId != item.toEntityId) { "O entitate nu poate fi propriul său părinte" }
        require(item.yearStart == null || item.yearEnd == null || item.yearStart <= item.yearEnd) {
            "Interval cronologic invalid"
        }
        if (item.relationType == "PART_OF") {
            val byId = dao.allEntities().associateBy { it.id }
            require(byId[item.fromEntityId]?.entityType == "PLACE" &&
                byId[item.toEntityId]?.entityType == "PLACE") { "Nestingul geografic necesită două entități de tip LOC" }
            val existing = dao.allRelations().filter { it.id != item.id }
            require(item.toEntityId !in descendantsOfPlace(
                item.fromEntityId, existing, item.yearStart, item.yearEnd
            )) { "Relația creează un ciclu în ierarhia spațială" }
        }
        if (old == null) dao.insertRelation(item)
        else {
            dao.insertRevision(RevisionEntry(recordType = "RELATION", recordId = old.id, previousJson = json.encodeToString(old)))
            dao.updateRelation(item)
        }
    }

    suspend fun saveAssertion(
        item: HistoricalAssertion,
        entityRoles: Map<String, String>,
        topicIds: Set<String>,
        updating: Boolean = false
    ) = db.withTransaction {
        require(item.passage.isNotBlank() && (item.page.isNotBlank() || item.section.isNotBlank())) {
            "Completează pasajul și pagina sau secțiunea"
        }
        require(item.yearStart != 0 && item.yearEnd != 0 &&
            (item.yearStart == null || item.yearEnd == null || item.yearStart <= item.yearEnd)) {
            "Interval istoric invalid"
        }
        val identityTypes = dao.allEntities().associateBy { it.id }
        require(entityRoles.all { (id, role) -> id in identityTypes &&
            role in setOf("LOC", "ACTOR", "SUBJECT") &&
            (role != "LOC" || identityTypes[id]?.entityType == "PLACE") }) {
            "Legătură invalidă între afirmație și identitate"
        }
        val existingTopics = dao.allTopics().map { it.id }.toSet()
        require(topicIds.all { it in existingTopics }) {
            "Tema aleasă nu există"
        }
        require(dao.exactDuplicateCount(item.sourceId, item.page, item.passage, item.id) == 0) {
            "Acest pasaj există deja pentru aceeași sursă și pagină"
        }
        if (updating) {
            val previous = dao.assertionById(item.id) ?: error("Afirmația originală nu a fost găsită")
            dao.insertRevision(RevisionEntry(recordType = "ASSERTION", recordId = item.id,
                previousJson = json.encodeToString(AssertionBeforeEdit(
                    previous, dao.claimEntityLinks(item.id), dao.claimTopicLinks(item.id)))))
            dao.updateAssertion(item.copy(updatedAt = nowMillis()))
            dao.clearAssertionEntityLinks(item.id)
            dao.clearAssertionTopicLinks(item.id)
        } else dao.insertAssertion(item)
        entityRoles.forEach { (id, role) ->
            dao.addAssertionEntityLink(AssertionEntityLink(item.id, id, role))
        }
        topicIds.forEach { id -> dao.addAssertionTopicLink(AssertionTopicLink(item.id, id)) }
    }

    suspend fun saveDraft(old: ResearchDraft?, item: ResearchDraft, claimIds: Set<String>) = db.withTransaction {
        if (old == null) dao.insertDraft(item)
        else {
            val linkedBefore = dao.claimLinksForDraft(old.id)
            dao.insertRevision(RevisionEntry(recordType = "DRAFT", recordId = old.id,
                previousJson = json.encodeToString(DraftBeforeEdit(old, linkedBefore))))
            dao.updateDraft(item.copy(updatedAt = nowMillis()))
            dao.clearDraftClaimLinks(item.id)
        }
        claimIds.forEach { id -> dao.insertDraftClaimLink(DraftClaimLink(item.id, id)) }
    }

    /** Default topics are editorial categories only; no fabricated historical data. */
    suspend fun bootstrapTopics() {
        db.withTransaction {
            if (dao.topicCount() == 0) {
                listOf("Politic", "Religios", "Economic", "Social", "Cultural", "Știință și tehnologie")
                    .forEach { dao.insertTopic(HistoricalTopic(name = it)) }
            }
        }
    }
}
