package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.shihuaidexianyu.money.data.entity.LocalMigrationStateEntity

@Dao
interface LocalMigrationStateDao {
    @Query("SELECT * FROM local_migration_state WHERE `key` = :key LIMIT 1")
    suspend fun queryByKey(key: String): LocalMigrationStateEntity?

    @Query("SELECT * FROM local_migration_state ORDER BY `key` ASC")
    suspend fun queryAll(): List<LocalMigrationStateEntity>

    @Upsert
    suspend fun upsert(migrationState: LocalMigrationStateEntity)

    @Upsert
    suspend fun upsertAll(migrationStates: List<LocalMigrationStateEntity>)

    @Query("DELETE FROM local_migration_state")
    suspend fun deleteAll()
}
