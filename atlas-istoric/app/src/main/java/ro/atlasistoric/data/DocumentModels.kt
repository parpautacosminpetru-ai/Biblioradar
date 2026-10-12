package ro.atlasistoric.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A source page is an immutable captured image plus separately amendable transcriptions.
 * storagePath is relative to the application's private files directory, NEVER a device URI.
 * sha256 verifies the original bytes; the original is not silently overwritten.
 */
@Entity(
    tableName = "document_pages",
    indices = [Index("sourceId"), Index(value = ["sourceId", "sha256"], unique = true)],
    foreignKeys = [ForeignKey(entity = HistoricalSource::class, parentColumns = ["id"],
        childColumns = ["sourceId"], onDelete = ForeignKey.RESTRICT)]
)
@Serializable
data class DocumentPage(
    @PrimaryKey val id: String = newId(),
    val sourceId: String,
    val pageLabel: String,
    val originalName: String,
    val mimeType: String,
    val storagePath: String,
    val sha256: String,
    val originalOcrText: String = "",
    val verifiedText: String = "",
    val status: String = "CAPTURED", // CAPTURED, TRANSCRIBED, VERIFIED, ANNOTATED
    val createdAt: Long = nowMillis(),
    val updatedAt: Long = nowMillis()
)

/** Text offsets are UTF-16 offsets into verifiedText, matching Android TextFieldValue.
 * The quote and neighboring context survive editing; invalidated offsets are flagged.
 */
@Entity(
    tableName = "page_annotations",
    foreignKeys = [ForeignKey(entity = DocumentPage::class, parentColumns = ["id"],
        childColumns = ["pageId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("pageId")]
)
@Serializable
data class PageAnnotation(
    @PrimaryKey val id: String = newId(),
    val pageId: String,
    val startOffset: Int,
    val endOffset: Int,
    val exactQuote: String,
    val contextBefore: String = "",
    val contextAfter: String = "",
    val category: String = "AFIRMATIE", // AFIRMATIE, PERSOANA, LOC, DATA, NOTA, INCERT
    val note: String = "",
    val createdAt: Long = nowMillis(),
    val updatedAt: Long = nowMillis()
)

/** Mapping a statement onto an annotated passage never changes the original document. */
@Entity(
    tableName = "annotation_claim_links",
    primaryKeys = ["annotationId", "assertionId"],
    foreignKeys = [
        ForeignKey(entity = PageAnnotation::class, parentColumns = ["id"], childColumns = ["annotationId"],
            onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = HistoricalAssertion::class, parentColumns = ["id"], childColumns = ["assertionId"],
            onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("assertionId")]
)
@Serializable
data class AnnotationClaimLink(val annotationId: String, val assertionId: String)
