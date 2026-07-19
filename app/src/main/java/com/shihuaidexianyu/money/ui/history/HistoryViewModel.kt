package com.shihuaidexianyu.money.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.HistoryFilters
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.HistoryAmountDirection
import com.shihuaidexianyu.money.domain.model.HistoryPageCursor
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.normalizeHistorySearchText
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.common.AsyncContent
import com.shihuaidexianyu.money.ui.common.EmptyKind
import com.shihuaidexianyu.money.ui.common.toAccountOptionUiModel
import com.shihuaidexianyu.money.util.AmountInputParser
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import com.shihuaidexianyu.money.domain.model.HistoryRecord as DomainHistoryRecord

private const val HISTORY_PAGE_SIZE = 100
private const val HISTORY_FILTER_DEBOUNCE_MILLIS = 250L

internal fun shouldApplyHistoryLoadResult(
    requestGeneration: Int,
    currentGeneration: Int,
    cancelled: Boolean,
): Boolean = !cancelled && requestGeneration == currentGeneration

enum class HistoryRecordKind {
    CASH_FLOW,
    TRANSFER,
    BALANCE_UPDATE,
    BALANCE_ADJUSTMENT,
}

enum class AmountDirectionFilter(val value: String, @param:StringRes val labelRes: Int) {
    ALL("all", R.string.history_direction_all),
    INCREASE("increase", R.string.history_direction_increase),
    DECREASE("decrease", R.string.history_direction_decrease),
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
    val selectedRecordTypes: Set<HistoryRecordType> = emptySet(),
    val selectedAccountId: Long? = null,
    val dateStartAt: Long? = null,
    val dateEndAt: Long? = null,
    val minAmountText: String = "",
    val maxAmountText: String = "",
    @param:StringRes val minAmountErrorRes: Int? = null,
    @param:StringRes val maxAmountErrorRes: Int? = null,
    val amountDirectionFilter: AmountDirectionFilter = AmountDirectionFilter.ALL,
    val records: List<HistoryRecordUiModel> = emptyList(),
    val totalRecordCount: Int = 0,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasCommittedContent: Boolean = false,
    @param:StringRes val errorMessageRes: Int? = null,
    val retryToken: String? = null,
    @param:StringRes val loadMoreErrorMessageRes: Int? = null,
    val isLoadingMore: Boolean = false,
    val hasMoreRecords: Boolean = false,
)

