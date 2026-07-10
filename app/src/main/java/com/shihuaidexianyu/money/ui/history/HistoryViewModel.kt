package com.shihuaidexianyu.money.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.HistoryFilters
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.HistoryAmountDirection
import com.shihuaidexianyu.money.domain.model.HistoryPageCursor
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModel
import com.shihuaidexianyu.money.util.AmountInputParser
import kotlin.math.abs
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.shihuaidexianyu.money.domain.model.HistoryRecord as DomainHistoryRecord

private const val HISTORY_PAGE_SIZE = 100
private const val HISTORY_FILTER_DEBOUNCE_MILLIS = 250L

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
    val settings: PortableSettings = PortableSettings(),
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
    val totalRecordCount: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreRecords: Boolean = false,
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

@OptIn(FlowPreview::class)
class HistoryViewModel(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val portableSettingsRepository: PortableSettingsRepository,
    private val devicePreferencesRepository: DevicePreferencesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    private val filterState = MutableStateFlow(HistoryFilterState())
    private var accountMap: Map<Long, Account> = emptyMap()
    private var loadedRecords: List<DomainHistoryRecord> = emptyList()
    private var totalRecordCount: Int = 0
    private var nextCursor: HistoryPageCursor? = null
    private var saveFiltersJob: Job? = null
    private var reloadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var loadGeneration = 0

    init {
        viewModelScope.launch {
            try {
                initialize()
            } catch (e: Exception) {
                android.util.Log.e("HistoryViewModel", "Failed to observe history", e)
                _uiState.update { it.copy(isLoading = false, isLoadingMore = false) }
            }
        }
    }

    fun updateKeyword(value: String) = applyLocalFilter(debounceReload = true) { copy(keyword = value) }
    fun updateExcludeKeyword(value: String) = applyLocalFilter(debounceReload = true) { copy(excludeKeyword = value) }
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

    fun updateMinAmount(value: String) = applyLocalFilter(debounceReload = true) { copy(minAmountText = value) }
    fun updateMaxAmount(value: String) = applyLocalFilter(debounceReload = true) { copy(maxAmountText = value) }
    fun updateAmountDirectionFilter(filter: AmountDirectionFilter) =
        applyLocalFilter { copy(amountDirectionFilter = filter) }

    fun loadMore() {
        if (_uiState.value.isLoading || _uiState.value.isLoadingMore || !_uiState.value.hasMoreRecords) return
        val cursor = nextCursor ?: return
        val generation = loadGeneration
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            runCatching {
                transactionRepository.queryHistoryRecords(
                    filters = filterState.value.toHistoryRecordFilters(),
                    cursor = cursor,
                    limit = HISTORY_PAGE_SIZE,
                )
            }.onSuccess { nextRecords ->
                if (generation != loadGeneration) return@onSuccess
                loadedRecords = loadedRecords + nextRecords
                nextCursor = nextRecords.lastOrNull()?.cursor ?: nextCursor
                val hasMore = loadedRecords.size < totalRecordCount && nextRecords.isNotEmpty()
                _uiState.update {
                    it.copy(
                        records = loadedRecords.toUiModels(),
                        totalRecordCount = totalRecordCount,
                        isLoadingMore = false,
                        hasMoreRecords = hasMore,
                    )
                }
            }.onFailure { error ->
                android.util.Log.e("HistoryViewModel", "Failed to load more history", error)
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private suspend fun initialize() {
        val initialSettings = portableSettingsRepository.query()
        val initialFilters = devicePreferencesRepository.query().historyFilters.toHistoryFilterState()
        filterState.value = initialFilters
        applySettings(initialSettings)
        applyFiltersToState(initialFilters)
        applyAccounts(observeAccounts().first())

        viewModelScope.launch {
            portableSettingsRepository.observe()
                .drop(1)
                .collect(::applySettings)
        }
        viewModelScope.launch {
            observeAccounts()
                .drop(1)
                .collect(::applyAccounts)
        }
        viewModelScope.launch {
            transactionRepository.observeChangeVersion()
                .drop(1)
                .debounce(300)
                .collect { reloadFirstPage() }
        }

        reloadFirstPage()
    }

    private fun observeAccounts() = accountRepository.observeAllAccounts()

    private fun applySettings(settings: PortableSettings) {
        _uiState.update { it.copy(settings = settings) }
    }

    private fun applyAccounts(accounts: List<Account>) {
        accountMap = accounts.associateBy(Account::id)
        _uiState.update {
            it.copy(
                accountOptions = accountMap.values
                    .sortedBy(Account::name)
                    .map(Account::toAccountOptionUiModel),
                records = loadedRecords.toUiModels(),
            )
        }
    }

    private fun applyLocalFilter(
        debounceReload: Boolean = false,
        transform: HistoryFilterState.() -> HistoryFilterState,
    ) {
        val updatedFilters = filterState.value.transform()
        filterState.value = updatedFilters
        applyFiltersToState(updatedFilters)
        scheduleFilterSave(updatedFilters)
        reloadFirstPage(
            debounceMillis = if (debounceReload) HISTORY_FILTER_DEBOUNCE_MILLIS else 0L,
        )
    }

    private fun applyFiltersToState(filters: HistoryFilterState) {
        _uiState.update { current ->
            current.copy(
                keyword = filters.keyword,
                excludeKeyword = filters.excludeKeyword,
                selectedAccountId = filters.selectedAccountId,
                dateStartAt = filters.dateStartAt,
                dateEndAt = filters.dateEndAt,
                minAmountText = filters.minAmountText,
                maxAmountText = filters.maxAmountText,
                amountDirectionFilter = filters.amountDirectionFilter,
            )
        }
    }

    private fun scheduleFilterSave(filters: HistoryFilterState) {
        saveFiltersJob?.cancel()
        saveFiltersJob = viewModelScope.launch {
            delay(500)
            devicePreferencesRepository.updateHistoryFilters(filters.toDeviceHistoryFilters())
        }
    }

    private fun reloadFirstPage(debounceMillis: Long = 0L) {
        reloadJob?.cancel()
        val generation = ++loadGeneration
        reloadJob = viewModelScope.launch {
            if (debounceMillis > 0L) delay(debounceMillis)
            _uiState.update { it.copy(isLoading = true, isLoadingMore = false) }
            val filters = filterState.value.toHistoryRecordFilters()
            runCatching {
                val total = transactionRepository.countHistoryRecords(filters)
                val records = transactionRepository.queryHistoryRecords(
                    filters = filters,
                    cursor = null,
                    limit = HISTORY_PAGE_SIZE,
                )
                total to records
            }.onSuccess { (total, records) ->
                if (generation != loadGeneration) return@onSuccess
                totalRecordCount = total
                loadedRecords = records
                nextCursor = records.lastOrNull()?.cursor
                _uiState.update {
                    it.copy(
                        records = records.toUiModels(),
                        totalRecordCount = total,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMoreRecords = records.size < total,
                    )
                }
            }.onFailure { error ->
                android.util.Log.e("HistoryViewModel", "Failed to load history", error)
                if (generation == loadGeneration) {
                    _uiState.update { it.copy(isLoading = false, isLoadingMore = false) }
                }
            }
        }
    }

    private fun List<DomainHistoryRecord>.toUiModels(): List<HistoryRecordUiModel> {
        return map { record ->
            val kind = record.type.toUiKind()
            val relatedAccountId = record.relatedAccountId
            HistoryRecordUiModel(
                id = "${kind.name.lowercase()}_${record.recordId}",
                recordId = record.recordId,
                kind = kind,
                title = record.title,
                subtitle = if (record.type == HistoryRecordType.TRANSFER && relatedAccountId != null) {
                    "${accountMap[record.accountId]?.name ?: "未知"} → ${accountMap[relatedAccountId]?.name ?: "未知"}"
                } else {
                    accountMap[record.accountId]?.name ?: "未知账户"
                },
                amount = record.amount,
                occurredAt = record.occurredAt,
                accountIds = if (relatedAccountId == null) {
                    setOf(record.accountId)
                } else {
                    setOf(record.accountId, relatedAccountId)
                },
                keywordSource = record.keywordSource,
            )
        }
    }

    private fun HistoryRecordType.toUiKind(): HistoryRecordKind {
        return when (this) {
            HistoryRecordType.CASH_FLOW -> HistoryRecordKind.CASH_FLOW
            HistoryRecordType.TRANSFER -> HistoryRecordKind.TRANSFER
            HistoryRecordType.BALANCE_UPDATE -> HistoryRecordKind.BALANCE_UPDATE
            HistoryRecordType.BALANCE_ADJUSTMENT -> HistoryRecordKind.BALANCE_ADJUSTMENT
        }
    }
}

private fun HistoryFilters.toHistoryFilterState(): HistoryFilterState {
    return HistoryFilterState(
        keyword = keyword,
        excludeKeyword = excludeKeyword,
        selectedAccountId = accountId,
        dateStartAt = dateStartAt,
        dateEndAt = dateEndAt,
        minAmountText = minAmountText,
        maxAmountText = maxAmountText,
        amountDirectionFilter = AmountDirectionFilter.fromValue(amountDirection.takeIf { it.isNotBlank() }),
    )
}

private fun HistoryFilterState.toDeviceHistoryFilters(): HistoryFilters = HistoryFilters(
    keyword = keyword,
    excludeKeyword = excludeKeyword,
    accountId = selectedAccountId,
    dateStartAt = dateStartAt,
    dateEndAt = dateEndAt,
    minAmountText = minAmountText,
    maxAmountText = maxAmountText,
    amountDirection = amountDirectionFilter.value,
)

private fun HistoryFilterState.toHistoryRecordFilters(): HistoryRecordFilters {
    return HistoryRecordFilters(
        keyword = keyword,
        excludeKeyword = excludeKeyword,
        accountId = selectedAccountId,
        dateStartAt = dateStartAt,
        dateEndAt = dateEndAt,
        minAmount = AmountInputParser.parseUnsignedToMinor(minAmountText),
        maxAmount = AmountInputParser.parseUnsignedToMinor(maxAmountText),
        amountDirection = when (amountDirectionFilter) {
            AmountDirectionFilter.ALL -> HistoryAmountDirection.ALL
            AmountDirectionFilter.INCREASE -> HistoryAmountDirection.INCREASE
            AmountDirectionFilter.DECREASE -> HistoryAmountDirection.DECREASE
        },
    )
}

/**
 * Pure filter function kept for direct unit testing of history-filter semantics.
 *
 * Production history filtering goes through [com.shihuaidexianyu.money.domain.repository.TransactionRepository.queryHistoryRecords]
 * (which delegates to a SQL `UNION ALL` query). This Kotlin implementation is exercised by
 * `HistoryFilterLogicTest` as a parallel specification — if the two diverge, the test will not
 * catch it (see `TransactionRepositoryContractTest` for that), but keeping the function around
 * documents the intended semantics and gives fast unit-test feedback on filter logic changes.
 */
internal fun filterHistoryRecords(
    source: List<HistoryRecordUiModel>,
    filters: HistoryFilterState,
): List<HistoryRecordUiModel> {
    val keyword = filters.keyword.trim().lowercase()
    val excludeKeyword = filters.excludeKeyword.trim().lowercase()
    val startAt = filters.dateStartAt
    val endAt = filters.dateEndAt
    val minAmount = AmountInputParser.parseUnsignedToMinor(filters.minAmountText)
    val maxAmount = AmountInputParser.parseUnsignedToMinor(filters.maxAmountText)

    return source.filter { record ->
        val keywordOk = keyword.isBlank() || record.keywordSource.lowercase().contains(keyword)
        val excludeOk = excludeKeyword.isBlank() || !record.keywordSource.lowercase().contains(excludeKeyword)
        val accountOk = filters.selectedAccountId == null || filters.selectedAccountId in record.accountIds
        val startOk = startAt == null || record.occurredAt >= startAt
        val endOk = endAt == null || record.occurredAt < endAt
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
