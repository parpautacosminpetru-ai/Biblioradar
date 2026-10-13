package ro.atlasistoric.util

import java.util.concurrent.CancellationException

/**
 * Safe photo-first transaction boundary:
 * - saveOriginal completes before OCR is even called;
 * - OCR failure preserves the saved original;
 * - cancellation is not swallowed by the OCR failure path.
 *
 * The original storage operation should be atomic and assign a stable page ID.
 */
data class PhotoOcrOutcome<Page>(
    val savedPage: Page,
    val recognizedText: String,
    val ocrProblem: Exception? = null
) {
    val hasRecognizedText: Boolean get() = recognizedText.isNotBlank()
}

/** Pure Kotlin coordinator, kept separately testable from CameraX and Android UI. */
suspend fun <Page> storeThenRecognize(
    saveOriginal: suspend () -> Page,
    onOriginalSaved: (Page) -> Unit = {},
    readText: suspend (Page) -> String
): PhotoOcrOutcome<Page> {
    val saved = saveOriginal() // If this fails, OCR must never run.
    onOriginalSaved(saved)
    return try {
        PhotoOcrOutcome(saved, readText(saved))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (recognitionFailed: Exception) {
        // Keep original's identity for retries; do not revert a successful photo capture.
        PhotoOcrOutcome(saved, "", recognitionFailed)
    }
}
