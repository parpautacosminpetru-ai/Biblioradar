package ro.atlasistoric.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class PhotoOcrWorkflowTest {
    @Test fun originalIsSavedBeforeOcrRuns() = runBlocking {
        val steps = mutableListOf<String>()
        val result = storeThenRecognize(
            saveOriginal = { steps.add("save-photo"); "image-001" },
            onOriginalSaved = { steps.add("show-$it") },
            readText = { photo -> steps.add("ocr-$photo"); "Text recunoscut" }
        )
        assertEquals(listOf("save-photo", "show-image-001", "ocr-image-001"), steps)
        assertEquals("image-001", result.savedPage)
        assertEquals("Text recunoscut", result.recognizedText)
        assertNull(result.ocrProblem)
    }

    @Test fun failedOcrNeverDeletesTheSavedOriginal() = runBlocking {
        val stored = mutableListOf<String>()
        val result = storeThenRecognize(
            saveOriginal = { stored.add("original-001"); "original-001" },
            readText = { throw IllegalStateException("Lumina prea slabă") }
        )
        assertEquals(listOf("original-001"), stored)
        assertEquals("original-001", result.savedPage)
        assertFalse(result.hasRecognizedText)
        assertNotNull(result.ocrProblem)
    }

    @Test fun failedPhotoSaveMustNotStartOcr() = runBlocking {
        var ocrCalled = false
        val error = runCatching {
            storeThenRecognize(
                saveOriginal = { throw IllegalArgumentException("Fără sursă") },
                readText = { ocrCalled = true; "Never" }
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertFalse(ocrCalled)
    }

    @Test fun blankOcrResultDoesNotInventText() = runBlocking {
        val result = storeThenRecognize(
            saveOriginal = { 42 },
            readText = { "" }
        )
        assertEquals(42, result.savedPage)
        assertFalse(result.hasRecognizedText)
        assertEquals("", result.recognizedText)
        assertNull(result.ocrProblem)
    }

    @Test fun cancellationPropagatesRatherThanBeingHidden() = runBlocking {
        val error = runCatching {
            storeThenRecognize(
                saveOriginal = { "page" },
                readText = { throw CancellationException("screen closed") }
            )
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }
}
