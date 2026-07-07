package com.shihuaidexianyu.money.data.backup

import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import com.shihuaidexianyu.money.domain.model.backup.MONEY_BACKUP_SCHEMA_VERSION
import com.shihuaidexianyu.money.domain.repository.BackupJsonEncoder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object BackupJsonCodec : BackupJsonEncoder {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
        prettyPrint = false
    }

    override fun encode(snapshot: MoneyBackupSnapshot): String =
        json.encodeToString(snapshot)

    fun decode(raw: String): MoneyBackupSnapshot =
        json.decodeFromString(migrateLegacyBackupJson(raw))

    /**
     * Sequence of [BackupMigration]s applied in order to bring a backup JSON up to
     * [MONEY_BACKUP_SCHEMA_VERSION]. Each migration takes the *previous* schema version and produces
     * the *next*; the chain lets us evolve the schema without one-off "if version == 1" branches.
     * Adding v3 only requires appending a new migration `2 -> 3` here — the dispatch is automatic.
     */
    private val BACKUP_MIGRATIONS: List<BackupMigration> = listOf(
        object : BackupMigration {
            override val from = 1
            override val to = 2
            override fun transform(root: JsonObject): JsonObject {
                val updatedFields = root.toMutableMap()
                val metadata = root["metadata"]?.jsonObject.orEmpty()
                updatedFields["metadata"] = JsonObject(
                    metadata + ("schemaVersion" to JsonPrimitive(2)),
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
                return JsonObject(updatedFields)
            }
        },
        object : BackupMigration {
            override val from = 2
            override val to = 3
            override fun transform(root: JsonObject): JsonObject {
                val updatedFields = root.toMutableMap()
                val metadata = root["metadata"]?.jsonObject.orEmpty()
                updatedFields["metadata"] = JsonObject(
                    metadata + ("schemaVersion" to JsonPrimitive(3)),
                )
                if ("savingsGoals" !in updatedFields) {
                    updatedFields["savingsGoals"] = JsonArray(emptyList())
                }
                return JsonObject(updatedFields)
            }
        },
    )

    private fun migrateLegacyBackupJson(raw: String): String {
        val root = json.parseToJsonElement(raw).jsonObject
        val schemaVersion = root["metadata"]
            ?.jsonObject
            ?.get("schemaVersion")
            ?.jsonPrimitive
            ?.content
            ?.toIntOrNull()
            ?: return raw
        if (schemaVersion >= MONEY_BACKUP_SCHEMA_VERSION) return raw

        var current = root
        var currentVersion = schemaVersion
        while (currentVersion < MONEY_BACKUP_SCHEMA_VERSION) {
            val migration = BACKUP_MIGRATIONS.firstOrNull { it.from == currentVersion }
                ?: throw IllegalArgumentException("No backup migration from schema version $currentVersion to ${currentVersion + 1}")
            current = migration.transform(current)
            // Stamp the new version so the next migration can find its `from`.
            val metadata = current["metadata"]?.jsonObject.orEmpty()
            current = JsonObject(current + ("metadata" to JsonObject(metadata + ("schemaVersion" to JsonPrimitive(migration.to)))))
            currentVersion = migration.to
        }
        return current.toString()
    }
}

private interface BackupMigration {
    val from: Int
    val to: Int

    fun transform(root: JsonObject): JsonObject
}

