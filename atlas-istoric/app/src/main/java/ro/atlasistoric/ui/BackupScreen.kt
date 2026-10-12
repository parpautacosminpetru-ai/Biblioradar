package ro.atlasistoric.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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

@Composable
fun BackupScreen(database: AtlasDatabase, onNotice: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val backup = remember(database) { BackupService(database) }
    val dbSize = remember(ctx) {
        val db = ctx.getDatabasePath("atlas_istoric.db")
        listOf(db, java.io.File(db.path + "-wal"), java.io.File(db.path + "-shm")).sumOf { it.length() }
    }
    val availableDisk = remember(ctx) { ctx.filesDir.usableSpace }
    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
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
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Datele aparțin cercetătorului", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary)
        Text("SQLite stochează datele local, fără cont și fără server. Pentru a le păstra la schimbarea telefonului, exportă periodic o copie într-un loc ales de tine.")
        Text("Bază locală: ${"%.2f".format(dbSize / 1048576.0)} MB · Spațiu disponibil: ${"%.1f".format(availableDisk / 1073741824.0)} GB",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        ElevatedCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Exportă arhiva", style = MaterialTheme.typography.titleMedium)
                Text("JSON complet, cu identificatori, surse, pagini, afirmații și toate relațiile.")
                Button(enabled = !busy, onClick = { exporter.launch("atlas-istoric-backup.json") }) { Text("Exportă JSON") }
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
        Hint("Imaginile paginilor nu sunt incluse în această primă arhivă: versiunea inițială salvează doar textul extras și metadatele.")
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
