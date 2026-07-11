package com.shihuaidexianyu.money.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.backup.BackupImportCoordinator
import com.shihuaidexianyu.money.data.backup.BackupFileReader
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
import com.shihuaidexianyu.money.domain.usecase.BuildExportJsonUseCase
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

data class SettingsUiState(
    val portableSettings: PortableSettings = PortableSettings(),
    val devicePreferences: DevicePreferences = DevicePreferences(),
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val importHistory: List<ImportReceipt> = emptyList(),
)

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
    private val buildExportJsonUseCase: BuildExportJsonUseCase,
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
    private val importHistory = MutableStateFlow<List<ImportReceipt>>(emptyList())
    private val effects = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> =
        combine(
            portableSettingsRepository.observe(),
            devicePreferencesRepository.observe(),
            isExporting,
            isImporting,
            importHistory,
        ) { portable, device, exporting, importing, history ->
            SettingsUiState(
                portableSettings = portable,
                devicePreferences = device,
                isExporting = exporting,
                isImporting = importing,
                importHistory = history,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(),
            )

    init {
        viewModelScope.launch {
            importHistory.value = withContext(Dispatchers.IO) { backupImportCoordinator.history() }
        }
    }

    fun updateCurrencySymbol(symbol: String) {
        viewModelScope.launch { portableSettingsRepository.updateCurrencySymbol(symbol) }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch { devicePreferencesRepository.updateThemeMode(themeMode) }
    }

    fun updateAmountColorMode(amountColorMode: AmountColorMode) {
        viewModelScope.launch { portableSettingsRepository.updateAmountColorMode(amountColorMode) }
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
                    val json = buildExportJsonUseCase(exportedAt = exportedAt)
                    exportJsonFileWriter.write(json = json, timestamp = exportedAt)
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
                    val receipt = backupImportCoordinator.confirm(stageId)
                    receipt to backupImportCoordinator.history()
                }
            }.onSuccess { (receipt, history) ->
                importHistory.value = history
                effects.emit(SettingsEffect.ImportFinished(receipt))
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
            runCatching {
                withContext(Dispatchers.IO) {
                    val receipt = backupImportCoordinator.rollback(receiptId)
                    receipt to backupImportCoordinator.history()
                }
            }
                .onSuccess { (receipt, history) ->
                    importHistory.value = history
                    effects.emit(SettingsEffect.RollbackFinished(receipt))
                }
                .onFailure { error ->
                    effects.emit(SettingsEffect.ShowMessage(error.userMessage("撤销导入失败")))
                }
            isImporting.value = false
        }
    }
}
