package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.RecurringReminderEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import org.json.JSONArray
import org.json.JSONObject

fun ExportJsonPayload.toJson(): JSONObject {
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
