package ro.atlasistoric.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ArchiveCryptoTest {
    private val password = "O parola sigura pentru atlas 2026".toCharArray()
    private val original = ("Pagina 12: eveniment, actor, sursă, referință.\n" +
        "Toate diacriticele: ăâîșț.\n").repeat(50).toByteArray(Charsets.UTF_8)

    private fun encryptedBytes(): ByteArray {
        val buffer = ByteArrayOutputStream()
        ArchiveCrypto.encrypt(ByteArrayInputStream(original), buffer, password)
        return buffer.toByteArray()
    }

    @Test fun encryptedFullArchiveRoundTripsWithoutLoss() {
        val ciphertext = encryptedBytes()
        assertTrue(ciphertext.size > 64)
        assertFalse(ciphertext.contentEquals(original))
        val restored = ByteArrayOutputStream()
        ArchiveCrypto.decrypt(ByteArrayInputStream(ciphertext), restored, password)
        assertArrayEquals(original, restored.toByteArray())
    }

    @Test fun userPasswordIsRequiredAndWrongPasswordFailsAuthentication() {
        val ciphertext = encryptedBytes()
        val error = runCatching {
            ArchiveCrypto.decrypt(ByteArrayInputStream(ciphertext),
                ByteArrayOutputStream(), "O alta parola complet diferita".toCharArray())
        }.exceptionOrNull()
        assertNotNull("AES-GCM must refuse invalid passphrases", error)
    }

    @Test fun changingSingleCiphertextByteInvalidatesArchive() {
        val ciphertext = encryptedBytes()
        ciphertext[ciphertext.size - 23] = (ciphertext[ciphertext.size - 23].toInt() xor 1).toByte()
        val error = runCatching {
            ArchiveCrypto.decrypt(ByteArrayInputStream(ciphertext),
                ByteArrayOutputStream(), password)
        }.exceptionOrNull()
        assertNotNull("AES-GCM must refuse tampered archive", error)
    }

    @Test fun wrongFormatAndTinyPasswordAreRejected() {
        val error = runCatching {
            ArchiveCrypto.decrypt(ByteArrayInputStream(original), ByteArrayOutputStream(), password)
        }.exceptionOrNull()
        assertNotNull(error)
        val short = runCatching {
            ArchiveCrypto.encrypt(ByteArrayInputStream(original), ByteArrayOutputStream(),
                "short".toCharArray())
        }.exceptionOrNull()
        assertNotNull(short)
    }

    @Test fun noUnlimitedDecompressionIsAllowed() {
        val ciphertext = encryptedBytes()
        val failure = runCatching {
            ArchiveCrypto.decrypt(ByteArrayInputStream(ciphertext), ByteArrayOutputStream(),
                password, maxPlainBytes = 90L)
        }.exceptionOrNull()
        assertNotNull(failure)
    }
}
