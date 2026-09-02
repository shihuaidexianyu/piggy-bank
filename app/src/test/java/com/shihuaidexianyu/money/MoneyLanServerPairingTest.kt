package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.lan.LanPairedDevice
import com.shihuaidexianyu.money.lan.LanPairedDeviceStore
import com.shihuaidexianyu.money.lan.MoneyLanErrorCodes
import com.shihuaidexianyu.money.lan.MoneyLanRequest
import com.shihuaidexianyu.money.lan.MoneyLanResponse
import com.shihuaidexianyu.money.lan.MoneyLanRouteHandler
import com.shihuaidexianyu.money.lan.MoneyLanRuntime
import com.shihuaidexianyu.money.lan.MoneyLanServer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Socket
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test

/**
 * Exercises the session.device.v1 pairing state machine over real loopback frames: confirmation
 * pairing (begin/poll), credential resume, revocation, and the legacy code path that additionally
 * issues a device credential. The router is a fake echo — these tests pin server-side session
 * behavior, not request routing.
 */
class MoneyLanServerPairingTest {

    private val wireJson = Json { ignoreUnknownKeys = true }

    @Test
    fun `pair begin then approve issues a credential and occupies the session`() = withServer { server, store ->
        val begin = call(server, "session.pair.begin", pairBeginArgs())
        assertTrue(begin.ok)
        val pairRequestId = begin.dataObject().getValue("pairRequestId").jsonPrimitive.content
        assertEquals("pending", begin.dataObject().getValue("status").jsonPrimitive.content)

        val beforeDecision = call(server, "session.pair.poll", pairPollArgs(pairRequestId))
        assertEquals("pending", beforeDecision.dataObject().getValue("status").jsonPrimitive.content)

        assertTrue(server.approvePairing(pairRequestId))
        val approved = call(server, "session.pair.poll", pairPollArgs(pairRequestId))
        assertTrue(approved.ok)
        val data = approved.dataObject()
        assertEquals("approved", data.getValue("status").jsonPrimitive.content)
        val credential = data.getValue("credential").jsonPrimitive.content
        assertTrue(credential.isNotEmpty())
        assertTrue(data.getValue("token").jsonPrimitive.content.isNotEmpty())
        assertTrue(data.getValue("sessionId").jsonPrimitive.content.isNotEmpty())

        val stored = store.find(DEVICE_ID)
        assertNotNull(stored)
        assertEquals(CLIENT_NAME, stored.clientName)
        assertEquals(sha256Hex(credential), stored.credentialHash)
        assertEquals(CLIENT_NAME, MoneyLanRuntime.state.value.pairedClientName)
        assertNull(MoneyLanRuntime.state.value.pendingPairRequestId)
    }

    @Test
    fun `deny resolves the poll as denied and consumes the request`() = withServer { server, _ ->
        val pairRequestId = beginPairing(server)
        assertTrue(server.denyPairing(pairRequestId))

        val denied = call(server, "session.pair.poll", pairPollArgs(pairRequestId))
        assertEquals("denied", denied.dataObject().getValue("status").jsonPrimitive.content)

        val again = call(server, "session.pair.poll", pairPollArgs(pairRequestId))
        assertEquals("expired", again.dataObject().getValue("status").jsonPrimitive.content)
    }

    @Test
    fun `poll with an unknown request id reports expired`() = withServer { server, _ ->
        val response = call(server, "session.pair.poll", pairPollArgs("no-such-request"))
        assertTrue(response.ok)
        assertEquals("expired", response.dataObject().getValue("status").jsonPrimitive.content)
    }

    @Test
    fun `approving twice fails because the resolution is consumed once`() = withServer { server, _ ->
        val pairRequestId = beginPairing(server)
        assertTrue(server.approvePairing(pairRequestId))
        assertFalse(server.approvePairing(pairRequestId))
        assertFalse(server.denyPairing(pairRequestId))
    }

    @Test
    fun `an approved poll mints the credential exactly once`() = withServer { server, _ ->
        val pairRequestId = beginPairing(server)
        server.approvePairing(pairRequestId)

        val first = call(server, "session.pair.poll", pairPollArgs(pairRequestId))
        assertEquals("approved", first.dataObject().getValue("status").jsonPrimitive.content)

        val second = call(server, "session.pair.poll", pairPollArgs(pairRequestId))
        assertEquals("expired", second.dataObject().getValue("status").jsonPrimitive.content)
    }

    @Test
    fun `begin is throttled within two seconds`() = withServer { server, _ ->
        assertTrue(call(server, "session.pair.begin", pairBeginArgs()).ok)
        val throttled = call(server, "session.pair.begin", pairBeginArgs())
        assertFalse(throttled.ok)
        assertEquals(MoneyLanErrorCodes.RATE_LIMITED, throttled.error?.code)
    }

