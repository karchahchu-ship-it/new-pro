package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Query("SELECT * FROM devices ORDER BY isOnline DESC, lastSeenTimestamp DESC, id ASC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    suspend fun getDeviceById(id: Long): DeviceEntity?

    @Query("SELECT * FROM devices WHERE macAddress = :macAddress LIMIT 1")
    suspend fun getDeviceByMac(macAddress: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE lastIp = :ip LIMIT 1")
    suspend fun getDeviceByIp(ip: String): DeviceEntity?

    @Query("SELECT COUNT(*) FROM devices")
    suspend fun countDevices(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: DeviceEntity): Long

    @Update
    suspend fun update(device: DeviceEntity)

    @Query("UPDATE devices SET customName = :name, deviceType = :type WHERE id = :id")
    suspend fun updateNameAndType(id: Long, name: String, type: String)

    @Query("UPDATE devices SET isOnline = :isOnline, lastSeenTimestamp = :timestamp, lastIp = :ip, responseTimeMs = :responseTime WHERE id = :id")
    suspend fun updateStatus(id: Long, isOnline: Boolean, timestamp: Long, ip: String, responseTime: Long)

    @Query("UPDATE devices SET isOnline = 0")
    suspend fun markAllOffline()

    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM devices")
    suspend fun clearAll()
}
