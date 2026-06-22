package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot

/**
 * Port for serializing a [MoneyBackupSnapshot] to a JSON string.
 * Implemented in the data layer; consumed by domain use cases to
 * keep `domain/` free of `data.*` imports.
 */
fun interface BackupJsonEncoder {
    fun encode(snapshot: MoneyBackupSnapshot): String
}
