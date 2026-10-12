package ro.atlasistoric.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

fun newId(): String = UUID.randomUUID().toString()
fun nowMillis(): Long = System.currentTimeMillis()

/** Identity is stable across claims and references. Names are NOT unique: homonyms exist. */
@Entity(tableName = "historical_entities", indices = [Index("name"), Index("entityType")])
@Serializable
data class HistoricalEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val entityType: String, // PERSON, PLACE, INSTITUTION, EVENT, OTHER
    val aliases: String = "",
    val description: String = "",
    val createdAt: Long = nowMillis(),
    val updatedAt: Long = nowMillis()
)

@Entity(tableName = "historical_sources", indices = [Index("title")])
@Serializable
data class HistoricalSource(
    @PrimaryKey val id: String = newId(),
    val author: String,
    val title: String,
    val edition: String = "",
    val publication: String = "",
    val year: String = "",
    val identifier: String = "",
    val notes: String = "",
    @ColumnInfo(defaultValue = "'NEINCEPUTA'") val readingStatus: String = "NEINCEPUTA",
    @ColumnInfo(defaultValue = "''") val lastPage: String = "",
    val createdAt: Long = nowMillis()
)

/** A source-backed assertion, not automatically a historical fact. */
@Entity(
    tableName = "historical_assertions",
    foreignKeys = [ForeignKey(
        entity = HistoricalSource::class, parentColumns = ["id"], childColumns = ["sourceId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("sourceId"), Index("yearStart"), Index("yearEnd"), Index("kind")]
)
@Serializable
data class HistoricalAssertion(
    @PrimaryKey val id: String = newId(),
    val sourceId: String,
    val kind: String = "CITAT", // CITAT, PARAFRAZA, EXTRAGERE
    val passage: String, // exact source quotation for CITAT, user's wording otherwise
    val page: String,
    val section: String = "",
    val contextNote: String = "", // explicitly separated from source text
    val periodLabel: String = "", // period as asserted, without forced precision
    val yearStart: Int? = null,
    val yearEnd: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
    val precision: String = "NECUNOSCUTA", // EPOCA, SECOL, AN, LUNA, ZI, INTERVAL, NECUNOSCUTA
    val createdAt: Long = nowMillis(),
    val updatedAt: Long = nowMillis()
)

@Entity(
    tableName = "topics",
    foreignKeys = [ForeignKey(
        entity = HistoricalTopic::class, parentColumns = ["id"], childColumns = ["parentId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("parentId")]
)
@Serializable
data class HistoricalTopic(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val parentId: String? = null,
    val description: String = ""
)

@Entity(
    tableName = "assertion_entities",
    primaryKeys = ["assertionId", "entityId", "role"],
    foreignKeys = [
        ForeignKey(entity = HistoricalAssertion::class, parentColumns = ["id"], childColumns = ["assertionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = HistoricalEntity::class, parentColumns = ["id"], childColumns = ["entityId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("entityId")]
)
@Serializable
data class AssertionEntityLink(
    val assertionId: String,
    val entityId: String,
    val role: String // LOC, ACTOR, SUBJECT; may repeat entity with different roles
)

@Entity(
    tableName = "assertion_topics",
    primaryKeys = ["assertionId", "topicId"],
    foreignKeys = [
        ForeignKey(entity = HistoricalAssertion::class, parentColumns = ["id"], childColumns = ["assertionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = HistoricalTopic::class, parentColumns = ["id"], childColumns = ["topicId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("topicId")]
)
@Serializable
data class AssertionTopicLink(
    val assertionId: String,
    val topicId: String
)

/** Includes time-varying geographic containment. The supporting assertion is optional,
 *  but relations without one must be displayed as unverified/provisional. */
@Entity(
    tableName = "entity_relations",
    foreignKeys = [
        ForeignKey(entity = HistoricalEntity::class, parentColumns = ["id"], childColumns = ["fromEntityId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = HistoricalEntity::class, parentColumns = ["id"], childColumns = ["toEntityId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = HistoricalAssertion::class, parentColumns = ["id"], childColumns = ["supportingAssertionId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("fromEntityId"), Index("toEntityId"), Index("supportingAssertionId")]
)
@Serializable
data class EntityRelation(
    @PrimaryKey val id: String = newId(),
    val fromEntityId: String,
    val toEntityId: String,
    val relationType: String, // PART_OF, RULES, CONFLICT, INFLUENCES, RELATED, etc.
    val yearStart: Int? = null,
    val yearEnd: Int? = null,
    val supportingAssertionId: String? = null,
    val comment: String = "",
    val createdAt: Long = nowMillis()
)

/** Immutable audit trail. Each edit stores the complete previous record (and links for claims). */
@Entity(tableName = "revisions", indices = [Index("recordType"), Index("recordId")])
@Serializable
data class RevisionEntry(
    @PrimaryKey val id: String = newId(),
    val recordType: String,
    val recordId: String,
    val previousJson: String,
    val changedAt: Long = nowMillis()
)

/** Researcher-authored synthesis. Never inserted into the evidence layer as a historical claim. */
@Entity(tableName = "research_drafts", indices = [Index("updatedAt")])
@Serializable
data class ResearchDraft(
    @PrimaryKey val id: String = newId(),
    val title: String,
    val content: String = "",
    val createdAt: Long = nowMillis(),
    val updatedAt: Long = nowMillis()
)

@Entity(
    tableName = "draft_claim_links",
    primaryKeys = ["draftId", "assertionId"],
    foreignKeys = [
        ForeignKey(entity = ResearchDraft::class, parentColumns = ["id"], childColumns = ["draftId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = HistoricalAssertion::class, parentColumns = ["id"], childColumns = ["assertionId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("assertionId")]
)
@Serializable
data class DraftClaimLink(val draftId: String, val assertionId: String)
