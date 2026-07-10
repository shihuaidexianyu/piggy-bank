package com.shihuaidexianyu.money

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import com.shihuaidexianyu.money.navigation.MoneyNavGraph
import kotlinx.coroutines.launch

@Composable
fun MoneyApp(
    container: MoneyAppContainer,
    shortcutAction: String? = null,
    sharedAmount: Long? = null,
) {
    val state by container.startupMigrationCoordinator.state.collectAsState()
    val scope = rememberCoroutineScope()
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
        )

        is StartupMigrationState.RecoverableError -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("账本迁移需要处理")
            Text(current.diagnostic, modifier = Modifier.padding(vertical = 16.dp))
            Button(onClick = { scope.launch { container.startupMigrationCoordinator.retry() } }) {
                Text("重试")
            }
            if (com.shihuaidexianyu.money.data.migration.StartupRecoveryAction.USE_CURRENT_DATABASE in current.actions) {
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
        }
    }
}
