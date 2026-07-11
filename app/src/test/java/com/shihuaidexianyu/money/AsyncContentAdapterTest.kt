package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.accounts.AccountsUiState
import com.shihuaidexianyu.money.ui.accounts.toAsyncContent
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.EmptyKind
import com.shihuaidexianyu.money.ui.history.HistoryUiState
import com.shihuaidexianyu.money.ui.history.toAsyncContent
import com.shihuaidexianyu.money.ui.home.HomeUiState
import com.shihuaidexianyu.money.ui.home.toAsyncContent
import com.shihuaidexianyu.money.ui.stats.StatsUiState
import com.shihuaidexianyu.money.ui.stats.toAsyncContent
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Test

class AsyncContentAdapterTest {
    @Test
    fun `initial states are loading rather than empty or zero data`() {
        assertIs<AsyncContent.Loading>(HomeUiState().toAsyncContent())
        assertIs<AsyncContent.Loading>(HistoryUiState().toAsyncContent())
        assertIs<AsyncContent.Loading>(StatsUiState().toAsyncContent())
        assertIs<AsyncContent.Loading>(AccountsUiState().toAsyncContent())
    }

    @Test
    fun `failed states win over stale values`() {
        val content = HomeUiState(
            hasCommittedContent = true,
            accountOptions = listOf(AccountOptionUiModel(id = 1L, name = "现金")),
            errorMessage = "失败",
            retryToken = "home:1",
        ).toAsyncContent()

        assertIs<AsyncContent.Error>(content)
        assertEquals("home:1", content.retryToken)
    }

    @Test
    fun `refreshing is explicit and retains only committed data`() {
        val content = AccountsUiState(
            hasCommittedContent = true,
            isRefreshing = true,
            openAccounts = emptyList(),
        ).toAsyncContent()

        assertIs<AsyncContent.Refreshing<AccountsUiState>>(content)
    }

    @Test
    fun `history distinguishes complete and filtered empty`() {
        val complete = HistoryUiState(hasCommittedContent = true).toAsyncContent()
        val filtered = HistoryUiState(
            hasCommittedContent = true,
            keyword = "午餐",
        ).toAsyncContent()

        assertEquals(EmptyKind.COMPLETELY_EMPTY, (complete as AsyncContent.Empty).kind)
        assertEquals(EmptyKind.FILTERED_EMPTY, (filtered as AsyncContent.Empty).kind)
    }

    @Test
    fun `analysis without accounts is an empty branch not zero data`() {
        val content = StatsUiState(
            hasCommittedContent = true,
            hasSourceAccounts = false,
        ).toAsyncContent()

        assertEquals(EmptyKind.COMPLETELY_EMPTY, (content as AsyncContent.Empty).kind)
    }
}
