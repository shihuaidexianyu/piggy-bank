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

    @Test
    fun `strict read rejects invalid legacy ledger domain semantics`() {
        val directory = createTempDirectory("money-legacy-domain-invalid").toFile()
        try {
            val file = File(directory, "money_store.json")
            val store = PersistentMoneyStore(file)
            val invalidRoots = listOf(
                validLegacyRoot().apply {
                    getJSONArray("cashFlowRecords").put(
                        cashFlowJson(1L, 60_000L, direction = "sideways"),
                    )
                },
                validLegacyRoot().apply {
                    getJSONArray("cashFlowRecords").put(cashFlowJson(1L, 60_000L, amount = 0L))
                },
                validLegacyRoot().apply {
                    getJSONArray("transferRecords").put(
                        transferJson(1L, 2L, 60_000L, amount = -1L),
                    )
                },
                validLegacyRoot().apply {
                    getJSONArray("adjustments").put(adjustmentJson(1L, 60_000L, delta = 0L))
                },
                validLegacyRoot(accountCreatedAt = 0L),
                validLegacyRoot().apply {
                    getJSONArray("accounts").getJSONObject(0)
                        .put("isArchived", true)
                        .put("archivedAt", 0L)
                },
                validLegacyRoot().apply {
                    getJSONArray("cashFlowRecords").put(
                        cashFlowJson(1L, 59_999L),
                    )
                },
                validLegacyRoot().apply {
                    getJSONArray("transferRecords").put(
                        transferJson(1L, 2L, 59_999L),
                    )
                },
                validLegacyRoot().apply {
                    getJSONArray("cashFlowRecords").put(
                        cashFlowJson(
                            accountId = 1L,
                            occurredAt = 60_000L,
                            createdAt = 80_000L,
                            updatedAt = 79_999L,
                        ),
                    )
                },
                validLegacyRoot().apply {
                    getJSONArray("transferRecords").put(
                        transferJson(
                            fromId = 1L,
                            toId = 2L,
                            occurredAt = 60_000L,
                            createdAt = 80_000L,
                            updatedAt = 79_999L,
                        ),
                    )
                },
                validLegacyRoot().apply {
                    getJSONArray("balanceUpdates").put(
                        balanceUpdateJson(
                            accountId = 1L,
                            occurredAt = 60_000L,
                            actualBalance = 10L,
                            systemBalanceBefore = 3L,
                            delta = 6L,
                        ),
                    )
                },
                validLegacyRoot().apply {
                    getJSONArray("balanceUpdates").put(
                        balanceUpdateJson(
                            accountId = 1L,
                            occurredAt = 60_000L,
                            actualBalance = Long.MAX_VALUE,
                            systemBalanceBefore = -1L,
                            delta = Long.MAX_VALUE,
                        ),
                    )
                },
                validLegacyRoot().apply {
                    getJSONArray("balanceUpdates").put(
                        balanceUpdateJson(accountId = 1L, occurredAt = 0L),
                    )
                },
                validLegacyRoot().apply {
                    getJSONArray("adjustments").put(
                        adjustmentJson(accountId = 1L, occurredAt = 60_000L, createdAt = 0L),
                    )
                },
            )

            invalidRoots.forEach { root ->
                file.writeText(root.toString())
                assertIs<LegacyMoneyStoreReadResult.Corrupt>(store.readStrict())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `strict read rejects every record type occurring after its creation`() {
        val directory = createTempDirectory("money-legacy-impossible-time").toFile()
        try {
            val file = File(directory, "money_store.json")
            val store = PersistentMoneyStore(file)
            val invalidRoots = listOf(
                validLegacyRoot().apply {
                    getJSONArray("cashFlowRecords").put(
                        cashFlowJson(
                            accountId = 1L,
                            occurredAt = 80_001L,
                            createdAt = 80_000L,
                            updatedAt = 80_000L,
                        ),
                    )
                },
                validLegacyRoot().apply {
                    getJSONArray("transferRecords").put(
                        transferJson(
                            fromId = 1L,
                            toId = 2L,
                            occurredAt = 80_001L,
                            createdAt = 80_000L,
                            updatedAt = 80_000L,
                        ),
                    )
                },
                validLegacyRoot().apply {
                    getJSONArray("balanceUpdates").put(
                        balanceUpdateJson(
                            accountId = 1L,
                            occurredAt = 80_001L,
                            createdAt = 80_000L,
                        ),
                    )
                },
                validLegacyRoot().apply {
                    getJSONArray("adjustments").put(
                        adjustmentJson(
                            accountId = 1L,
                            occurredAt = 80_001L,
                            createdAt = 80_000L,
                        ),
                    )
                },
            )

            invalidRoots.forEach { root ->
                file.writeText(root.toString())
                assertIs<LegacyMoneyStoreReadResult.Corrupt>(store.readStrict())
            }
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

    private fun validLegacyRoot(accountCreatedAt: Long = 60_001L) = JSONObject(emptyLegacyStoreJson()).apply {
        getJSONArray("accounts")
            .put(activeAccountJson(1L, accountCreatedAt))
            .put(activeAccountJson(2L, accountCreatedAt))
    }

    private fun activeAccountJson(id: Long, createdAt: Long = 1L) = JSONObject()
        .put("id", id)
        .put("name", "账户$id")
        .put("initialBalance", 0L)
        .put("createdAt", createdAt)
        .put("displayOrder", id.toInt())

    private fun archivedAccountJson(id: Long) = activeAccountJson(id)
        .put("isArchived", true)
        .put("archivedAt", 2L)
        .put("lastUsedAt", 3L)
        .put("lastBalanceUpdateAt", 4L)

    private fun cashFlowJson(
        accountId: Long,
        occurredAt: Long,
        direction: String = "inflow",
        amount: Long = 1L,
        createdAt: Long = occurredAt,
        updatedAt: Long = createdAt,
    ) = JSONObject()
        .put("id", 1L)
        .put("accountId", accountId)
        .put("direction", direction)
        .put("amount", amount)
        .put("purpose", "测试")
        .put("occurredAt", occurredAt)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

    private fun transferJson(
        fromId: Long,
        toId: Long,
        occurredAt: Long,
        amount: Long = 1L,
        createdAt: Long = occurredAt,
        updatedAt: Long = createdAt,
    ) = JSONObject()
        .put("id", 1L)
        .put("fromAccountId", fromId)
        .put("toAccountId", toId)
        .put("amount", amount)
        .put("note", "测试")
        .put("occurredAt", occurredAt)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

    private fun balanceUpdateJson(
        accountId: Long,
        occurredAt: Long,
        actualBalance: Long = 1L,
        systemBalanceBefore: Long = 0L,
        delta: Long = 1L,
        createdAt: Long = occurredAt,
    ) = JSONObject()
        .put("id", 1L)
        .put("accountId", accountId)
        .put("actualBalance", actualBalance)
        .put("systemBalanceBeforeUpdate", systemBalanceBefore)
        .put("delta", delta)
        .put("occurredAt", occurredAt)
        .put("createdAt", createdAt)

    private fun adjustmentJson(
        accountId: Long,
        occurredAt: Long,
        delta: Long = 1L,
        createdAt: Long = occurredAt,
    ) = JSONObject()
        .put("id", 1L)
        .put("accountId", accountId)
        .put("delta", delta)
        .put("occurredAt", occurredAt)
        .put("createdAt", createdAt)
}
