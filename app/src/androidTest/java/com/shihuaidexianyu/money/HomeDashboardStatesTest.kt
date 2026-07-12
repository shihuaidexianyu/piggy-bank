package com.shihuaidexianyu.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.usecase.calculateMonthlyBudgetStatus
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.home.DueReminderUiModel
import com.shihuaidexianyu.money.ui.home.HomeScreen
import com.shihuaidexianyu.money.ui.home.HomeUiState
import com.shihuaidexianyu.money.ui.home.StaleAccountUiModel
import com.shihuaidexianyu.money.ui.theme.MoneyTheme
import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeDashboardStatesTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingErrorAndNoAccountNeverExposeZeroMetricsAndKeepTheirOwnAction() {
        val state = mutableStateOf(HomeUiState())
        var retries = 0
        var creates = 0
        composeRule.setContent {
            MoneyTheme {
                HomeScreen(
                    state = state.value,
                    onStartUpdateBalance = {},
                    onAllRemindersClick = {},
                    onOpenSettings = {},
                    onCreateAccount = { creates += 1 },
                    onRetry = { retries += 1 },
                )
            }
        }

        composeRule.onNodeWithText("当前净资产").assertDoesNotExist()
        composeRule.runOnIdle {
            state.value = HomeUiState(errorMessageRes = R.string.home_load_failed, retryToken = "1")
        }
        composeRule.onNodeWithText("首页加载失败，请重试").assertIsDisplayed()
        composeRule.onNodeWithText("当前净资产").assertDoesNotExist()
        composeRule.onNodeWithText("重试").performClick()
        composeRule.runOnIdle {
            state.value = HomeUiState(isLoading = false, hasCommittedContent = true)
        }
        composeRule.onNodeWithText("创建第一个账户").assertIsDisplayed()
        composeRule.onNodeWithText("立即创建").performClick()
        composeRule.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, creates)
        }
    }

    @Test
    fun closedOnlyDashboardKeepsNetAssetsAndOffersAccountManagementWithoutOpenMutations() {
        var manageClicks = 0
        composeRule.setContent {
            MoneyTheme {
                HomeScreen(
                    state = HomeUiState(
                        isLoading = false,
                        hasCommittedContent = true,
                        hasAnyAccounts = true,
                        allAccountCount = 1,
                        totalAssets = -5_000L,
                    ),
                    onStartUpdateBalance = {},
                    onAllRemindersClick = {},
                    onOpenSettings = {},
                    onManageAccounts = { manageClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("当前净资产").assertIsDisplayed()
        composeRule.onNodeWithText("创建或重新开启可用账户").assertIsDisplayed()
        composeRule.onNodeWithText("管理账户").performClick()
        composeRule.onNodeWithText("创建第一个账户").assertDoesNotExist()
        composeRule.onNodeWithText("快速记录").assertDoesNotExist()
        composeRule.onNodeWithText("核对账户").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, manageClicks) }
    }

    @Test
    fun successfulDashboardShowsIndependentDueAndStaleEmptyStatesAndBudgetEntry() {
        var reminderClicks = 0
        var budgetClicks = 0
        composeRule.setContent {
            MoneyTheme {
                HomeScreen(
                    state = dataState(),
                    onStartUpdateBalance = {},
                    onAllRemindersClick = { reminderClicks += 1 },
                    onOpenSettings = {},
                    onOpenMonthlyBudgetEditor = { budgetClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("当前净资产").assertIsDisplayed()
        composeRule.onNodeWithText("本月收入").assertIsDisplayed()
        composeRule.onNodeWithText("本月支出").assertIsDisplayed()
        composeRule.onNodeWithText("本月净现金流").assertIsDisplayed()
        composeRule.onNodeWithText("暂无到期提醒").assertIsDisplayed()
        composeRule.onNodeWithText("管理提醒").performClick()
        composeRule.onNodeWithText("暂无待核对账户").assertIsDisplayed()
        composeRule.onNodeWithText("设置月预算").performClick()
        composeRule.onNodeWithText("核对账户").performClick()
        composeRule.onNodeWithText("选择核对余额账户").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, reminderClicks)
            assertEquals(1, budgetClicks)
        }
    }

    @Test
    fun dueAndStaleRowsRemainActionableAndOverBudgetTextIsNotCapped() {
        var reminderClicks = 0
        var reconciledAccount: Long? = null
        composeRule.setContent {
            MoneyTheme {
                HomeScreen(
                    state = dataState().copy(
                        monthlyBudget = calculateMonthlyBudgetStatus(10_000L, 15_001L),
                        dueReminders = listOf(
                            DueReminderUiModel(
                                id = 3L,
                                name = "宽带费",
                                type = ReminderType.MANUAL,
                                amountFormatted = "¥100.00",
                                accountId = 1L,
                                direction = "outflow",
                                amount = 10_000L,
                            ),
                        ),
                        staleAccountCount = 1,
                        staleAccounts = listOf(
                            StaleAccountUiModel(
                                accountId = 1L,
                                name = "现金",
                                colorName = "blue",
                                currentBalance = 12_300L,
                                lastBalanceUpdateAt = null,
                            ),
                        ),
                    ),
                    onStartUpdateBalance = { reconciledAccount = it },
                    onAllRemindersClick = { reminderClicks += 1 },
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("150.01%").assertIsDisplayed()
        composeRule.onNodeWithText("超支 50.01% · ¥50.01").assertIsDisplayed()
        composeRule.onNodeWithText("宽带费").performClick()
        composeRule.onNodeWithText("现金").performClick()
        composeRule.runOnIdle {
            assertEquals(1, reminderClicks)
            assertEquals(1L, reconciledAccount)
        }
    }

    private fun dataState() = HomeUiState(
        isLoading = false,
        hasCommittedContent = true,
        hasAnyAccounts = true,
        allAccountCount = 1,
        totalAssets = 12_300L,
        periodCashInflow = 5_000L,
        periodCashOutflow = 1_200L,
        accountOptions = listOf(AccountOptionUiModel(id = 1L, name = "现金", balance = 12_300L)),
    )
}
