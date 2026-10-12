package ro.atlasistoric.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ro.atlasistoric.util.selectedTextAnchor
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Original page binaries are immutable, private local files; Room stores their provenance. */
class DocumentRepository(private val context: Context, private val db: AtlasDatabase) {
    private val dao = db.dao()
    private val json = Json { encodeDefaults = true }

    companion object {
        const val MAX_IMAGE_BYTES = 40L * 1024L * 1024L
        private val allowed = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
        fun mimeExtension(mime: String) = when (mime) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/heic", "image/heif" -> ".heic"
            else -> ".jpg"
        }
    }

    fun fileFor(page: DocumentPage): File {
        require(page.storagePath.startsWith("atlas_pages/") &&
            !page.storagePath.contains("..") && !page.storagePath.contains('\\')) {
            "Cale invalidă pentru original"
        }
        val root = File(context.filesDir, "atlas_pages").canonicalFile
        val file = File(context.filesDir, page.storagePath).canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "Fișier în afara arhivei private" }
        return file
    }

    suspend fun importImage(sourceId: String, pageLabel: String, uri: Uri): DocumentPage =
        withContext(Dispatchers.IO) {
            require(pageLabel.trim().isNotBlank()) { "Completează pagina sau fila" }
            require(dao.allSources().any { it.id == sourceId }) { "Alege sursa originală" }
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            require(mime in allowed) { "Format neacceptat. Folosește JPG, PNG, WebP sau HEIC" }
            val id = newId()
            val relative = "atlas_pages/$id${mimeExtension(mime)}"
            val target = File(context.filesDir, relative)
            target.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            size += n
                            require(size <= MAX_IMAGE_BYTES) { "Fotografia este prea mare (maxim 40 MB)" }
                            digest.update(buffer, 0, n)
                            output.write(buffer, 0, n)
                        }
                        output.fd.sync()
                    }
                } ?: error("Nu s-a putut citi fotografia")
                require(size > 0) { "Imaginea nu conține date" }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                val duplicate = dao.documentPageByHash(sourceId, hash)
                if (duplicate != null) {
                    target.delete()
                    return@withContext duplicate
                }
                val result = DocumentPage(id = id, sourceId = sourceId,
                    pageLabel = pageLabel.trim(), originalName = uri.lastPathSegment.orEmpty().take(120),
                    mimeType = mime, storagePath = relative, sha256 = hash)
                dao.insertDocumentPage(result)
                result
            } catch (ex: Exception) {
                target.delete()
                throw ex
            }
        }

    suspend fun recognizePage(pageId: String): DocumentPage {
        val page = withContext(Dispatchers.IO) {
            dao.documentPageById(pageId) ?: error("Pagina nu mai există")
        }
        val original = fileFor(page)
        require(original.isFile) { "Fotografia originală lipsește. Restabilește arhiva ZIP." }
        val raw = suspendCancellableCoroutine<String> { continuation ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val input = InputImage.fromFilePath(context, Uri.fromFile(original))
                recognizer.process(input)
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result.text)
                    }.addOnFailureListener { e ->
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }.addOnCompleteListener { recognizer.close() }
            } catch (e: Exception) {
                recognizer.close()
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                val current = dao.documentPageById(pageId) ?: error("Pagina a dispărut")
                if (current.originalOcrText.isNotBlank()) {
                    dao.insertRevision(RevisionEntry(recordType = "PAGE_OCR", recordId = pageId,
                        previousJson = json.encodeToString(current)))
                }
                val changed = current.copy(
                    originalOcrText = raw,
                    verifiedText = if (current.verifiedText.isBlank()) raw else current.verifiedText,
                    status = if (current.status == "CAPTURED") "TRANSCRIBED" else current.status,
                    updatedAt = nowMillis()
                )
                dao.updateDocumentPage(changed)
                changed
            }
        }
    }

    suspend fun saveVerifiedText(pageId: String, corrected: String, confirmed: Boolean) =
        withContext(Dispatchers.IO) {
            require(corrected.length <= 3_000_000) { "Transcriere prea mare pentru o pagină" }
            db.withTransaction {
                val old = dao.documentPageById(pageId) ?: error("Pagina nu există")
                if (old.verifiedText == corrected &&
                    old.status == (if (confirmed) "VERIFIED" else "TRANSCRIBED")) return@withTransaction
                dao.insertRevision(RevisionEntry(recordType = "PAGE", recordId = pageId,
                    previousJson = json.encodeToString(old)))
                dao.updateDocumentPage(old.copy(verifiedText = corrected,
                    status = if (confirmed) "VERIFIED" else "TRANSCRIBED",
                    updatedAt = nowMillis()))
            }
        }

    suspend fun annotate(pageId: String, start: Int, end: Int, category: String, note: String) =
        withContext(Dispatchers.IO) {
            require(category in setOf("AFIRMATIE", "PERSOANA", "LOC", "DATA", "NOTA", "INCERT",
                "SEGMENT_VERIFICAT")) {
                "Categorie de adnotare necunoscută"
            }
            db.withTransaction {
                val page = dao.documentPageById(pageId) ?: error("Pagina nu există")
                require(page.verifiedText.isNotEmpty()) { "Transcrie și verifică pagina înainte" }
                if (category == "SEGMENT_VERIFICAT") require(page.status == "VERIFIED") {
                    "Confirmă întâi transcrierea înainte să marchezi segmentele parcurse"
                }
                val annotation = selectedTextAnchor(pageId, page.verifiedText, start, end, category, note)
                dao.pageAnnotationsByPage(pageId).firstOrNull {
                    it.category == annotation.category &&
                        it.exactQuote == annotation.exactQuote &&
                        it.startOffset == annotation.startOffset &&
                        it.endOffset == annotation.endOffset
                }?.let { return@withTransaction it }
                dao.insertPageAnnotation(annotation)
                annotation
            }
        }

    /** The source of the claim MUST match the original book/page; link is atomic. */
    suspend fun linkClaim(annotationId: String, assertionId: String) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val annotation = dao.pageAnnotationById(annotationId)
                    ?: error("Adnotarea nu există")
                val page = dao.documentPageById(annotation.pageId)
                    ?: error("Pagina originală lipsește")
                val claim = dao.assertionById(assertionId)
                    ?: error("Afirmația nu există")
                require(claim.sourceId == page.sourceId) {
                    "Afirmația nu aparține aceleiași surse"
                }
                dao.insertAnnotationClaimLink(AnnotationClaimLink(annotationId, assertionId))
            }
        }
}
