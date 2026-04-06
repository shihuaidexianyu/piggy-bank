package com.shihuaidexianyu.money

import android.content.Context
import com.shihuaidexianyu.money.data.db.LegacyMoneyStoreImporter
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.repository.AccountReminderSettingsRepositoryImpl
import com.shihuaidexianyu.money.data.repository.AccountRepositoryImpl
import com.shihuaidexianyu.money.data.repository.RecurringReminderRepositoryImpl
import com.shihuaidexianyu.money.data.repository.SettingsRepositoryImpl
import com.shihuaidexianyu.money.data.repository.TransactionRepositoryImpl
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ConfirmReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.ExportJsonUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveAccountDetailUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveStatsUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveDueRemindersUseCase
import com.shihuaidexianyu.money.domain.usecase.RecalculateInvestmentSettlementsUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountDisplayOrderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountOrderingUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import kotlinx.coroutines.runBlocking

class MoneyAppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val moneyDatabase = MoneyDatabase.getInstance(appContext)

    init {
        runBlocking {
            LegacyMoneyStoreImporter.importIfNeeded(
                context = appContext,
                database = moneyDatabase,
            )
        }
    }

    val accountRepository: AccountRepository =
        AccountRepositoryImpl(moneyDatabase.accountDao())

    val transactionRepository: TransactionRepository =
        TransactionRepositoryImpl(
            database = moneyDatabase,
            cashFlowRecordDao = moneyDatabase.cashFlowRecordDao(),
            transferRecordDao = moneyDatabase.transferRecordDao(),
            balanceUpdateRecordDao = moneyDatabase.balanceUpdateRecordDao(),
            balanceAdjustmentRecordDao = moneyDatabase.balanceAdjustmentRecordDao(),
            investmentSettlementDao = moneyDatabase.investmentSettlementDao(),
        )

    val settingsRepository: SettingsRepository =
        SettingsRepositoryImpl(appContext)

    val accountReminderSettingsRepository: AccountReminderSettingsRepository =
        AccountReminderSettingsRepositoryImpl(appContext)

    val recurringReminderRepository: RecurringReminderRepository =
        RecurringReminderRepositoryImpl(moneyDatabase.recurringReminderDao())

    val calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
    )

    val resolveBalanceUpdateContextUseCase = ResolveBalanceUpdateContextUseCase(
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
    )

    val refreshAccountActivityStateUseCase = RefreshAccountActivityStateUseCase(
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
    )

    val observeHomeDashboardUseCase = ObserveHomeDashboardUseCase(
        accountReminderSettingsRepository = accountReminderSettingsRepository,
        accountRepository = accountRepository,
        recurringReminderRepository = recurringReminderRepository,
        settingsRepository = settingsRepository,
        transactionRepository = transactionRepository,
        calculateCurrentBalanceUseCase = calculateCurrentBalanceUseCase,
    )

    fun observeAccountDetailUseCase(accountId: Long): ObserveAccountDetailUseCase {
        return ObserveAccountDetailUseCase(
            accountId = accountId,
            accountReminderSettingsRepository = accountReminderSettingsRepository,
            accountRepository = accountRepository,
            settingsRepository = settingsRepository,
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = calculateCurrentBalanceUseCase,
        )
    }

    val createAccountUseCase = CreateAccountUseCase(
        accountRepository = accountRepository,
        accountReminderSettingsRepository = accountReminderSettingsRepository,
    )

    val recalculateInvestmentSettlementsUseCase = RecalculateInvestmentSettlementsUseCase(
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
    )

    val createCashFlowRecordUseCase = CreateCashFlowRecordUseCase(
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
        recalculateInvestmentSettlementsUseCase = recalculateInvestmentSettlementsUseCase,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val createTransferRecordUseCase = CreateTransferRecordUseCase(
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
        recalculateInvestmentSettlementsUseCase = recalculateInvestmentSettlementsUseCase,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val updateCashFlowRecordUseCase = UpdateCashFlowRecordUseCase(
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
        recalculateInvestmentSettlementsUseCase = recalculateInvestmentSettlementsUseCase,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val deleteCashFlowRecordUseCase = DeleteCashFlowRecordUseCase(
        transactionRepository = transactionRepository,
        recalculateInvestmentSettlementsUseCase = recalculateInvestmentSettlementsUseCase,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val updateTransferRecordUseCase = UpdateTransferRecordUseCase(
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
        recalculateInvestmentSettlementsUseCase = recalculateInvestmentSettlementsUseCase,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val deleteTransferRecordUseCase = DeleteTransferRecordUseCase(
        transactionRepository = transactionRepository,
        recalculateInvestmentSettlementsUseCase = recalculateInvestmentSettlementsUseCase,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val updateBalanceUseCase = UpdateBalanceUseCase(
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
        resolveBalanceUpdateContextUseCase = resolveBalanceUpdateContextUseCase,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val updateBalanceUpdateRecordUseCase = UpdateBalanceUpdateRecordUseCase(
        transactionRepository = transactionRepository,
        resolveBalanceUpdateContextUseCase = resolveBalanceUpdateContextUseCase,
        recalculateInvestmentSettlementsUseCase = recalculateInvestmentSettlementsUseCase,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val deleteBalanceUpdateRecordUseCase = DeleteBalanceUpdateRecordUseCase(
        transactionRepository = transactionRepository,
        recalculateInvestmentSettlementsUseCase = recalculateInvestmentSettlementsUseCase,
        refreshAccountActivityStateUseCase = refreshAccountActivityStateUseCase,
    )

    val updateAccountUseCase = UpdateAccountUseCase(
        accountRepository = accountRepository,
        accountReminderSettingsRepository = accountReminderSettingsRepository,
        transactionRepository = transactionRepository,
        recalculateInvestmentSettlementsUseCase = recalculateInvestmentSettlementsUseCase,
    )

    val updateAccountDisplayOrderUseCase = UpdateAccountDisplayOrderUseCase(
        accountRepository = accountRepository,
    )

    val updateAccountOrderingUseCase = UpdateAccountOrderingUseCase(
        accountRepository = accountRepository,
        settingsRepository = settingsRepository,
        updateAccountDisplayOrderUseCase = updateAccountDisplayOrderUseCase,
    )

    val exportJsonUseCase = ExportJsonUseCase(
        context = appContext,
        accountRepository = accountRepository,
        accountReminderSettingsRepository = accountReminderSettingsRepository,
        transactionRepository = transactionRepository,
        settingsRepository = settingsRepository,
        recurringReminderRepository = recurringReminderRepository,
    )

    val createReminderUseCase = CreateReminderUseCase(
        accountRepository = accountRepository,
        reminderRepository = recurringReminderRepository,
    )

    val updateReminderUseCase = UpdateReminderUseCase(
        accountRepository = accountRepository,
        reminderRepository = recurringReminderRepository,
    )

    val deleteReminderUseCase = DeleteReminderUseCase(
        reminderRepository = recurringReminderRepository,
    )

    val confirmReminderUseCase = ConfirmReminderUseCase(
        reminderRepository = recurringReminderRepository,
    )

    val observeDueRemindersUseCase = ObserveDueRemindersUseCase(
        reminderRepository = recurringReminderRepository,
    )

    val observeStatsUseCase = ObserveStatsUseCase(
        accountRepository = accountRepository,
        settingsRepository = settingsRepository,
        transactionRepository = transactionRepository,
        calculateCurrentBalanceUseCase = calculateCurrentBalanceUseCase,
    )
}

