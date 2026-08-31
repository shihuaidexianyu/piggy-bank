package com.shihuaidexianyu.money.lan

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

const val MONEY_LAN_PROTOCOL_VERSION = 1
const val MONEY_LAN_MAX_FRAME_BYTES = 256 * 1024

@Serializable
data class MoneyLanRequest(
    val version: Int,
    val requestId: String,
    val action: String,
    val token: String? = null,
    val arguments: JsonObject = JsonObject(emptyMap()),
)
@Serializable
data class MoneyLanResponse(
    val version: Int = MONEY_LAN_PROTOCOL_VERSION,
    val requestId: String,
    val ok: Boolean,
    val data: JsonElement? = null,
    val error: MoneyLanError? = null,
)

@Serializable
data class MoneyLanError(
    val code: String,
    val message: String,
)

class MoneyLanProtocolException(
    val code: String,
    override val message: String,
) : IllegalArgumentException(message)

data class MoneyLanClient(
    val sessionId: String,
    val name: String,
    val allowWrite: Boolean,
)
