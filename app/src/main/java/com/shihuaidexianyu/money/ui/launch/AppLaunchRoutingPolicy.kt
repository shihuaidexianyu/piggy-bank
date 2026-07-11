package com.shihuaidexianyu.money.ui.launch

import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import com.shihuaidexianyu.money.domain.launch.AppLaunchRequest
import com.shihuaidexianyu.money.ui.lock.AppLockState

fun pendingRequestForRouting(
    startupState: StartupMigrationState,
    lockState: AppLockState,
    request: AppLaunchRequest?,
): AppLaunchRequest? = request?.takeIf {
    startupState == StartupMigrationState.Ready && lockState == AppLockState.Unlocked
}
