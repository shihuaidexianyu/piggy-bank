package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.model.LedgerUndoToken
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals

class LedgerUndoTokenSerializationTest {
    @Test
    fun tokenRoundTripsThroughJson() {
        val token = LedgerUndoToken(
            kind = LedgerRecordKind.BALANCE_UPDATE,
            recordId = 42,
            operationId = "reconcile-42",
            deletedAt = 123_456,
        )

        assertEquals(token, Json.decodeFromString<LedgerUndoToken>(Json.encodeToString(token)))
    }
}
