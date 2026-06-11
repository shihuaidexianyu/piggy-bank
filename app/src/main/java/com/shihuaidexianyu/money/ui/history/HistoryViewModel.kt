package com.shihuaidexianyu.money.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModel
import com.shihuaidexianyu.money.util.AmountInputParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

enum class HistoryRecordKind {
    CASH_FLOW,
    TRANSFER,
    BALANCE_UPDATE,
    BALANCE_ADJUSTMENT,
}

enum class AmountDirectionFilter(val value: String, val displayName: String) {
    ALL("all", "全部"),
    INCREASE("increase", "金额增加"),
    DECREASE("decrease", "金额减少"),
    ;

    companion object {
        fun fromValue(value: String?): AmountDirectionFilter =
            entries.firstOrNull { it.value == value } ?: ALL
    }
}

data class HistoryRecordUiModel(
    val id: String,
    val recordId: Long,
    val kind: HistoryRecordKind,
    val title: String,
    val subtitle: String,
    val amount: Long,
    val occurredAt: Long,
    val accountIds: Set<Long>,
    val keywordSource: String,
)

data class HistoryUiState(
    val settings: AppSettings = AppSettings(),
    val accountOptions: List<AccountOptionUiModel> = emptyList(),
    val keyword: String = "",
    val excludeKeyword: String = "",
    val selectedAccountId: Long? = null,
    val dateStartAt: Long? = null,
    val dateEndAt: Long? = null,
    val minAmountText: String = "",
    val maxAmountText: String = "",
    val amountDirectionFilter: AmountDirectionFilter = AmountDirectionFilter.ALL,
    val records: List<HistoryRecordUiModel> = emptyList(),
)

internal data class HistoryFilterState(
    val keyword: String = "",
    val excludeKeyword: String = "",
    val selectedAccountId: Long? = null,
    val dateStartAt: Long? = null,
    val dateEndAt: Long? = null,
    val minAmountText: String = "",
    val maxAmountText: String = "",
    val amountDirectionFilter: AmountDirectionFilter = AmountDirectionFilter.ALL,
)

private data class HistoryBaseData(
    val settings: AppSettings,
    val accountOptions: List<AccountOptionUiModel>,
    val allRecords: List<HistoryRecordUiModel>,
)

