package com.shihuaidexianyu.money.data.backup

import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot

object BackupContentHasher {
    fun sha256(snapshot: MoneyBackupSnapshot): String {
        val normalized = BackupSnapshotNormalizer.normalizeForPersistence(snapshot)
        val canonical = normalized.copy(
            metadata = normalized.metadata.copy(
                databaseVersion = 0,
                exportedAt = 1L,
                appVersionName = null,
                appVersionCode = null,
            ),
        )
        return com.shihuaidexianyu.money.data.backup.sha256(BackupJsonCodec.encode(canonical).encodeToByteArray())
    }
}
