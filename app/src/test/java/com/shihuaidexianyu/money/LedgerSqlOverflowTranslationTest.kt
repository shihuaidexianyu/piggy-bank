package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.translateLedgerSqlOverflow
import com.shihuaidexianyu.money.domain.model.LedgerOverflowException
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LedgerSqlOverflowTranslationTest {
    @Test
    fun `sqlite integer overflow is translated through shared domain failure`() = runBlocking {
        assertFailsWith<LedgerOverflowException> {
            translateLedgerSqlOverflow<Long> {
                throw FakeSQLiteException("integer overflow")
            }
        }
        Unit
    }

    private class FakeSQLiteException(message: String) : RuntimeException(message)
}
