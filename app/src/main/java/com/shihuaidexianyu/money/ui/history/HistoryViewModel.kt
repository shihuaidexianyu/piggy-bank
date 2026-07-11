package com.shihuaidexianyu.money.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
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
import java.math.BigDecimal

private const val HISTORY_PAGE_SIZE = 100
private const val HISTORY_FILTER_DEBOUNCE_MILLIS = 250L
private const val EXACT_FILTER_ACTIVE = "history_exact_active"
private const val EXACT_KEYWORD = "history_exact_keyword"
private const val EXACT_EXCLUDE = "history_exact_exclude"
private const val EXACT_TYPES = "history_exact_types"
private const val EXACT_ACCOUNT = "history_exact_account"
private const val EXACT_TRANSFER_FROM = "history_exact_transfer_from"
private const val EXACT_TRANSFER_TO = "history_exact_transfer_to"
private const val EXACT_START = "history_exact_start"
private const val EXACT_END = "history_exact_end"
private const val EXACT_MIN = "history_exact_min"
private const val EXACT_MAX = "history_exact_max"
private const val EXACT_DIRECTION = "history_exact_direction"

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
    val transferFromAccountId: Long? = null,
    val transferToAccountId: Long? = null,
)

data class HistoryUiState(
    val settings: PortableSettings = PortableSettings(),
    val accountOptions: List<AccountOptionUiModel> = emptyList(),
    val keyword: String = "",
    val excludeKeyword: String = "",
    val selectedRecordTypes: Set<HistoryRecordType> = emptySet(),
    val selectedAccountId: Long? = null,
    val transferFromAccountId: Long? = null,
    val transferToAccountId: Long? = null,
    val dateStartAt: Long? = null,
    val dateEndAt: Long? = null,
    val minAmountText: String = "",
    val maxAmountText: String = "",
    val minAmountError: String? = null,
    val maxAmountError: String? = null,
    val amountDirectionFilter: AmountDirectionFilter = AmountDirectionFilter.ALL,
    val records: List<HistoryRecordUiModel> = emptyList(),
    val totalRecordCount: Int = 0,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasCommittedContent: Boolean = false,
    val errorMessage: String? = null,
    val retryToken: String? = null,
    val loadMoreErrorMessage: String? = null,
    val isLoadingMore: Boolean = false,
    val hasMoreRecords: Boolean = false,
)

