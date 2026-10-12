package ro.atlasistoric.util

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

/** Authenticated, portable ZIP encryption; pure JVM code with offline unit tests. */
object ArchiveCrypto {
    private val magic = "ATLASENC1".toByteArray(Charsets.US_ASCII)
    private const val saltLen = 16
    private const val nonceLen = 12
    private const val iterations = 210_000

    private fun cipher(mode: Int, passphrase: CharArray, salt: ByteArray, nonce: ByteArray): Cipher {
        require(passphrase.size >= 12) { "Parola trebuie să aibă minimum 12 caractere" }
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        val encoded = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
        val key = try { SecretKeySpec(encoded, "AES") }
            finally { encoded.fill(0) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD(magic + salt + nonce)
        return cipher
    }

    fun encrypt(input: InputStream, output: OutputStream, passphrase: CharArray) {
        val random = SecureRandom()
        val salt = ByteArray(saltLen).also(random::nextBytes)
        val nonce = ByteArray(nonceLen).also(random::nextBytes)
        val transform = cipher(Cipher.ENCRYPT_MODE, passphrase, salt, nonce)
        output.write(magic)
        output.write(salt)
        output.write(nonce)
        CipherOutputStream(output, transform).use { authenticated ->
            input.copyTo(authenticated, bufferSize = 65536)
        }
    }

    /** Decrypt fully BEFORE any database mutation; AES-GCM authentication is at stream end. */
    fun decrypt(
        input: InputStream,
        output: OutputStream,
        passphrase: CharArray,
        maxPlainBytes: Long = 2L * 1024L * 1024L * 1024L
    ) {
        require(maxPlainBytes > 0)
        val header = ByteArray(magic.size + saltLen + nonceLen)
        var offset = 0
        while (offset < header.size) {
            val count = input.read(header, offset, header.size - offset)
            require(count >= 0) { "Arhivă criptată incompletă" }
            offset += count
        }
        require(header.copyOfRange(0, magic.size).contentEquals(magic)) {
            "Arhivă .atlas necunoscută"
        }
        val salt = header.copyOfRange(magic.size, magic.size + saltLen)
        val nonce = header.copyOfRange(magic.size + saltLen, header.size)
        val transform = cipher(Cipher.DECRYPT_MODE, passphrase, salt, nonce)
        CipherInputStream(input, transform).use { authenticated ->
            val buffer = ByteArray(65536)
            var copied = 0L
            while (true) {
                val count = authenticated.read(buffer)
                if (count < 0) break
                copied += count
                require(copied <= maxPlainBytes) { "Arhiva decriptată depășește limita" }
                output.write(buffer, 0, count)
            }
        }
        output.flush()
    }
}
