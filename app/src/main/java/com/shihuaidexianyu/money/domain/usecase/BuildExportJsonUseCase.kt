package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.repository.BackupJsonEncoder

class BuildExportJsonUseCase(
    private val buildExportSnapshotUseCase: BuildExportSnapshotUseCase,
    private val backupJsonEncoder: BackupJsonEncoder,
) {
    suspend operator fun invoke(): String {
        val snapshot = buildExportSnapshotUseCase()
        return backupJsonEncoder.encode(snapshot)
    }

    suspend operator fun invoke(exportedAt: Long): String {
        val snapshot = buildExportSnapshotUseCase(exportedAt)
        return backupJsonEncoder.encode(snapshot)
    }
}
