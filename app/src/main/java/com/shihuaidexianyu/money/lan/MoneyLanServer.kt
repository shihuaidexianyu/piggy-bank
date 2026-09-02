package com.shihuaidexianyu.money.lan

import android.util.Log
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.sync.SyncCapabilities
import com.shihuaidexianyu.money.domain.model.sync.SyncDatasetMismatchException
import com.shihuaidexianyu.money.domain.model.sync.SyncResyncRequiredException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * LAN server for the Money Link Protocol. Two pairing styles share one active session:
 *
 * - Legacy one-time code (`session.pair`): 8-digit code shown on the phone, 10-minute window,
 *   5 failures lock pairing. When the client also sends a `deviceId`, success additionally
 *   issues a persistent device credential (session.device.v1).
 * - Confirmation pairing (`session.pair.begin` / `session.pair.poll`): the client presents
 *   itself, the user approves on the phone (in-app dialog or notification action), and the
 *   client polls for the outcome. Approval issues a persistent device credential.
 *
 * A paired device resumes later sessions with `session.resume` (deviceId + credential) without
 * any on-phone interaction. The phone stores only the SHA-256 hash of each credential; the
 * plaintext is handed to the client exactly once. Revoking the row on the phone forces the
 * device to pair again.
 */
class MoneyLanServer(
    private val scope: CoroutineScope,
    private val router: MoneyLanRouteHandler,
    private val pairedDeviceStore: LanPairedDeviceStore,
    private val allowWrite: Boolean,
    private val startedAt: Long,
    private val expiresAt: Long,
    private val writeRateLimiter: MoneyLanWriteRateLimiter = MoneyLanWriteRateLimiter(),
) {
    private val session = AtomicReference<PairedSession?>(null)
    private val failedPairAttempts = AtomicInteger(0)
    private val connectionSlots = Semaphore(MAX_CONCURRENT_CONNECTIONS)
    private val serverSocket = ServerSocket(0, SERVER_BACKLOG, InetAddress.getByName("0.0.0.0"))
    private val pairingCode = securePairingCode()
    private val pairingExpiresAt = minOf(startedAt + PAIRING_WINDOW_MILLIS, expiresAt)
    private val pendingPair = AtomicReference<PendingPair?>(null)
    private val lastPairBeginAt = AtomicLong(0)
    private var acceptJob: Job? = null

    val port: Int get() = serverSocket.localPort

    fun start() {
        val addresses = localIpv4Addresses()
        MoneyLanRuntime.publish(
            MoneyLanRuntimeState(
                status = MoneyLanServerStatus.RUNNING,
                addresses = addresses,
                port = port,
                pairingCode = pairingCode,
                allowWrite = allowWrite,
                startedAt = startedAt,
                expiresAt = expiresAt,
            ),
        )
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && !serverSocket.isClosed) {
                val socket = try {
                    serverSocket.accept()
                } catch (error: Exception) {
                    if (isActive && !serverSocket.isClosed) {
                        Log.e(TAG, "LAN server accept failed", error)
                    }
                    break
                }
                launch {
                    connectionSlots.withPermit { handle(socket) }
                }
            }
        }
    }

    suspend fun stop() {
        close()
        acceptJob?.cancelAndJoin()
    }

    fun close() {
        runCatching { serverSocket.close() }
        acceptJob?.cancel()
    }

    /**
     * Approve the pending confirmation pairing (from the in-app dialog or the notification
     * action). The credential is only minted when the client polls the outcome, so the
     * plaintext credential never touches UI-side state.
     */
    fun approvePairing(pairRequestId: String): Boolean =
        resolvePendingPair(pairRequestId, PairResolution.APPROVED)

    fun denyPairing(pairRequestId: String): Boolean =
        resolvePendingPair(pairRequestId, PairResolution.DENIED)

    private fun resolvePendingPair(pairRequestId: String, resolution: PairResolution): Boolean {
        val pending = pendingPair.get() ?: return false
        if (pending.requestId != pairRequestId || pending.resolution != null ||
            System.currentTimeMillis() >= pending.expiresAt
        ) {
            return false
        }
        val resolved = pendingPair.compareAndSet(pending, pending.copy(resolution = resolution))
        if (resolved) {
            MoneyLanRuntime.update {
                it.copy(
                    pendingPairRequestId = null,
                    pendingPairClientName = null,
                    pendingPairExpiresAt = null,
                )
            }
        }
        return resolved
    }

    /**
     * Revoke a paired device (called from the devices UI). Drops the stored credential and, when
     * the device holds the active session, ends that session immediately.
     */
    suspend fun revokeDevice(deviceId: String) {
        pairedDeviceStore.remove(deviceId)
        val active = session.get()
        if (active != null && active.deviceId == deviceId) {
            session.set(null)
            MoneyLanRuntime.update { it.copy(pairedClientName = null) }
        }
    }

    private suspend fun handle(socket: Socket) {
        socket.use { clientSocket ->
            if (!clientSocket.inetAddress.isTrustedLocalAddress()) {
                Log.w(TAG, "Rejected non-local LAN peer")
                return
            }
            clientSocket.soTimeout = SOCKET_TIMEOUT_MILLIS
            var requestId = "unknown"
            val response = try {
                val input = DataInputStream(clientSocket.getInputStream())
                val size = input.readInt()
                if (size !in 1..MONEY_LAN_MAX_FRAME_BYTES) {
                    throw MoneyLanProtocolException(MoneyLanErrorCodes.FRAME_TOO_LARGE, "请求帧大小无效")
                }
                val payload = ByteArray(size)
                input.readFully(payload)
                val request = try {
                    protocolJson.decodeFromString<MoneyLanRequest>(payload.decodeToString())
                } catch (error: SerializationException) {
                    throw MoneyLanProtocolException(MoneyLanErrorCodes.INVALID_REQUEST, "请求 JSON 或字段无效")
                }
                requestId = request.requestId
                requireRequest(request)
                val data = when (request.action) {
                    "server.info" -> serverInfo()
                    "session.pair" -> pair(request)
                    "session.pair.begin" -> pairBegin(request)
                    "session.pair.poll" -> pairPoll(request)
                    "session.resume" -> resume(request)
                    else -> {
                        val paired = authenticate(request.token)
                        enforceWriteRate(request.action)
                        router.route(
                            request,
                            MoneyLanClient(
                                sessionId = paired.sessionId,
                                name = paired.clientName,
                                allowWrite = allowWrite,
                            ),
                        )
                    }
                }
                MoneyLanResponse(requestId = request.requestId, ok = true, data = data)
            } catch (error: CancellationException) {
                throw error
            } catch (error: MoneyLanProtocolException) {
                errorResponse(requestId, error.code, error.message)
            } catch (error: LedgerRecordChangedException) {
                errorResponse(requestId, MoneyLanErrorCodes.CONFLICT, error.message ?: "记录已变化")
            } catch (error: SyncDatasetMismatchException) {
                errorResponse(requestId, MoneyLanErrorCodes.DATASET_MISMATCH, error.message ?: "数据集不匹配")
            } catch (error: SyncResyncRequiredException) {
                errorResponse(requestId, MoneyLanErrorCodes.RESYNC_REQUIRED, error.message ?: "需要重新快照")
            } catch (error: IllegalArgumentException) {
                errorResponse(requestId, MoneyLanErrorCodes.VALIDATION_FAILED, error.message ?: "请求参数无效")
            } catch (error: ArithmeticException) {
                errorResponse(requestId, MoneyLanErrorCodes.AMOUNT_OVERFLOW, "金额计算超出可表示范围")
            } catch (error: Exception) {
                Log.e(TAG, "LAN request failed", error)
                errorResponse(requestId, MoneyLanErrorCodes.INTERNAL_ERROR, "手机端处理请求失败")
            }

            runCatching {
                var bytes = protocolJson.encodeToString(response).encodeToByteArray()
                if (bytes.size > MONEY_LAN_MAX_FRAME_BYTES) {
                    bytes = protocolJson.encodeToString(
                        errorResponse(
                            requestId,
                            MoneyLanErrorCodes.RESPONSE_TOO_LARGE,
                            "响应超过协议大小上限，请缩小查询范围",
                        ),
                    ).encodeToByteArray()
                }
                DataOutputStream(clientSocket.getOutputStream()).use { output ->
                    output.writeInt(bytes.size)
                    output.write(bytes)
                    output.flush()
                }
            }.onFailure { error ->
                Log.w(TAG, "LAN response write failed: ${error::class.simpleName}")
            }
        }
    }

    private fun requireRequest(request: MoneyLanRequest) {
        if (request.version != MONEY_LAN_PROTOCOL_VERSION) {
            throw MoneyLanProtocolException(
                MoneyLanErrorCodes.UNSUPPORTED_VERSION,
                "手机仅支持协议版本 $MONEY_LAN_PROTOCOL_VERSION",
            )
        }
        if (request.requestId.isBlank() || request.requestId.length > 128) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.INVALID_REQUEST, "requestId 无效")
        }
        if (System.currentTimeMillis() >= expiresAt) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.SESSION_EXPIRED, "手机局域网会话已到期")
        }
    }

    private fun serverInfo() = protocolJson.encodeToJsonElement(
        ServerInfoResult(
            protocolVersion = MONEY_LAN_PROTOCOL_VERSION,
            app = "Money",
            pairingAllowed = session.get() == null &&
                failedPairAttempts.get() < MAX_PAIRING_FAILURES &&
                System.currentTimeMillis() < pairingExpiresAt,
            allowWrite = allowWrite,
            expiresAt = expiresAt,
            capabilities = SyncCapabilities.ALL,
        ),
    )

    private suspend fun pair(request: MoneyLanRequest): JsonElement {
        val arguments = try {
            protocolJson.decodeFromJsonElement<PairArguments>(request.arguments)
        } catch (error: SerializationException) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.VALIDATION_FAILED, "配对参数无效")
        }
        val issued = synchronized(session) {
            if (System.currentTimeMillis() >= pairingExpiresAt) {
                throw MoneyLanProtocolException(MoneyLanErrorCodes.PAIRING_EXPIRED, "配对码已过期，请在手机上重新启动服务")
            }
            if (failedPairAttempts.get() >= MAX_PAIRING_FAILURES) {
                throw MoneyLanProtocolException(MoneyLanErrorCodes.PAIRING_LOCKED, "配对失败次数过多，请在手机上重新启动服务")
            }
            if (session.get() != null) {
                throw MoneyLanProtocolException(MoneyLanErrorCodes.ALREADY_PAIRED, "本次会话已经配对一台电脑")
            }
            if (!constantTimeEquals(arguments.code, pairingCode)) {
                if (failedPairAttempts.incrementAndGet() >= MAX_PAIRING_FAILURES) {
                    MoneyLanRuntime.update { it.copy(pairingCode = null) }
                }
                throw MoneyLanProtocolException(MoneyLanErrorCodes.PAIRING_FAILED, "配对码错误")
            }
            val clientName = sanitizeClientName(arguments.clientName)
            val deviceId = arguments.deviceId?.trim()?.takeIf { it.isNotEmpty() }
            if (deviceId != null) {
                requireValidDeviceId(deviceId)
            }
            val paired = PairedSession(
                sessionId = UUID.randomUUID().toString(),
                clientName = clientName,
                token = secureToken(),
                deviceId = deviceId,
            )
            session.set(paired)
            IssuedPairing(paired, deviceId, deviceId?.let { secureToken() })
        }
        MoneyLanRuntime.update {
            it.copy(pairingCode = null, pairedClientName = issued.session.clientName)
        }
        val deviceId = issued.deviceId
        val credential = issued.credential
        if (deviceId != null && credential != null) {
            pairedDeviceStore.upsert(
                LanPairedDevice(
                    deviceId = deviceId,
                    clientName = issued.session.clientName,
                    credentialHash = sha256Hex(credential),
                    pairedAt = System.currentTimeMillis(),
                    lastSeenAt = System.currentTimeMillis(),
                ),
            )
        }
        return protocolJson.encodeToJsonElement(
            PairResult(
                token = issued.session.token,
                sessionId = issued.session.sessionId,
                allowWrite = allowWrite,
                expiresAt = expiresAt,
                credential = credential,
            ),
        )
    }

    /** Outcome of a successful legacy code pairing, carried outside the session lock. */
    private data class IssuedPairing(
        val session: PairedSession,
        val deviceId: String?,
        val credential: String?,
    )

    private fun pairBegin(request: MoneyLanRequest): JsonElement {
        if (failedPairAttempts.get() >= MAX_PAIRING_FAILURES) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.PAIRING_LOCKED, "配对失败次数过多，请在手机上重新启动服务")
        }
        if (session.get() != null) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.ALREADY_PAIRED, "本次会话已经配对一台电脑")
        }
        val now = System.currentTimeMillis()
        // Cheap throttle against notification spam: at most one begin per couple of seconds.
        val lastBegin = lastPairBeginAt.get()
        if (now - lastBegin < PAIR_BEGIN_MIN_INTERVAL_MILLIS ||
            !lastPairBeginAt.compareAndSet(lastBegin, now)
        ) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.RATE_LIMITED, "配对请求过于频繁，请稍后再试")
        }
        val arguments = try {
            protocolJson.decodeFromJsonElement<PairBeginArguments>(request.arguments)
        } catch (error: SerializationException) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.VALIDATION_FAILED, "配对参数无效")
        }
        val deviceId = arguments.deviceId.trim()
        requireValidDeviceId(deviceId)
        val clientName = sanitizeClientName(arguments.clientName)
        val pending = PendingPair(
            requestId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            clientName = clientName,
            expiresAt = now + PAIR_CONFIRM_WINDOW_MILLIS,
        )
        // Single pending slot: a newer begin replaces an expired-or-waiting request; the client
        // holding the old requestId will observe "expired" on its next poll.
        pendingPair.set(pending)
        MoneyLanRuntime.update {
            it.copy(
                pendingPairRequestId = pending.requestId,
                pendingPairClientName = pending.clientName,
                pendingPairExpiresAt = pending.expiresAt,
            )
        }
        return protocolJson.encodeToJsonElement(
            PairBeginResult(
                pairRequestId = pending.requestId,
                status = PAIR_STATUS_PENDING,
                expiresInSec = (PAIR_CONFIRM_WINDOW_MILLIS / 1_000L).toInt(),
            ),
        )
    }

    private suspend fun pairPoll(request: MoneyLanRequest): JsonElement {
        val arguments = try {
            protocolJson.decodeFromJsonElement<PairPollArguments>(request.arguments)
        } catch (error: SerializationException) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.VALIDATION_FAILED, "配对参数无效")
        }
        val pending = pendingPair.get()
        if (pending == null || pending.requestId != arguments.pairRequestId) {
            return pairPollStatus(PAIR_STATUS_EXPIRED)
        }
        if (System.currentTimeMillis() >= pending.expiresAt) {
            clearPendingPair(pending)
            return pairPollStatus(PAIR_STATUS_EXPIRED)
        }
        when (pending.resolution) {
            null -> return pairPollStatus(PAIR_STATUS_PENDING)
            PairResolution.DENIED -> {
                clearPendingPair(pending)
                return pairPollStatus(PAIR_STATUS_DENIED)
            }
            PairResolution.APPROVED -> Unit
        }
        // Consume the pending request atomically so a double poll can never mint two credentials.
        if (!pendingPair.compareAndSet(pending, null)) {
            return pairPollStatus(PAIR_STATUS_EXPIRED)
        }
        val now = System.currentTimeMillis()
        val credential = secureToken()
        pairedDeviceStore.upsert(
            LanPairedDevice(
                deviceId = pending.deviceId,
                clientName = pending.clientName,
                credentialHash = sha256Hex(credential),
                pairedAt = now,
                lastSeenAt = now,
            ),
        )
        val paired = PairedSession(
            sessionId = UUID.randomUUID().toString(),
            clientName = pending.clientName,
            token = secureToken(),
            deviceId = pending.deviceId,
        )
        synchronized(session) {
            if (session.get() != null) {
                throw MoneyLanProtocolException(MoneyLanErrorCodes.ALREADY_PAIRED, "本次会话已经配对一台电脑")
            }
            session.set(paired)
        }
        MoneyLanRuntime.update {
            it.copy(
                pairingCode = null,
                pairedClientName = paired.clientName,
                pendingPairRequestId = null,
                pendingPairClientName = null,
                pendingPairExpiresAt = null,
            )
        }
        return protocolJson.encodeToJsonElement(
            PairPollResult(
                status = PAIR_STATUS_APPROVED,
                credential = credential,
                token = paired.token,
                sessionId = paired.sessionId,
                allowWrite = allowWrite,
                expiresAt = expiresAt,
            ),
        )
    }

    private suspend fun resume(request: MoneyLanRequest): JsonElement {
        val arguments = try {
            protocolJson.decodeFromJsonElement<ResumeArguments>(request.arguments)
        } catch (error: SerializationException) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.VALIDATION_FAILED, "会话恢复参数无效")
        }
        val deviceId = arguments.deviceId.trim()
        if (arguments.credential.isBlank() || arguments.credential.length > MAX_CREDENTIAL_LENGTH) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.VALIDATION_FAILED, "设备凭据无效")
        }
        val device = pairedDeviceStore.find(deviceId)
            ?: throw MoneyLanProtocolException(
                MoneyLanErrorCodes.DEVICE_REVOKED,
                "设备未配对或配对已撤销，请重新配对",
            )
        if (!constantTimeEquals(sha256Hex(arguments.credential), device.credentialHash)) {
            if (failedPairAttempts.incrementAndGet() >= MAX_PAIRING_FAILURES) {
                MoneyLanRuntime.update { it.copy(pairingCode = null) }
            }
            throw MoneyLanProtocolException(MoneyLanErrorCodes.UNAUTHORIZED, "设备凭据无效")
        }
        val paired = PairedSession(
            sessionId = UUID.randomUUID().toString(),
            clientName = device.clientName,
            token = secureToken(),
            deviceId = device.deviceId,
        )
        synchronized(session) {
            if (session.get() != null) {
                throw MoneyLanProtocolException(MoneyLanErrorCodes.ALREADY_PAIRED, "本次会话已经配对一台电脑")
            }
            session.set(paired)
        }
        runCatching { pairedDeviceStore.touchLastSeen(device.deviceId, System.currentTimeMillis()) }
        MoneyLanRuntime.update { it.copy(pairedClientName = paired.clientName) }
        return protocolJson.encodeToJsonElement(
            PairResult(
                token = paired.token,
                sessionId = paired.sessionId,
                allowWrite = allowWrite,
                expiresAt = expiresAt,
            ),
        )
    }

    private fun clearPendingPair(pending: PendingPair) {
        if (pendingPair.compareAndSet(pending, null)) {
            MoneyLanRuntime.update {
                it.copy(
                    pendingPairRequestId = null,
                    pendingPairClientName = null,
                    pendingPairExpiresAt = null,
                )
            }
        }
    }

    private fun pairPollStatus(status: String): JsonElement =
        protocolJson.encodeToJsonElement(PairPollResult(status = status))

    private fun authenticate(token: String?): PairedSession {
        val paired = session.get()
            ?: throw MoneyLanProtocolException(MoneyLanErrorCodes.UNAUTHORIZED, "请先完成配对")
        if (token == null || !constantTimeEquals(token, paired.token)) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.UNAUTHORIZED, "会话 Token 无效")
        }
        return paired
    }

    private fun enforceWriteRate(action: String) {
        if (action !in writeActions) return
        writeRateLimiter.chargeWriteAction()
    }

    private fun errorResponse(requestId: String, code: String, message: String) = MoneyLanResponse(
        requestId = requestId,
        ok = false,
        error = MoneyLanError(code = code, message = message),
    )

    private enum class PairResolution { APPROVED, DENIED }

    private data class PendingPair(
        val requestId: String,
        val deviceId: String,
        val clientName: String,
        val expiresAt: Long,
        val resolution: PairResolution? = null,
    )

    private data class PairedSession(
        val sessionId: String,
        val clientName: String,
        val token: String,
        /** Set for sessions held by a persistently paired device (null for legacy pairings). */
        val deviceId: String? = null,
    )

    private companion object {
        const val TAG = "MoneyLanServer"
        const val MAX_CONCURRENT_CONNECTIONS = 4
        const val SERVER_BACKLOG = 16
        const val SOCKET_TIMEOUT_MILLIS = 15_000
        const val PAIRING_WINDOW_MILLIS = 10 * 60 * 1_000L
        const val MAX_PAIRING_FAILURES = 5
        const val PAIR_CONFIRM_WINDOW_MILLIS = 60 * 1_000L
        const val PAIR_BEGIN_MIN_INTERVAL_MILLIS = 2_000L
        const val MAX_CREDENTIAL_LENGTH = 128
        const val MAX_DEVICE_ID_LENGTH = 64

        const val PAIR_STATUS_PENDING = "pending"
        const val PAIR_STATUS_APPROVED = "approved"
        const val PAIR_STATUS_DENIED = "denied"
        const val PAIR_STATUS_EXPIRED = "expired"

        // sync.push is deliberately absent: the router charges it after the idempotent-replay
        // check (replays are free) against both rate windows of the shared limiter.
        val writeActions = setOf(
            "journal.undo_latest",
            "cashflow.create",
            "cashflow.update",
            "cashflow.delete",
            "transfer.create",
            "transfer.update",
            "transfer.delete",
        )

        val protocolJson = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
            encodeDefaults = true
        }

        fun securePairingCode(): String = SecureRandom().nextInt(100_000_000)
            .toString()
            .padStart(8, '0')

        fun secureToken(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
            left.encodeToByteArray(),
            right.encodeToByteArray(),
        )

        fun sha256Hex(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun sanitizeClientName(raw: String): String = raw.trim().takeIf { it.isNotEmpty() }?.take(80)
            ?: throw MoneyLanProtocolException(MoneyLanErrorCodes.VALIDATION_FAILED, "客户端名称不能为空")

        fun requireValidDeviceId(deviceId: String) {
            if (deviceId.isEmpty() || deviceId.length > MAX_DEVICE_ID_LENGTH) {
                throw MoneyLanProtocolException(MoneyLanErrorCodes.VALIDATION_FAILED, "设备标识无效")
            }
        }

        fun localIpv4Addresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress || it.isLinkLocalAddress }
                .sortedByDescending(InetAddress::isSiteLocalAddress)
                .mapNotNull(InetAddress::getHostAddress)
                .distinct()
                .toList()
        }.getOrDefault(emptyList())

        fun InetAddress.isTrustedLocalAddress(): Boolean =
            isSiteLocalAddress || isLinkLocalAddress || isLoopbackAddress
    }
}

@Serializable
private data class ServerInfoResult(
    val protocolVersion: Int,
    val app: String,
    val pairingAllowed: Boolean,
    val allowWrite: Boolean,
    val expiresAt: Long,
    val capabilities: List<String>,
)

@Serializable
private data class PairArguments(
    val code: String,
    val clientName: String,
    /** Present when the client wants a persistent device credential (session.device.v1). */
    val deviceId: String? = null,
)

@Serializable
private data class PairResult(
    val token: String,
    val sessionId: String,
    val allowWrite: Boolean,
    val expiresAt: Long,
    /** Plaintext device credential, returned exactly once; the phone stores only its hash. */
    val credential: String? = null,
)

@Serializable
private data class PairBeginArguments(val deviceId: String, val clientName: String)

@Serializable
private data class PairBeginResult(
    val pairRequestId: String,
    val status: String,
    val expiresInSec: Int,
)

@Serializable
private data class PairPollArguments(val pairRequestId: String)

@Serializable
private data class PairPollResult(
    val status: String,
    val credential: String? = null,
    val token: String? = null,
    val sessionId: String? = null,
    val allowWrite: Boolean? = null,
    val expiresAt: Long? = null,
)

@Serializable
private data class ResumeArguments(val deviceId: String, val credential: String)
