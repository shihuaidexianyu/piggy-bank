package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.accounts.AccountsUiState
import com.shihuaidexianyu.money.ui.accounts.toAsyncContent
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.EmptyKind
import com.shihuaidexianyu.money.ui.history.HistoryUiState
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.ui.history.toAsyncContent
import com.shihuaidexianyu.money.ui.home.HomeUiState
import com.shihuaidexianyu.money.ui.home.toAsyncContent
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Test

class AsyncContentAdapterTest {
    @Test
    fun `initial states are loading rather than empty or zero data`() {
        assertIs<AsyncContent.Loading>(HomeUiState().toAsyncContent())
        assertIs<AsyncContent.Loading>(HistoryUiState().toAsyncContent())
        assertIs<AsyncContent.Loading>(AccountsUiState().toAsyncContent())
    }

    @Test
    fun `failed states win over stale values`() {
        val content = HomeUiState(
            hasCommittedContent = true,
            accountOptions = listOf(AccountOptionUiModel(id = 1L, name = "现金")),
            errorMessageRes = R.string.home_load_failed,
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
            selectedRecordTypes = setOf(HistoryRecordType.TRANSFER),
        ).toAsyncContent()

        assertEquals(EmptyKind.COMPLETELY_EMPTY, (complete as AsyncContent.Empty).kind)
        assertEquals(EmptyKind.FILTERED_EMPTY, (filtered as AsyncContent.Empty).kind)
    }

    @Test
    fun `history load failure is error rather than either empty state`() {
        val content = HistoryUiState(
            hasCommittedContent = true,
            errorMessageRes = R.string.history_load_failed,
            retryToken = "history:failed",
        ).toAsyncContent()

        assertIs<AsyncContent.Error>(content)
        assertEquals("history:failed", content.retryToken)
    }

}
