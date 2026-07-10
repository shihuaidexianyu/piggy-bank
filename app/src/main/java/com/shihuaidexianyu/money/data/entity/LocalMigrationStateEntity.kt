package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_migration_state")
data class LocalMigrationStateEntity(
    @PrimaryKey
    val key: String,
    val state: String,
    val completedAt: Long?,
    val detail: String?,
)
