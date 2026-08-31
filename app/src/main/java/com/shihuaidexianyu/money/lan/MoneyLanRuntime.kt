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
)

object MoneyLanRuntime {
    private val mutableState = MutableStateFlow(MoneyLanRuntimeState())
    val state: StateFlow<MoneyLanRuntimeState> = mutableState.asStateFlow()

    internal fun publish(state: MoneyLanRuntimeState) {
        mutableState.value = state
    }

    internal fun update(transform: (MoneyLanRuntimeState) -> MoneyLanRuntimeState) {
        mutableState.value = transform(mutableState.value)
    }
}
