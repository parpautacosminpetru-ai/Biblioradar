package ro.atlasistoric.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HistoricalEntity::class, HistoricalSource::class, HistoricalAssertion::class,
        HistoricalTopic::class, AssertionEntityLink::class, AssertionTopicLink::class,
        EntityRelation::class, RevisionEntry::class, ResearchDraft::class, DraftClaimLink::class,
        StudyCard::class, StudyReview::class,
        DocumentPage::class, PageAnnotation::class, AnnotationClaimLink::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AtlasDatabase : RoomDatabase() {
    abstract fun dao(): AtlasDao
    companion object {
        // Keeps the original 0.1 schema intact when upgrading an existing installation.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE historical_sources ADD COLUMN readingStatus TEXT NOT NULL DEFAULT 'NEINCEPUTA'")
                db.execSQL("ALTER TABLE historical_sources ADD COLUMN lastPage TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS research_drafts (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_research_drafts_updatedAt ON research_drafts (`updatedAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS draft_claim_links (`draftId` TEXT NOT NULL, `assertionId` TEXT NOT NULL, PRIMARY KEY(`draftId`, `assertionId`), FOREIGN KEY(`draftId`) REFERENCES `research_drafts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`assertionId`) REFERENCES `historical_assertions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_draft_claim_links_assertionId ON draft_claim_links (`assertionId`)")
            }
        }
        // Non-destructive learning extension: research records and existing ID links are preserved.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS study_cards (
                      id TEXT NOT NULL,
                      assertionId TEXT NOT NULL,
                      question TEXT NOT NULL,
                      answerMode TEXT NOT NULL,
                      note TEXT NOT NULL,
                      suspended INTEGER NOT NULL,
                      dueAt INTEGER NOT NULL,
                      intervalDays INTEGER NOT NULL,
                      easePermille INTEGER NOT NULL,
                      repetitions INTEGER NOT NULL,
                      lapses INTEGER NOT NULL,
                      lastReviewedAt INTEGER,
                      createdAt INTEGER NOT NULL,
                      updatedAt INTEGER NOT NULL,
                      PRIMARY KEY(id),
                      FOREIGN KEY(assertionId) REFERENCES historical_assertions(id)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_cards_assertionId ON study_cards (assertionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_cards_dueAt ON study_cards (dueAt)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS study_reviews (
                      id TEXT NOT NULL,
                      cardId TEXT NOT NULL,
                      grade INTEGER NOT NULL,
                      reviewedAt INTEGER NOT NULL,
                      previousIntervalDays INTEGER NOT NULL,
                      nextIntervalDays INTEGER NOT NULL,
                      nextDueAt INTEGER NOT NULL,
                      PRIMARY KEY(id),
                      FOREIGN KEY(cardId) REFERENCES study_cards(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_reviews_cardId ON study_reviews (cardId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_reviews_reviewedAt ON study_reviews (reviewedAt)")
            }
        }
        // Version four adds the documentary laboratory without overwriting historical records.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS document_pages (
                        id TEXT NOT NULL,
                        sourceId TEXT NOT NULL,
                        pageLabel TEXT NOT NULL,
                        originalName TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        storagePath TEXT NOT NULL,
                        sha256 TEXT NOT NULL,
                        originalOcrText TEXT NOT NULL,
                        verifiedText TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(sourceId) REFERENCES historical_sources(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_document_pages_sourceId ON document_pages (sourceId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_document_pages_sourceId_sha256 ON document_pages (sourceId, sha256)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS page_annotations (
                        id TEXT NOT NULL,
                        pageId TEXT NOT NULL,
                        startOffset INTEGER NOT NULL,
                        endOffset INTEGER NOT NULL,
                        exactQuote TEXT NOT NULL,
                        contextBefore TEXT NOT NULL,
                        contextAfter TEXT NOT NULL,
                        category TEXT NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(pageId) REFERENCES document_pages(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_page_annotations_pageId ON page_annotations (pageId)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS annotation_claim_links (
                        annotationId TEXT NOT NULL,
                        assertionId TEXT NOT NULL,
                        PRIMARY KEY(annotationId, assertionId),
                        FOREIGN KEY(annotationId) REFERENCES page_annotations(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(assertionId) REFERENCES historical_assertions(id)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_annotation_claim_links_assertionId ON annotation_claim_links (assertionId)")
            }
        }
        @Volatile private var instance: AtlasDatabase? = null
        fun get(context: Context): AtlasDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AtlasDatabase::class.java,
                "atlas_istoric.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}
