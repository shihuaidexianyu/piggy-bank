package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.EmptyKind
import com.shihuaidexianyu.money.ui.common.formAsyncContent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AsyncContentContractTest {
    @Test
    fun `all asynchronous branches are distinct and carry only their branch payload`() {
        val branches: List<AsyncContent<Int>> = listOf(
            AsyncContent.Loading,
            AsyncContent.Data(7),
            AsyncContent.Refreshing(8),
            AsyncContent.Empty(EmptyKind.COMPLETELY_EMPTY),
            AsyncContent.Empty(EmptyKind.FILTERED_EMPTY),
            AsyncContent.Error("加载失败", "retry-1"),
        )

        assertEquals(6, branches.distinct().size)
        assertIs<AsyncContent.Loading>(branches[0])
        assertEquals(7, (branches[1] as AsyncContent.Data).value)
        assertEquals(8, (branches[2] as AsyncContent.Refreshing).value)
        assertEquals(EmptyKind.COMPLETELY_EMPTY, (branches[3] as AsyncContent.Empty).kind)
        assertEquals(EmptyKind.FILTERED_EMPTY, (branches[4] as AsyncContent.Empty).kind)
        assertEquals("retry-1", (branches[5] as AsyncContent.Error).retryToken)
    }

    @Test
    fun `form failure cannot be projected as loaded draft`() {
        val draft = "用户未提交的草稿"

        val content = formAsyncContent(
            value = draft,
            isLoading = false,
            errorMessage = "加载失败",
            retryToken = "form:1",
        )

        assertIs<AsyncContent.Error>(content)
        assertEquals("form:1", content.retryToken)
    }
}
