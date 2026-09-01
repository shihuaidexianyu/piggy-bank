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
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class MoneyLanServer(
    private val scope: CoroutineScope,
    private val router: MoneyLanRequestRouter,
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

    private fun pair(request: MoneyLanRequest) = synchronized(session) {
        if (System.currentTimeMillis() >= pairingExpiresAt) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.PAIRING_EXPIRED, "配对码已过期，请在手机上重新启动服务")
        }
        if (failedPairAttempts.get() >= MAX_PAIRING_FAILURES) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.PAIRING_LOCKED, "配对失败次数过多，请在手机上重新启动服务")
        }
        if (session.get() != null) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.ALREADY_PAIRED, "本次会话已经配对一台电脑")
        }
        val arguments = try {
            protocolJson.decodeFromJsonElement<PairArguments>(request.arguments)
        } catch (error: SerializationException) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.VALIDATION_FAILED, "配对参数无效")
        }
        if (!constantTimeEquals(arguments.code, pairingCode)) {
            if (failedPairAttempts.incrementAndGet() >= MAX_PAIRING_FAILURES) {
                MoneyLanRuntime.update { it.copy(pairingCode = null) }
            }
            throw MoneyLanProtocolException(MoneyLanErrorCodes.PAIRING_FAILED, "配对码错误")
        }
        val clientName = arguments.clientName.trim().takeIf { it.isNotEmpty() }?.take(80)
            ?: throw MoneyLanProtocolException(MoneyLanErrorCodes.VALIDATION_FAILED, "客户端名称不能为空")
        val paired = PairedSession(
            sessionId = UUID.randomUUID().toString(),
            clientName = clientName,
            token = secureToken(),
        )
        session.set(paired)
        MoneyLanRuntime.update {
            it.copy(pairingCode = null, pairedClientName = paired.clientName)
        }
        protocolJson.encodeToJsonElement(
            PairResult(
                token = paired.token,
                sessionId = paired.sessionId,
                allowWrite = allowWrite,
                expiresAt = expiresAt,
            ),
        )
    }

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

    private data class PairedSession(
        val sessionId: String,
        val clientName: String,
        val token: String,
    )

    private companion object {
        const val TAG = "MoneyLanServer"
        const val MAX_CONCURRENT_CONNECTIONS = 4
        const val SERVER_BACKLOG = 16
        const val SOCKET_TIMEOUT_MILLIS = 15_000
        const val PAIRING_WINDOW_MILLIS = 10 * 60 * 1_000L
        const val MAX_PAIRING_FAILURES = 5

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
private data class PairArguments(val code: String, val clientName: String)

@Serializable
private data class PairResult(
    val token: String,
    val sessionId: String,
    val allowWrite: Boolean,
    val expiresAt: Long,
)
