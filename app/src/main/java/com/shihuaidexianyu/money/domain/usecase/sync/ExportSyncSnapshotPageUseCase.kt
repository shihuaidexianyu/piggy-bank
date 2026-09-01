package com.shihuaidexianyu.money.domain.usecase.sync

import com.shihuaidexianyu.money.domain.model.sync.SyncDatasetMismatchException
import com.shihuaidexianyu.money.domain.model.sync.SyncEntityKind
import com.shihuaidexianyu.money.domain.model.sync.SyncResyncRequiredException
import com.shihuaidexianyu.money.domain.model.sync.SyncSnapshotPage
import com.shihuaidexianyu.money.domain.model.sync.SyncSnapshotRow
import com.shihuaidexianyu.money.domain.repository.SyncRepository
import java.util.Base64

/**
 * Exports one snapshot page of the current ledger projection (accounts + all four record kinds,
 * including soft-deleted rows as tombstones) for initial sync or post-RESYNC rebuilds.
 *
 * The first call (no cursor) pins `snapshotRevision` to the current dataset revision; later calls
 * resume from an opaque keyset cursor encoding `snapshotRevision + entityKind + lastRecordId`.
 * The server stays stateless: correctness after mid-snapshot mutations comes from the client's
 * catch-up pull from `snapshotRevision` once the snapshot completes.
 *
 * Pages are byte-capped ([SNAPSHOT_BYTE_BUDGET]) rather than row-capped and may shrink to a
 * single row to fit very long notes.
 */
class ExportSyncSnapshotPageUseCase(
    private val syncRepository: SyncRepository,
) {
    suspend operator fun invoke(
        expectedDatasetId: String,
        snapshotRevision: Long? = null,
        cursor: String? = null,
    ): SyncSnapshotPage {
        val dataset = syncRepository.readDatasetState()
        if (dataset.datasetId != expectedDatasetId) throw SyncDatasetMismatchException()

        val position = cursor?.let { decodeCursor(it) }
        require(cursor == null || snapshotRevision != null) { "续传快照必须携带 snapshotRevision" }
        if (position != null) {
            require(position.snapshotRevision == snapshotRevision) { "游标与 snapshotRevision 不一致" }
        }
        val pinnedRevision = position?.snapshotRevision ?: dataset.currentRevision
        // If the retained change-log window has advanced past the pinned revision, a completed
        // snapshot could never catch up — the client must rebase instead of resuming.
        val minAvailable = syncRepository.minLoggedRevision() ?: 1L
        if (pinnedRevision + 1L < minAvailable) throw SyncResyncRequiredException()

        val startKindIndex = position?.let { positionKindIndex(it.entityKind) } ?: 0
        val rows = mutableListOf<SyncSnapshotRow>()
        var bytes = 0

        val kinds = SyncEntityKind.SNAPSHOT_ORDER
        var kindIndex = startKindIndex
        while (kindIndex < kinds.size) {
            val kind = kinds[kindIndex]
            var afterRecordId = if (kindIndex == startKindIndex) position?.recordId ?: 0L else 0L
            var kindExhausted = false
            while (!kindExhausted) {
                val chunk = syncRepository.querySnapshotRows(
                    entityKind = kind,
                    afterRecordId = afterRecordId,
                    limit = SNAPSHOT_CHUNK_ROWS,
                )
                if (chunk.isEmpty()) {
                    kindExhausted = true
                    break
                }
                val sourceRevisions = syncRepository.queryLatestRevisions(kind, chunk.map { it.recordId })
                for (source in chunk) {
                    val row = SyncSnapshotRow(
                        entityKind = kind,
                        recordId = source.recordId,
                        sourceRevision = sourceRevisions[source.recordId] ?: 0L,
                        payloadJson = if (source.deletedAt == null) source.payloadJson else null,
                        updatedAt = source.updatedAt,
                        deletedAt = source.deletedAt,
                    )
                    val rowBytes = estimateRowBytes(row)
                    if (rows.isNotEmpty() && bytes + rowBytes > SNAPSHOT_BYTE_BUDGET) {
                        // Budget exhausted: resume exactly at this row on the next page.
                        return SyncSnapshotPage(
                            datasetId = dataset.datasetId,
                            snapshotRevision = pinnedRevision,
                            rows = rows,
                            nextCursor = encodeCursor(pinnedRevision, kind, afterRecordId),
                            done = false,
                        )
                    }
                    rows += row
                    bytes += rowBytes
                    afterRecordId = source.recordId
                }
                kindExhausted = chunk.size < SNAPSHOT_CHUNK_ROWS
            }
            kindIndex += 1
        }

        return SyncSnapshotPage(
            datasetId = dataset.datasetId,
            snapshotRevision = pinnedRevision,
            rows = rows,
            nextCursor = null,
            done = true,
        )
    }

    private fun positionKindIndex(kind: SyncEntityKind): Int {
        val index = SyncEntityKind.SNAPSHOT_ORDER.indexOf(kind)
        require(index >= 0) { "游标包含未知的记录类型" }
        return index
    }

    private data class CursorPosition(
        val snapshotRevision: Long,
        val entityKind: SyncEntityKind,
        val recordId: Long,
    )

    private fun encodeCursor(snapshotRevision: Long, entityKind: SyncEntityKind, recordId: Long): String {
        val raw = "$CURSOR_MARKER:$snapshotRevision:${entityKind.value}:$recordId"
        return Base64.getEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))
    }

    private fun decodeCursor(cursor: String): CursorPosition {
        val raw = try {
            String(Base64.getDecoder().decode(cursor), Charsets.UTF_8)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("快照游标无效")
        }
        val parts = raw.split(":")
        require(parts.size == CURSOR_PARTS && parts[0] == CURSOR_MARKER) { "快照游标无效" }
        val snapshotRevision = parts[1].toLongOrNull() ?: throw IllegalArgumentException("快照游标无效")
        val kind = SyncEntityKind.fromValue(parts[2]) ?: throw IllegalArgumentException("快照游标无效")
        val recordId = parts[3].toLongOrNull() ?: throw IllegalArgumentException("快照游标无效")
        return CursorPosition(snapshotRevision, kind, recordId)
    }

    private fun estimateRowBytes(row: SyncSnapshotRow): Int {
        // Envelope keys plus separators are well under this constant; payload dominates.
        val payloadBytes = row.payloadJson?.toByteArray(Charsets.UTF_8)?.size ?: 0
        return payloadBytes + ENVELOPE_OVERHEAD_BYTES
    }

    companion object {
        /** Serialized page target: 128 KiB, comfortably below the 256 KiB frame limit. */
        const val SNAPSHOT_BYTE_BUDGET = 128 * 1024
        private const val SNAPSHOT_CHUNK_ROWS = 64
        private const val ENVELOPE_OVERHEAD_BYTES = 160
        private const val CURSOR_MARKER = "s"
        private const val CURSOR_PARTS = 4
    }
}
