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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R

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
                Text(stringResource(R.string.lock_checking), modifier = Modifier.padding(top = 16.dp))
            }
            AppLockState.Locked -> {
                Text(stringResource(R.string.lock_locked))
                Button(onClick = onAuthenticate, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.lock_authenticate))
                }
            }
            AppLockState.Authenticating -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.lock_authenticating), modifier = Modifier.padding(top = 16.dp))
            }
            is AppLockState.Unavailable -> {
                Text(stringResource(state.reason.messageRes()))
                Button(onClick = onOpenSecuritySettings, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.lock_open_security_settings))
                }
                Button(onClick = onAuthenticate, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            AppLockState.Unlocked -> Unit
        }
    }
}

private fun AppLockUnavailableReason.messageRes(): Int = when (this) {
    AppLockUnavailableReason.NO_HARDWARE -> R.string.lock_no_hardware
    AppLockUnavailableReason.NOT_ENROLLED -> R.string.lock_not_enrolled
    AppLockUnavailableReason.TEMPORARILY_UNAVAILABLE -> R.string.lock_temporarily_unavailable
    AppLockUnavailableReason.PREFERENCES_UNAVAILABLE -> R.string.lock_preferences_unavailable
}
