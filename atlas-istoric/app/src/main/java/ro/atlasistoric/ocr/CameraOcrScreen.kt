package ro.atlasistoric.ocr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraOcrScreen(onUseText: (String) -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permitted by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    var liveText by remember { mutableStateOf("") }
    var frozenText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val previewView = remember { PreviewView(ctx) }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val working = remember { AtomicBoolean(false) }
    val freezeFlag = remember { AtomicBoolean(false) }
    DisposableEffect(recognizer) { onDispose { recognizer.close() } }

    DisposableEffect(permitted, lifecycleOwner, previewView) {
        var provider: ProcessCameraProvider? = null
        val cancelled = AtomicBoolean(false)
        if (permitted) {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                if (!cancelled.get()) try {
                    val ready = future.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                        if (freezeFlag.get() || !working.compareAndSet(false, true)) {
                            proxy.close()
                        } else {
                            val media = proxy.image
                            if (media == null) {
                                working.set(false)
                                proxy.close()
                            } else {
                                val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                recognizer.process(input)
                                    .addOnSuccessListener { r ->
                                        if (!freezeFlag.get() && r.text.isNotBlank()) liveText = r.text
                                    }
                                    .addOnFailureListener { e -> error = e.localizedMessage }
                                    .addOnCompleteListener {
                                        working.set(false)
                                        proxy.close()
                                    }
                            }
                        }
                    }
                    ready.unbindAll()
                    ready.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    provider = ready
                } catch (e: Exception) { error = e.localizedMessage ?: "Camera nu a putut fi pornită" }
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose { cancelled.set(true); provider?.unbindAll() }
    }

    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Extrage pasajul în direct", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary)
        Text("OCR pe dispozitiv · fotografiere continuă · nu se salvează automat nicio afirmație",
            style = MaterialTheme.typography.bodySmall)
        if (!permitted) {
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(50.dp))
                    Text("Permite accesul la cameră pentru a citi o pagină. Textul rămâne pe telefon.")
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Permite camera") }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(0.48f)) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().padding(14.dp).border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = .8f), RoundedCornerShape(16.dp)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { frozenText = liveText; freezeFlag.set(true) },
                    enabled = frozenText == null && liveText.isNotBlank()) { Text("Capturează pasajul") }
                OutlinedButton(onClick = { frozenText = null; freezeFlag.set(false) }) { Text("Reia scanarea") }
            }
            val content = frozenText ?: liveText
            OutlinedTextField(
                value = content, onValueChange = { frozenText = it; freezeFlag.set(true) },
                label = { Text(if (frozenText == null) "Text OCR live (atinge pentru corectură)" else "Corectează pasajul") },
                modifier = Modifier.fillMaxWidth().weight(0.52f), minLines = 3
            )
            Button(onClick = { onUseText(content) }, enabled = content.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Verifică sursa, pagina și încadrarea →")
            }
            if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
    }
}