internal data class HistoryFilterState(
    val keyword: String = "",
    val excludeKeyword: String = "",
    val selectedRecordTypes: Set<HistoryRecordType> = emptySet(),
    val selectedAccountId: Long? = null,
    val transferFromAccountId: Long? = null,
    val transferToAccountId: Long? = null,
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
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
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
                errorMessage = null,
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
                        errorMessage = "明细加载失败，请重试",
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

    fun applyExternalFilters(filters: HistoryRecordFilters) {
        val updated = filters.toHistoryFilterState()
        filterState.value = updated
        persistExactFilterState(updated)
        applyFiltersToState(updated)
        scheduleFilterSave(updated)
        reloadFirstPage()
    }

    fun clearFilters() {
        val cleared = HistoryFilterState()
        filterState.value = cleared
        clearExactFilterState()
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
            _uiState.update { it.copy(isLoadingMore = true, loadMoreErrorMessage = null) }
            runCatching {
                transactionRepository.queryHistoryRecords(
                    filters = filters,
                    cursor = cursor,
                    limit = HISTORY_PAGE_SIZE,
                )
            }.onSuccess { nextRecords ->
                if (!shouldApplyHistoryLoadResult(generation, loadGeneration, cancelled = false)) return@onSuccess
                loadedRecords = loadedRecords + nextRecords
                nextCursor = nextRecords.lastOrNull()?.cursor ?: nextCursor
                val hasMore = loadedRecords.size < totalRecordCount && nextRecords.isNotEmpty()
                _uiState.update {
                    it.copy(
                        records = loadedRecords.toUiModels(),
                        totalRecordCount = totalRecordCount,
                        isLoadingMore = false,
                        loadMoreErrorMessage = null,
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
                        loadMoreErrorMessage = "加载更多明细失败",
                    )
                }
            }
        }
    }

    private suspend fun initialize() {
        val initialSettings = portableSettingsRepository.query()
        val deviceFilters = devicePreferencesRepository.query().historyFilters.toHistoryFilterState()
        // Re-read after the suspend query so an external drill-down delivered during startup wins.
        val initialFilters = restoreExactFilterState() ?: deviceFilters
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
        if (savedStateHandle.get<Boolean>(EXACT_FILTER_ACTIVE) == true) persistExactFilterState(updatedFilters)
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
                transferFromAccountId = filters.transferFromAccountId,
                transferToAccountId = filters.transferToAccountId,
                dateStartAt = filters.dateStartAt,
                dateEndAt = filters.dateEndAt,
                minAmountText = filters.minAmountText,
                maxAmountText = filters.maxAmountText,
                minAmountError = amountValidation.minError,
                maxAmountError = amountValidation.maxError,
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
                    loadMoreErrorMessage = null,
                    hasMoreRecords = false,
                    errorMessage = null,
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
                    errorMessage = null,
                    retryToken = null,
                    loadMoreErrorMessage = null,
                )
            }
            runCatching {
                val total = transactionRepository.countHistoryRecords(filters)
                val records = transactionRepository.queryHistoryRecords(
                    filters = filters,
                    cursor = null,
                    limit = HISTORY_PAGE_SIZE,
                )
                total to records
            }.onSuccess { (total, records) ->
                if (!shouldApplyHistoryLoadResult(generation, loadGeneration, cancelled = false)) return@onSuccess
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
                        errorMessage = null,
                        retryToken = null,
                        isLoadingMore = false,
                        loadMoreErrorMessage = null,
                        hasMoreRecords = records.size < total,
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
                        errorMessage = "明细加载失败，请重试",
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
                transferFromAccountId = record.accountId.takeIf { record.type == HistoryRecordType.TRANSFER },
                transferToAccountId = record.relatedAccountId.takeIf { record.type == HistoryRecordType.TRANSFER },
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

    private fun persistExactFilterState(filters: HistoryFilterState) {
        savedStateHandle[EXACT_FILTER_ACTIVE] = true
        savedStateHandle[EXACT_KEYWORD] = filters.keyword
        savedStateHandle[EXACT_EXCLUDE] = filters.excludeKeyword
        savedStateHandle[EXACT_TYPES] = ArrayList(filters.selectedRecordTypes.map(HistoryRecordType::name))
        savedStateHandle[EXACT_ACCOUNT] = filters.selectedAccountId
        savedStateHandle[EXACT_TRANSFER_FROM] = filters.transferFromAccountId
        savedStateHandle[EXACT_TRANSFER_TO] = filters.transferToAccountId
        savedStateHandle[EXACT_START] = filters.dateStartAt
        savedStateHandle[EXACT_END] = filters.dateEndAt
        savedStateHandle[EXACT_MIN] = filters.minAmountText
        savedStateHandle[EXACT_MAX] = filters.maxAmountText
        savedStateHandle[EXACT_DIRECTION] = filters.amountDirectionFilter.value
    }

    private fun restoreExactFilterState(): HistoryFilterState? {
        if (savedStateHandle.get<Boolean>(EXACT_FILTER_ACTIVE) != true) return null
        return HistoryFilterState(
            keyword = savedStateHandle.get<String>(EXACT_KEYWORD).orEmpty(),
            excludeKeyword = savedStateHandle.get<String>(EXACT_EXCLUDE).orEmpty(),
            selectedRecordTypes = savedStateHandle.get<ArrayList<String>>(EXACT_TYPES)
                .orEmpty()
                .mapNotNull { stored -> HistoryRecordType.entries.firstOrNull { it.name == stored } }
                .toSet(),
            selectedAccountId = savedStateHandle.get<Long>(EXACT_ACCOUNT),
            transferFromAccountId = savedStateHandle.get<Long>(EXACT_TRANSFER_FROM),
            transferToAccountId = savedStateHandle.get<Long>(EXACT_TRANSFER_TO),
            dateStartAt = savedStateHandle.get<Long>(EXACT_START),
            dateEndAt = savedStateHandle.get<Long>(EXACT_END),
            minAmountText = savedStateHandle.get<String>(EXACT_MIN).orEmpty(),
            maxAmountText = savedStateHandle.get<String>(EXACT_MAX).orEmpty(),
            amountDirectionFilter = AmountDirectionFilter.fromValue(savedStateHandle.get(EXACT_DIRECTION)),
        )
    }

    private fun clearExactFilterState() {
        listOf(
            EXACT_FILTER_ACTIVE,
            EXACT_KEYWORD,
            EXACT_EXCLUDE,
            EXACT_TYPES,
            EXACT_ACCOUNT,
            EXACT_TRANSFER_FROM,
            EXACT_TRANSFER_TO,
            EXACT_START,
            EXACT_END,
            EXACT_MIN,
            EXACT_MAX,
            EXACT_DIRECTION,
        ).forEach { key -> savedStateHandle.remove<Any>(key) }
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

internal fun HistoryUiState.toAsyncContent(): AsyncContent<HistoryUiState> {
    errorMessage?.let { return AsyncContent.Error(it, retryToken) }
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
        transferFromAccountId != null ||
        transferToAccountId != null ||
        dateStartAt != null ||
        dateEndAt != null ||
        minAmountText.isNotBlank() ||
        maxAmountText.isNotBlank() ||
        amountDirectionFilter != AmountDirectionFilter.ALL

private data class HistoryAmountValidation(
    val minAmount: Long?,
    val maxAmount: Long?,
    val minError: String?,
    val maxError: String?,
) {
    val isValid: Boolean get() = minError == null && maxError == null
}

private fun HistoryFilterState.validateAmounts(): HistoryAmountValidation {
    val minAmount = minAmountText.takeIf(String::isNotBlank)?.let(AmountInputParser::parseUnsignedToMinor)
    val maxAmount = maxAmountText.takeIf(String::isNotBlank)?.let(AmountInputParser::parseUnsignedToMinor)
    val minError = if (minAmountText.isNotBlank() && minAmount == null) "请输入有效金额" else null
    var maxError = if (maxAmountText.isNotBlank() && maxAmount == null) "请输入有效金额" else null
    if (minError == null && maxError == null && minAmount != null && maxAmount != null && minAmount > maxAmount) {
        maxError = "最大金额不能小于最小金额"
    }
    return HistoryAmountValidation(minAmount, maxAmount, minError, maxError)
}

private fun HistoryFilterState.toHistoryRecordFiltersOrNull(): HistoryRecordFilters? {
    val amounts = validateAmounts()
    if (!amounts.isValid) return null
    return HistoryRecordFilters(
        keyword = keyword,
        excludeKeyword = excludeKeyword,
        recordTypes = selectedRecordTypes,
        accountId = selectedAccountId,
        transferFromAccountId = transferFromAccountId,
        transferToAccountId = transferToAccountId,
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
        val transferFromOk = filters.transferFromAccountId == null ||
            record.transferFromAccountId == filters.transferFromAccountId
        val transferToOk = filters.transferToAccountId == null ||
            record.transferToAccountId == filters.transferToAccountId
        val startOk = startAt == null || record.occurredAt >= startAt
        val endOk = endAt == null || record.occurredAt < endAt
        val minOk = minAmount == null || record.amount >= minAmount || record.amount <= -minAmount
        val maxOk = maxAmount == null || (record.amount >= -maxAmount && record.amount <= maxAmount)
        val directionOk = when (filters.amountDirectionFilter) {
            AmountDirectionFilter.ALL -> true
            AmountDirectionFilter.INCREASE -> record.amount > 0 && record.kind != HistoryRecordKind.TRANSFER
            AmountDirectionFilter.DECREASE -> record.amount < 0 && record.kind != HistoryRecordKind.TRANSFER
        }
        keywordOk && excludeOk && typeOk && accountOk && transferFromOk && transferToOk &&
            startOk && endOk && minOk && maxOk && directionOk
    }
}

private fun HistoryRecordKind.toDomainType(): HistoryRecordType = when (this) {
    HistoryRecordKind.CASH_FLOW -> HistoryRecordType.CASH_FLOW
    HistoryRecordKind.TRANSFER -> HistoryRecordType.TRANSFER
    HistoryRecordKind.BALANCE_UPDATE -> HistoryRecordType.BALANCE_UPDATE
    HistoryRecordKind.BALANCE_ADJUSTMENT -> HistoryRecordType.BALANCE_ADJUSTMENT
}

private fun HistoryRecordFilters.toHistoryFilterState(): HistoryFilterState = HistoryFilterState(
    keyword = keyword,
    excludeKeyword = excludeKeyword,
    selectedRecordTypes = recordTypes,
    selectedAccountId = accountId,
    transferFromAccountId = transferFromAccountId,
    transferToAccountId = transferToAccountId,
    dateStartAt = dateStartAt,
    dateEndAt = dateEndAt,
    minAmountText = minAmount?.toHistoryAmountInput().orEmpty(),
    maxAmountText = maxAmount?.toHistoryAmountInput().orEmpty(),
    amountDirectionFilter = when (amountDirection) {
        HistoryAmountDirection.ALL -> AmountDirectionFilter.ALL
        HistoryAmountDirection.INCREASE -> AmountDirectionFilter.INCREASE
        HistoryAmountDirection.DECREASE -> AmountDirectionFilter.DECREASE
    },
)

private fun Long.toHistoryAmountInput(): String = BigDecimal.valueOf(this, 2)
    .stripTrailingZeros()
    .toPlainString()
