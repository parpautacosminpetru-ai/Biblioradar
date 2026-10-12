package ro.atlasistoric.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.atlasistoric.data.AtlasDatabase
import ro.atlasistoric.data.BackupService
import ro.atlasistoric.data.AtlasArchiveZip
import ro.atlasistoric.data.ProtectedArchive

@Composable
fun BackupScreen(database: AtlasDatabase, onNotice: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val backup = remember(database) { BackupService(database) }
    val archive = remember(database, ctx) { AtlasArchiveZip(ctx, database) }
    val protected = remember(database, ctx) { ProtectedArchive(ctx, database) }
    var archivePassword by remember { mutableStateOf("") }
    var pendingProtectedImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var revealPassword by remember { mutableStateOf(false) }
    val protectedExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val pass = archivePassword.toCharArray()
            try {
                val count = withContext(Dispatchers.IO) {
                    protected.exportEncrypted(uri, pass)
                }
                onNotice("Arhiva .atlas a fost criptată, cu $count imagini originale")
            } catch (e: Exception) {
                onNotice("Export protejat eșuat: ${e.localizedMessage}")
            } finally {
                pass.fill('\u0000')
                archivePassword = ""
                busy = false
            }
        }
    }
    val protectedImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingProtectedImport = uri }
    val documentPages by database.dao().documentPages().collectAsState(initial = emptyList())
    val dbSize = remember(ctx) {
        val db = ctx.getDatabasePath("atlas_istoric.db")
        listOf(db, java.io.File(db.path + "-wal"), java.io.File(db.path + "-shm")).sumOf { it.length() }
    }
    val availableDisk = remember(ctx) { ctx.filesDir.usableSpace }
    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var pendingZip by remember { mutableStateOf<android.net.Uri?>(null) }
    val zipExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val count = withContext(Dispatchers.IO) { archive.exportZip(uri) }
                onNotice("Arhivă ZIP completă exportată, inclusiv $count fotografii originale")
            } catch (e: Exception) {
                onNotice("Export ZIP eșuat: ${e.localizedMessage}")
            } finally { busy = false }
        }
    }
    val zipImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingZip = uri
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val json = withContext(Dispatchers.IO) { backup.exportJson() }
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Nu se poate deschide destinația")
                }
                onNotice("Arhiva JSON a fost exportată")
            } catch (e: Exception) { onNotice("Export eșuat: ${e.localizedMessage}") }
            finally { busy = false }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                pendingImport = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Nu se poate citi arhiva")
                }
            } catch (e: Exception) { onNotice("Citire eșuată: ${e.localizedMessage}") }
            finally { busy = false }
        }
    }
    if (pendingProtectedImport != null) {
        AlertDialog(onDismissRequest = { pendingProtectedImport = null },
            title = { Text("Restabilești arhiva protejată?") },
            text = {
                Text("Se verifică parola și autentificarea AES-GCM înainte de a înlocui baza de date. " +
                    "Păstrează o copie verificată a datelor curente.")
            },
            confirmButton = {
                TextButton(enabled = !busy && archivePassword.length >= 12, onClick = {
                    val uri = pendingProtectedImport!!
                    pendingProtectedImport = null
                    scope.launch {
                        busy = true
                        val pass = archivePassword.toCharArray()
                        try {
                            val count = withContext(Dispatchers.IO) {
                                protected.importEncrypted(uri, pass)
                            }
                            onNotice("Arhivă protejată restaurată: $count imagini")
                        } catch (e: Exception) {
                            onNotice("Restaurarea protejată a eșuat: ${e.localizedMessage}")
                        } finally {
                            pass.fill('\u0000')
                            archivePassword = ""
                            busy = false
                        }
                    }
                }) { Text("Restabilește datele", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingProtectedImport = null }) { Text("Anulează") }
            })
    }
    if (pendingZip != null) {
        AlertDialog(onDismissRequest = { pendingZip = null },
            title = { Text("Restabilești arhiva ZIP completă?") },
            text = {
                Text("Importul înlocuiește baza actuală. Imaginile și identificatorii din ZIP sunt verificați înainte. " +
                    "Exportă mai întâi o copie a datelor actuale.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingZip!!
                    pendingZip = null
                    scope.launch {
                        busy = true
                        try {
                            val count = withContext(Dispatchers.IO) { archive.importZip(uri) }
                            onNotice("Arhivă ZIP restaurată cu $count imagini verificate")
                        } catch (e: Exception) {
                            onNotice("Import ZIP respins: ${e.localizedMessage}")
                        } finally { busy = false }
                    }
                }) { Text("Înlocuiește din ZIP", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingZip = null }) { Text("Anulează") }
            })
    }
    if (pendingImport != null) {
        AlertDialog(onDismissRequest = { pendingImport = null },
            title = { Text("Înlocuiești toate datele?") },
            text = { Text("IMPORT DESTRUCTIV: toate entitățile, sursele, afirmațiile, relațiile și temele existente vor fi înlocuite cu conținutul arhivei. Exportă întâi o copie a datelor curente.") },
            confirmButton = { TextButton(onClick = {
                val raw = pendingImport!!
                pendingImport = null
                scope.launch {
                    busy = true
                    try {
                        withContext(Dispatchers.IO) { backup.importReplaceJson(raw) }
                        onNotice("Restaurare reușită")
                    } catch (e: Exception) { onNotice("Arhivă respinsă: ${e.localizedMessage}") }
                    finally { busy = false }
                }
            }) { Text("Înlocuiește datele", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Anulează") } }
        )
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Datele aparțin cercetătorului", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary)
        Text("SQLite stochează datele local, fără cont și fără server. Pentru a le păstra la schimbarea telefonului, exportă periodic o copie într-un loc ales de tine.")
        Text("Bază locală: ${"%.2f".format(dbSize / 1048576.0)} MB · Spațiu disponibil: ${"%.1f".format(availableDisk / 1073741824.0)} GB",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        ElevatedCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("ARHIVĂ PROTEJATĂ — recomandată", style = MaterialTheme.typography.titleMedium)
                Text("Criptare AES-256-GCM cu parolă personală, include originalele, OCR, " +
                    "adnotările, sintezele și toate referințele.")
                OutlinedTextField(
                    value = archivePassword, onValueChange = { archivePassword = it },
                    label = { Text("Parola arhivei (minimum 12 caractere)") },
                    visualTransformation = if (revealPassword)
                        androidx.compose.ui.text.input.VisualTransformation.None
                    else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                TextButton(onClick = { revealPassword = !revealPassword }) {
                    Text(if (revealPassword) "Ascunde parola" else "Arată parola")
                }
                Button(enabled = !busy && archivePassword.length >= 12,
                    onClick = { protectedExporter.launch("atlas-istoric-protejat.atlas") }) {
                    Text("Exportă arhiva criptată")
                }
                OutlinedButton(enabled = !busy, onClick = {
                    protectedImporter.launch(arrayOf("application/octet-stream", "*/*"))
                }) { Text("Restabilește arhiva criptată") }
                Text("Parola nu este memorată. Dacă o pierzi, arhiva criptată nu poate fi recuperată. " +
                    "Testează restaurarea pe un alt dispozitiv înainte de a șterge originalele.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
        OutlinedCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Arhivă COMPLETĂ ZIP — necriptată", style = MaterialTheme.typography.titleMedium)
                Text("Include baza, OCR-ul, adnotările, sintezele, fișele de învățare și fotografiile originale.")
                Text("${documentPages.size} fotografii în baza locală · verificare SHA-256 la import",
                    style = MaterialTheme.typography.labelSmall)
                Button(enabled = !busy, onClick = {
                    zipExporter.launch("atlas-istoric-arhiva-completa.zip")
                }) { Text("Exportă ZIP cu imagini") }
                OutlinedButton(enabled = !busy, onClick = {
                    zipImporter.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }) { Text("Restabilește ZIP complet") }
                Text("ZIP-ul nu este criptat. Păstrează-l într-un loc protejat.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
        OutlinedCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("JSON — doar arhiva fără fotografii", style = MaterialTheme.typography.titleMedium)
                Text("Folosește-l numai dacă nu ai pagini fotografiate. Pentru cercetarea cu imagini, folosește ZIP.")
                Button(enabled = !busy && documentPages.isEmpty(),
                    onClick = { exporter.launch("atlas-istoric-backup.json") }) {
                    Text("Exportă JSON fără imagini")
                }
            }
        }
        OutlinedCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Restaurare", style = MaterialTheme.typography.titleMedium)
                Text("Importul necesită confirmare explicită și înlocuiește întreaga bază locală. Dacă arhiva nu este validă, tranzacția se anulează.")
                OutlinedButton(enabled = !busy, onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Text("Importă din fișier")
                }
            }
        }
        Hint("Importul JSON este blocat dacă există fotografii originale, pentru a preveni pierderea lor accidentală.")
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
