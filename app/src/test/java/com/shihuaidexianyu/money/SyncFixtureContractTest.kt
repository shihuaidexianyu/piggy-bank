package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AccountKind
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.model.sync.SyncCapabilities
import com.shihuaidexianyu.money.domain.model.sync.SyncChange
import com.shihuaidexianyu.money.domain.model.sync.SyncChangeOperation
import com.shihuaidexianyu.money.domain.model.sync.SyncDatasetMismatchException
import com.shihuaidexianyu.money.domain.model.sync.SyncEntityKind
import com.shihuaidexianyu.money.domain.model.sync.SyncMirrorJson
import com.shihuaidexianyu.money.domain.model.sync.SyncMirrorPayloads
import com.shihuaidexianyu.money.domain.model.sync.SyncResyncRequiredException
import com.shihuaidexianyu.money.domain.model.sync.SyncSnapshotRow
import com.shihuaidexianyu.money.lan.MoneyLanErrorCodes
import com.shihuaidexianyu.money.lan.MoneyLanRequest
import com.shihuaidexianyu.money.lan.MoneyLanResponse
import com.shihuaidexianyu.money.lan.RecordsListDetailedResult
import com.shihuaidexianyu.money.lan.SyncPullArguments
import com.shihuaidexianyu.money.lan.SyncPullResult
import com.shihuaidexianyu.money.lan.SyncPushArguments
import com.shihuaidexianyu.money.lan.SyncPushResult
import com.shihuaidexianyu.money.lan.SyncSnapshotArguments
import com.shihuaidexianyu.money.lan.SyncSnapshotResult
import com.shihuaidexianyu.money.lan.SyncStateResult
import com.shihuaidexianyu.money.lan.toWireJson
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume
import org.junit.Test

/**
 * Contract test pinning the wire DTOs to docs/protocol/sync-v1/fixtures (owned by the protocol
 * repo copy — never edited here). Each fixture is decoded with the production DTOs, re-encoded,
 * and compared as an order-insensitive JSON tree; manifest.json hashes guard fixture integrity.
 */
