package ro.atlasistoric.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AtlasDao {
    @Query("SELECT * FROM historical_entities ORDER BY name COLLATE NOCASE")
    fun entities(): Flow<List<HistoricalEntity>>
    @Query("SELECT * FROM historical_sources ORDER BY title COLLATE NOCASE")
    fun sources(): Flow<List<HistoricalSource>>
    @Query("SELECT * FROM historical_assertions ORDER BY createdAt DESC")
    fun assertions(): Flow<List<HistoricalAssertion>>
    @Query("SELECT * FROM topics ORDER BY name COLLATE NOCASE")
    fun topics(): Flow<List<HistoricalTopic>>
    @Query("SELECT * FROM assertion_entities")
    fun assertionEntities(): Flow<List<AssertionEntityLink>>
    @Query("SELECT * FROM assertion_topics")
    fun assertionTopics(): Flow<List<AssertionTopicLink>>
    @Query("SELECT * FROM research_drafts ORDER BY updatedAt DESC")
    fun drafts(): Flow<List<ResearchDraft>>
    @Query("SELECT * FROM draft_claim_links")
    fun draftClaimLinks(): Flow<List<DraftClaimLink>>
    @Query("SELECT * FROM draft_claim_links WHERE draftId=:id")
    suspend fun claimLinksForDraft(id: String): List<DraftClaimLink>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDraft(item: ResearchDraft)
    @Update suspend fun updateDraft(item: ResearchDraft)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDraftClaimLink(item: DraftClaimLink)
    @Query("DELETE FROM draft_claim_links WHERE draftId=:id") suspend fun clearDraftClaimLinks(id: String)
    @Query("DELETE FROM draft_claim_links") suspend fun clearAllDraftClaimLinks()
    @Query("DELETE FROM research_drafts") suspend fun clearDrafts()
    @Query("SELECT * FROM research_drafts") suspend fun allDrafts(): List<ResearchDraft>
    @Query("SELECT * FROM draft_claim_links") suspend fun allDraftClaimLinks(): List<DraftClaimLink>
    @Query("SELECT * FROM revisions ORDER BY changedAt DESC")
    fun revisions(): Flow<List<RevisionEntry>>
    @Query("SELECT * FROM revisions") suspend fun allRevisions(): List<RevisionEntry>
    @Query("SELECT * FROM historical_assertions WHERE id=:id LIMIT 1")
    suspend fun assertionById(id: String): HistoricalAssertion?
    @Query("SELECT * FROM assertion_entities WHERE assertionId=:id")
    suspend fun claimEntityLinks(id: String): List<AssertionEntityLink>
    @Query("SELECT * FROM assertion_topics WHERE assertionId=:id")
    suspend fun claimTopicLinks(id: String): List<AssertionTopicLink>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRevision(item: RevisionEntry)
    @Query("DELETE FROM revisions") suspend fun clearRevisions()

    @Query("SELECT * FROM entity_relations ORDER BY createdAt DESC")
    fun relations(): Flow<List<EntityRelation>>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertEntity(item: HistoricalEntity)
    @Update suspend fun updateEntity(item: HistoricalEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSource(item: HistoricalSource)
    @Update suspend fun updateSource(item: HistoricalSource)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAssertion(item: HistoricalAssertion)
    @Update suspend fun updateAssertion(item: HistoricalAssertion)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTopic(item: HistoricalTopic)
    @Update suspend fun updateTopic(item: HistoricalTopic)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addAssertionEntityLink(item: AssertionEntityLink)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addAssertionTopicLink(item: AssertionTopicLink)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRelation(item: EntityRelation)
    @Update suspend fun updateRelation(item: EntityRelation)

    @Query("DELETE FROM assertion_entities WHERE assertionId=:assertionId")
    suspend fun clearAssertionEntityLinks(assertionId: String)
    @Query("DELETE FROM assertion_topics WHERE assertionId=:assertionId")
    suspend fun clearAssertionTopicLinks(assertionId: String)
    @Query("DELETE FROM assertion_entities") suspend fun clearEntityLinks()
    @Query("DELETE FROM assertion_topics") suspend fun clearTopicLinks()
    @Query("DELETE FROM entity_relations") suspend fun clearRelations()
    @Query("DELETE FROM historical_assertions") suspend fun clearAssertions()
    @Query("DELETE FROM topics") suspend fun clearTopics()
    @Query("DELETE FROM historical_entities") suspend fun clearEntities()
    @Query("DELETE FROM historical_sources") suspend fun clearSources()

    @Query("SELECT COUNT(*) FROM topics") suspend fun topicCount(): Int
    @Query("SELECT COUNT(*) FROM historical_assertions WHERE sourceId=:sourceId AND page=:page AND passage=:passage AND id!=:excludedId")
    suspend fun exactDuplicateCount(sourceId: String, page: String, passage: String, excludedId: String): Int

    @Query("SELECT * FROM historical_entities") suspend fun allEntities(): List<HistoricalEntity>
    @Query("SELECT * FROM historical_sources") suspend fun allSources(): List<HistoricalSource>
    @Query("SELECT * FROM historical_assertions") suspend fun allAssertions(): List<HistoricalAssertion>
    @Query("SELECT * FROM topics") suspend fun allTopics(): List<HistoricalTopic>
    @Query("SELECT * FROM assertion_entities") suspend fun allAssertionEntities(): List<AssertionEntityLink>
    @Query("SELECT * FROM assertion_topics") suspend fun allAssertionTopics(): List<AssertionTopicLink>
    @Query("SELECT * FROM entity_relations") suspend fun allRelations(): List<EntityRelation>

    @Query("SELECT COUNT(*) FROM historical_assertions")
    suspend fun assertionCount(): Int
}
