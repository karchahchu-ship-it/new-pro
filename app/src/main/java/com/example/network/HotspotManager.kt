package com.example.network

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class HotspotState {
    data object Inactive : HotspotState()
    data object Starting : HotspotState()
    data class Active(
        val ssid: String = "esp",
        val passphrase: String = "00000001",
        val targetSsid: String = "esp",
        val targetPassphrase: String = "00000001",
        val isLocalOnly: Boolean = true
    ) : HotspotState()
    data class Failed(val message: String) : HotspotState()
}

class HotspotManager(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null

    private val _state = MutableStateFlow<HotspotState>(HotspotState.Inactive)
    val state: StateFlow<HotspotState> = _state.asStateFlow()

    fun hasRequiredPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return fineLocation && nearbyDevices
    }

    /**
     * Automatically initiates Hotspot startup.
     * Target credentials: SSID "esp", Passphrase "00000001".
     */
    fun startHotspot() {
        if (_state.value is HotspotState.Active || _state.value is HotspotState.Starting) {
            return
        }

        if (wifiManager == null) {
            _state.value = HotspotState.Failed("Wi-Fi Manager non disponible sur cet appareil")
            return
        }

        if (!hasRequiredPermissions()) {
            _state.value = HotspotState.Failed("Autorisations Wi-Fi et localisation requises pour démarrer le Hotspot")
            return
        }

        _state.value = HotspotState.Starting

        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    super.onStarted(reservation)
                    hotspotReservation = reservation

                    var activeSsid = "esp"
                    var activePassphrase = "00000001"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        reservation.softApConfiguration?.let { config ->
                            config.ssid?.let { activeSsid = it }
                            config.passphrase?.let { activePassphrase = it }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        reservation.wifiConfiguration?.let { config ->
                            config.SSID?.let { activeSsid = it }
                            config.preSharedKey?.let { activePassphrase = it }
                        }
                    }

                    _state.value = HotspotState.Active(
                        ssid = activeSsid,
                        passphrase = activePassphrase,
                        targetSsid = "esp",
                        targetPassphrase = "00000001",
                        isLocalOnly = true
                    )
                }

                override fun onStopped() {
                    super.onStopped()
                    hotspotReservation = null
                    _state.value = HotspotState.Inactive
                }

                override fun onFailed(reason: Int) {
                    super.onFailed(reason)
                    hotspotReservation = null
                    val message = when (reason) {
                        ERROR_NO_CHANNEL -> "Aucun canal Wi-Fi disponible actuellement"
                        ERROR_GENERIC -> "Erreur système lors du démarrage du Hotspot"
                        ERROR_INCOMPATIBLE_MODE -> "Mode Wi-Fi incompatible"
                        ERROR_TETHERING_DISALLOWED -> "Le partage de connexion n'est pas autorisé par l'appareil"
                        else -> "Échec du démarrage du Hotspot (Code $reason)"
                    }
                    _state.value = HotspotState.Failed(message)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            _state.value = HotspotState.Failed("Erreur de sécurité: permission refusée par le système")
        } catch (e: Exception) {
            _state.value = HotspotState.Failed("Erreur: ${e.localizedMessage ?: "Inconnue"}")
        }
    }

    fun stopHotspot() {
        try {
            hotspotReservation?.close()
        } catch (ignored: Exception) {
        } finally {
            hotspotReservation = null
            _state.value = HotspotState.Inactive
        }
    }

    fun openHotspotSettings(context: Context) {
        val intents = listOf(
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings")),
            Intent("android.settings.WIFI_TETHER_SETTINGS"),
            Intent("android.settings.TETHER_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (ignored: ActivityNotFoundException) {
            } catch (ignored: Exception) {}
        }
    }
}
