package com.shihuaidexianyu.money.navigation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.shihuaidexianyu.money.domain.model.CashFlowDirection

sealed class MoneyDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Home : MoneyDestination("home", "首页", Icons.Outlined.Home)
    data object History : MoneyDestination("history", "历史", Icons.Outlined.History)
    data object Accounts : MoneyDestination("accounts", "账户", Icons.Outlined.AccountBalanceWallet)
    data object Settings : MoneyDestination("settings", "设置", Icons.Outlined.Settings)

    companion object {
        val topLevel = listOf(Home, History, Accounts, Settings)
        const val CreateAccountRoute = "accounts/create"
        const val ReorderAccountsRoute = "accounts/reorder"
        const val EditAccountRoute = "accounts/{accountId}/edit"
        const val AccountDetailRoute = "accounts/{accountId}"
        const val RecordCashFlowRoute = "records/cashflow/{direction}/{accountId}"
        const val RecordTransferRoute = "records/transfer/{fromAccountId}"
        const val EditCashFlowRoute = "history/cashflow/{recordId}"
        const val EditTransferRoute = "history/transfer/{recordId}"
        const val BalanceUpdateDetailRoute = "history/balance-update/{recordId}"
        const val EditBalanceUpdateRoute = "history/balance-update/{recordId}/edit"
        const val BalanceAdjustmentDetailRoute = "history/balance-adjustment/{recordId}"
        const val UpdateBalanceRoute = "balance/update/{accountId}"
        const val BalanceUpdateResultRoute = "balance/update/{accountId}/result"
        const val ReminderListRoute = "reminders"
        const val CreateReminderRoute = "reminders/create"
        const val EditReminderRoute = "reminders/{reminderId}/edit"

        fun accountDetailRoute(accountId: Long): String = "accounts/$accountId"
        fun editAccountRoute(accountId: Long): String = "accounts/$accountId/edit"
        fun recordCashFlowRoute(direction: CashFlowDirection, accountId: Long): String {
            return "records/cashflow/${direction.value}/$accountId"
        }

        fun recordCashFlowRoute(
            direction: CashFlowDirection,
            accountId: Long,
            amount: Long?,
            purpose: String?,
            reminderId: Long?,
        ): String {
            val baseRoute = recordCashFlowRoute(direction, accountId)
            val query = buildList {
                amount?.takeIf { it > 0 }?.let { add("amount=$it") }
                purpose?.takeIf { it.isNotBlank() }?.let { add("purpose=${NavigationQueryCodec.encode(it)}") }
                reminderId?.takeIf { it > 0 }?.let { add("reminderId=$it") }
            }
            return if (query.isEmpty()) baseRoute else "$baseRoute?${query.joinToString("&")}"
        }

        fun recordTransferRoute(fromAccountId: Long = 0L): String = "records/transfer/$fromAccountId"
        fun editCashFlowRoute(recordId: Long): String = "history/cashflow/$recordId"
        fun editTransferRoute(recordId: Long): String = "history/transfer/$recordId"
        fun balanceUpdateDetailRoute(recordId: Long): String = "history/balance-update/$recordId"
        fun editBalanceUpdateRoute(recordId: Long): String = "history/balance-update/$recordId/edit"
        fun balanceAdjustmentDetailRoute(recordId: Long): String = "history/balance-adjustment/$recordId"

        fun updateBalanceRoute(accountId: Long = 0L): String = "balance/update/$accountId"

        fun balanceUpdateResultRoute(accountId: Long): String = "balance/update/$accountId/result"

        fun editReminderRoute(reminderId: Long): String = "reminders/$reminderId/edit"
    }
}
