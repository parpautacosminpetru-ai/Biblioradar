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
        EntityRelation::class, RevisionEntry::class, ResearchDraft::class, DraftClaimLink::class
    ],
    version = 2,
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
        @Volatile private var instance: AtlasDatabase? = null
        fun get(context: Context): AtlasDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AtlasDatabase::class.java,
                "atlas_istoric.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