internal data class HistoryFilterState(
    val keyword: String = "",
    val excludeKeyword: String = "",
    val selectedRecordTypes: Set<HistoryRecordType> = emptySet(),
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
    private var initialized = false
    private var initializationJob: Job? = null
    private var accountCollectionJob: Job? = null
    private var accountUpdates: ReceiveChannel<List<Account>>? = null

    init {
        initializeSafely()
    }

    fun retry() {
        if (initialized) {
            reloadFirstPage()
        } else {
            initializeSafely()
        }
    }

    private fun initializeSafely() {
        initializationJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = !it.hasCommittedContent,
                isRefreshing = it.hasCommittedContent,
                errorMessageRes = null,
                retryToken = null,
            )
        }
        initializationJob = viewModelScope.launch {
            try {
                initialize()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { android.util.Log.e("HistoryViewModel", "Failed to observe history", e) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessageRes = R.string.history_load_failed,
                        retryToken = "history:init",
                    )
                }
            }
        }
    }

    fun updateKeyword(value: String) = applyLocalFilter(debounceReload = true) { copy(keyword = value) }
    fun updateExcludeKeyword(value: String) = applyLocalFilter(debounceReload = true) { copy(excludeKeyword = value) }
    fun updateRecordTypes(value: Set<HistoryRecordType>) = applyLocalFilter { copy(selectedRecordTypes = value) }
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

    fun clearFilters() {
        val cleared = HistoryFilterState()
        filterState.value = cleared
        applyFiltersToState(cleared)
        scheduleFilterSave(cleared)
        reloadFirstPage()
    }

    fun loadMore() {
        if (_uiState.value.isLoading || _uiState.value.isLoadingMore || !_uiState.value.hasMoreRecords) return
        val cursor = nextCursor ?: return
        val filters = filterState.value.toHistoryRecordFiltersOrNull() ?: return
        val generation = loadGeneration
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreErrorMessageRes = null) }
            runCatching {
                transactionRepository.queryHistoryRecords(
                    filters = filters,
                    cursor = cursor,
                    limit = HISTORY_PAGE_SIZE + 1,
                )
            }.onSuccess { nextRecords ->
                if (!shouldApplyHistoryLoadResult(generation, loadGeneration, cancelled = false)) return@onSuccess
                val page = nextRecords.take(HISTORY_PAGE_SIZE)
                val hasMore = nextRecords.size > HISTORY_PAGE_SIZE
                loadedRecords = loadedRecords + page
                nextCursor = page.lastOrNull()?.cursor ?: nextCursor
                totalRecordCount = loadedRecords.size + if (hasMore) 1 else 0
                _uiState.update {
                    it.copy(
                        records = loadedRecords.toUiModels(),
                        totalRecordCount = totalRecordCount,
                        isLoadingMore = false,
                        loadMoreErrorMessageRes = null,
                        hasMoreRecords = hasMore,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!shouldApplyHistoryLoadResult(generation, loadGeneration, cancelled = false)) return@onFailure
                runCatching { android.util.Log.e("HistoryViewModel", "Failed to load more history", error) }
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        loadMoreErrorMessageRes = R.string.history_load_more_failed,
                    )
                }
            }
        }
    }

    private suspend fun initialize() {
        val initialSettings = portableSettingsRepository.query()
        val initialFilters = devicePreferencesRepository.query().historyFilters.toHistoryFilterState()
        filterState.value = initialFilters
        applySettings(initialSettings)
        applyFiltersToState(initialFilters)
        accountCollectionJob?.cancel()
        accountUpdates?.cancel()
        val updates = observeAccounts().produceIn(viewModelScope)
        accountUpdates = updates
        applyAccounts(updates.receive())
        initialized = true

        viewModelScope.launch {
            portableSettingsRepository.observe()
                .drop(1)
                .collect(::applySettings)
        }
        accountCollectionJob = viewModelScope.launch {
            for (accounts in updates) applyAccounts(accounts)
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
        val previousSearchNames = accountMap.mapValues { it.value.name }
        accountMap = accounts.associateBy(Account::id)
        val currentSearchNames = accountMap.mapValues { it.value.name }
        _uiState.update {
            it.copy(
                accountOptions = accountMap.values
                    .sortedBy(Account::name)
                    .map(Account::toAccountOptionUiModel),
                records = loadedRecords.toUiModels(),
            )
        }
        if (initialized && previousSearchNames != currentSearchNames) {
            reloadFirstPage()
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
        val amountValidation = filters.validateAmounts()
        _uiState.update { current ->
            current.copy(
                keyword = filters.keyword,
                excludeKeyword = filters.excludeKeyword,
                selectedRecordTypes = filters.selectedRecordTypes,
                selectedAccountId = filters.selectedAccountId,
                dateStartAt = filters.dateStartAt,
                dateEndAt = filters.dateEndAt,
                minAmountText = filters.minAmountText,
                maxAmountText = filters.maxAmountText,
                minAmountErrorRes = amountValidation.minErrorRes,
                maxAmountErrorRes = amountValidation.maxErrorRes,
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
        loadMoreJob?.cancel()
        loadMoreJob = null
        val generation = ++loadGeneration
        val filters = filterState.value.toHistoryRecordFiltersOrNull()
        if (filters == null) {
            loadedRecords = emptyList()
            totalRecordCount = 0
            nextCursor = null
            _uiState.update {
                it.copy(
                    records = emptyList(),
                    totalRecordCount = 0,
                    isLoading = false,
                    isRefreshing = false,
                    hasCommittedContent = true,
                    isLoadingMore = false,
                    loadMoreErrorMessageRes = null,
                    hasMoreRecords = false,
                    errorMessageRes = null,
                    retryToken = null,
                )
            }
            return
        }
        reloadJob = viewModelScope.launch {
            if (debounceMillis > 0L) delay(debounceMillis)
            _uiState.update {
                it.copy(
                    isLoading = !it.hasCommittedContent,
                    isRefreshing = it.hasCommittedContent,
                    isLoadingMore = false,
                    errorMessageRes = null,
                    retryToken = null,
                    loadMoreErrorMessageRes = null,
                )
            }
            runCatching {
                transactionRepository.queryHistoryRecords(
                    filters = filters,
                    cursor = null,
                    limit = HISTORY_PAGE_SIZE + 1,
                )
            }.onSuccess { queriedRecords ->
                if (!shouldApplyHistoryLoadResult(generation, loadGeneration, cancelled = false)) return@onSuccess
                val records = queriedRecords.take(HISTORY_PAGE_SIZE)
                val hasMore = queriedRecords.size > HISTORY_PAGE_SIZE
                val total = records.size + if (hasMore) 1 else 0
                totalRecordCount = total
                loadedRecords = records
                nextCursor = records.lastOrNull()?.cursor
                _uiState.update {
                    it.copy(
                        records = records.toUiModels(),
                        totalRecordCount = total,
                        isLoading = false,
                        isRefreshing = false,
                        hasCommittedContent = true,
                        errorMessageRes = null,
                        retryToken = null,
                        isLoadingMore = false,
                        loadMoreErrorMessageRes = null,
                        hasMoreRecords = hasMore,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!shouldApplyHistoryLoadResult(generation, loadGeneration, cancelled = false)) return@onFailure
                runCatching { android.util.Log.e("HistoryViewModel", "Failed to load history", error) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessageRes = R.string.history_load_failed,
                        retryToken = "history:$generation",
                    )
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
                    "${accountMap[record.accountId]?.name ?: "—"} → ${accountMap[relatedAccountId]?.name ?: "—"}"
                } else {
                    accountMap[record.accountId]?.name ?: "—"
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
        selectedRecordTypes = recordTypes.mapNotNull { stored ->
            HistoryRecordType.entries.firstOrNull { it.name == stored }
        }.toSet(),
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
    recordTypes = selectedRecordTypes.mapTo(linkedSetOf()) { it.name },
    accountId = selectedAccountId,
    dateStartAt = dateStartAt,
    dateEndAt = dateEndAt,
    minAmountText = minAmountText,
    maxAmountText = maxAmountText,
    amountDirection = amountDirectionFilter.value,
)

internal fun HistoryUiState.toAsyncContent(errorMessage: String = ""): AsyncContent<HistoryUiState> {
    errorMessageRes?.let { return AsyncContent.Error(errorMessage, retryToken) }
    if (!hasCommittedContent) return AsyncContent.Loading
    if (isRefreshing) return AsyncContent.Refreshing(this)
    if (records.isEmpty()) {
        return AsyncContent.Empty(
            if (hasActiveFilters()) EmptyKind.FILTERED_EMPTY else EmptyKind.COMPLETELY_EMPTY,
        )
    }

    return AsyncContent.Data(this)
}

private fun HistoryUiState.hasActiveFilters(): Boolean =
    keyword.isNotBlank() ||
        excludeKeyword.isNotBlank() ||
        selectedRecordTypes.isNotEmpty() ||
        selectedAccountId != null ||
        dateStartAt != null ||
        dateEndAt != null ||
        minAmountText.isNotBlank() ||
        maxAmountText.isNotBlank() ||
        amountDirectionFilter != AmountDirectionFilter.ALL

private data class HistoryAmountValidation(
    val minAmount: Long?,
    val maxAmount: Long?,
    @param:StringRes val minErrorRes: Int?,
    @param:StringRes val maxErrorRes: Int?,
) {
    val isValid: Boolean get() = minErrorRes == null && maxErrorRes == null
}

private fun HistoryFilterState.validateAmounts(): HistoryAmountValidation {
    val minAmount = minAmountText.takeIf(String::isNotBlank)?.let(AmountInputParser::parseUnsignedToMinor)
    val maxAmount = maxAmountText.takeIf(String::isNotBlank)?.let(AmountInputParser::parseUnsignedToMinor)
    val minErrorRes = if (minAmountText.isNotBlank() && minAmount == null) R.string.validation_valid_amount else null
    var maxErrorRes = if (maxAmountText.isNotBlank() && maxAmount == null) R.string.validation_valid_amount else null
    if (minErrorRes == null && maxErrorRes == null && minAmount != null && maxAmount != null && minAmount > maxAmount) {
        maxErrorRes = R.string.validation_max_less_than_min
    }
    return HistoryAmountValidation(minAmount, maxAmount, minErrorRes, maxErrorRes)
}

private fun HistoryFilterState.toHistoryRecordFiltersOrNull(): HistoryRecordFilters? {
    val amounts = validateAmounts()
    if (!amounts.isValid) return null
    return HistoryRecordFilters(
        keyword = keyword,
        excludeKeyword = excludeKeyword,
        recordTypes = selectedRecordTypes,
        accountId = selectedAccountId,
        dateStartAt = dateStartAt,
        dateEndAt = dateEndAt,
        minAmount = amounts.minAmount,
        maxAmount = amounts.maxAmount,
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
    val keyword = normalizeHistorySearchText(filters.keyword.trim())
    val excludeKeyword = normalizeHistorySearchText(filters.excludeKeyword.trim())
    val startAt = filters.dateStartAt
    val endAt = filters.dateEndAt
    val amountValidation = filters.validateAmounts()
    if (!amountValidation.isValid) return emptyList()
    val minAmount = amountValidation.minAmount
    val maxAmount = amountValidation.maxAmount

    return source.filter { record ->
        val searchableText = normalizeHistorySearchText(
            sequenceOf(record.keywordSource, record.title, record.subtitle).joinToString(" "),
        )
        val keywordOk = keyword.isBlank() || searchableText.contains(keyword)
        val excludeOk = excludeKeyword.isBlank() || !searchableText.contains(excludeKeyword)
        val typeOk = filters.selectedRecordTypes.isEmpty() || record.kind.toDomainType() in filters.selectedRecordTypes
        val accountOk = filters.selectedAccountId == null || filters.selectedAccountId in record.accountIds
        val startOk = startAt == null || record.occurredAt >= startAt
        val endOk = endAt == null || record.occurredAt < endAt
        val minOk = minAmount == null || record.amount >= minAmount || record.amount <= -minAmount
        val maxOk = maxAmount == null || (record.amount >= -maxAmount && record.amount <= maxAmount)
        val directionOk = when (filters.amountDirectionFilter) {
            AmountDirectionFilter.ALL -> true
            AmountDirectionFilter.INCREASE -> record.amount > 0 && record.kind != HistoryRecordKind.TRANSFER
            AmountDirectionFilter.DECREASE -> record.amount < 0 && record.kind != HistoryRecordKind.TRANSFER
        }
        keywordOk && excludeOk && typeOk && accountOk &&
            startOk && endOk && minOk && maxOk && directionOk
    }
}

private fun HistoryRecordKind.toDomainType(): HistoryRecordType = when (this) {
    HistoryRecordKind.CASH_FLOW -> HistoryRecordType.CASH_FLOW
    HistoryRecordKind.TRANSFER -> HistoryRecordType.TRANSFER
    HistoryRecordKind.BALANCE_UPDATE -> HistoryRecordType.BALANCE_UPDATE
    HistoryRecordKind.BALANCE_ADJUSTMENT -> HistoryRecordType.BALANCE_ADJUSTMENT
}
