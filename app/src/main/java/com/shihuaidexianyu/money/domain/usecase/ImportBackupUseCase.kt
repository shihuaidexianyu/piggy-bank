package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import com.shihuaidexianyu.money.domain.repository.BackupRepository

class ImportBackupUseCase(
    private val backupRepository: BackupRepository,
    private val validateBackupSnapshotUseCase: ValidateBackupSnapshotUseCase,
) {
    suspend operator fun invoke(snapshot: MoneyBackupSnapshot): BackupValidationResult {
        val result = validateBackupSnapshotUseCase(snapshot)
        backupRepository.replaceAll(snapshot)
        return result
    }
}