    @Test
    fun `a newer begin supersedes the previous pending request`() = withServer { server, _ ->
        val staleRequestId = beginPairing(server)
        Thread.sleep(2_100)
        val freshRequestId = beginPairing(server)
        assertNotEquals(staleRequestId, freshRequestId)

        val stale = call(server, "session.pair.poll", pairPollArgs(staleRequestId))
        assertEquals("expired", stale.dataObject().getValue("status").jsonPrimitive.content)
        val fresh = call(server, "session.pair.poll", pairPollArgs(freshRequestId))
        assertEquals("pending", fresh.dataObject().getValue("status").jsonPrimitive.content)
    }

    @Test
    fun `resume succeeds with the stored credential hash and touches last seen`() = withServer { server, store ->
        store.upsert(pairedDevice(credential = "known-credential", lastSeenAt = 1L))

        val resumed = call(server, "session.resume", resumeArgs(DEVICE_ID, "known-credential"))
        assertTrue(resumed.ok)
        val data = resumed.dataObject()
        assertTrue(data.getValue("token").jsonPrimitive.content.isNotEmpty())
        assertEquals(true, data.getValue("allowWrite").jsonPrimitive.content.toBoolean())
        assertTrue(store.find(DEVICE_ID)!!.lastSeenAt > 1L)

        val echo = call(server, "sync.state", JsonObject(emptyMap()), token = data.getValue("token").jsonPrimitive.content)
        assertTrue(echo.ok)
    }

    @Test
    fun `resume with an unknown device id is reported as revoked`() = withServer { server, _ ->
        val response = call(server, "session.resume", resumeArgs("ghost-device", "whatever"))
        assertFalse(response.ok)
        assertEquals(MoneyLanErrorCodes.DEVICE_REVOKED, response.error?.code)
    }

    @Test
    fun `resume with a wrong credential is unauthorized and locks pairing after five failures`() = withServer { server, store ->
        store.upsert(pairedDevice(credential = "real-credential"))

        repeat(5) {
            val response = call(server, "session.resume", resumeArgs(DEVICE_ID, "wrong-credential"))
            assertFalse(response.ok)
            assertEquals(MoneyLanErrorCodes.UNAUTHORIZED, response.error?.code)
        }

        val begin = call(server, "session.pair.begin", pairBeginArgs())
        assertFalse(begin.ok)
        assertEquals(MoneyLanErrorCodes.PAIRING_LOCKED, begin.error?.code)
    }

    @Test
    fun `resume while another session is active is rejected`() = withServer { server, store ->
        store.upsert(pairedDevice(deviceId = "device-a", credential = "cred-a"))
        store.upsert(pairedDevice(deviceId = "device-b", credential = "cred-b"))

        assertTrue(call(server, "session.resume", resumeArgs("device-a", "cred-a")).ok)
        val second = call(server, "session.resume", resumeArgs("device-b", "cred-b"))
        assertFalse(second.ok)
        assertEquals(MoneyLanErrorCodes.ALREADY_PAIRED, second.error?.code)
    }

    @Test
    fun `revoking a device drops its session and forgets the credential`() = withServer { server, store ->
        store.upsert(pairedDevice(credential = "known-credential"))
        val resumed = call(server, "session.resume", resumeArgs(DEVICE_ID, "known-credential"))
        val token = resumed.dataObject().getValue("token").jsonPrimitive.content
        assertTrue(call(server, "sync.state", JsonObject(emptyMap()), token = token).ok)

        server.revokeDevice(DEVICE_ID)

        assertNull(store.find(DEVICE_ID))
        assertNull(MoneyLanRuntime.state.value.pairedClientName)
        val afterRevoke = call(server, "sync.state", JsonObject(emptyMap()), token = token)
        assertFalse(afterRevoke.ok)
        assertEquals(MoneyLanErrorCodes.UNAUTHORIZED, afterRevoke.error?.code)
        val resumeAgain = call(server, "session.resume", resumeArgs(DEVICE_ID, "known-credential"))
        assertEquals(MoneyLanErrorCodes.DEVICE_REVOKED, resumeAgain.error?.code)
    }

    @Test
    fun `legacy code pairing with a device id also issues a credential`() = withServer { server, store ->
        val code = MoneyLanRuntime.state.value.pairingCode
        assertNotNull(code)

        val response = call(server, "session.pair", buildJsonObject {
            put("code", JsonPrimitive(code))
            put("clientName", JsonPrimitive(CLIENT_NAME))
            put("deviceId", JsonPrimitive(DEVICE_ID))
        })
        assertTrue(response.ok)
        val credential = response.dataObject().getValue("credential").jsonPrimitive.content
        assertTrue(credential.isNotEmpty())
        assertEquals(sha256Hex(credential), store.find(DEVICE_ID)?.credentialHash)
    }

