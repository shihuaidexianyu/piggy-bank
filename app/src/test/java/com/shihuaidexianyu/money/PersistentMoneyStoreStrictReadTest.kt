package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.LegacyMoneyStoreReadResult
import com.shihuaidexianyu.money.data.repository.PersistentMoneyStore
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Test
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
            assertEquals(LegacyMoneyStoreReadResult.Empty, store.readStrict())

            file.writeText(
                """
                {"accounts":[{"id":1,"name":"现金","initialBalance":0,"createdAt":1,"displayOrder":0}]}
                """.trimIndent(),
            )
            val data = assertIs<LegacyMoneyStoreReadResult.Data>(store.readStrict())
            assertEquals(listOf("现金"), data.snapshot.accounts.map { it.name })
        } finally {
            directory.deleteRecursively()
        }
    }
}
