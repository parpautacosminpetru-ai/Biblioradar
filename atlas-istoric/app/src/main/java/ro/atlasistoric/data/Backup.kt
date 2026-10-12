package ro.atlasistoric.data

import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class AtlasSnapshot(
    val format: String = "atlas-istoric-json",
    val schemaVersion: Int = 2,
    val entities: List<HistoricalEntity>,
    val sources: List<HistoricalSource>,
    val assertions: List<HistoricalAssertion>,
    val topics: List<HistoricalTopic>,
    val assertionEntities: List<AssertionEntityLink>,
    val assertionTopics: List<AssertionTopicLink>,
    val relations: List<EntityRelation>,
    val revisions: List<RevisionEntry> = emptyList(),
    val drafts: List<ResearchDraft> = emptyList(),
    val draftClaims: List<DraftClaimLink> = emptyList()
)

class BackupService(private val db: AtlasDatabase) {
    private val dao = db.dao()
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun exportJson(): String = db.withTransaction {
        json.encodeToString(AtlasSnapshot(
            entities = dao.allEntities(),
            sources = dao.allSources(),
            assertions = dao.allAssertions(),
            topics = dao.allTopics(),
            assertionEntities = dao.allAssertionEntities(),
            assertionTopics = dao.allAssertionTopics(),
            relations = dao.allRelations(),
            revisions = dao.allRevisions(),
            drafts = dao.allDrafts(),
            draftClaims = dao.allDraftClaimLinks()
        ))
    }

    /** Explicit *replacement*, never implicit merge. Validates referential integrity before clearing.
     *  A failed transaction rolls back every delete. */
    suspend fun importReplaceJson(raw: String) {
        val data = json.decodeFromString<AtlasSnapshot>(raw)
        require(data.format == "atlas-istoric-json" && data.schemaVersion in 1..2) { "Format sau versiune necunoscută" }
        validate(data)
        db.withTransaction {
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
            data.relations.forEach { dao.insertRelation(it) }
            data.drafts.forEach { dao.insertDraft(it) }
            data.draftClaims.forEach { dao.insertDraftClaimLink(it) }
            data.revisions.forEach { dao.insertRevision(it) }
        }
    }

    private fun validate(data: AtlasSnapshot) {
        val eIds = data.entities.map { it.id }.toSet()
        val sIds = data.sources.map { it.id }.toSet()
        val aIds = data.assertions.map { it.id }.toSet()
        val tIds = data.topics.map { it.id }.toSet()
        val dIds = data.drafts.map { it.id }.toSet()
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
