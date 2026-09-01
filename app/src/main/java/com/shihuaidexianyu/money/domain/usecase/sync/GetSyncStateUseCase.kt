package com.shihuaidexianyu.money.domain.usecase.sync

import com.shihuaidexianyu.money.domain.model.sync.SyncState
import com.shihuaidexianyu.money.domain.repository.SyncRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider

class GetSyncStateUseCase(
    private val syncRepository: SyncRepository,
    private val clockProvider: ClockProvider,
) {
    suspend operator fun invoke(): SyncState {
        val dataset = syncRepository.readDatasetState()
        return SyncState(
            datasetId = dataset.datasetId,
            revision = dataset.currentRevision,
            // The fixture convention: 1 when the log is empty (revision 1 not yet allocated),
            // otherwise the smallest retained revision.
            minAvailableRevision = syncRepository.minLoggedRevision() ?: 1L,
            serverTime = clockProvider.nowMillis(),
        )
    }
}