class SyncFixtureContractTest {
    /** Same configuration as MoneyLanServer/MoneyLanRequestRouter.protocolJson. */
    private val wireJson = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
    }

    private val fixturesDir: File
        get() = File("../docs/protocol/sync-v1/fixtures")

    private fun assumeFixturesPresent(): File {
        val dir = fixturesDir
        Assume.assumeTrue("sync v1 fixtures not present at ${dir.absolutePath}", dir.isDirectory)
        return dir
    }

    private fun readFixture(dir: File, name: String): JsonObject =
        wireJson.parseToJsonElement(File(dir, name).readText()).jsonObject

    @Test
    fun manifestHashesMatchEveryFixtureFile() {
        val dir = assumeFixturesPresent()
        val manifest = readFixture(dir, "manifest.json")
        assertEquals("sha256", manifest.getValue("algorithm").jsonPrimitive.content)
        val files = manifest.getValue("files").jsonObject

        val listedNames = files.keys.sorted()
        val onDisk = dir.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .map { it.name }
            .filter { it != "manifest.json" }
            .sorted()
        assertEquals(onDisk, listedNames, "manifest.json must list every fixture file")

        files.forEach { (name, hashElement) ->
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(File(dir, name).readBytes())
                .joinToString("") { byte -> "%02x".format(byte) }
            assertEquals(hashElement.jsonPrimitive.content, digest, "SHA-256 mismatch for $name")
        }
    }

    @Test
    fun requestFixturesRoundTripThroughWireDtos() {
        val dir = assumeFixturesPresent()

        val snapshotFirst = roundTripRequest(dir, "sync_snapshot_request_first.json")
        assertEquals("sync.snapshot", snapshotFirst.action)
        val firstArgs = wireJson.decodeFromJsonElement(
            SyncSnapshotArguments.serializer(),
            snapshotFirst.arguments,
        )
        assertEquals(DATASET_ID, firstArgs.datasetId)
        assertNull(firstArgs.snapshotRevision)
        assertNull(firstArgs.cursor)
        assertJsonTreeEquals(
            snapshotFirst.arguments,
            wireJson.encodeToJsonElement(SyncSnapshotArguments.serializer(), firstArgs),
        )

        val snapshotNext = roundTripRequest(dir, "sync_snapshot_request_next.json")
        val nextArgs = wireJson.decodeFromJsonElement(
            SyncSnapshotArguments.serializer(),
            snapshotNext.arguments,
        )
        assertEquals(1842L, nextArgs.snapshotRevision)
        assertEquals(SNAPSHOT_CURSOR, nextArgs.cursor)
        assertJsonTreeEquals(
            snapshotNext.arguments,
            wireJson.encodeToJsonElement(SyncSnapshotArguments.serializer(), nextArgs),
        )

        val pull = roundTripRequest(dir, "sync_pull_request.json")
        assertEquals("sync.pull", pull.action)
        val pullArgs = wireJson.decodeFromJsonElement(SyncPullArguments.serializer(), pull.arguments)
        assertEquals(1842L, pullArgs.afterRevision)
        assertEquals(SyncPullArguments.DEFAULT_PULL_LIMIT, pullArgs.limit)
        assertJsonTreeEquals(
            pull.arguments,
            wireJson.encodeToJsonElement(SyncPullArguments.serializer(), pullArgs),
        )

        val push = roundTripRequest(dir, "sync_push_request.json")
        assertEquals("sync.push", push.action)
        val pushArgs = wireJson.decodeFromJsonElement(SyncPushArguments.serializer(), push.arguments)
        assertEquals(3, pushArgs.patches.size)
        val patch = pushArgs.patches.first()
        assertEquals("p-0001", patch.patchId)
        assertEquals("cash_flow", patch.entityKind)
        assertEquals(27L, patch.recordId)
        assertEquals(1786747810000L, patch.expectedUpdatedAt)
        assertEquals(
            mapOf("note" to "聚餐 翅客（王慨然和他小弟）"),
            patch.changes.mapValues { entry -> entry.value.jsonPrimitive.content },
        )
        assertNull(patch.op)
        assertJsonTreeEquals(
            push.arguments,
            wireJson.encodeToJsonElement(SyncPushArguments.serializer(), pushArgs),
        )

        val recordsPush = roundTripRequest(dir, "sync_push_records_request.json")
        assertEquals("sync.push", recordsPush.action)
        val recordsArgs = wireJson.decodeFromJsonElement(
            SyncPushArguments.serializer(),
            recordsPush.arguments,
        )
        assertEquals(3, recordsArgs.patches.size)
        val create = recordsArgs.patches[0]
        assertEquals("create", create.op)
        assertNull(create.recordId)
        assertNull(create.expectedUpdatedAt)
        assertEquals("1288", create.changes.getValue("amount").jsonPrimitive.content)
        val delete = recordsArgs.patches[2]
        assertEquals("delete", delete.op)
        assertEquals(45L, delete.recordId)
        assertTrue(delete.changes.isEmpty())
        assertJsonTreeEquals(
            recordsPush.arguments,
            wireJson.encodeToJsonElement(SyncPushArguments.serializer(), recordsArgs),
        )
    }

    @Test
    fun sessionDeviceFixturesRoundTripThroughWireShaping() {
        val dir = assumeFixturesPresent()

        val begin = roundTripRequest(dir, "session_pair_begin_request.json")
        assertEquals("session.pair.begin", begin.action)
        val beginArgs = wireJson.decodeFromJsonElement(
            PairBeginArgumentsFixture.serializer(),
            begin.arguments,
        )
        assertEquals("9f8b7c2d-3a4e-4f1a-8b2c-5d6e7f8a9b0c", beginArgs.deviceId)
        assertJsonTreeEquals(
            begin.arguments,
            wireJson.encodeToJsonElement(PairBeginArgumentsFixture.serializer(), beginArgs),
        )

        val beginResponse = roundTripResponse(dir, "session_pair_begin_response.json")
        val beginResult = wireJson.decodeFromJsonElement(
            PairBeginResultFixture.serializer(),
            beginResponse.getValue("data"),
        )
        assertEquals("pending", beginResult.status)
        assertEquals(60, beginResult.expiresInSec)
        assertJsonTreeEquals(
            beginResponse.getValue("data"),
            wireJson.encodeToJsonElement(PairBeginResultFixture.serializer(), beginResult),
        )

        val pollApproved = roundTripResponse(dir, "session_pair_poll_approved_response.json")
        val pollResult = wireJson.decodeFromJsonElement(
            PairPollResultFixture.serializer(),
            pollApproved.getValue("data"),
        )
        assertEquals("approved", pollResult.status)
        assertNotNull(pollResult.credential)
        assertNotNull(pollResult.token)
        assertJsonTreeEquals(
            pollApproved.getValue("data"),
            wireJson.encodeToJsonElement(PairPollResultFixture.serializer(), pollResult),
        )

        val resume = roundTripRequest(dir, "session_resume_request.json")
        assertEquals("session.resume", resume.action)
        val resumeArgs = wireJson.decodeFromJsonElement(
            ResumeArgumentsFixture.serializer(),
            resume.arguments,
        )
        assertEquals("example-device-credential", resumeArgs.credential)
        assertJsonTreeEquals(
            resume.arguments,
            wireJson.encodeToJsonElement(ResumeArgumentsFixture.serializer(), resumeArgs),
        )

        val resumeResponse = roundTripResponse(dir, "session_resume_response.json")
        val resumeResult = wireJson.decodeFromJsonElement(
            PairResultFixture.serializer(),
            resumeResponse.getValue("data"),
        )
        assertEquals("example-session-token", resumeResult.token)
        assertTrue(resumeResult.allowWrite)
        // Legacy pair/resume results omit the credential key entirely.
        assertFalse(resumeResponse.getValue("data").jsonObject.containsKey("credential"))
        assertJsonTreeEquals(
            resumeResponse.getValue("data"),
            wireJson.encodeToJsonElement(PairResultFixture.serializer(), resumeResult),
        )
    }

    @Test
    fun responseFixturesRoundTripThroughWireDtos() {
        val dir = assumeFixturesPresent()

        val serverInfo = roundTripResponse(dir, "server_info_response.json")
        val infoData = wireJson.decodeFromJsonElement(
            ServerInfoFixtureData.serializer(),
            serverInfo.getValue("data"),
        )
        assertEquals(1, infoData.protocolVersion)
        assertEquals("Money", infoData.app)
        assertEquals(SyncCapabilities.ALL, infoData.capabilities)
        assertJsonTreeEquals(
            serverInfo.getValue("data"),
            wireJson.encodeToJsonElement(ServerInfoFixtureData.serializer(), infoData),
        )

        val state = roundTripResponse(dir, "sync_state_response.json")
        val stateData = wireJson.decodeFromJsonElement(
            SyncStateResult.serializer(),
            state.getValue("data"),
        )
        assertEquals(DATASET_ID, stateData.datasetId)
        assertEquals(1842L, stateData.revision)
        assertEquals(1L, stateData.minAvailableRevision)
        assertEquals(1788264000000L, stateData.serverTime)
        assertJsonTreeEquals(
            state.getValue("data"),
            wireJson.encodeToJsonElement(SyncStateResult.serializer(), stateData),
        )

        val snapshotPage = roundTripResponse(dir, "sync_snapshot_response_page.json")
        val pageData = wireJson.decodeFromJsonElement(
            SyncSnapshotResult.serializer(),
            snapshotPage.getValue("data"),
        )
        assertEquals(1842L, pageData.snapshotRevision)
        assertEquals(2, pageData.rows.size)
        assertEquals(SNAPSHOT_CURSOR, pageData.nextCursor)
        assertFalse(pageData.done)
        assertJsonTreeEquals(
            snapshotPage.getValue("data"),
            wireJson.encodeToJsonElement(SyncSnapshotResult.serializer(), pageData),
        )

        val snapshotDone = roundTripResponse(dir, "sync_snapshot_response_done.json")
        val doneData = wireJson.decodeFromJsonElement(
            SyncSnapshotResult.serializer(),
            snapshotDone.getValue("data"),
        )
        assertTrue(doneData.done)
        assertNull(doneData.nextCursor)
        // nextCursor must be omitted (not null) when absent.
        assertFalse(snapshotDone.getValue("data").jsonObject.containsKey("nextCursor"))
        assertJsonTreeEquals(
            snapshotDone.getValue("data"),
            wireJson.encodeToJsonElement(SyncSnapshotResult.serializer(), doneData),
        )

        val pull = roundTripResponse(dir, "sync_pull_response.json")
        val pullData = wireJson.decodeFromJsonElement(
            SyncPullResult.serializer(),
            pull.getValue("data"),
        )
        assertEquals(1842L, pullData.fromRevision)
        assertEquals(1844L, pullData.toRevision)
        assertEquals(2, pullData.changes.size)
        assertFalse(pullData.hasMore)
        assertJsonTreeEquals(
            pull.getValue("data"),
            wireJson.encodeToJsonElement(SyncPullResult.serializer(), pullData),
        )

        val push = roundTripResponse(dir, "sync_push_response.json")
        val pushData = wireJson.decodeFromJsonElement(
            SyncPushResult.serializer(),
            push.getValue("data"),
        )
        assertEquals(listOf("applied", "conflict", "invalid"), pushData.results.map { it.status })
        assertEquals(1845L, pushData.results[0].revision)
        assertEquals(1787200000000L, pushData.results[1].serverUpdatedAt)
        assertNotNull(pushData.results[1].serverPayload)
        assertEquals("INVALID_PATCH", pushData.results[2].error?.code)
        // The response envelope must not carry a replayed field.
        assertFalse(push.getValue("data").jsonObject.containsKey("replayed"))
        assertJsonTreeEquals(
            push.getValue("data"),
            wireJson.encodeToJsonElement(SyncPushResult.serializer(), pushData),
        )

        val recordsPushResponse = roundTripResponse(dir, "sync_push_records_response.json")
        val batchData = wireJson.decodeFromJsonElement(
            SyncPushResult.serializer(),
            recordsPushResponse.getValue("data"),
        )
        assertEquals(listOf("applied", "conflict", "applied"), batchData.results.map { it.status })
        // Applied create results report the newly allocated record id.
        assertEquals(78L, batchData.results[0].recordId)
        assertEquals(1902L, batchData.results[0].revision)
        assertEquals(27L, batchData.results[1].recordId)
        assertNotNull(batchData.results[1].serverPayload)
        assertEquals(45L, batchData.results[2].recordId)
        assertJsonTreeEquals(
            recordsPushResponse.getValue("data"),
            wireJson.encodeToJsonElement(SyncPushResult.serializer(), batchData),
        )

        val records = roundTripResponse(dir, "records_list_detailed_response.json")
        val recordsData = wireJson.decodeFromJsonElement(
            RecordsListDetailedResult.serializer(),
            records.getValue("data"),
        )
        val record = recordsData.records.single()
        assertEquals(27L, record.recordId)
        assertEquals("cash_flow", record.type)
        assertEquals("-8650", record.amount)
        assertEquals("聚餐 翅客 王慨然和他小弟", record.note)
        assertEquals(1786747810000L, record.updatedAt)
        assertEquals("ai:req-dinner-0001", record.operationId)
        assertNull(record.deletedAt)
        // deletedAt is omitted (not null) for active records.
        assertFalse(
            records.getValue("data").jsonObject
                .getValue("records").let { it as JsonArray }[0].jsonObject
                .containsKey("deletedAt"),
        )
        assertNotNull(recordsData.nextCursor)
        assertJsonTreeEquals(
            records.getValue("data"),
            wireJson.encodeToJsonElement(RecordsListDetailedResult.serializer(), recordsData),
        )
    }

    @Test
    fun errorFixturesPinCodesAndMessages() {
        val dir = assumeFixturesPresent()
        val expected = mapOf(
            "error_dataset_mismatch.json" to MoneyLanErrorCodes.DATASET_MISMATCH,
            "error_resync_required.json" to MoneyLanErrorCodes.RESYNC_REQUIRED,
            "error_rate_limited.json" to MoneyLanErrorCodes.RATE_LIMITED,
            "error_unsupported_capability.json" to MoneyLanErrorCodes.UNSUPPORTED_CAPABILITY,
            "error_write_disabled.json" to MoneyLanErrorCodes.WRITE_DISABLED,
            "error_device_revoked.json" to MoneyLanErrorCodes.DEVICE_REVOKED,
        )
        expected.forEach { (name, code) ->
            val response = roundTripErrorResponse(dir, name)
            val error = assertNotNull(response.error, "$name must carry an error body")
            assertEquals(code, error.code, "$name error code")
        }

        // The domain exceptions thrown by the sync use cases must carry the pinned messages.
        val mismatch = readFixture(dir, "error_dataset_mismatch.json")
        assertEquals(
            mismatch.getValue("error").jsonObject.getValue("message").jsonPrimitive.content,
            SyncDatasetMismatchException().message,
        )
        val resync = readFixture(dir, "error_resync_required.json")
        assertEquals(
            resync.getValue("error").jsonObject.getValue("message").jsonPrimitive.content,
            SyncResyncRequiredException().message,
        )
    }

    @Test
    fun snapshotRowFixturesMatchWireShaping() {
        val dir = assumeFixturesPresent()
        listOf(
            "payload_account.json",
            "payload_cash_flow.json",
            "payload_transfer.json",
            "payload_balance_update.json",
            "payload_balance_adjustment.json",
        ).forEach { name ->
            val fixture = readFixture(dir, name)
            val payload = fixture.getValue("payload").jsonObject
            val row = SyncSnapshotRow(
                entityKind = SyncEntityKind.fromValue(
                    fixture.getValue("entityKind").jsonPrimitive.content,
                )!!,
                recordId = (payload["recordId"] ?: payload.getValue("accountId")).jsonPrimitive.long,
                sourceRevision = fixture.getValue("sourceRevision").jsonPrimitive.long,
                payloadJson = payload.toString(),
            )
            assertJsonTreeEquals(fixture, row.toWireJson(), name)
        }

        val tombstone = readFixture(dir, "tombstone_cash_flow.json")
        val tombstoneRow = SyncSnapshotRow(
            entityKind = SyncEntityKind.CASH_FLOW,
            recordId = 44,
            sourceRevision = 0,
            payloadJson = null,
            updatedAt = 1788261000000L,
            deletedAt = 1788261000000L,
        )
        assertJsonTreeEquals(tombstone, tombstoneRow.toWireJson(), "tombstone_cash_flow.json")
    }

    @Test
    fun mirrorPayloadBuildersProduceFixturePayloads() {
        val dir = assumeFixturesPresent()

        val accountPayload = SyncMirrorPayloads.accountPayloadJson(
            Account(
                id = 3,
                name = "招商银行储蓄卡",
                initialBalance = 100000,
                createdAt = 1754000000000L,
                displayOrder = 0,
                colorName = "blue",
                iconName = "bank",
                lastUsedAt = 1788260000000L,
                lastBalanceUpdateAt = 1785000000000L,
                kind = AccountKind.FUNDING,
            ),
        )
        assertPayloadEquals(dir, "payload_account.json", accountPayload)

        val cashFlowPayload = SyncMirrorPayloads.cashFlowPayloadJson(
            CashFlowRecord(
                id = 27,
                accountId = 3,
                direction = "outflow",
                amount = 8650,
                note = "聚餐 翅客 王慨然和他小弟",
                occurredAt = 1786747800000L,
                createdAt = 1786747810000L,
                updatedAt = 1786747810000L,
                operationId = "ai:req-dinner-0001",
            ),
        )
        assertPayloadEquals(dir, "payload_cash_flow.json", cashFlowPayload)

        val transferPayload = SyncMirrorPayloads.transferPayloadJson(
            TransferRecord(
                id = 31,
                fromAccountId = 3,
                toAccountId = 5,
                amount = 200000,
                note = "还信用卡",
                occurredAt = 1787000000000L,
                createdAt = 1787000010000L,
                updatedAt = 1787000010000L,
                operationId = "op-9f2e7b31-4c2d-4e8a-9d1f-3a5b7c9e2d4f",
            ),
        )
        assertPayloadEquals(dir, "payload_transfer.json", transferPayload)

        val balanceUpdatePayload = SyncMirrorPayloads.balanceUpdatePayloadJson(
            BalanceUpdateRecord(
                id = 12,
                accountId = 3,
                actualBalance = 152300,
                systemBalanceBeforeUpdate = 152100,
                delta = 200,
                occurredAt = 1785000000000L,
                createdAt = 1785000010000L,
                updatedAt = 1785000010000L,
                operationId = "op-7d1c4a55-2e8b-4f3a-8c6d-1b9e5a7c3f2d",
            ),
        )
        assertPayloadEquals(dir, "payload_balance_update.json", balanceUpdatePayload)

        val balanceAdjustmentPayload = SyncMirrorPayloads.balanceAdjustmentPayloadJson(
            BalanceAdjustmentRecord(
                id = 9,
                accountId = 5,
                delta = -1500,
                occurredAt = 1784500000000L,
                createdAt = 1784500010000L,
                updatedAt = 1784500010000L,
                operationId = "op-2b8e6f14-9a3c-4d7b-b5e1-6f4a8c2d9e1b",
            ),
        )
        assertPayloadEquals(dir, "payload_balance_adjustment.json", balanceAdjustmentPayload)
    }

    @Test
    fun pullChangeWireShapingMatchesFixture() {
        val dir = assumeFixturesPresent()
        val response = readFixture(dir, "sync_pull_response.json")
        val fixtureChanges = response.getValue("data").jsonObject
            .getValue("changes").let { it as JsonArray }

        val upsertFixture = fixtureChanges[0].jsonObject
        val upsert = SyncChange(
            revision = 1843,
            entityKind = SyncEntityKind.CASH_FLOW,
            recordId = 45,
            operation = SyncChangeOperation.UPSERT,
            payloadJson = upsertFixture.getValue("payload").toString(),
            updatedAt = 1788263880000L,
        )
        assertJsonTreeEquals(upsertFixture, upsert.toWireJson(), "pull upsert change")

        val deleteFixture = fixtureChanges[1].jsonObject
        val delete = SyncChange(
            revision = 1844,
            entityKind = SyncEntityKind.CASH_FLOW,
            recordId = 44,
            operation = SyncChangeOperation.DELETE,
            payloadJson = null,
            updatedAt = 1788261000000L,
            deletedAt = 1788261000000L,
        )
        assertJsonTreeEquals(deleteFixture, delete.toWireJson(), "pull delete change")
    }

    private fun assertPayloadEquals(dir: File, fixtureName: String, payloadJson: String) {
        val fixture = readFixture(dir, fixtureName)
        assertJsonTreeEquals(
            fixture.getValue("payload"),
            SyncMirrorJson.parseToJsonElement(payloadJson),
            "$fixtureName payload",
        )
    }

    /** Decodes a request fixture and asserts the whole envelope re-encodes tree-equal. */
    private fun roundTripRequest(dir: File, name: String): MoneyLanRequest {
        val text = File(dir, name).readText()
        val request = wireJson.decodeFromString(MoneyLanRequest.serializer(), text)
        assertJsonTreeEquals(
            wireJson.parseToJsonElement(text),
            wireJson.encodeToJsonElement(MoneyLanRequest.serializer(), request),
            name,
        )
        return request
    }

    /** Decodes an ok-response fixture and asserts the whole envelope re-encodes tree-equal. */
    private fun roundTripResponse(dir: File, name: String): JsonObject {
        val text = File(dir, name).readText()
        val response = wireJson.decodeFromString(MoneyLanResponse.serializer(), text)
        assertTrue(response.ok, "$name must be an ok response")
        assertJsonTreeEquals(
            wireJson.parseToJsonElement(text),
            wireJson.encodeToJsonElement(MoneyLanResponse.serializer(), response),
            name,
        )
        return wireJson.parseToJsonElement(text).jsonObject
    }

    private fun roundTripErrorResponse(dir: File, name: String): MoneyLanResponse {
        val text = File(dir, name).readText()
        val response = wireJson.decodeFromString(MoneyLanResponse.serializer(), text)
        assertFalse(response.ok, "$name must be an error response")
        assertJsonTreeEquals(
            wireJson.parseToJsonElement(text),
            wireJson.encodeToJsonElement(MoneyLanResponse.serializer(), response),
            name,
        )
        return response
    }

    /** Order-insensitive structural equality with path diagnostics (JsonObject key order ignored). */
    private fun assertJsonTreeEquals(expected: JsonElement, actual: JsonElement, path: String = "$") {
        if (expected is JsonObject && actual is JsonObject) {
            assertEquals(expected.keys, actual.keys, "keys differ at $path")
            expected.keys.forEach { key ->
                assertJsonTreeEquals(expected.getValue(key), actual.getValue(key), "$path.$key")
            }
            return
        }
        if (expected is JsonArray && actual is JsonArray) {
            assertEquals(expected.size, actual.size, "array size differs at $path")
            expected.indices.forEach { index ->
                assertJsonTreeEquals(expected[index], actual[index], "$path[$index]")
            }
            return
        }
        assertEquals(expected, actual, "value differs at $path")
    }

    @Serializable
    private data class ServerInfoFixtureData(
        val protocolVersion: Int,
        val app: String,
        val pairingAllowed: Boolean,
        val allowWrite: Boolean,
        val expiresAt: Long,
        val capabilities: List<String>,
    )

    // session.device.v1 wire shapes mirror the server-private DTOs in MoneyLanServer.

    @Serializable
    private data class PairBeginArgumentsFixture(val deviceId: String, val clientName: String)

    @Serializable
    private data class PairBeginResultFixture(
        val pairRequestId: String,
        val status: String,
        val expiresInSec: Int,
    )

    @Serializable
    private data class PairPollResultFixture(
        val status: String,
        val credential: String? = null,
        val token: String? = null,
        val sessionId: String? = null,
        val allowWrite: Boolean? = null,
        val expiresAt: Long? = null,
    )

    @Serializable
    private data class ResumeArgumentsFixture(val deviceId: String, val credential: String)

    @Serializable
    private data class PairResultFixture(
        val token: String,
        val sessionId: String,
        val allowWrite: Boolean,
        val expiresAt: Long,
        val credential: String? = null,
    )

    private companion object {
        const val DATASET_ID = "3f6b8c2a-9c1e-4f7a-9b3d-2f6a1c8e5d47"
        const val SNAPSHOT_CURSOR = "czoxODQyOmNhc2hfZmxvdzoyNw"
    }
}
