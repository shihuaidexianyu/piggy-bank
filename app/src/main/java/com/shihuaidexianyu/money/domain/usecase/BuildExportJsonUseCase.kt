package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.BackupJsonEncoder

class BuildExportJsonUseCase(
    private val buildExportSnapshotUseCase: BuildExportSnapshotUseCase,
    private val backupJsonEncoder: BackupJsonEncoder,
) {
    suspend operator fun invoke(exportedAt: Long = System.currentTimeMillis()): String {
        val snapshot = buildExportSnapshotUseCase(exportedAt)
        return backupJsonEncoder.encode(snapshot)
    }
}
