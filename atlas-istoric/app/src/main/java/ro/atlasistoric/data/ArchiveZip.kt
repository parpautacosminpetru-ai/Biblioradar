package ro.atlasistoric.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Complete, offline, portable archive: atlas.json AND all immutable page originals.
 * Files are verified before ANY destructive database operation on restore.
 * ZIP is not encrypted: researchers must store it in a protected location.
 */
class AtlasArchiveZip(private val context: Context, private val db: AtlasDatabase) {
    private val backup = BackupService(db)
    private val docs = DocumentRepository(context, db)
    private val maxTotalBytes = 2L * 1024 * 1024 * 1024
    private val maxJsonBytes = 100L * 1024 * 1024

    private fun shaHex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun shaFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun exportZip(uri: Uri): Int = withContext(Dispatchers.IO) {
        val snapshot = backup.exportSnapshot()
        backup.validate(snapshot)
        val file = File.createTempFile("atlas-export-", ".zip", context.cacheDir)
        try {
            ZipOutputStream(FileOutputStream(file).buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("atlas.json"))
                zip.write(backup.encodeSnapshot(snapshot).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                snapshot.documentPages.forEach { page ->
                    val original = docs.fileFor(page)
                    require(original.isFile && original.length() > 0) {
                        "Pagina ${page.pageLabel} nu are imaginea originală pe telefon"
                    }
                    require(shaFile(original) == page.sha256) {
                        "Integritatea imaginii ${page.pageLabel} nu corespunde bazei de date"
                    }
                    val path = "pages/${page.id}${DocumentRepository.mimeExtension(page.mimeType)}"
                    zip.putNextEntry(ZipEntry(path))
                    original.inputStream().buffered().use { it.copyTo(zip, bufferSize = 65536) }
                    zip.closeEntry()
                }
            }
            context.contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().buffered().use { it.copyTo(output, bufferSize = 65536) }
            } ?: error("Nu s-a putut scrie arhiva ZIP")
            snapshot.documentPages.size
        } finally {
            file.delete()
        }
    }

    /**
     * Import is replace-only by explicit user confirmation. New images use their
     * own unique directory, so unsuccessful validation never overwrites old originals.
     * Old image files are deliberately retained after a replacement for recovery.
     */
    suspend fun importZip(uri: Uri): Int = withContext(Dispatchers.IO) {
        val root = "atlas_pages/import_${UUID.randomUUID()}"
        val directory = File(context.filesDir, root).apply { mkdirs() }
        val observedHashes = mutableMapOf<String, String>()
        val seenNames = mutableSetOf<String>()
        var jsonText: String? = null
        var totalBytes = 0L
        try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        require(!entry.isDirectory) { "Director neașteptat în arhivă" }
                        require(seenNames.add(entry.name)) { "Fișier duplicat în ZIP" }
                        if (entry.name == "atlas.json") {
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(65536)
                            var jsonBytes = 0L
                            while (true) {
                                val n = zip.read(buffer)
                                if (n < 0) break
                                jsonBytes += n
                                totalBytes += n
                                require(jsonBytes <= maxJsonBytes && totalBytes <= maxTotalBytes) {
                                    "Arhivă prea mare pentru import în siguranță"
                                }
                                output.write(buffer, 0, n)
                            }
                            jsonText = output.toString("UTF-8")
                        } else {
                            val pattern = Regex("^pages/([a-f0-9-]{36})\\.(jpg|png|webp|heic)$")
                            require(pattern.matches(entry.name)) { "Cale neașteptată în ZIP" }
                            val filename = entry.name.removePrefix("pages/")
                            val destination = File(directory, filename)
                            val digest = MessageDigest.getInstance("SHA-256")
                            var length = 0L
                            FileOutputStream(destination).use { output ->
                                val buffer = ByteArray(65536)
                                while (true) {
                                    val n = zip.read(buffer)
                                    if (n < 0) break
                                    length += n
                                    totalBytes += n
                                    require(length <= DocumentRepository.MAX_IMAGE_BYTES &&
                                        totalBytes <= maxTotalBytes) { "Imagine ori arhivă prea mare" }
                                    digest.update(buffer, 0, n)
                                    output.write(buffer, 0, n)
                                }
                                output.fd.sync()
                            }
                            require(length > 0) { "Imagine goală în ZIP" }
                            observedHashes[entry.name] = digest.digest()
                                .joinToString("") { "%02x".format(it) }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: error("Nu s-a putut deschide arhiva")
            val snapshot = backup.decodeSnapshot(jsonText ?: error("Lipsește atlas.json"))
            require(snapshot.format == "atlas-istoric-json" && snapshot.schemaVersion in 1..4) {
                "Arhivă neacceptată"
            }
            backup.validate(snapshot)
            val expected = snapshot.documentPages.associate { page ->
                "pages/${page.id}${DocumentRepository.mimeExtension(page.mimeType)}" to page.sha256
            }
            require(observedHashes == expected) {
                "Arhiva nu conține toate imaginile sau acestea nu corespund SHA-256"
            }
            val adjusted = snapshot.copy(documentPages = snapshot.documentPages.map { page ->
                page.copy(storagePath = "$root/${page.id}${DocumentRepository.mimeExtension(page.mimeType)}")
            })
            backup.importReplaceSnapshot(adjusted, withMedia = true)
            adjusted.documentPages.size
        } catch (ex: Exception) {
            directory.deleteRecursively()
            throw ex
        }
    }
}
