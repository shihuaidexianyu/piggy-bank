package com.shihuaidexianyu.money.lan

import com.shihuaidexianyu.money.domain.model.sync.SyncChange
import com.shihuaidexianyu.money.domain.model.sync.SyncChangeOperation
import com.shihuaidexianyu.money.domain.model.sync.SyncMirrorJson
import com.shihuaidexianyu.money.domain.model.sync.SyncSnapshotRow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Wire DTOs for the sync v1 routes and records.list.detailed, pinned by
 * docs/protocol/sync-v1/fixtures. Mirror payloads travel as nested JsonObjects parsed from the
 * stored payload JSON so no information is re-serialized differently than it was appended.
 */
@Serializable
data class SyncStateResult(
    val datasetId: String,
    val revision: Long,
    val minAvailableRevision: Long,
    val serverTime: Long,
)

@Serializable
data class SyncSnapshotArguments(
    val datasetId: String,
    val snapshotRevision: Long? = null,
    val cursor: String? = null,
)

@Serializable
data class SyncSnapshotResult(
    val datasetId: String,
    val snapshotRevision: Long,
    val rows: List<JsonObject>,
    val nextCursor: String? = null,
    val done: Boolean,
)

@Serializable
data class SyncPullArguments(
    val datasetId: String,
    val afterRevision: Long,
    val limit: Int = DEFAULT_PULL_LIMIT,
) {
    companion object {
        const val DEFAULT_PULL_LIMIT = 100
    }
}

@Serializable
data class SyncPullResult(
    val datasetId: String,
    val fromRevision: Long,
    val toRevision: Long,
    val changes: List<JsonObject>,
    val hasMore: Boolean,
)

@Serializable
data class SyncPushArguments(
    val datasetId: String,
    val patches: List<SyncPushPatch>,
)

@Serializable
data class SyncPushPatch(
    val patchId: String,
    val entityKind: String,
    val recordId: Long,
    val expectedUpdatedAt: Long,
    val changes: Map<String, String>,
)

@Serializable
data class SyncPushResult(val results: List<SyncPushPatchResult>)

@Serializable
data class SyncPushPatchResult(
    val patchId: String,
    val status: String,
    val revision: Long? = null,
    val serverUpdatedAt: Long? = null,
    val serverPayload: JsonObject? = null,
    val error: SyncPushPatchError? = null,
)

@Serializable
data class SyncPushPatchError(val code: String, val message: String)

/** Keyset cursor shared by records.list / records.list.detailed. */
@Serializable
data class RecordCursorResult(val occurredAt: Long, val sourceOrder: Int, val recordId: Long)

/** records.list.detailed row: records.list fields plus note/updatedAt/deletedAt/operationId. */
@Serializable
data class DetailedHistoryRecordResult(
    val recordId: Long,
    val type: String,
    val accountId: Long,
    val accountName: String,
    val relatedAccountId: Long? = null,
    val relatedAccountName: String? = null,
    val title: String,
    val amount: String,
    val occurredAt: Long,
    val balanceBefore: String? = null,
    val balanceAfter: String? = null,
    val note: String? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val operationId: String,
)

@Serializable
data class RecordsListDetailedResult(
    val records: List<DetailedHistoryRecordResult>,
    val nextCursor: RecordCursorResult? = null,
)

/** Mirror-row envelope: {entityKind, sourceRevision, payload}; tombstones drop sourceRevision. */
fun SyncSnapshotRow.toWireJson(): JsonObject {
    val payloadJson = payloadJson
    return if (payloadJson != null) {
        buildJsonObject {
            put("entityKind", entityKind.value)
            put("sourceRevision", sourceRevision)
            put("payload", SyncMirrorJson.parseToJsonElement(payloadJson))
        }
    } else {
        buildJsonObject {
            put("entityKind", entityKind.value)
            put("recordId", recordId)
            put("deletedAt", requireNotNull(deletedAt))
            put("updatedAt", requireNotNull(updatedAt))
        }
    }
}

/** Pull change envelope: upserts carry payload; deletes carry deletedAt instead. */
fun SyncChange.toWireJson(): JsonObject = buildJsonObject {
    put("revision", revision)
    put("entityKind", entityKind.value)
    put("recordId", recordId)
    put("operation", operation.value)
    put("updatedAt", updatedAt)
    when (operation) {
        SyncChangeOperation.UPSERT -> put("payload", SyncMirrorJson.parseToJsonElement(requireNotNull(payloadJson)))
        SyncChangeOperation.DELETE -> put("deletedAt", deletedAt ?: updatedAt)
    }
}
