package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.shihuaidexianyu.money.data.entity.PortableSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PortableSettingsDao {
    @Query("SELECT * FROM portable_settings WHERE id = 1 LIMIT 1")
    fun observe(): Flow<PortableSettingsEntity?>

    @Query("SELECT * FROM portable_settings WHERE id = 1 LIMIT 1")
    suspend fun query(): PortableSettingsEntity?

    @Upsert
    suspend fun upsert(settings: PortableSettingsEntity)

    @Query("DELETE FROM portable_settings")
    suspend fun deleteAll()
}
