package com.shihuaidexianyu.money.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.RecurringReminderEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ExportJsonPayload(
    val accounts: List<AccountEntity>,
    val accountReminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
    val cashFlowRecords: List<CashFlowRecordEntity>,
    val transferRecords: List<TransferRecordEntity>,
    val balanceUpdateRecords: List<BalanceUpdateRecordEntity>,
    val balanceAdjustmentRecords: List<BalanceAdjustmentRecordEntity>,
    val recurringReminders: List<RecurringReminderEntity>,
    val settings: AppSettings,
    val exportedAt: Long,
    val appVersion: String,
)

data class ExportJsonResult(
    val uri: Uri,
    val fileName: String,
    val relativePath: String,
    val exportedAt: Long,
)

object ExportJsonPayloadFactory {
    fun build(
        accounts: List<AccountEntity>,
        accountReminderConfigs: Map<Long, BalanceUpdateReminderConfig> = emptyMap(),
        cashFlowRecords: List<CashFlowRecordEntity>,
        transferRecords: List<TransferRecordEntity>,
        balanceUpdateRecords: List<BalanceUpdateRecordEntity>,
        balanceAdjustmentRecords: List<BalanceAdjustmentRecordEntity>,
        recurringReminders: List<RecurringReminderEntity> = emptyList(),
        settings: AppSettings,
        exportedAt: Long,
        appVersion: String,
    ): ExportJsonPayload {
        return ExportJsonPayload(
            accounts = accounts.sortedWith(compareBy<AccountEntity> { it.isArchived }.thenBy { it.displayOrder }.thenBy { it.id }),
            accountReminderConfigs = accountReminderConfigs,
            cashFlowRecords = cashFlowRecords.sortedWith(compareBy<CashFlowRecordEntity> { it.occurredAt }.thenBy { it.id }),
            transferRecords = transferRecords.sortedWith(compareBy<TransferRecordEntity> { it.occurredAt }.thenBy { it.id }),
            balanceUpdateRecords = balanceUpdateRecords.sortedWith(compareBy<BalanceUpdateRecordEntity> { it.occurredAt }.thenBy { it.id }),
            balanceAdjustmentRecords = balanceAdjustmentRecords.sortedWith(compareBy<BalanceAdjustmentRecordEntity> { it.occurredAt }.thenBy { it.id }),
            recurringReminders = recurringReminders.sortedWith(compareBy<RecurringReminderEntity> { it.nextDueAt }.thenBy { it.id }),
            settings = settings,
            exportedAt = exportedAt,
            appVersion = appVersion,
        )
    }
}

class ExportJsonUseCase(
    private val context: Context,
    private val accountRepository: AccountRepository,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val recurringReminderRepository: RecurringReminderRepository,
) {
    suspend operator fun invoke(): ExportJsonResult {
        val exportedAt = System.currentTimeMillis()
        val payload = ExportJsonPayloadFactory.build(
            accounts = accountRepository.queryActiveAccounts() + accountRepository.queryArchivedAccounts(),
            accountReminderConfigs = accountReminderSettingsRepository.observeReminderConfigs().first(),
            cashFlowRecords = transactionRepository.queryAllCashFlowRecords(),
            transferRecords = transactionRepository.queryAllTransferRecords(),
            balanceUpdateRecords = transactionRepository.queryAllBalanceUpdateRecords(),
            balanceAdjustmentRecords = transactionRepository.queryAllBalanceAdjustmentRecords(),
            recurringReminders = recurringReminderRepository.queryAll(),
            settings = settingsRepository.observeSettings().first(),
            exportedAt = exportedAt,
            appVersion = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                ?: "unknown",
        )

        val fileName = buildFileName(exportedAt)
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/money"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建导出文件")

        return runCatching {
            resolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(payload.toJson().toString(2))
            } ?: throw IOException("无法写入导出文件")
            ExportJsonResult(
                uri = uri,
                fileName = fileName,
                relativePath = relativePath,
                exportedAt = exportedAt,
            )
        }.getOrElse { error ->
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun buildFileName(exportedAt: Long): String {
        val stamp = Instant.ofEpochMilli(exportedAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        return "money-backup-$stamp.json"
    }
}

private fun ExportJsonPayload.toJson(): JSONObject {
    return JSONObject().apply {
        put(
            "accounts",
            JSONArray().apply {
                accounts.forEach { account ->
                    put(account.toJson(accountReminderConfigs[account.id] ?: BalanceUpdateReminderConfig()))
                }
            },
        )
        put("cashFlowRecords", JSONArray().apply { cashFlowRecords.forEach { put(it.toJson()) } })
        put("transferRecords", JSONArray().apply { transferRecords.forEach { put(it.toJson()) } })
        put("balanceUpdateRecords", JSONArray().apply { balanceUpdateRecords.forEach { put(it.toJson()) } })
        put("balanceAdjustmentRecords", JSONArray().apply { balanceAdjustmentRecords.forEach { put(it.toJson()) } })
        put("recurringReminders", JSONArray().apply { recurringReminders.forEach { put(it.toJson()) } })
        put("settings", settings.toJson())
        put("exportedAt", exportedAt)
        put("appVersion", appVersion)
    }
}

private fun AppSettings.toJson(): JSONObject = JSONObject().apply {
    put("homePeriod", homePeriod.value)
    put("currencySymbol", currencySymbol)
    put("showStaleMark", showStaleMark)
    put("themeMode", themeMode.value)
    put("accountGroupOrder", JSONArray().apply { accountGroupOrder.forEach { put(it.value) } })
}

private fun AccountEntity.toJson(balanceUpdateReminderConfig: BalanceUpdateReminderConfig): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("groupType", groupType)
    put("initialBalance", initialBalance)
    put("createdAt", createdAt)
    put("archivedAt", archivedAt ?: JSONObject.NULL)
    put("isArchived", isArchived)
    put("lastUsedAt", lastUsedAt ?: JSONObject.NULL)
    put("lastBalanceUpdateAt", lastBalanceUpdateAt ?: JSONObject.NULL)
    put("balanceUpdateReminderWeekday", balanceUpdateReminderConfig.weekday.value)
    put("balanceUpdateReminderTime", balanceUpdateReminderConfig.timeText)
    put("displayOrder", displayOrder)
}

private fun CashFlowRecordEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("accountId", accountId)
    put("direction", direction)
    put("amount", amount)
    put("purpose", purpose)
    put("occurredAt", occurredAt)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    put("isDeleted", isDeleted)
}

private fun TransferRecordEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("fromAccountId", fromAccountId)
    put("toAccountId", toAccountId)
    put("amount", amount)
    put("note", note)
    put("occurredAt", occurredAt)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    put("isDeleted", isDeleted)
}

private fun BalanceUpdateRecordEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("accountId", accountId)
    put("actualBalance", actualBalance)
    put("systemBalanceBeforeUpdate", systemBalanceBeforeUpdate)
    put("delta", delta)
    put("occurredAt", occurredAt)
    put("createdAt", createdAt)
}

private fun BalanceAdjustmentRecordEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("accountId", accountId)
    put("delta", delta)
    put("sourceUpdateRecordId", sourceUpdateRecordId)
    put("occurredAt", occurredAt)
    put("createdAt", createdAt)
}

private fun RecurringReminderEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("type", type)
    put("accountId", accountId)
    put("direction", direction)
    put("amount", amount)
    put("periodType", periodType)
    put("periodValue", periodValue)
    put("periodMonth", periodMonth ?: JSONObject.NULL)
    put("isEnabled", isEnabled)
    put("nextDueAt", nextDueAt)
    put("lastConfirmedAt", lastConfirmedAt ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}
