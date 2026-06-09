package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.backup.BackupJsonCodec

class BuildExportJsonUseCase(
    private val buildExportSnapshotUseCase: BuildExportSnapshotUseCase,
) {
    suspend operator fun invoke(exportedAt: Long = System.currentTimeMillis()): String {
        val snapshot = buildExportSnapshotUseCase(exportedAt)
        return BackupJsonCodec.encode(snapshot)
    }
}