    @Test
    fun `legacy code pairing without a device id stays session only`() = withServer { server, store ->
        val code = MoneyLanRuntime.state.value.pairingCode
        assertNotNull(code)

        val response = call(server, "session.pair", buildJsonObject {
            put("code", JsonPrimitive(code))
            put("clientName", JsonPrimitive(CLIENT_NAME))
        })
        assertTrue(response.ok)
        assertFalse("credential" in response.dataObject())
        assertNull(store.find(DEVICE_ID))
    }

    // --- helpers ---

    private fun withServer(block: suspend (MoneyLanServer, FakeLanPairedDeviceStore) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = FakeLanPairedDeviceStore()
        val now = System.currentTimeMillis()
        val server = MoneyLanServer(
            scope = scope,
            router = MoneyLanRouteHandler { _, _ -> JsonObject(mapOf("echo" to JsonPrimitive(true))) },
            pairedDeviceStore = store,
            allowWrite = true,
            startedAt = now,
            expiresAt = now + 60 * 60 * 1_000L,
        )
        try {
            server.start()
            block(server, store)
        } finally {
            server.stop()
            scope.cancel()
        }
    }

    private fun call(
        server: MoneyLanServer,
        action: String,
        arguments: JsonObject,
        token: String? = null,
    ): MoneyLanResponse {
        val request = MoneyLanRequest(
            version = 1,
            requestId = "test-${System.nanoTime()}",
            action = action,
            token = token,
            arguments = arguments,
        )
        Socket(InetAddress.getLoopbackAddress(), server.port).use { socket ->
            val bytes = wireJson.encodeToString(MoneyLanRequest.serializer(), request).encodeToByteArray()
            DataOutputStream(socket.getOutputStream()).apply {
                writeInt(bytes.size)
                write(bytes)
                flush()
            }
            val input = DataInputStream(socket.getInputStream())
            val size = input.readInt()
            val payload = ByteArray(size)
            input.readFully(payload)
            return wireJson.decodeFromString(MoneyLanResponse.serializer(), payload.decodeToString())
        }
    }

    private fun beginPairing(server: MoneyLanServer): String {
        val response = call(server, "session.pair.begin", pairBeginArgs())
        assertTrue(response.ok, "begin failed: ${response.error}")
        return response.dataObject().getValue("pairRequestId").jsonPrimitive.content
    }

    private fun pairBeginArgs(deviceId: String = DEVICE_ID) = buildJsonObject {
        put("deviceId", JsonPrimitive(deviceId))
        put("clientName", JsonPrimitive(CLIENT_NAME))
    }

    private fun pairPollArgs(pairRequestId: String) = buildJsonObject {
        put("pairRequestId", JsonPrimitive(pairRequestId))
    }

    private fun resumeArgs(deviceId: String, credential: String) = buildJsonObject {
        put("deviceId", JsonPrimitive(deviceId))
        put("credential", JsonPrimitive(credential))
    }

    private fun pairedDevice(
        deviceId: String = DEVICE_ID,
        credential: String,
        lastSeenAt: Long = 0L,
    ) = LanPairedDevice(
        deviceId = deviceId,
        clientName = CLIENT_NAME,
        credentialHash = sha256Hex(credential),
        pairedAt = 1L,
        lastSeenAt = lastSeenAt,
    )

    private fun sha256Hex(value: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun MoneyLanResponse.dataObject(): JsonObject =
        data as? JsonObject ?: error("expected a data object, got $this")

    private class FakeLanPairedDeviceStore : LanPairedDeviceStore {
        private val devices = LinkedHashMap<String, LanPairedDevice>()
        override fun observeDevices(): Flow<List<LanPairedDevice>> = MutableStateFlow(devices.values.toList())
        override suspend fun find(deviceId: String): LanPairedDevice? = devices[deviceId]
        override suspend fun upsert(device: LanPairedDevice) {
            devices[device.deviceId] = device
        }
        override suspend fun remove(deviceId: String) {
            devices.remove(deviceId)
        }
        override suspend fun touchLastSeen(deviceId: String, seenAt: Long) {
            devices[deviceId]?.let { devices[deviceId] = it.copy(lastSeenAt = seenAt) }
        }
    }

    private companion object {
        const val DEVICE_ID = "test-device-0001"
        const val CLIENT_NAME = "Unit Test Rig"
    }
}
