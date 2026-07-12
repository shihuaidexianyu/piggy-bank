package com.shihuaidexianyu.money.data.backup

import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_HOUR
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_MINUTE
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_MONTH_DAY
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_PERIOD
import com.shihuaidexianyu.money.domain.model.DEFAULT_BALANCE_UPDATE_REMINDER_WEEKDAY
import com.shihuaidexianyu.money.domain.model.backup.MONEY_BACKUP_SCHEMA_VERSION
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import com.shihuaidexianyu.money.domain.repository.BackupJsonEncoder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.encodeToStream
import java.io.OutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

object BackupJsonCodec : BackupJsonEncoder {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
        prettyPrint = false
    }

    override fun encode(snapshot: MoneyBackupSnapshot): String = json.encodeToString(snapshot)

    @OptIn(ExperimentalSerializationApi::class)
    override fun encodeToStream(snapshot: MoneyBackupSnapshot, output: OutputStream) {
        json.encodeToStream(MoneyBackupSnapshot.serializer(), snapshot, output)
    }

    fun decode(raw: String): MoneyBackupSnapshot =
        json.decodeFromString(migrateLegacyBackupJson(raw).toString())

    internal fun migrateLegacyBackupJson(raw: String): JsonObject {
        val root = json.parseToJsonElement(raw).jsonObject
        val schemaVersion = root.metadataVersion()
        require(schemaVersion in 1..MONEY_BACKUP_SCHEMA_VERSION) {
            "不支持的备份版本：$schemaVersion"
        }
        if (schemaVersion < MONEY_BACKUP_SCHEMA_VERSION) root.requireLegacyShape(schemaVersion)
        var current = root
        var currentVersion = schemaVersion
        while (currentVersion < MONEY_BACKUP_SCHEMA_VERSION) {
            val migration = BACKUP_MIGRATIONS.singleOrNull { it.from == currentVersion }
                ?: error("No backup migration from schema version $currentVersion")
            current = migration.transform(current).withVersion(migration.to)
            currentVersion = migration.to
        }
        return current
    }

    private val BACKUP_MIGRATIONS: List<BackupMigration> = listOf(
        object : BackupMigration {
            override val from = 1
            override val to = 2

            override fun transform(root: JsonObject): JsonObject = root.replacing(
                "balanceAdjustmentRecords",
                JsonArray(
                    root.requiredArray("balanceAdjustmentRecords").mapNotNull { element ->
                        val adjustment = element.jsonObject
                        if (adjustment.requiredLong("sourceUpdateRecordId") == 0L) {
                            JsonObject(adjustment - "sourceUpdateRecordId")
                        } else {
                            null
                        }
                    },
                ),
            )
        },
        object : BackupMigration {
            override val from = 2
            override val to = 3

            override fun transform(root: JsonObject): JsonObject =
                if ("savingsGoals" in root) root else root.replacing("savingsGoals", JsonArray(emptyList()))
        },
        object : BackupMigration {
            override val from = 3
            override val to = 4

            override fun transform(root: JsonObject): JsonObject = migrateV3ToV4(root)
        },
    )
}

