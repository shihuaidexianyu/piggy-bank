package com.shihuaidexianyu.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.shihuaidexianyu.money.navigation.MoneyNavGraph

@Composable
fun MoneyApp(
    container: MoneyAppContainer,
    shortcutAction: String? = null,
    sharedAmount: Long? = null,
) {
    MoneyNavGraph(
        container = container,
        shortcutAction = shortcutAction,
        sharedAmount = sharedAmount,
    )
}
