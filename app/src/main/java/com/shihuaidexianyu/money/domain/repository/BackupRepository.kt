package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot

interface BackupRepository {
    suspend fun replaceAll(snapshot: MoneyBackupSnapshot)
}
