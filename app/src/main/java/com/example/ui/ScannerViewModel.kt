package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.DeviceEntity
import com.example.data.model.DeviceType
import com.example.data.model.NetworkInfo
import com.example.data.model.ScanProgress
import com.example.data.repository.DeviceRepository
import com.example.network.HotspotManager
import com.example.network.HotspotState
import com.example.network.NetworkScanner
import com.example.network.NetworkUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScannerViewModel(
    application: Application,
    private val repository: DeviceRepository,
    private val networkScanner: NetworkScanner = NetworkScanner(),
    private val hotspotManager: HotspotManager = HotspotManager(application)
) : AndroidViewModel(application) {

    val devices: StateFlow<List<DeviceEntity>> = repository.allDevices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val hotspotState: StateFlow<HotspotState> = hotspotManager.state

    private val _networkInfo = MutableStateFlow<NetworkInfo?>(null)
    val networkInfo: StateFlow<NetworkInfo?> = _networkInfo.asStateFlow()

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _selectedDeviceForDetails = MutableStateFlow<DeviceEntity?>(null)
    val selectedDeviceForDetails: StateFlow<DeviceEntity?> = _selectedDeviceForDetails.asStateFlow()

    private val _deviceToRename = MutableStateFlow<DeviceEntity?>(null)
    val deviceToRename: StateFlow<DeviceEntity?> = _deviceToRename.asStateFlow()

    private var scanJob: Job? = null

    init {
        refreshNetworkInfo()
        tryAutoStartHotspot()
    }

    fun hasHotspotPermissions(): Boolean {
        return hotspotManager.hasRequiredPermissions()
    }

    fun tryAutoStartHotspot() {
        if (hasHotspotPermissions()) {
            startHotspot()
        }
    }

    fun startHotspot() {
        hotspotManager.startHotspot()
        viewModelScope.launch {
            delay(1500)
            refreshNetworkInfo()
            delay(2000)
            refreshNetworkInfo()
        }
    }

    fun stopHotspot() {
        hotspotManager.stopHotspot()
        viewModelScope.launch {
            delay(500)
            refreshNetworkInfo()
        }
    }

    fun openHotspotSettings(context: Context) {
        hotspotManager.openHotspotSettings(context)
    }

    fun refreshNetworkInfo() {
        viewModelScope.launch {
            try {
                val info = NetworkUtils.detectCurrentNetwork(getApplication())
                _networkInfo.value = info
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startScan() {
        if (_scanProgress.value.isScanning) return

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            try {
                // 1. Detect dynamic network
                var info = NetworkUtils.detectCurrentNetwork(getApplication())
                if (info == null) {
                    // Try refreshing once more in case hotspot just started
                    delay(300)
                    info = NetworkUtils.detectCurrentNetwork(getApplication())
                }
                _networkInfo.value = info

                if (info == null) {
                    _scanProgress.value = ScanProgress(
                        isScanning = false,
                        current = 0,
                        total = 0,
                        statusMessage = "⚠️ Aucun réseau local détecté"
                    )
                    return@launch
                }

                // 2. Mark existing devices offline prior to scan
                try {
                    repository.markAllOffline()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 3. Compute dynamic host range based on subnet mask
                val hosts = NetworkUtils.calculateHostRange(info)

                if (hosts.isEmpty()) {
                    _scanProgress.value = ScanProgress(
                        isScanning = false,
                        current = 0,
                        total = 0,
                        statusMessage = "Aucun hôte à analyser dans ce sous-réseau"
                    )
                    return@launch
                }

                // 4. Run concurrent scan safely
                networkScanner.scanHosts(
                    hosts = hosts,
                    onDeviceDiscovered = { host ->
                        try {
                            repository.saveOrUpdateDiscovered(
                                ip = host.ip,
                                hostname = host.hostname,
                                port = host.port,
                                macAddress = host.macAddress,
                                serverBanner = host.serverBanner,
                                httpTitle = host.httpTitle,
                                responseTimeMs = host.responseTimeMs
                            )
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }
                ).collect { progress ->
                    _scanProgress.value = progress
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                _scanProgress.value = ScanProgress(
                    isScanning = false,
                    statusMessage = "Erreur: ${e.message ?: "Échec du scan"}"
                )
            }
        }
    }

    fun renameDevice(id: Long, newName: String, newType: DeviceType) {
        viewModelScope.launch {
            try {
                repository.updateNameAndType(id, newName, newType)
                _deviceToRename.value = null
                _selectedDeviceForDetails.value?.let { current ->
                    if (current.id == id) {
                        _selectedDeviceForDetails.value = current.copy(
                            customName = newName.trim(),
                            deviceType = newType.name
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteDevice(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteDevice(id)
                if (_selectedDeviceForDetails.value?.id == id) {
                    _selectedDeviceForDetails.value = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectDeviceForDetails(device: DeviceEntity?) {
        _selectedDeviceForDetails.value = device
    }

    fun selectDeviceToRename(device: DeviceEntity?) {
        _deviceToRename.value = device
    }

    fun stopScan() {
        scanJob?.cancel()
        _scanProgress.value = _scanProgress.value.copy(
            isScanning = false,
            statusMessage = "Scan interrompu"
        )
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getDatabase(application)
            val repo = DeviceRepository(db.deviceDao())
            return ScannerViewModel(application, repo) as T
        }
    }
}
