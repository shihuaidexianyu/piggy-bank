package com.shihuaidexianyu.money.domain.usecase.sync

import com.shihuaidexianyu.money.domain.model.sync.SyncDatasetMismatchException
import com.shihuaidexianyu.money.domain.model.sync.SyncPullPage
import com.shihuaidexianyu.money.domain.model.sync.SyncResyncRequiredException
import com.shihuaidexianyu.money.domain.repository.SyncRepository

/**
 * Incremental catch-up: returns change-log entries after [afterRevision] in ascending revision
 * order. A cursor earlier than the retained window ([SyncRepository.minLoggedRevision]) is
 * rejected with [SyncResyncRequiredException]; the client must re-snapshot instead.
 *
 * Cursor semantics: [afterRevision] = 0 means "from the beginning" and is always valid while
 * nothing has been pruned (minAvailableRevision starts at 1), so the resync check is
 * `afterRevision + 1 < minAvailableRevision`.
 */
class PullSyncChangesUseCase(
    private val syncRepository: SyncRepository,
) {
    suspend operator fun invoke(
        expectedDatasetId: String,
        afterRevision: Long,
        limit: Int,
    ): SyncPullPage {
        require(limit in 1..MAX_PULL_LIMIT) { "limit 必须在 1 到 $MAX_PULL_LIMIT 之间" }
        require(afterRevision >= 0L) { "afterRevision 必须是非负整数" }
        val dataset = syncRepository.readDatasetState()
        if (dataset.datasetId != expectedDatasetId) throw SyncDatasetMismatchException()
        val minAvailable = syncRepository.minLoggedRevision() ?: 1L
        if (afterRevision + 1L < minAvailable) throw SyncResyncRequiredException()
        val fetched = syncRepository.queryChangesAfter(afterRevision, limit + 1)
        val changes = fetched.take(limit)
        return SyncPullPage(
            datasetId = dataset.datasetId,
            fromRevision = afterRevision,
            toRevision = changes.lastOrNull()?.revision ?: afterRevision,
            changes = changes,
            hasMore = fetched.size > limit,
        )
    }

    companion object {
        const val MAX_PULL_LIMIT = 500
    }
}
