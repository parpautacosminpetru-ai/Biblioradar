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
    @Query("SELECT * FROM study_cards ORDER BY dueAt ASC")
    fun studyCards(): Flow<List<StudyCard>>
    @Query("SELECT * FROM study_reviews ORDER BY reviewedAt DESC")
    fun studyReviews(): Flow<List<StudyReview>>
    @Query("SELECT * FROM study_cards WHERE id=:id LIMIT 1")
    suspend fun studyCardById(id: String): StudyCard?
    @Query("SELECT * FROM study_cards")
    suspend fun allStudyCards(): List<StudyCard>
    @Query("SELECT * FROM study_reviews")
    suspend fun allStudyReviews(): List<StudyReview>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertStudyCard(item: StudyCard)
    @Update suspend fun updateStudyCard(item: StudyCard)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertStudyReview(item: StudyReview)
    @Query("DELETE FROM study_reviews") suspend fun clearAllStudyReviews()
    @Query("DELETE FROM study_cards") suspend fun clearAllStudyCards()

    @Query("SELECT * FROM document_pages ORDER BY createdAt DESC")
    fun documentPages(): Flow<List<DocumentPage>>
    @Query("SELECT * FROM page_annotations ORDER BY createdAt DESC")
    fun pageAnnotations(): Flow<List<PageAnnotation>>
    @Query("SELECT * FROM page_annotations WHERE pageId=:pageId")
    suspend fun pageAnnotationsByPage(pageId: String): List<PageAnnotation>
    @Query("SELECT * FROM annotation_claim_links")
    fun annotationClaimLinks(): Flow<List<AnnotationClaimLink>>
    @Query("SELECT * FROM annotation_claim_links WHERE assertionId=:id")
    suspend fun annotationLinksForAssertion(id: String): List<AnnotationClaimLink>
    @Query("SELECT * FROM document_pages WHERE id = :id LIMIT 1")
    suspend fun documentPageById(id: String): DocumentPage?
    @Query("SELECT * FROM page_annotations WHERE id = :id LIMIT 1")
    suspend fun pageAnnotationById(id: String): PageAnnotation?
    @Query("SELECT * FROM document_pages WHERE sourceId = :sourceId AND sha256 = :sha256 LIMIT 1")
    suspend fun documentPageByHash(sourceId: String, sha256: String): DocumentPage?
    @Query("SELECT COUNT(*) FROM document_pages")
    suspend fun documentPageCount(): Int
    @Query("SELECT * FROM document_pages")
    suspend fun allDocumentPages(): List<DocumentPage>
    @Query("SELECT * FROM page_annotations")
    suspend fun allPageAnnotations(): List<PageAnnotation>
    @Query("SELECT * FROM annotation_claim_links")
    suspend fun allAnnotationClaimLinks(): List<AnnotationClaimLink>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocumentPage(page: DocumentPage)
    @Update
    suspend fun updateDocumentPage(page: DocumentPage)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPageAnnotation(annotation: PageAnnotation)
    @Update
    suspend fun updatePageAnnotation(annotation: PageAnnotation)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnnotationClaimLink(link: AnnotationClaimLink)
    @Query("DELETE FROM annotation_claim_links")
    suspend fun clearAnnotationClaimLinks()
    @Query("DELETE FROM page_annotations")
    suspend fun clearPageAnnotations()
    @Query("DELETE FROM document_pages")
    suspend fun clearDocumentPages()

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
