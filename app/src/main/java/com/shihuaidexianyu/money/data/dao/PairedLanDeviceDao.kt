package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shihuaidexianyu.money.data.entity.PairedLanDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PairedLanDeviceDao {
    @Query("SELECT * FROM paired_lan_device ORDER BY pairedAt ASC")
    fun observeDevices(): Flow<List<PairedLanDeviceEntity>>

    @Query("SELECT * FROM paired_lan_device WHERE deviceId = :deviceId")
    suspend fun findById(deviceId: String): PairedLanDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: PairedLanDeviceEntity)

    @Query("DELETE FROM paired_lan_device WHERE deviceId = :deviceId")
    suspend fun deleteById(deviceId: String): Int

    @Query("UPDATE paired_lan_device SET lastSeenAt = :seenAt WHERE deviceId = :deviceId")
    suspend fun touchLastSeen(deviceId: String, seenAt: Long)
}
