package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.DeviceType

@Entity(
    tableName = "devices",
    indices = [
        Index(value = ["macAddress"], unique = true)
    ]
)
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val macAddress: String, // MAC address or unique hardware/network fingerprint
    val hostname: String? = null, // Hostname (e.g. "device-12.local", "router.lan")
    val customName: String? = null, // Custom user label (e.g. "Chaudière principale")
    val defaultName: String = "Appareil #1", // Default sequential label
    val deviceType: String = DeviceType.MODULE.name,
    val lastIp: String,
    val port: Int = 80,
    val isOnline: Boolean = true,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val serverBanner: String? = null,
    val httpTitle: String? = null,
    val responseTimeMs: Long = 0
) {
    val displayName: String
        get() = if (!customName.isNullOrBlank()) customName else defaultName

    val displayHostname: String
        get() = when {
            !hostname.isNullOrBlank() -> hostname
            !httpTitle.isNullOrBlank() -> httpTitle
            else -> "host-${lastIp.replace('.', '-')}.local"
        }

    val type: DeviceType
        get() = DeviceType.fromName(deviceType)

    val webUrl: String
        get() = if (port == 80) "http://$lastIp" else "http://$lastIp:$port"
}
