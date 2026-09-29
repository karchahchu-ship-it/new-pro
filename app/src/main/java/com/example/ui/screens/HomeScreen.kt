package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.network.ChromeLauncher
import com.example.ui.ScannerViewModel
import com.example.ui.components.DeviceDetailsSheet
import com.example.ui.components.NetworkHeaderCard
import com.example.ui.components.RenameDeviceDialog
import com.example.ui.components.ResponsiveDeviceList
import com.example.ui.components.ScanStatusBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val networkInfo by viewModel.networkInfo.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val hotspotState by viewModel.hotspotState.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDeviceForDetails.collectAsStateWithLifecycle()
    val deviceToRename by viewModel.deviceToRename.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Auto Hotspot Permissions launcher on startup (in background without buttons)
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        if (granted) {
            viewModel.startHotspot()
        }
    }

    LaunchedEffect(Unit) {
        if (!viewModel.hasHotspotPermissions()) {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            permissionsLauncher.launch(perms.toTypedArray())
        } else {
            viewModel.startHotspot()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "📡", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Network Scanner",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (scanProgress.isScanning) "Scan en cours..." else "Prêt à scanner",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (scanProgress.isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (scanProgress.isScanning) {
                        IconButton(
                            onClick = { viewModel.stopScan() },
                            modifier = Modifier.testTag("stop_scan_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Arrêter le scan",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.refreshNetworkInfo() },
                            modifier = Modifier.testTag("refresh_network_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualiser le réseau",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("home_device_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Current Network Details Card with Background Hotspot Status
            item(key = "network_card") {
                NetworkHeaderCard(
                    networkInfo = networkInfo,
                    hotspotState = hotspotState,
                    onRefreshNetwork = { viewModel.refreshNetworkInfo() }
                )
            }

            // 2. Scan Actions & Progress Bar
            item(key = "scan_bar") {
                ScanStatusBar(
                    progress = scanProgress,
                    deviceCount = devices.size,
                    onStartScan = { viewModel.startScan() },
                    onRefresh = { viewModel.startScan() }
                )
            }

            // 3. Responsive Discovered Devices List (with IP addresses, Hostnames, Search & Sorting)
            item(key = "device_list_component") {
                ResponsiveDeviceList(
                    devices = devices,
                    isScanning = scanProgress.isScanning,
                    onOpenWeb = { clickedDevice ->
                        ChromeLauncher.openWebInterface(context, clickedDevice.webUrl)
                    },
                    onShowDetails = { clickedDevice ->
                        viewModel.selectDeviceForDetails(clickedDevice)
                    }
                )
            }
        }
    }

    // Modal Details Sheet
    if (selectedDevice != null) {
        DeviceDetailsSheet(
            device = selectedDevice,
            sheetState = sheetState,
            onDismiss = { viewModel.selectDeviceForDetails(null) },
            onOpenBrowser = { dev ->
                ChromeLauncher.openWebInterface(context, dev.webUrl)
            },
            onCopyIp = { ip ->
                ChromeLauncher.copyToClipboard(context, ip)
            },
            onRename = { dev ->
                viewModel.selectDeviceToRename(dev)
            },
            onDelete = { id ->
                viewModel.deleteDevice(id)
            }
        )
    }

    // Rename Dialog
    if (deviceToRename != null) {
        RenameDeviceDialog(
            device = deviceToRename!!,
            onDismiss = { viewModel.selectDeviceToRename(null) },
            onSave = { id, newName, newType ->
                viewModel.renameDevice(id, newName, newType)
            }
        )
    }
}
