package com.shihuaidexianyu.money.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppLockScreen(
    state: AppLockState,
    onAuthenticate: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            AppLockState.Loading -> {
                CircularProgressIndicator()
                Text("正在检查应用锁…", modifier = Modifier.padding(top = 16.dp))
            }
            AppLockState.Locked -> {
                Text("账本已锁定")
                Button(onClick = onAuthenticate, modifier = Modifier.padding(top = 16.dp)) {
                    Text("验证身份")
                }
            }
            AppLockState.Authenticating -> {
                CircularProgressIndicator()
                Text("正在验证身份…", modifier = Modifier.padding(top = 16.dp))
            }
            is AppLockState.Unavailable -> {
                Text(state.reason.userMessage())
                Button(onClick = onOpenSecuritySettings, modifier = Modifier.padding(top = 16.dp)) {
                    Text("打开系统安全设置")
                }
                Button(onClick = onAuthenticate, modifier = Modifier.padding(top = 8.dp)) {
                    Text("重试")
                }
            }
            AppLockState.Unlocked -> Unit
        }
    }
}

private fun AppLockUnavailableReason.userMessage(): String = when (this) {
    AppLockUnavailableReason.NO_HARDWARE -> "设备不支持当前身份验证方式，账本保持锁定"
    AppLockUnavailableReason.NOT_ENROLLED -> "请先在系统设置中录入屏幕锁或生物识别"
    AppLockUnavailableReason.TEMPORARILY_UNAVAILABLE -> "身份验证暂时不可用，账本保持锁定"
    AppLockUnavailableReason.PREFERENCES_UNAVAILABLE -> "无法读取应用锁设置，账本保持锁定"
}
