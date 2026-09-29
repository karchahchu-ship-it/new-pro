package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DeviceEntity
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline

enum class DeviceSortOption(val label: String) {
    DEFAULT("Par défaut"),
    IP_ADDRESS("Adresse IP"),
    HOSTNAME("Nom d'hôte"),
    RESPONSE_TIME("Temps de réponse")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResponsiveDeviceList(
    devices: List<DeviceEntity>,
    isScanning: Boolean,
    onOpenWeb: (DeviceEntity) -> Unit,
    onShowDetails: (DeviceEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterOnlyOnline by remember { mutableStateOf<Boolean?>(null) } // null = all, true = online, false = offline
    var sortOption by remember { mutableStateOf(DeviceSortOption.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }

    val filteredAndSortedDevices by remember(devices, searchQuery, filterOnlyOnline, sortOption) {
        derivedStateOf {
            devices
                .filter { device ->
                    val query = searchQuery.trim().lowercase()
                    if (query.isEmpty()) {
                        true
                    } else {
                        device.lastIp.lowercase().contains(query) ||
                                device.displayHostname.lowercase().contains(query) ||
                                device.displayName.lowercase().contains(query) ||
                                device.macAddress.lowercase().contains(query)
                    }
                }
                .filter { device ->
                    when (filterOnlyOnline) {
                        true -> device.isOnline
                        false -> !device.isOnline
                        null -> true
                    }
                }
                .let { list ->
                    when (sortOption) {
                        DeviceSortOption.DEFAULT -> list
                        DeviceSortOption.IP_ADDRESS -> list.sortedBy { it.lastIp }
                        DeviceSortOption.HOSTNAME -> list.sortedBy { it.displayHostname.lowercase() }
                        DeviceSortOption.RESPONSE_TIME -> list.sortedBy { it.responseTimeMs }
                    }
                }
        }
    }

    val onlineCount = devices.count { it.isOnline }
    val offlineCount = devices.size - onlineCount

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("responsive_device_list_container")
    ) {
        // Search and Filter Header (Visible when there are devices or during active search)
        if (devices.isNotEmpty() || searchQuery.isNotEmpty()) {
            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Rechercher par IP, Hostname, ou Nom...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Rechercher",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.testTag("clear_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Effacer la recherche",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("device_list_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips + Sort Menu Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filter Chips (Tous / En ligne / Hors ligne)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    FilterChip(
                        selected = filterOnlyOnline == null,
                        onClick = { filterOnlyOnline = null },
                        label = { Text("Tous (${devices.size})", fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("filter_all_chip")
                    )

                    FilterChip(
                        selected = filterOnlyOnline == true,
                        onClick = { filterOnlyOnline = if (filterOnlyOnline == true) null else true },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(StatusOnline)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("En ligne ($onlineCount)", fontSize = 12.sp)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("filter_online_chip")
                    )

                    if (offlineCount > 0) {
                        FilterChip(
                            selected = filterOnlyOnline == false,
                            onClick = { filterOnlyOnline = if (filterOnlyOnline == false) null else false },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(StatusOffline)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Hors ligne ($offlineCount)", fontSize = 12.sp)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("filter_offline_chip")
                        )
                    }
                }

                // Sort Dropdown button
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.testTag("sort_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Trier les appareils",
                            tint = if (sortOption != DeviceSortOption.DEFAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DeviceSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.label,
                                        fontWeight = if (option == sortOption) FontWeight.Bold else FontWeight.Normal,
                                        color = if (option == sortOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    sortOption = option
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle info count
            Text(
                text = "${filteredAndSortedDevices.size} appareil(s) trouvé(s)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Responsive Devices Layout using BoxWithConstraints
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isTabletOrWide = maxWidth >= 600.dp

            if (filteredAndSortedDevices.isNotEmpty()) {
                if (isTabletOrWide) {
                    // Wide screen / Tablet layout: 2-column Grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 340.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(((filteredAndSortedDevices.size / 2 + 1) * 230).dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        userScrollEnabled = false
                    ) {
                        items(
                            items = filteredAndSortedDevices,
                            key = { it.id }
                        ) { device ->
                            DeviceCard(
                                device = device,
                                onOpenWeb = onOpenWeb,
                                onShowDetails = onShowDetails
                            )
                        }
                    }
                } else {
                    // Compact Phone layout: Column of Cards
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        filteredAndSortedDevices.forEach { device ->
                            DeviceCard(
                                device = device,
                                onOpenWeb = onOpenWeb,
                                onShowDetails = onShowDetails
                            )
                        }
                    }
                }
            } else if (searchQuery.isNotEmpty()) {
                // Search empty state
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .testTag("empty_search_state"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Aucun appareil ne correspond à \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Vérifiez l'adresse IP, le nom d'hôte ou effacez la recherche.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (!isScanning) {
                // Global Empty State
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .testTag("empty_device_list_state"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🔌", fontSize = 28.sp)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Aucun appareil détecté",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "1. Le point d'accès est actif en arrière-plan (SSID: esp, Clé: 00000001).\n" +
                                    "2. Vos appareils doivent s'y connecter.\n" +
                                    "3. Assurez-vous que leur serveur Web écoute sur le port 80.\n" +
                                    "4. Cliquez sur SCANNER.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}
