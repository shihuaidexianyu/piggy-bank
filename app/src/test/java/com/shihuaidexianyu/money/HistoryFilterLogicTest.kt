package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.history.HistoryFilterState
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.history.HistoryRecordUiModel
import com.shihuaidexianyu.money.ui.history.filterHistoryRecords
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.HistoryBusinessSemantic
import kotlin.test.assertEquals
import org.junit.Test

class HistoryFilterLogicTest {
    @Test
    fun `business semantic filters use record meaning and account kind together`() {
        val records = listOf(
            record(id = "daily", amount = -100L, isFunding = true),
            record(id = "investment_cash", amount = -200L, isInvestment = true),
            record(
                id = "gain",
                kind = HistoryRecordKind.BALANCE_UPDATE,
                amount = 300L,
                isInvestment = true,
            ),
            record(
                id = "loss",
                kind = HistoryRecordKind.BALANCE_UPDATE,
                amount = -150L,
                isInvestment = true,
            ),
            record(id = "funding_reconcile", kind = HistoryRecordKind.BALANCE_UPDATE, amount = 20L, isFunding = true),
        )

        assertEquals(
            listOf("daily"),
            filterHistoryRecords(
                records,
                HistoryFilterState(businessSemantic = HistoryBusinessSemantic.DAILY_EXPENSE),
            ).map { it.id },
        )
        assertEquals(
            listOf("gain", "loss"),
            filterHistoryRecords(
                records,
                HistoryFilterState(businessSemantic = HistoryBusinessSemantic.INVESTMENT_PNL),
            ).map { it.id },
        )
        assertEquals(
            listOf("gain"),
            filterHistoryRecords(
                records,
                HistoryFilterState(businessSemantic = HistoryBusinessSemantic.INVESTMENT_GAIN),
            ).map { it.id },
        )
        assertEquals(
            listOf("loss"),
            filterHistoryRecords(
                records,
                HistoryFilterState(businessSemantic = HistoryBusinessSemantic.INVESTMENT_LOSS),
            ).map { it.id },
        )
    }

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
        source: String = "",
        amount: Long = 100L,
        isInvestment: Boolean = false,
        isFunding: Boolean = false,
    ): HistoryRecordUiModel {
        return HistoryRecordUiModel(
            id = id,
            recordId = 1L,
            kind = kind,
            title = title,
            subtitle = subtitle,
            amount = amount,
            occurredAt = 1_000L,
            accountIds = setOf(1L),
            keywordSource = source,
            isInvestmentAccount = isInvestment,
            isFundingAccount = isFunding,
        )
    }
}
