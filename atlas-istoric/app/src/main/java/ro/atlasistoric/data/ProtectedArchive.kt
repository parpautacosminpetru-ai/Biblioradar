package ro.atlasistoric.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable protected backup. A complete ZIP archive is encrypted with AES-256-GCM
 * using a user-supplied passphrase and random salt/nonce. No password is stored.
 *
 * Wire format: 9-byte magic + 16-byte salt + 12-byte nonce + encrypted ZIP + 16-byte tag.
 * Refuse incorrect passwords BEFORE invoking the destructive ZIP restore.
 * Temporary plaintext ZIPs reside briefly in app-private cache, always deleted in finally.
 */
class ProtectedArchive(private val context: Context, private val db: AtlasDatabase) {
    private val zip = AtlasArchiveZip(context, db)
    private val random = SecureRandom()
    private val magic = "ATLASENC1".toByteArray(Charsets.US_ASCII)
    private val maxBytes = 2L * 1024L * 1024L * 1024L

    private fun derive(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        require(passphrase.size >= 12) { "Parola arhivei trebuie să aibă minimum 12 caractere" }
        val spec = PBEKeySpec(passphrase, salt, 210_000, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun cryptCipher(mode: Int, passphrase: CharArray, salt: ByteArray, nonce: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, derive(passphrase, salt), GCMParameterSpec(128, nonce))
        // The header is authenticated to prevent changing format/salt/nonce silently.
        cipher.updateAAD(magic + salt + nonce)
        return cipher
    }

    suspend fun exportEncrypted(uri: Uri, passphrase: CharArray): Int =
        withContext(Dispatchers.IO) {
            val plain = File.createTempFile("atlas-before-encrypt-", ".zip", context.cacheDir)
            try {
                val count = zip.exportZip(Uri.fromFile(plain))
                val salt = ByteArray(16).also { random.nextBytes(it) }
                val nonce = ByteArray(12).also { random.nextBytes(it) }
                val cipher = cryptCipher(Cipher.ENCRYPT_MODE, passphrase, salt, nonce)
                context.contentResolver.openOutputStream(uri)?.use { destination ->
                    destination.write(magic)
                    destination.write(salt)
                    destination.write(nonce)
                    CipherOutputStream(destination, cipher).use { encrypting ->
                        plain.inputStream().buffered().use { input ->
                            input.copyTo(encrypting, bufferSize = 65536)
                        }
                    }
                } ?: error("Nu poate fi deschisă destinația pentru arhiva protejată")
                count
            } finally {
                plain.delete()
            }
        }

    suspend fun importEncrypted(uri: Uri, passphrase: CharArray): Int =
        withContext(Dispatchers.IO) {
            val plain = File.createTempFile("atlas-after-decrypt-", ".zip", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { encrypted ->
                    val header = ByteArray(magic.size + 16 + 12)
                    var done = 0
                    while (done < header.size) {
                        val count = encrypted.read(header, done, header.size - done)
                        if (count < 0) error("Arhiva protejată este incompletă")
                        done += count
                    }
                    require(header.copyOfRange(0, magic.size).contentEquals(magic)) {
                        "Format necunoscut: selectează o arhivă .atlas"
                    }
                    val salt = header.copyOfRange(magic.size, magic.size + 16)
                    val nonce = header.copyOfRange(magic.size + 16, header.size)
                    val cipher = cryptCipher(Cipher.DECRYPT_MODE, passphrase, salt, nonce)
                    try {
                        CipherInputStream(encrypted, cipher).use { decrypted ->
                            FileOutputStream(plain).use { output ->
                                val buffer = ByteArray(65536)
                                var bytes = 0L
                                while (true) {
                                    val count = decrypted.read(buffer)
                                    if (count < 0) break
                                    bytes += count
                                    require(bytes <= maxBytes) {
                                        "Arhiva depășește limita pentru restaurare"
                                    }
                                    output.write(buffer, 0, count)
                                }
                                output.fd.sync()
                            }
                        }
                    } catch (e: Exception) {
                        throw IllegalArgumentException(
                            "Parola este greșită, arhiva este deteriorată sau depășește limita admisă",
                            e
                        )
                    }
                } ?: error("Arhiva nu poate fi citită")
                // Complete authentication has succeeded; ONLY NOW can backup replace records.
                zip.importZip(Uri.fromFile(plain))
            } finally {
                plain.delete()
            }
        }
}