@OptIn(FlowPreview::class)
class HistoryViewModel(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    private val filterState = MutableStateFlow(HistoryFilterState())
    private var allRecords: List<HistoryRecordUiModel> = emptyList()
    private var saveFiltersJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val initialSettings = settingsRepository.observeSettings().first()
                filterState.value = HistoryFilterState(
                    keyword = initialSettings.lastHistoryKeyword,
                    excludeKeyword = initialSettings.lastHistoryExcludeKeyword,
                    selectedAccountId = initialSettings.lastHistoryAccountId.takeIf { it >= 0 },
                    dateStartAt = initialSettings.lastHistoryDateStartAt.takeIf { it >= 0 },
                    dateEndAt = initialSettings.lastHistoryDateEndAt.takeIf { it >= 0 },
                    minAmountText = initialSettings.lastHistoryMinAmountText,
                    maxAmountText = initialSettings.lastHistoryMaxAmountText,
                    amountDirectionFilter = AmountDirectionFilter.fromValue(
                        initialSettings.lastHistoryAmountDirection.takeIf { it.isNotBlank() }
                    ),
                )
                val baseDataFlow = combine(
                    accountRepository.observeActiveAccounts(),
                    accountRepository.observeArchivedAccounts(),
                    settingsRepository.observeSettings(),
                    transactionRepository.observeChangeVersion().debounce(300),
                ) { activeAccounts, archivedAccounts, settings, _ ->
                    Triple(
                        (activeAccounts + archivedAccounts).distinctBy(Account::id),
                        settings,
                        Unit,
                    )
                }.mapLatest { (accounts, settings) ->
                    buildBaseData(accounts, settings)
                }

                combine(baseDataFlow, filterState) { baseData, filters ->
                    allRecords = baseData.allRecords
                    HistoryUiState(
                        settings = baseData.settings,
                        accountOptions = baseData.accountOptions,
                        keyword = filters.keyword,
                        excludeKeyword = filters.excludeKeyword,
                        selectedAccountId = filters.selectedAccountId,
                        dateStartAt = filters.dateStartAt,
                        dateEndAt = filters.dateEndAt,
                        minAmountText = filters.minAmountText,
                        maxAmountText = filters.maxAmountText,
                        amountDirectionFilter = filters.amountDirectionFilter,
                        records = applyFilters(baseData.allRecords, filters),
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                android.util.Log.e("HistoryViewModel", "Failed to observe history", e)
            }
        }
    }

    fun updateKeyword(value: String) = applyLocalFilter { copy(keyword = value) }
    fun updateExcludeKeyword(value: String) = applyLocalFilter { copy(excludeKeyword = value) }
    fun updateAccount(accountId: Long?) = applyLocalFilter { copy(selectedAccountId = accountId) }
    fun updateDateRange(startAt: Long?, endAt: Long?) {
        val normalizedStart = startAt
        val normalizedEnd = endAt
        if (normalizedStart != null && normalizedEnd != null && normalizedStart > normalizedEnd) {
            applyLocalFilter { copy(dateStartAt = normalizedEnd, dateEndAt = normalizedStart) }
        } else {
            applyLocalFilter { copy(dateStartAt = normalizedStart, dateEndAt = normalizedEnd) }
        }
    }

    fun updateMinAmount(value: String) = applyLocalFilter { copy(minAmountText = value) }
    fun updateMaxAmount(value: String) = applyLocalFilter { copy(maxAmountText = value) }
    fun updateAmountDirectionFilter(filter: AmountDirectionFilter) =
        applyLocalFilter { copy(amountDirectionFilter = filter) }

    private fun applyLocalFilter(transform: HistoryFilterState.() -> HistoryFilterState) {
        val updatedFilters = filterState.value.transform()
        filterState.value = updatedFilters

        saveFiltersJob?.cancel()
        saveFiltersJob = viewModelScope.launch {
            delay(500)
            settingsRepository.updateLastHistoryFilters(
                keyword = updatedFilters.keyword,
                excludeKeyword = updatedFilters.excludeKeyword,
                accountId = updatedFilters.selectedAccountId ?: -1L,
                dateStartAt = updatedFilters.dateStartAt ?: -1L,
                dateEndAt = updatedFilters.dateEndAt ?: -1L,
                minAmountText = updatedFilters.minAmountText,
                maxAmountText = updatedFilters.maxAmountText,
                amountDirection = updatedFilters.amountDirectionFilter.value,
            )
        }

        _uiState.update { current ->
            current.copy(
                keyword = updatedFilters.keyword,
                excludeKeyword = updatedFilters.excludeKeyword,
                selectedAccountId = updatedFilters.selectedAccountId,
                dateStartAt = updatedFilters.dateStartAt,
                dateEndAt = updatedFilters.dateEndAt,
                minAmountText = updatedFilters.minAmountText,
                maxAmountText = updatedFilters.maxAmountText,
                amountDirectionFilter = updatedFilters.amountDirectionFilter,
                records = applyFilters(allRecords, updatedFilters),
            )
        }
    }

    private suspend fun buildBaseData(
        accounts: List<Account>,
        settings: AppSettings,
    ): HistoryBaseData = withContext(Dispatchers.Default) {
        val accountMap = accounts.associateBy(Account::id)
        HistoryBaseData(
            settings = settings,
            accountOptions = accountMap.values
                .sortedBy(Account::name)
                .map(Account::toAccountOptionUiModel),
            allRecords = buildAllRecords(accountMap),
        )
    }

    private suspend fun buildAllRecords(
        accounts: Map<Long, Account>,
    ): List<HistoryRecordUiModel> {
        val cashFlowRecords = transactionRepository.queryAllActiveCashFlowRecords().map { record ->
            HistoryRecordUiModel(
                id = "cash_${record.id}",
                recordId = record.id,
                kind = HistoryRecordKind.CASH_FLOW,
                title = if (record.purpose.isBlank()) {
                    "未填写用途"
                } else {
                    record.purpose
                },
                subtitle = accounts[record.accountId]?.name ?: "未知账户",
                amount = if (record.direction == CashFlowDirection.INFLOW.value) record.amount else -record.amount,
                occurredAt = record.occurredAt,
                accountIds = setOf(record.accountId),
                keywordSource = record.purpose,
            )
        }
        val transferRecords = transactionRepository.queryAllActiveTransferRecords().map { record ->
            HistoryRecordUiModel(
                id = "transfer_${record.id}",
                recordId = record.id,
                kind = HistoryRecordKind.TRANSFER,
                title = record.note.ifBlank { "账户间转移" },
                subtitle = "${(accounts[record.fromAccountId]?.name ?: "未知")} → ${(accounts[record.toAccountId]?.name ?: "未知")}",
                amount = record.amount,
                occurredAt = record.occurredAt,
                accountIds = setOf(record.fromAccountId, record.toAccountId),
                keywordSource = record.note,
            )
        }
        val updateRecords = transactionRepository.queryAllBalanceUpdateRecords()
            .map { record ->
                HistoryRecordUiModel(
                    id = "balance_update_${record.id}",
                    recordId = record.id,
                    kind = HistoryRecordKind.BALANCE_UPDATE,
                    title = if (record.delta == 0L) "余额核对" else "对账调整",
                    subtitle = accounts[record.accountId]?.name ?: "未知账户",
                    amount = record.delta,
                    occurredAt = record.occurredAt,
                    accountIds = setOf(record.accountId),
                    keywordSource = "",
                )
            }
        val adjustmentRecords = transactionRepository.queryAllBalanceAdjustmentRecords()
            .map { record ->
                HistoryRecordUiModel(
                    id = "balance_adjustment_${record.id}",
                    recordId = record.id,
                    kind = HistoryRecordKind.BALANCE_ADJUSTMENT,
                    title = "余额矫正",
                    subtitle = accounts[record.accountId]?.name ?: "未知账户",
                    amount = record.delta,
                    occurredAt = record.occurredAt,
                    accountIds = setOf(record.accountId),
                    keywordSource = "",
                )
            }
        return (cashFlowRecords + transferRecords + updateRecords + adjustmentRecords)
            .sortedByDescending(HistoryRecordUiModel::occurredAt)
    }

    private fun applyFilters(
        source: List<HistoryRecordUiModel>,
        filters: HistoryFilterState,
    ): List<HistoryRecordUiModel> = filterHistoryRecords(source, filters)
}