private fun migrateV3ToV4(root: JsonObject): JsonObject {
    val accountEventMax = mutableMapOf<Long, Long>()
    fun track(accountId: Long, occurredAt: Long) {
        if (accountId > 0L && occurredAt > 0L) {
            accountEventMax[accountId] = maxOf(accountEventMax[accountId] ?: 0L, occurredAt)
        }
    }
    root.requiredArray("cashFlowRecords").forEach {
        it.jsonObject.let { row -> track(row.requiredLong("accountId"), row.requiredLong("occurredAt")) }
    }
    root.requiredArray("transferRecords").forEach {
        it.jsonObject.let { row ->
            track(row.requiredLong("fromAccountId"), row.requiredLong("occurredAt"))
            track(row.requiredLong("toAccountId"), row.requiredLong("occurredAt"))
        }
    }
    root.requiredArray("balanceUpdateRecords").forEach {
        it.jsonObject.let { row -> track(row.requiredLong("accountId"), row.requiredLong("occurredAt")) }
    }
    root.requiredArray("balanceAdjustmentRecords").forEach {
        it.jsonObject.let { row -> track(row.requiredLong("accountId"), row.requiredLong("occurredAt")) }
    }

    val migratedAccounts = root.requiredArray("accounts").map { element ->
        val old = element.jsonObject
        val archived = old.requiredBoolean("isArchived")
        val accountId = old.requiredLong("id")
        val createdAt = old.requiredLong("createdAt")
        val closedAt = if (archived) {
            maxOf(createdAt, old.optionalLong("archivedAt") ?: 0L, accountEventMax[accountId] ?: 0L)
        } else {
            null
        }
        JsonObject(
            (old - "archivedAt" - "isArchived") + mapOf(
                "isHidden" to JsonPrimitive(false),
                "closedAt" to (closedAt?.let(::JsonPrimitive) ?: JsonNull),
            ),
        )
    }
    val closedIds = migratedAccounts.mapNotNull { account ->
        account.jsonObject["closedAt"]?.takeUnless { it is JsonNull }
            ?.let { account.jsonObject.requiredLong("id") }
    }.toSet()

    val cash = root.requiredArray("cashFlowRecords").map { element ->
        val old = element.jsonObject
        val updatedAt = old.requiredLong("updatedAt")
        old.requiredLong("createdAt")
        JsonObject(
            (old - "purpose" - "isDeleted") + mapOf(
                "note" to JsonPrimitive(old.requiredString("purpose")),
                "updatedAt" to JsonPrimitive(updatedAt),
                "deletedAt" to if (old.requiredBoolean("isDeleted")) JsonPrimitive(updatedAt) else JsonNull,
                "operationId" to JsonPrimitive("cash:legacy-backup:${old.requiredLong("id")}"),
            ),
        )
    }
    val transfers = root.requiredArray("transferRecords").map { element ->
        val old = element.jsonObject
        val updatedAt = old.requiredLong("updatedAt")
        old.requiredLong("createdAt")
        JsonObject(
            (old - "isDeleted") + mapOf(
                "updatedAt" to JsonPrimitive(updatedAt),
                "deletedAt" to if (old.requiredBoolean("isDeleted")) JsonPrimitive(updatedAt) else JsonNull,
                "operationId" to JsonPrimitive("transfer:legacy-backup:${old.requiredLong("id")}"),
            ),
        )
    }
    fun migrateLedgerRows(name: String, operationPrefix: String): List<JsonElement> =
        root.requiredArray(name).map { element ->
            val old = element.jsonObject
            val createdAt = old.requiredLong("createdAt")
            JsonObject(
                old + mapOf(
                    "updatedAt" to JsonPrimitive(createdAt),
                    "deletedAt" to JsonNull,
                    "operationId" to JsonPrimitive("$operationPrefix:legacy-backup:${old.requiredLong("id")}"),
                ),
            )
        }

    val reminders = root.requiredArray("recurringReminders").map { element ->
        val old = element.jsonObject
        JsonObject(old + ("anchorDueAt" to JsonPrimitive(old.requiredLong("nextDueAt"))))
    }
    val configs = root.requiredArray("accountReminderConfigs").map { element ->
        val old = element.jsonObject
        val accountId = old.requiredLong("accountId")
        val config = old.requiredObject("config")
        config.requireLegacyReminderFields()
        JsonObject(
            old + ("config" to JsonObject(config + ("isEnabled" to JsonPrimitive(accountId !in closedIds)))),
        )
    }.toMutableList()
    val configuredAccountIds = configs.mapTo(mutableSetOf()) { it.jsonObject.requiredLong("accountId") }
    migratedAccounts.forEach { account ->
        val accountId = account.jsonObject.requiredLong("id")
        if (accountId !in configuredAccountIds) {
            configs += defaultReminderConfig(accountId, accountId !in closedIds)
        }
    }
    val oldGoal = root.requiredArray("savingsGoals")
        .map { it.jsonObject }
        .minByOrNull { it.requiredLong("id") }
    val goal = oldGoal?.let {
        val createdAt = it.requiredLong("createdAt")
        it.requiredLong("targetAmount")
        JsonObject(
            (it - "id") + mapOf(
                "id" to JsonPrimitive(1L),
                "updatedAt" to JsonPrimitive(createdAt),
            ),
        )
    }
    val oldSettings = root.requiredObject("settings")

    return JsonObject(
        linkedMapOf(
            "metadata" to (root["metadata"] ?: JsonObject(emptyMap())),
            "portableSettings" to JsonObject(
                mapOf(
                    "currencySymbol" to JsonPrimitive(oldSettings.requiredString("currencySymbol")),
                    "amountColorMode" to JsonPrimitive(oldSettings.requiredString("amountColorMode")),
                    "monthlyBudgetAmount" to JsonNull,
                ),
            ),
            "accounts" to JsonArray(migratedAccounts),
            "cashFlowRecords" to JsonArray(cash),
            "transferRecords" to JsonArray(transfers),
            "balanceUpdateRecords" to JsonArray(migrateLedgerRows("balanceUpdateRecords", "balance-update")),
            "balanceAdjustmentRecords" to JsonArray(
                migrateLedgerRows("balanceAdjustmentRecords", "balance-adjustment"),
            ),
            "recurringReminders" to JsonArray(reminders),
            "accountReminderConfigs" to JsonArray(configs),
            "savingsGoal" to (goal ?: JsonNull),
        ),
    )
}

