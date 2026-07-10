package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.LegacyMoneyStoreReadResult
import com.shihuaidexianyu.money.data.repository.PersistentMoneyStore
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PersistentMoneyStoreStrictReadTest {
    @Test
    fun `strict read distinguishes missing empty corrupt and data`() {
        val directory = createTempDirectory("money-legacy-test").toFile()
        try {
            val file = File(directory, "money_store.json")
            val store = PersistentMoneyStore(file)
            assertEquals(LegacyMoneyStoreReadResult.Missing, store.readStrict())

            file.writeText("  ")
            assertEquals(LegacyMoneyStoreReadResult.Empty, store.readStrict())

            file.writeText("{broken")
            assertIs<LegacyMoneyStoreReadResult.Corrupt>(store.readStrict())

            file.writeText("{}")
            assertIs<LegacyMoneyStoreReadResult.Corrupt>(store.readStrict())

            file.writeText(emptyLegacyStoreJson())
            assertEquals(LegacyMoneyStoreReadResult.Empty, store.readStrict())

            file.writeText(
                JSONObject(emptyLegacyStoreJson())
                    .put("cashFlowRecords", "damaged")
                    .toString(),
            )
            assertIs<LegacyMoneyStoreReadResult.Corrupt>(store.readStrict())

            file.writeText(
                """
                {
                  "accounts":[{"id":1,"name":"现金","initialBalance":0,"createdAt":1,"displayOrder":0}],
                  "cashFlowRecords":[],
                  "transferRecords":[],
                  "balanceUpdates":[],
                  "adjustments":[]
                }
                """.trimIndent(),
            )
            val data = assertIs<LegacyMoneyStoreReadResult.Data>(store.readStrict())
            assertEquals(listOf("现金"), data.snapshot.accounts.map { it.name })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `strict read rejects duplicate ids and orphan foreign keys before Room`() {
        val directory = createTempDirectory("money-legacy-invalid").toFile()
        try {
            val file = File(directory, "money_store.json")
            val store = PersistentMoneyStore(file)
            val root = JSONObject(emptyLegacyStoreJson())
            val account = JSONObject()
                .put("id", 1L)
                .put("name", "现金")
                .put("initialBalance", 0L)
                .put("createdAt", 1L)
                .put("displayOrder", 0)
            root.put("accounts", JSONArray().put(account).put(JSONObject(account.toString())))
            file.writeText(root.toString())
            assertIs<LegacyMoneyStoreReadResult.Corrupt>(store.readStrict())

            root.put("accounts", JSONArray().put(account))
            root.put(
                "cashFlowRecords",
                JSONArray().put(cashFlowJson(accountId = 99L, occurredAt = 10L)),
            )
            file.writeText(root.toString())
            assertIs<LegacyMoneyStoreReadResult.Corrupt>(store.readStrict())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `archived account close time includes metadata and every bilateral ledger event`() {
        val directory = createTempDirectory("money-legacy-close-time").toFile()
        try {
            val file = File(directory, "money_store.json")
            val store = PersistentMoneyStore(file)
            val eventCases = listOf<(JSONObject) -> Unit>(
                { root -> root.getJSONArray("cashFlowRecords").put(cashFlowJson(1L, 20L)) },
                { root -> root.getJSONArray("transferRecords").put(transferJson(1L, 2L, 20L)) },
                { root -> root.getJSONArray("transferRecords").put(transferJson(2L, 1L, 20L)) },
                { root -> root.getJSONArray("balanceUpdates").put(balanceUpdateJson(1L, 20L)) },
                { root -> root.getJSONArray("adjustments").put(adjustmentJson(1L, 20L)) },
            )

            eventCases.forEach { addEvent ->
                val root = JSONObject(emptyLegacyStoreJson()).apply {
                    getJSONArray("accounts")
                        .put(archivedAccountJson(id = 1L))
                        .put(activeAccountJson(id = 2L))
                    addEvent(this)
                }
                file.writeText(root.toString())

                val data = assertIs<LegacyMoneyStoreReadResult.Data>(store.readStrict())
                assertEquals(20L, data.snapshot.accounts.first { it.id == 1L }.closedAt)
            }

            val metadataOnly = JSONObject(emptyLegacyStoreJson()).apply {
                getJSONArray("accounts").put(
                    archivedAccountJson(id = 1L)
                        .put("archivedAt", 5L)
                        .put("lastUsedAt", 7L)
                        .put("lastBalanceUpdateAt", 9L),
                )
            }
            file.writeText(metadataOnly.toString())
            val data = assertIs<LegacyMoneyStoreReadResult.Data>(store.readStrict())
            assertEquals(9L, data.snapshot.accounts.single().closedAt)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun emptyLegacyStoreJson(): String = JSONObject()
        .put("accounts", JSONArray())
        .put("cashFlowRecords", JSONArray())
        .put("transferRecords", JSONArray())
        .put("balanceUpdates", JSONArray())
        .put("adjustments", JSONArray())
        .toString()

    private fun activeAccountJson(id: Long) = JSONObject()
        .put("id", id)
        .put("name", "账户$id")
        .put("initialBalance", 0L)
        .put("createdAt", 1L)
        .put("displayOrder", id.toInt())

    private fun archivedAccountJson(id: Long) = activeAccountJson(id)
        .put("isArchived", true)
        .put("archivedAt", 2L)
        .put("lastUsedAt", 3L)
        .put("lastBalanceUpdateAt", 4L)

    private fun cashFlowJson(accountId: Long, occurredAt: Long) = JSONObject()
        .put("id", 1L)
        .put("accountId", accountId)
        .put("direction", "inflow")
        .put("amount", 1L)
        .put("purpose", "测试")
        .put("occurredAt", occurredAt)
        .put("createdAt", occurredAt)
        .put("updatedAt", occurredAt)

    private fun transferJson(fromId: Long, toId: Long, occurredAt: Long) = JSONObject()
        .put("id", 1L)
        .put("fromAccountId", fromId)
        .put("toAccountId", toId)
        .put("amount", 1L)
        .put("note", "测试")
        .put("occurredAt", occurredAt)
        .put("createdAt", occurredAt)
        .put("updatedAt", occurredAt)

    private fun balanceUpdateJson(accountId: Long, occurredAt: Long) = JSONObject()
        .put("id", 1L)
        .put("accountId", accountId)
        .put("actualBalance", 1L)
        .put("systemBalanceBeforeUpdate", 0L)
        .put("delta", 1L)
        .put("occurredAt", occurredAt)
        .put("createdAt", occurredAt)

    private fun adjustmentJson(accountId: Long, occurredAt: Long) = JSONObject()
        .put("id", 1L)
        .put("accountId", accountId)
        .put("delta", 1L)
        .put("occurredAt", occurredAt)
        .put("createdAt", occurredAt)
}