internal fun filterHistoryRecords(
    source: List<HistoryRecordUiModel>,
    filters: HistoryFilterState,
): List<HistoryRecordUiModel> {
    val keyword = filters.keyword.trim().lowercase()
    val excludeKeyword = filters.excludeKeyword.trim().lowercase()
    val startAt = filters.dateStartAt
    val endAt = filters.dateEndAt
    val minAmount = AmountInputParser.parseToMinor(filters.minAmountText)
    val maxAmount = AmountInputParser.parseToMinor(filters.maxAmountText)

    return source.filter { record ->
        val keywordOk = keyword.isBlank() || record.keywordSource.lowercase().contains(keyword)
        val excludeOk = excludeKeyword.isBlank() || !record.keywordSource.lowercase().contains(excludeKeyword)
        val accountOk = filters.selectedAccountId == null || filters.selectedAccountId in record.accountIds
        val startOk = startAt == null || record.occurredAt >= startAt
        val endOk = endAt == null || record.occurredAt <= endAt
        val amountAbs = abs(record.amount)
        val minOk = minAmount == null || amountAbs >= minAmount
        val maxOk = maxAmount == null || amountAbs <= maxAmount
        val directionOk = when (filters.amountDirectionFilter) {
            AmountDirectionFilter.ALL -> true
            AmountDirectionFilter.INCREASE -> record.amount > 0 && record.kind != HistoryRecordKind.TRANSFER
            AmountDirectionFilter.DECREASE -> record.amount < 0 && record.kind != HistoryRecordKind.TRANSFER
        }
        keywordOk && excludeOk && accountOk && startOk && endOk && minOk && maxOk && directionOk
    }
}
