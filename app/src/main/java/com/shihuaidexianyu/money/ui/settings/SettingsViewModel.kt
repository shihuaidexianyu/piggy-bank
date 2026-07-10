package com.shihuaidexianyu.money.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shihuaidexianyu.money.data.backup.BackupFileReader
import com.shihuaidexianyu.money.data.backup.PreImportBackupWriter
import com.shihuaidexianyu.money.data.export.ExportJsonFileWriter
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.ThemeMode
import com.shihuaidexianyu.money.domain.usecase.BackupValidationResult
import com.shihuaidexianyu.money.domain.usecase.BuildExportJsonUseCase
import com.shihuaidexianyu.money.domain.usecase.ImportBackupUseCase
import com.shihuaidexianyu.money.domain.usecase.ValidateBackupSnapshotUseCase
import com.shihuaidexianyu.money.ui.common.UiEffect
import com.shihuaidexianyu.money.ui.common.userMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val portableSettings: PortableSettings = PortableSettings(),
    val devicePreferences: DevicePreferences = DevicePreferences(),
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
)

sealed interface SettingsEffect {
    data class ExportReady(
        val uri: Uri,
        val fileName: String,
        val mimeType: String,
    ) : SettingsEffect

    data class ImportPreviewReady(
        val preview: BackupValidationResult,
        val uri: Uri,
    ) : SettingsEffect

    data object ImportFinished : SettingsEffect, UiEffect.HasMessage {
        override val message: String = "导入完成"
    }

    data class PreImportBackupReady(
        val uri: Uri,
        val fileName: String,
        val mimeType: String,
    ) : SettingsEffect

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
    private val preImportBackupWriter: PreImportBackupWriter,
    private val importBackupUseCase: ImportBackupUseCase,
    private val validateBackupSnapshotUseCase: ValidateBackupSnapshotUseCase,
) : ViewModel() {
    private val isExporting = MutableStateFlow(false)
    private val isImporting = MutableStateFlow(false)
    private val effects = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
    val effectFlow = effects.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> =
        combine(
            portableSettingsRepository.observe(),
            devicePreferencesRepository.observe(),
            isExporting,
            isImporting,
        ) { portable, device, exporting, importing ->
            SettingsUiState(
                portableSettings = portable,
                devicePreferences = device,
                isExporting = exporting,
                isImporting = importing,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(),
            )

    fun updateCurrencySymbol(symbol: String) {
        viewModelScope.launch { portableSettingsRepository.updateCurrencySymbol(symbol) }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch { devicePreferencesRepository.updateThemeMode(themeMode) }
    }

    fun updateAmountColorMode(amountColorMode: AmountColorMode) {
        viewModelScope.launch { portableSettingsRepository.updateAmountColorMode(amountColorMode) }
    }

    fun updateBiometricLock(enabled: Boolean) {
        viewModelScope.launch { devicePreferencesRepository.updateBiometricLock(enabled) }
    }

    fun exportData() {
        if (isExporting.value) return
        viewModelScope.launch {
            isExporting.value = true
            runCatching {
                val exportedAt = System.currentTimeMillis()
                val json = buildExportJsonUseCase(exportedAt = exportedAt)
                exportJsonFileWriter.write(json = json, timestamp = exportedAt)
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
                val snapshot = backupFileReader.readSnapshot(uri)
                validateBackupSnapshotUseCase(snapshot)
            }.onSuccess { preview ->
                effects.emit(SettingsEffect.ImportPreviewReady(preview = preview, uri = uri))
            }.onFailure { error ->
                effects.emit(SettingsEffect.ShowMessage(error.userMessage("无法读取备份文件")))
            }
            isImporting.value = false
        }
    }

    fun confirmImport(uri: Uri) {
        if (isImporting.value || isExporting.value) return
        viewModelScope.launch {
            isImporting.value = true
            var backupEffect: SettingsEffect.PreImportBackupReady? = null
            runCatching {
                val timestamp = System.currentTimeMillis()
                val currentJson = buildExportJsonUseCase(exportedAt = timestamp)
                val preImportBackup = preImportBackupWriter.write(
                    json = currentJson,
                    timestamp = timestamp,
                )
                backupEffect = SettingsEffect.PreImportBackupReady(
                    uri = preImportBackup.uri,
                    fileName = preImportBackup.fileName,
                    mimeType = preImportBackup.mimeType,
                )
                val snapshot = backupFileReader.readSnapshot(uri)
                importBackupUseCase(snapshot)
            }.onSuccess {
                effects.emit(SettingsEffect.ImportFinished)
            }.onFailure { error ->
                backupEffect?.let { effects.emit(it) }
                effects.emit(SettingsEffect.ShowMessage(error.userMessage("导入失败")))
            }
            isImporting.value = false
        }
    }
}
