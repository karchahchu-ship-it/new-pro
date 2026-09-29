package com.example.data.repository

import com.example.data.local.DeviceDao
import com.example.data.local.DeviceEntity
import com.example.data.model.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DeviceRepository(private val deviceDao: DeviceDao) {

    val allDevices: Flow<List<DeviceEntity>> = deviceDao.getAllDevices()

    suspend fun saveOrUpdateDiscovered(
        ip: String,
        hostname: String?,
        port: Int,
        macAddress: String?,
        serverBanner: String?,
        httpTitle: String?,
        responseTimeMs: Long
    ): DeviceEntity = withContext(Dispatchers.IO) {
        val effectiveMac = macAddress?.trim()?.uppercase()?.takeIf { it.isNotBlank() && it != "00:00:00:00:00:00" }

        // 1. Try finding existing device by MAC
        var existing = if (effectiveMac != null) {
            deviceDao.getDeviceByMac(effectiveMac)
        } else {
            null
        }

        // 2. If no match by MAC, try finding by IP as secondary fallback
        if (existing == null) {
            existing = deviceDao.getDeviceByIp(ip)
        }

        val updatedEntity = if (existing != null) {
            // Preserve user's customName and chosen deviceType
            existing.copy(
                macAddress = effectiveMac ?: existing.macAddress,
                hostname = hostname ?: existing.hostname,
                lastIp = ip,
                port = port,
                isOnline = true,
                lastSeenTimestamp = System.currentTimeMillis(),
                serverBanner = serverBanner ?: existing.serverBanner,
                httpTitle = httpTitle ?: existing.httpTitle,
                responseTimeMs = responseTimeMs
            )
        } else {
            // New device discovered
            val count = deviceDao.countDevices() + 1
            val fallbackKey = effectiveMac ?: "DEV-$ip"
            DeviceEntity(
                macAddress = fallbackKey,
                hostname = hostname,
                customName = null,
                defaultName = "Appareil #$count",
                deviceType = DeviceType.MODULE.name,
                lastIp = ip,
                port = port,
                isOnline = true,
                lastSeenTimestamp = System.currentTimeMillis(),
                serverBanner = serverBanner,
                httpTitle = httpTitle,
                responseTimeMs = responseTimeMs
            )
        }

        deviceDao.insertOrUpdate(updatedEntity)
        updatedEntity
    }

    suspend fun updateNameAndType(id: Long, customName: String, deviceType: DeviceType) =
        withContext(Dispatchers.IO) {
            deviceDao.updateNameAndType(id, customName.trim(), deviceType.name)
        }

    suspend fun markAllOffline() = withContext(Dispatchers.IO) {
        deviceDao.markAllOffline()
    }

    suspend fun deleteDevice(id: Long) = withContext(Dispatchers.IO) {
        deviceDao.deleteById(id)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        deviceDao.clearAll()
    }
}
