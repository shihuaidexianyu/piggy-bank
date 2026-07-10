package com.shihuaidexianyu.money

import android.content.ClipData
import android.content.Intent
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import com.shihuaidexianyu.money.data.migration.StartupRecoveryAction
import com.shihuaidexianyu.money.navigation.MoneyNavGraph
import kotlinx.coroutines.launch
import com.shihuaidexianyu.money.domain.notification.NotificationLaunchRequest

@Composable
fun MoneyApp(
    container: MoneyAppContainer,
    shortcutAction: String? = null,
    sharedAmount: Long? = null,
    notificationLaunchRequest: NotificationLaunchRequest? = null,
    onNotificationLaunchConsumed: (Long) -> Unit = {},
) {
    val state by container.startupMigrationCoordinator.state.collectAsState()
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
            Text("正在准备账本…", modifier = Modifier.padding(top = 16.dp))
        }

        StartupMigrationState.Ready -> MoneyNavGraph(
            container = container,
            shortcutAction = shortcutAction,
            sharedAmount = sharedAmount,
            notificationLaunchRequest = notificationLaunchRequest,
            onNotificationLaunchConsumed = onNotificationLaunchConsumed,
        )

        is StartupMigrationState.RecoverableError -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("账本迁移需要处理")
            Text(current.diagnostic, modifier = Modifier.padding(vertical = 16.dp))
            recoveryActionError?.let { Text(it, modifier = Modifier.padding(bottom = 8.dp)) }
            Button(onClick = { scope.launch { container.startupMigrationCoordinator.retry() } }) {
                Text("重试")
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
                    Text("保留当前账本并忽略旧文件")
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
                                        Intent.createChooser(shareIntent, "保存旧账本源文件"),
                                    )
                                }
                                .onFailure { error ->
                                    recoveryActionError = error.message ?: "旧账本源文件导出失败"
                                }
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("导出旧账本源文件")
                }
            }
            if (StartupRecoveryAction.RESET_LOCAL_SETTINGS in current.actions) {
                Button(
                    onClick = { showResetConfirmation = true },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("重置损坏的本地设置")
                }
            }
            if (showResetConfirmation) {
                AlertDialog(
                    onDismissRequest = { showResetConfirmation = false },
                    title = { Text("确认重置本地设置") },
                    text = {
                        Text(
                            "仅重置检测为损坏的本机显示、隐私、历史筛选或旧提醒设置，" +
                                "不会删除账户和账本记录。",
                        )
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
                            Text("确认重置并继续")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showResetConfirmation = false }) {
                            Text("取消")
                        }
                    },
                )
            }
        }
    }
}
