package com.shihuaidexianyu.money

import android.content.ClipData
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import com.shihuaidexianyu.money.data.migration.StartupRecoveryAction
import com.shihuaidexianyu.money.navigation.MoneyNavGraph
import com.shihuaidexianyu.money.ui.lock.AppLockFeedback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import com.shihuaidexianyu.money.domain.launch.AppLaunchRequest
import com.shihuaidexianyu.money.domain.model.AmountPrivacy
import com.shihuaidexianyu.money.ui.common.LocalAmountPrivacy

@Composable
fun MoneyApp(
    container: MoneyAppContainer,
    appLaunchRequest: AppLaunchRequest? = null,
    onAppLaunchConsumed: (String) -> Unit = {},
    onBiometricLockChange: (Boolean) -> Unit = {},
    amountPrivacy: AmountPrivacy = AmountPrivacy.Visible,
    appLockFeedback: Flow<AppLockFeedback>? = null,
) {
    CompositionLocalProvider(LocalAmountPrivacy provides amountPrivacy) {
        MoneyNavGraph(
            container = container,
            appLaunchRequest = appLaunchRequest,
            onAppLaunchConsumed = onAppLaunchConsumed,
            onBiometricLockChange = onBiometricLockChange,
            appLockFeedback = appLockFeedback,
        )
    }
}

@Composable
fun StartupMigrationSurface(
    container: MoneyAppContainer,
    state: StartupMigrationState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showResetConfirmation by remember { mutableStateOf(false) }
    var recoveryActionError by remember { mutableStateOf<String?>(null) }
    when (val current = state) {
        StartupMigrationState.Loading -> Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.migration_preparing), modifier = Modifier.padding(top = 16.dp))
        }

        StartupMigrationState.Ready -> Unit

        is StartupMigrationState.RecoverableError -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.migration_needed_title))
            Text(current.diagnostic, modifier = Modifier.padding(vertical = 16.dp))
            recoveryActionError?.let { Text(it, modifier = Modifier.padding(bottom = 8.dp)) }
            Button(onClick = { scope.launch { container.startupMigrationCoordinator.retry() } }) {
                Text(stringResource(R.string.action_retry))
            }
            if (StartupRecoveryAction.USE_CURRENT_DATABASE in current.actions) {
                Button(
                    onClick = {
                        scope.launch {
                            container.startupMigrationCoordinator.useCurrentDatabaseAndIgnoreLegacy()
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.migration_use_current))
                }
            }
            if (StartupRecoveryAction.EXPORT_LEGACY_SOURCE in current.actions) {
                Button(
                    onClick = {
                        scope.launch {
                            recoveryActionError = null
                            container.startupMigrationCoordinator.exportLegacySource()
                                .onSuccess { export ->
                                    val uri = export.contentUri.toUri()
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = export.mimeType
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_TITLE, export.fileName)
                                        putExtra(Intent.EXTRA_SUBJECT, export.fileName)
                                        clipData = ClipData.newUri(
                                            context.contentResolver,
                                            export.fileName,
                                            uri,
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, context.getString(R.string.migration_save_legacy_chooser)),
                                    )
                                }
                                .onFailure { error ->
                                    recoveryActionError = error.message ?: context.getString(R.string.migration_export_failed)
                                }
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.migration_export_legacy))
                }
            }
            if (StartupRecoveryAction.RESET_LOCAL_SETTINGS in current.actions) {
                Button(
                    onClick = { showResetConfirmation = true },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.migration_reset_local))
                }
            }
            if (showResetConfirmation) {
                AlertDialog(
                    onDismissRequest = { showResetConfirmation = false },
                    title = { Text(stringResource(R.string.migration_reset_confirm_title)) },
                    text = {
                        Text(stringResource(R.string.migration_reset_confirm_message))
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showResetConfirmation = false
                                scope.launch {
                                    container.startupMigrationCoordinator.resetCorruptLocalSettings()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.migration_reset_confirm_action))
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showResetConfirmation = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                )
            }
        }
    }
}
