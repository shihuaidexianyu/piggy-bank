package com.shihuaidexianyu.money.domain.model.sync

/**
 * Domain models for the Money sync v1 feature (design: docs/design/money-sync-v1-design.md).
 *
 * These types are pure Kotlin + kotlinx.serialization and must not depend on Android, Room,
 * or the LAN JSON DTOs; the `lan` layer converts between these models and wire DTOs.
 */

/** Entity kinds mirrored to the desktop, serialized as lowercase snake-case wire values. */
enum class SyncEntityKind(val value: String) {
    ACCOUNT("account"),
    CASH_FLOW("cash_flow"),
    TRANSFER("transfer"),
    BALANCE_UPDATE("balance_update"),
    BALANCE_ADJUSTMENT("balance_adjustment"),
    ;

    companion object {
        /** Snapshot/paging order: accounts first, then record tables in a stable order. */
        val SNAPSHOT_ORDER: List<SyncEntityKind> = listOf(
            ACCOUNT,
            CASH_FLOW,
            TRANSFER,
            BALANCE_UPDATE,
            BALANCE_ADJUSTMENT,
        )

        fun fromValue(value: String): SyncEntityKind? = entries.firstOrNull { it.value == value }
    }
}

enum class SyncChangeOperation(val value: String) {
    UPSERT("upsert"),
    DELETE("delete"),
    ;

    companion object {
        fun fromValue(value: String): SyncChangeOperation =
            entries.firstOrNull { it.value == value } ?: error("Unknown sync change operation: $value")
    }
}

/** Persisted dataset state: [nextRevision] is the revision the NEXT appended change receives. */
data class SyncDatasetState(
    val datasetId: String,
    val nextRevision: Long,
    val createdAt: Long,
) {
    /** Highest allocated revision; 0 when nothing has been logged yet. */
    val currentRevision: Long get() = nextRevision - 1L
}

/** A change entry about to be appended; the repository allocates the revision. */
data class NewSyncChange(
    val entityKind: SyncEntityKind,
    val recordId: Long,
    val operation: SyncChangeOperation,
    /** Mirror-row payload JSON (spec 4.3) for upserts; null for delete tombstones. */
    val payloadJson: String? = null,
    /** Record `updatedAt` for record kinds; registration timestamp for accounts. */
    val updatedAt: Long,
    /** Set together with [updatedAt] on delete tombstones; null otherwise. */
    val deletedAt: Long? = null,
    /** Originating LAN request id when the change came from an AI/sync write. */
    val requestId: String? = null,
)

/** One persisted change-log row, returned by pull in ascending [revision] order. */
data class SyncChange(
    val revision: Long,
    val entityKind: SyncEntityKind,
    val recordId: Long,
    val operation: SyncChangeOperation,
    val payloadJson: String? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val requestId: String? = null,
)

/** Raw snapshot source row before `sourceRevision` enrichment; tombstone when [deletedAt] != null. */
data class SyncSnapshotSourceRow(
    val recordId: Long,
    val payloadJson: String?,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** One snapshot page row: full payload envelope, or a tombstone ([payloadJson] == null). */
data class SyncSnapshotRow(
    val entityKind: SyncEntityKind,
    val recordId: Long,
    val sourceRevision: Long,
    val payloadJson: String? = null,
    val updatedAt: Long? = null,
    val deletedAt: Long? = null,
)

data class SyncSnapshotPage(
    val datasetId: String,
    val snapshotRevision: Long,
    val rows: List<SyncSnapshotRow>,
    /** Opaque keyset cursor; null exactly when [done] is true. */
    val nextCursor: String?,
    val done: Boolean,
)

data class SyncPullPage(
    val datasetId: String,
    val fromRevision: Long,
    val toRevision: Long,
    val changes: List<SyncChange>,
    val hasMore: Boolean,
)

data class SyncState(
    val datasetId: String,
    val revision: Long,
    val minAvailableRevision: Long,
    val serverTime: Long,
)

/**
 * One note patch as decoded from the wire. [entityKind] and [changes] stay raw here so the
 * use case can reject them per-patch (INVALID_PATCH) instead of failing the whole batch.
 */
data class NotePatch(
    val patchId: String,
    val entityKind: String,
    val recordId: Long,
    val expectedUpdatedAt: Long,
    val changes: Map<String, String>,
)

/**
 * One record patch (sync.push.records.v1) as decoded from the wire. [op], [entityKind] and
 * [changes] stay raw (changes as the wire JsonObject) so the use case can reject them per-patch
 * instead of failing the whole batch. `create` patches carry no recordId/expectedUpdatedAt;
 * `update`/`delete` patches require both. A null [op] inside a record batch is itself an
 * invalid patch (legacy note batches are routed to the note pipeline before this stage).
 */
data class RecordPatch(
    val patchId: String,
    val op: String? = null,
    val entityKind: String,
    val recordId: Long? = null,
    val expectedUpdatedAt: Long? = null,
    val changes: kotlinx.serialization.json.JsonObject,
)

enum class SyncPatchStatus(val value: String) {
    APPLIED("applied"),
    CONFLICT("conflict"),
    INVALID("invalid"),
    ;

    companion object {
        fun fromValue(value: String): SyncPatchStatus =
            entries.firstOrNull { it.value == value } ?: error("Unknown sync patch status: $value")
    }
}

data class PatchResult(
    val patchId: String,
    val status: SyncPatchStatus,
    /** New record id for applied `create` patches; null otherwise (the patch carries its id). */
    val recordId: Long? = null,
    /** Allocated change-log revision for applied patches. */
    val revision: Long? = null,
    /** Current server `updatedAt` for conflict results. */
    val serverUpdatedAt: Long? = null,
    /** Current server mirror payload JSON for conflict results. */
    val serverPayloadJson: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

data class PushSyncBatchResult(
    val results: List<PatchResult>,
    val replayed: Boolean,
)

/** Wire error code for invalid patches; mirrored by `MoneyLanErrorCodes` in the LAN layer. */
const val SYNC_ERROR_INVALID_PATCH = "INVALID_PATCH"

/** The request datasetId does not match the phone's current dataset (mapped to DATASET_MISMATCH). */
class SyncDatasetMismatchException(
    message: String = "本地镜像与手机不是同一个数据集",
) : IllegalStateException(message)

/** The requested cursor predates the retained change-log window (mapped to RESYNC_REQUIRED). */
class SyncResyncRequiredException(
    message: String = "同步游标早于服务器保留的最早 revision，请重新快照",
) : IllegalStateException(message)

/** Stable capability names advertised in the unauthenticated `server.info` response. */
object SyncCapabilities {
    const val STATE = "sync.state.v1"
    const val SNAPSHOT = "sync.snapshot.v1"
    const val PULL = "sync.pull.v1"
    const val PUSH_NOTE = "sync.push.note.v1"
    const val RECORDS_LIST_DETAILED = "records.list.detailed.v1"

    /** session.pair.begin/poll + session.resume with persistent device credentials. */
    const val SESSION_DEVICE = "session.device.v1"

    /** `sync.push` accepting mixed create/update/delete record patches. */
    const val PUSH_RECORDS = "sync.push.records.v1"

    val ALL: List<String> = listOf(
        STATE,
        SNAPSHOT,
        PULL,
        PUSH_NOTE,
        RECORDS_LIST_DETAILED,
        SESSION_DEVICE,
        PUSH_RECORDS,
    )
}
