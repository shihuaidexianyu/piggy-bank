package com.shihuaidexianyu.money.data.backup

import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import com.shihuaidexianyu.money.domain.model.backup.MONEY_BACKUP_SCHEMA_VERSION
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object BackupJsonCodec {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
        prettyPrint = false
    }

    fun encode(snapshot: MoneyBackupSnapshot): String =
        json.encodeToString(snapshot)

    fun decode(raw: String): MoneyBackupSnapshot =
        json.decodeFromString(migrateLegacyBackupJson(raw))

    private fun migrateLegacyBackupJson(raw: String): String {
        val root = json.parseToJsonElement(raw).jsonObject
        val schemaVersion = root["metadata"]
            ?.jsonObject
            ?.get("schemaVersion")
            ?.jsonPrimitive
            ?.content
            ?.toIntOrNull()
        if (schemaVersion != 1) return raw

        val updatedFields = root.toMutableMap()
        val metadata = root["metadata"]?.jsonObject.orEmpty()
        updatedFields["metadata"] = JsonObject(
            metadata + ("schemaVersion" to JsonPrimitive(MONEY_BACKUP_SCHEMA_VERSION)),
        )
        updatedFields["balanceAdjustmentRecords"] = JsonArray(
            root["balanceAdjustmentRecords"]
                ?.jsonArray
                .orEmpty()
                .mapNotNull { element ->
                    val adjustment = element.jsonObject
                    val sourceUpdateRecordId = adjustment["sourceUpdateRecordId"]
                        ?.jsonPrimitive
                        ?.content
                        ?.toLongOrNull()
                        ?: 0L
                    if (sourceUpdateRecordId == 0L) {
                        JsonObject(adjustment.filterKeys { key -> key != "sourceUpdateRecordId" })
                    } else {
                        null
                    }
                },
        )
        return JsonObject(updatedFields).toString()
    }
}
