package ro.atlasistoric.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ro.atlasistoric.data.HistoricalEntity
import ro.atlasistoric.util.lookupGeographicPlaces

/**
 * Same searchable geographic selector on Atlas, overlapping grids and category
 * bands. Searches ALL local places, not just the first 12/28 chips.
 *
 * Historical identities are NEVER merged merely because their names match.
 * Adding new geographical entries is explicitly researcher-controlled.
 */
@Composable
fun RegionPicker(
    places: List<HistoricalEntity>,
    selectedIds: Set<String>,
    onSelected: (Set<String>) -> Unit,
    onCreatePlace: () -> Unit,
    compact: Boolean = false
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val knownById = remember(places) { places.filter { it.entityType == "PLACE" }.associateBy { it.id } }
    val chosen = selectedIds.mapNotNull { knownById[it] }.sortedBy { it.name }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { open = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Search, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (selectedIds.isEmpty()) "Caută regiuni și locuri"
                    else "Modifică regiunile (${selectedIds.size})"
                )
            }
            if (!compact) {
                IconButton(onClick = onCreatePlace) {
                    Icon(Icons.Default.Add, "Adaugă regiune nouă")
                }
            }
        }
        if (selectedIds.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                item {
                    InputChip(
                        selected = true,
                        onClick = { onSelected(emptySet()) },
                        label = { Text("Șterge filtrele") }
                    )
                }
                items(chosen, key = { it.id }) { p ->
                    InputChip(
                        selected = true,
                        onClick = { onSelected(selectedIds - p.id) },
                        label = { Text(p.name, maxLines = 1) }
                    )
                }
            }
        } else if (!compact) {
            Text(
                "${knownById.size} locuri în catalogul local. Căutarea include denumiri alternative.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (open) {
        var query by rememberSaveable { mutableStateOf("") }
        var limit by remember { mutableIntStateOf(35) }
        val result = remember(places, query, selectedIds, limit) {
            lookupGeographicPlaces(places, query, limit, selectedIds)
        }
        AlertDialog(
            onDismissRequest = { open = false },
            icon = { Icon(Icons.Default.Public, contentDescription = null) },
            title = { Text("Catalogul geografic") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            limit = 35
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Regiune, țară, oraș, nume istoric...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        singleLine = true
                    )
                    Text(
                        "${result.count} rezultate · ${selectedIds.size} selectate",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (result.count == 0) {
                        Text(
                            if (query.isBlank()) "Încă nu ai adăugat locuri."
                            else "Nicio potrivire locală. Poți crea o identitate nouă, fără să inventăm date.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(result.matches, key = { it.id }) { place ->
                            val active = place.id in selectedIds
                            Surface(
                                onClick = {
                                    onSelected(
                                        if (active) selectedIds - place.id
                                        else selectedIds + place.id
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (active) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = active,
                                        onCheckedChange = null)
                                    Spacer(Modifier.width(7.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(place.name, fontWeight = if (active)
                                            FontWeight.SemiBold else FontWeight.Normal)
                                        if (place.aliases.isNotBlank()) {
                                            Text(place.aliases.take(120),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        if (result.count > result.matches.size) {
                            item {
                                OutlinedButton(
                                    onClick = { limit = (limit + 45).coerceAtMost(1000) },
                                    enabled = limit < 1000,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Mai multe (${result.count - result.matches.size} rămase)")
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            open = false
                            onCreatePlace()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Adaugă o regiune / un loc nou")
                    }
                    Text(
                        "O regiune istorică trebuie datată și documentată; " +
                            "un nume modern nu dovedește frontierele din trecut.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("Gata") }
            },
            dismissButton = {
                TextButton(onClick = { onSelected(emptySet()) }) {
                    Text("Toate regiunile")
                }
            }
        )
    }
}
