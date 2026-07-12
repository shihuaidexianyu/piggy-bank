package com.shihuaidexianyu.money.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.backup.BackupImportCoordinator
import com.shihuaidexianyu.money.data.backup.BackupFileReader
import com.shihuaidexianyu.money.data.backup.ImportHistoryWithRollbackEligibility
import com.shihuaidexianyu.money.data.backup.ImportReceipt
import com.shihuaidexianyu.money.data.export.ExportJsonFileWriter
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.AppRelockDelay
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.usecase.BackupValidationResult
import com.shihuaidexianyu.money.domain.usecase.BuildExportSnapshotUseCase
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.ui.common.UiEffect
import com.shihuaidexianyu.money.ui.common.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

data class SettingsUiState(
    val portableSettings: PortableSettings = PortableSettings(),
    val devicePreferences: DevicePreferences = DevicePreferences(),
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val importHistory: List<ImportReceipt> = emptyList(),
    val rollbackEligibleReceiptId: String? = null,
    val isLoadingImportHistory: Boolean = true,
    val importHistoryErrorMessage: String? = null,
)

internal data class ImportHistoryLoadState(
    val receipts: List<ImportReceipt> = emptyList(),
    val rollbackEligibleReceiptId: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

internal suspend fun loadImportHistoryState(
    load: suspend () -> ImportHistoryWithRollbackEligibility,
): ImportHistoryLoadState = try {
    val result = load()
    ImportHistoryLoadState(
        receipts = result.receipts,
        rollbackEligibleReceiptId = result.rollbackEligibleReceiptId,
        isLoading = false,
    )
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    ImportHistoryLoadState(
        isLoading = false,
        errorMessage = error.userMessage("导入记录加载失败，请重试"),
    )
}

internal suspend fun commitPortableSettingsMutation(
    mutation: suspend () -> Unit,
    refreshImportHistory: () -> Unit,
): Result<Unit> = try {
    mutation()
    refreshImportHistory()
    Result.success(Unit)
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}

internal suspend fun <T> rollbackAndRefreshImportHistory(
    rollback: suspend () -> T,
    refreshImportHistory: () -> Unit,
): Result<T> = try {
    val result = rollback()
    refreshImportHistory()
    Result.success(result)
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    refreshImportHistory()
    Result.failure(error)
}

sealed interface SettingsEffect {
    data class ExportReady(
        val uri: Uri,
        val fileName: String,
        val mimeType: String,
    ) : SettingsEffect

    data class ImportPreviewReady(
        val preview: BackupValidationResult,
        val stageId: String,
    ) : SettingsEffect

    data class ImportFinished(
        val receipt: ImportReceipt,
    ) : SettingsEffect, UiEffect.HasMessage {
        override val message: String = "导入完成"
    }

    data class RollbackFinished(
        val receipt: ImportReceipt,
    ) : SettingsEffect, UiEffect.HasMessage {
        override val message: String = "已撤销并生成新的保护快照"
    }

    data class ShowMessage(
        override val message: String,
    ) : SettingsEffect, UiEffect.HasMessage
}

class SettingsViewModel(
    private val portableSettingsRepository: PortableSettingsRepository,
    private val devicePreferencesRepository: DevicePreferencesRepository,
    private val buildExportSnapshotUseCase: BuildExportSnapshotUseCase,
    private val exportJsonFileWriter: ExportJsonFileWriter,
    private val backupFileReader: BackupFileReader,
    private val backupImportCoordinator: BackupImportCoordinator,
    private val clockProvider: ClockProvider,
    private val forceRefreshNotificationPrivacy: suspend () -> Unit = {},
    private val onWidgetPrivacyChanging: (Boolean) -> Unit = {},
    private val onNotificationPrivacyChanging: (Boolean) -> Unit = {},
) : ViewModel() {
    private val isExporting = MutableStateFlow(false)
    private val isImporting = MutableStateFlow(false)
    private val importHistoryState = MutableStateFlow(ImportHistoryLoadState())
    private val effects = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()
    private var importHistoryLoadJob: Job? = null

    val uiState: StateFlow<SettingsUiState> =
        combine(
            portableSettingsRepository.observe(),
            devicePreferencesRepository.observe(),
            isExporting,
            isImporting,
            importHistoryState,
        ) { portable, device, exporting, importing, history ->
            SettingsUiState(
                portableSettings = portable,
                devicePreferences = device,
                isExporting = exporting,
                isImporting = importing,
                importHistory = history.receipts,
                rollbackEligibleReceiptId = history.rollbackEligibleReceiptId,
                isLoadingImportHistory = history.isLoading,
                importHistoryErrorMessage = history.errorMessage,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(),
            )

    init {
        refreshImportHistory()
    }

    fun retryImportHistory() {
        refreshImportHistory()
    }

    private fun refreshImportHistory() {
        importHistoryLoadJob?.cancel()
        importHistoryState.value = importHistoryState.value.copy(
            isLoading = true,
            errorMessage = null,
            rollbackEligibleReceiptId = null,
        )
        importHistoryLoadJob = viewModelScope.launch {
            importHistoryState.value = withContext(Dispatchers.IO) {
                loadImportHistoryState {
                    backupImportCoordinator.historyWithRollbackEligibility()
                }
            }
        }
    }

    fun updateCurrencySymbol(symbol: String) {
        commitPortableSettingsChange {
            portableSettingsRepository.updateCurrencySymbol(symbol)
        }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch { devicePreferencesRepository.updateThemeMode(themeMode) }
    }

    fun updateAmountColorMode(amountColorMode: AmountColorMode) {
        commitPortableSettingsChange {
            portableSettingsRepository.updateAmountColorMode(amountColorMode)
        }
    }

    fun updateRelockDelay(delay: AppRelockDelay) {
        viewModelScope.launch { devicePreferencesRepository.updateRelockDelay(delay) }
    }

    fun updateMaskAmountsInApp(enabled: Boolean) {
        viewModelScope.launch { devicePreferencesRepository.updateMaskAmountsInApp(enabled) }
    }

    fun updateHideWidgetAmounts(enabled: Boolean) {
        viewModelScope.launch {
            onWidgetPrivacyChanging(enabled)
            devicePreferencesRepository.updateHideWidgetAmounts(enabled)
        }
    }

    fun updateHideNotificationAmounts(enabled: Boolean) {
        viewModelScope.launch {
            onNotificationPrivacyChanging(enabled)
            devicePreferencesRepository.updateHideNotificationAmounts(enabled)
            forceRefreshNotificationPrivacy()
        }
    }

    fun updateHideRecentTasks(enabled: Boolean) {
        viewModelScope.launch { devicePreferencesRepository.updateHideRecentTasks(enabled) }
    }

    fun exportData() {
        if (isExporting.value) return
        viewModelScope.launch {
            isExporting.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val exportedAt = clockProvider.nowMillis()
                    val snapshot = buildExportSnapshotUseCase(exportedAt = exportedAt)
                    exportJsonFileWriter.write(snapshot = snapshot, timestamp = exportedAt)
                }
            }.onSuccess { file ->
                effects.emit(
                    SettingsEffect.ExportReady(
                        uri = file.uri,
                        fileName = file.fileName,
                        mimeType = file.mimeType,
                    ),
                )
            }.onFailure { error ->
                effects.emit(SettingsEffect.ShowMessage(error.userMessage("导出失败")))
            }
            isExporting.value = false
        }
    }

    fun previewImport(uri: Uri) {
        if (isImporting.value || isExporting.value) return
        viewModelScope.launch {
            isImporting.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val stage = backupFileReader.stage(uri, clockProvider.nowMillis())
                    backupImportCoordinator.preview(stage.id)
                }
            }.onSuccess { preview ->
                effects.emit(
                    SettingsEffect.ImportPreviewReady(
                        preview = preview.validation,
                        stageId = preview.stageId,
                    ),
                )
            }.onFailure { error ->
                effects.emit(SettingsEffect.ShowMessage(error.userMessage("无法读取备份文件")))
            }
            isImporting.value = false
        }
    }

    fun confirmImport(stageId: String) {
        if (isImporting.value || isExporting.value) return
        viewModelScope.launch {
            isImporting.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    backupImportCoordinator.confirm(stageId)
                }
            }.onSuccess { receipt ->
                effects.emit(SettingsEffect.ImportFinished(receipt))
                refreshImportHistory()
            }.onFailure { error ->
                effects.emit(SettingsEffect.ShowMessage(error.userMessage("导入失败")))
            }
            isImporting.value = false
        }
    }

    fun rollbackImport(receiptId: String) {
        if (isImporting.value || isExporting.value) return
        viewModelScope.launch {
            isImporting.value = true
            rollbackAndRefreshImportHistory(
                rollback = {
                    withContext(Dispatchers.IO) {
                        backupImportCoordinator.rollback(receiptId)
                    }
                },
                refreshImportHistory = ::refreshImportHistory,
            )
                .onSuccess { receipt ->
                    effects.emit(SettingsEffect.RollbackFinished(receipt))
                }
                .onFailure { error ->
                    effects.emit(SettingsEffect.ShowMessage(error.userMessage("撤销导入失败")))
                }
            isImporting.value = false
        }
    }

    private fun commitPortableSettingsChange(mutation: suspend () -> Unit) {
        viewModelScope.launch {
            commitPortableSettingsMutation(
                mutation = {
                    withContext(Dispatchers.IO) { mutation() }
                },
                refreshImportHistory = ::refreshImportHistory,
            ).onFailure { error ->
                effects.emit(SettingsEffect.ShowMessage(error.userMessage("设置保存失败")))
            }
        }
    }
}