private interface BackupMigration {
    val from: Int
    val to: Int
    fun transform(root: JsonObject): JsonObject
}

private fun JsonObject.metadataVersion(): Int {
    val value = requiredObject("metadata").requiredLong("schemaVersion")
    require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "metadata.schemaVersion 超出范围" }
    return value.toInt()
}

private fun JsonObject.withVersion(version: Int): JsonObject {
    val metadata = this["metadata"]?.jsonObject.orEmpty()
    return JsonObject(this + ("metadata" to JsonObject(metadata + ("schemaVersion" to JsonPrimitive(version)))))
}

private fun JsonObject.requireLegacyShape(schemaVersion: Int) {
    val metadata = requiredObject("metadata")
    metadata.requiredLong("databaseVersion")
    metadata.requiredLong("exportedAt")
    requiredObject("settings")
    listOf(
        "accounts",
        "cashFlowRecords",
        "transferRecords",
        "balanceUpdateRecords",
        "balanceAdjustmentRecords",
        "recurringReminders",
        "accountReminderConfigs",
    ).forEach(::requiredArray)
    if (schemaVersion >= 3) requiredArray("savingsGoals")
}

private fun JsonObject.requiredArray(name: String): JsonArray =
    this[name] as? JsonArray ?: throw IllegalArgumentException("$name 缺失或类型无效")

private fun JsonObject.requiredObject(name: String): JsonObject =
    this[name] as? JsonObject ?: throw IllegalArgumentException("$name 缺失或类型无效")

private fun JsonObject.requiredLong(name: String): Long {
    val value = this[name] as? JsonPrimitive
        ?: throw IllegalArgumentException("$name 缺失或类型无效")
    require(!value.isString) { "$name 必须为整数" }
    return value.content.toLongOrNull() ?: throw IllegalArgumentException("$name 必须为整数")
}

private fun JsonObject.optionalLong(name: String): Long? {
    val element = this[name] ?: return null
    if (element is JsonNull) return null
    return requiredLong(name)
}

private fun JsonObject.requiredBoolean(name: String): Boolean {
    val value = this[name] as? JsonPrimitive
        ?: throw IllegalArgumentException("$name 缺失或类型无效")
    require(!value.isString) { "$name 必须为布尔值" }
    return value.content.toBooleanStrictOrNull() ?: throw IllegalArgumentException("$name 必须为布尔值")
}

private fun JsonObject.requiredString(name: String): String {
    val value = this[name] as? JsonPrimitive
        ?: throw IllegalArgumentException("$name 缺失或类型无效")
    require(value.isString) { "$name 必须为字符串" }
    return value.content
}

private fun JsonObject.requireLegacyReminderFields() {
    requiredString("period")
    requiredString("weekday")
    requiredLong("monthDay")
    requiredLong("hour")
    requiredLong("minute")
}

private fun defaultReminderConfig(accountId: Long, isEnabled: Boolean): JsonObject = JsonObject(
    mapOf(
        "accountId" to JsonPrimitive(accountId),
        "config" to JsonObject(
            mapOf(
                "period" to JsonPrimitive(DEFAULT_BALANCE_UPDATE_REMINDER_PERIOD),
                "weekday" to JsonPrimitive(DEFAULT_BALANCE_UPDATE_REMINDER_WEEKDAY),
                "monthDay" to JsonPrimitive(DEFAULT_BALANCE_UPDATE_REMINDER_MONTH_DAY),
                "hour" to JsonPrimitive(DEFAULT_BALANCE_UPDATE_REMINDER_HOUR),
                "minute" to JsonPrimitive(DEFAULT_BALANCE_UPDATE_REMINDER_MINUTE),
                "isEnabled" to JsonPrimitive(isEnabled),
            ),
        ),
    ),
)

private fun JsonObject.replacing(name: String, value: JsonElement): JsonObject = JsonObject(this + (name to value))
