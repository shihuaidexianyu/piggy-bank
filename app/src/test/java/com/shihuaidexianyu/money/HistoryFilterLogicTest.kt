package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.history.HistoryFilterState
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.history.HistoryRecordUiModel
import com.shihuaidexianyu.money.ui.history.filterHistoryRecords
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import kotlin.test.assertEquals
import org.junit.Test

class HistoryFilterLogicTest {
    @Test
    fun `exclude keyword works without include keyword`() {
        val records = listOf(
            record(id = "cash_1", source = "早餐 咖啡"),
            record(id = "cash_2", source = "早餐 包子"),
            record(id = "balance_1", kind = HistoryRecordKind.BALANCE_UPDATE, source = ""),
        )

        val filtered = filterHistoryRecords(
            source = records,
            filters = HistoryFilterState(excludeKeyword = "咖啡"),
        )

        assertEquals(listOf("cash_2", "balance_1"), filtered.map { it.id })
    }

    @Test
    fun `include and exclude keywords work together`() {
        val records = listOf(
            record(id = "cash_1", source = "午餐 米饭"),
            record(id = "cash_2", source = "午餐 咖啡"),
            record(id = "transfer_1", kind = HistoryRecordKind.TRANSFER, source = "午餐垫付"),
        )

        val filtered = filterHistoryRecords(
            source = records,
            filters = HistoryFilterState(
                keyword = "午餐",
                excludeKeyword = "咖啡",
            ),
        )

        assertEquals(listOf("cash_1", "transfer_1"), filtered.map { it.id })
    }

    @Test
    fun `primary keyword searches localized title and account subtitle while type remains advanced`() {
        val records = listOf(
            record(id = "cash_1", title = "余额核对", subtitle = "工资卡", source = ""),
            record(id = "transfer_1", kind = HistoryRecordKind.TRANSFER, title = "转账", subtitle = "现金 → 储蓄", source = ""),
        )

        assertEquals(
            listOf("cash_1"),
            filterHistoryRecords(records, HistoryFilterState(keyword = "工资卡")).map { it.id },
        )
        assertEquals(
            listOf("transfer_1"),
            filterHistoryRecords(
                records,
                HistoryFilterState(selectedRecordTypes = setOf(HistoryRecordType.TRANSFER)),
            ).map { it.id },
        )
    }

    private fun record(
        id: String,
        kind: HistoryRecordKind = HistoryRecordKind.CASH_FLOW,
        title: String = id,
        subtitle: String = "",
        source: String,
    ): HistoryRecordUiModel {
        return HistoryRecordUiModel(
            id = id,
            recordId = 1L,
            kind = kind,
            title = title,
            subtitle = subtitle,
            amount = 100L,
            occurredAt = 1_000L,
            accountIds = setOf(1L),
            keywordSource = source,
        )
    }
}
