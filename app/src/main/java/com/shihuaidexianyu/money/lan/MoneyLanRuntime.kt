package com.shihuaidexianyu.money.lan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MoneyLanServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
}
data class MoneyLanRuntimeState(
    val status: MoneyLanServerStatus = MoneyLanServerStatus.STOPPED,
    val addresses: List<String> = emptyList(),
    val port: Int? = null,
    val pairingCode: String? = null,
    val pairedClientName: String? = null,
    val allowWrite: Boolean = true,
    val startedAt: Long? = null,
    val expiresAt: Long? = null,
    val errorMessage: String? = null,
    /** Pending confirmation-style pairing (session.device.v1); null when no request is waiting. */
    val pendingPairRequestId: String? = null,
    val pendingPairClientName: String? = null,
    val pendingPairExpiresAt: Long? = null,
    /** NSD service name once discovery broadcast is registered; null when not broadcasting. */
    val discoveryName: String? = null,
)

object MoneyLanRuntime {
    private val mutableState = MutableStateFlow(MoneyLanRuntimeState())
    val state: StateFlow<MoneyLanRuntimeState> = mutableState.asStateFlow()

    /**
     * In-process bridge from UI and notification actions to the live server's pairing/device
     * controls. Set by MoneyLanService while the server runs; null when stopped.
     */
    @Volatile
    var pairingResponder: MoneyLanPairingResponder? = null

    internal fun publish(state: MoneyLanRuntimeState) {
        mutableState.value = state
    }

    internal fun update(transform: (MoneyLanRuntimeState) -> MoneyLanRuntimeState) {
        mutableState.value = transform(mutableState.value)
    }
}

/** User-facing pairing decisions and device revocation, answered by the live server. */
interface MoneyLanPairingResponder {
    fun approve(pairRequestId: String): Boolean
    fun deny(pairRequestId: String): Boolean
    suspend fun revokeDevice(deviceId: String)
}
