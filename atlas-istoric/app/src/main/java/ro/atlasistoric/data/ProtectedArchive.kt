package ro.atlasistoric.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.atlasistoric.util.ArchiveCrypto
import java.io.File
import java.io.FileOutputStream

/**
 * Protected full archive: encryption/decryption completes in a private temporary
 * file BEFORE an imported ZIP can replace any database records.
 * Passwords never enter the repository, database, logs, or ZIP contents.
 */
class ProtectedArchive(private val context: Context, private val db: AtlasDatabase) {
    private val archive = AtlasArchiveZip(context, db)

    suspend fun exportEncrypted(uri: Uri, password: CharArray): Int =
        withContext(Dispatchers.IO) {
            val plain = File.createTempFile("atlas-before-encrypt-", ".zip", context.cacheDir)
            try {
                val count = archive.exportZip(Uri.fromFile(plain))
                context.contentResolver.openOutputStream(uri)?.use { destination ->
                    plain.inputStream().buffered().use { original ->
                        ArchiveCrypto.encrypt(original, destination, password)
                    }
                } ?: error("Nu se poate scrie arhiva protejată")
                count
            } finally {
                plain.delete()
            }
        }

    suspend fun importEncrypted(uri: Uri, password: CharArray): Int =
        withContext(Dispatchers.IO) {
            val plain = File.createTempFile("atlas-after-decrypt-", ".zip", context.cacheDir)
            try {
                try {
                    context.contentResolver.openInputStream(uri)?.use { encrypted ->
                        FileOutputStream(plain).use { decrypted ->
                            ArchiveCrypto.decrypt(encrypted, decrypted, password)
                            decrypted.fd.sync()
                        }
                    } ?: error("Nu se poate citi arhiva protejată")
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "Parola este greșită, arhiva este deteriorată sau prea mare",
                        e
                    )
                }
                // Authentication completed successfully. ZIP now verifies its own page checksums.
                archive.importZip(Uri.fromFile(plain))
            } finally {
                plain.delete()
            }
        }
}
