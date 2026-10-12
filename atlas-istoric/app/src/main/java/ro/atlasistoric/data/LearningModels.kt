package ro.atlasistoric.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Learning material points to the original source-backed assertion.
 * The answer is never copied: amendments and bibliography stay attached to the source record.
 * Study prompts and personal notes are a SEPARATE editable layer, not historical evidence.
 */
@Entity(
    tableName = "study_cards",
    foreignKeys = [ForeignKey(
        entity = HistoricalAssertion::class,
        parentColumns = ["id"],
        childColumns = ["assertionId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("assertionId"), Index("dueAt")]
)
@Serializable
data class StudyCard(
    @PrimaryKey val id: String = newId(),
    val assertionId: String,
    val question: String,
    val answerMode: String = "PASAJ", // PASAJ, DATA, LOC; answer always derived from source-linked claim
    val note: String = "",
    val dueAt: Long = 0L,
    val intervalDays: Int = 0,
    val easePermille: Int = 2300,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val lastReviewedAt: Long? = null,
    val createdAt: Long = nowMillis(),
    val updatedAt: Long = nowMillis()
)

/** An append-only history of recall attempts; it never rewrites the evidence. */
@Entity(
    tableName = "study_reviews",
    foreignKeys = [ForeignKey(
        entity = StudyCard::class,
        parentColumns = ["id"],
        childColumns = ["cardId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("cardId"), Index("reviewedAt")]
)
@Serializable
data class StudyReview(
    @PrimaryKey val id: String = newId(),
    val cardId: String,
    val grade: Int, // 0 = Din nou, 1 = Greu, 2 = Bine, 3 = Ușor
    val reviewedAt: Long = nowMillis(),
    val previousIntervalDays: Int = 0,
    val nextIntervalDays: Int = 0,
    val nextDueAt: Long = 0L
)
