package ro.atlasistoric.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Embedded CameraX still-photo capture, without launching another camera app.
 *
 * The full-resolution JPEG is created in app-private cache, then passed to
 * DocumentRepository.importImage() by the parent. The parent preserves it as
 * an immutable original BEFORE starting OCR. The temporary file is disposed
 * after the import; a failed OCR never removes the archived photograph.
 */
@Composable
fun DocumentCameraCapture(
    onCaptured: (File) -> Unit,
    onCancel: () -> Unit
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember(ctx) {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var granted by remember(ctx) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }
    var shutter by remember { mutableStateOf<ImageCapture?>(null) }
    var ready by remember { mutableStateOf(false) }
    var taking by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    DisposableEffect(granted, owner, previewView) {
        var provider: ProcessCameraProvider? = null
        var photo: ImageCapture? = null
        var preview: Preview? = null
        val cancelled = AtomicBoolean(false)
        if (granted) {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                if (!cancelled.get()) {
                    try {
                        val cameraProvider = future.get()
                        val livePreview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val stillPhoto = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()
                        cameraProvider.bindToLifecycle(
                            owner, CameraSelector.DEFAULT_BACK_CAMERA,
                            livePreview, stillPhoto
                        )
                        if (cancelled.get()) {
                            cameraProvider.unbind(livePreview, stillPhoto)
                        } else {
                            provider = cameraProvider
                            photo = stillPhoto
                            preview = livePreview
                            shutter = stillPhoto
                            ready = true
                            failure = null
                        }
                    } catch (e: Exception) {
                        ready = false
                        shutter = null
                        failure = e.localizedMessage ?: "Nu am putut deschide camera."
                    }
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose {
            cancelled.set(true)
            ready = false
            shutter = null
            val p = preview
            val photoCapture = photo
            if (p != null && photoCapture != null) {
                provider?.unbind(p, photoCapture)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel, enabled = !taking) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Înapoi la documente")
            }
            Column {
                Text("Fotografiază pagina", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Camera Atlas Istoric · salvează originalul, apoi extrage textul prin OCR",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!granted) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Atlas are nevoie de permisiunea pentru cameră doar cât fotografiezi. " +
                            "Imaginea și textul OCR rămân pe dispozitiv."
                    )
                    Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Permite camera")
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                Box(
                    Modifier.align(Alignment.Center).fillMaxWidth(0.88f)
                        .fillMaxHeight(0.83f)
                        .border(
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                            RoundedCornerShape(12.dp)
                        )
                )
                if (!ready) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                "Încadrează întreaga pagină, evită umbrele și ține telefonul nemișcat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = ready && !taking,
                onClick = {
                    val capture = shutter ?: return@Button
                    failure = null
                    taking = true
                    val temporary = try {
                        File.createTempFile("atlas-page-", ".jpg", ctx.cacheDir)
                    } catch (e: Exception) {
                        failure = e.localizedMessage ?: "Nu pot crea fotografia temporară"
                        taking = false
                        return@Button
                    }
                    try {
                        // CameraX writes EXIF orientation for the captured still image.
                        capture.targetRotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                        val output = ImageCapture.OutputFileOptions.Builder(temporary).build()
                        capture.takePicture(
                            output,
                            ContextCompat.getMainExecutor(ctx),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                    taking = false
                                    if (temporary.length() <= 0L) {
                                        temporary.delete()
                                        failure = "Camera nu a produs o imagine validă. Încearcă din nou."
                                    } else {
                                        onCaptured(temporary)
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    temporary.delete()
                                    taking = false
                                    failure = exception.localizedMessage ?: "Fotografiere eșuată"
                                }
                            }
                        )
                    } catch (e: Exception) {
                        temporary.delete()
                        taking = false
                        failure = e.localizedMessage ?: "Fotografiere eșuată"
                    }
                }
            ) {
                if (taking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (taking) "Se fotografiază..." else "Fotografiază → extrage OCR")
            }
        }

        if (failure != null) Text(
            failure.orEmpty(), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        if (taking) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
