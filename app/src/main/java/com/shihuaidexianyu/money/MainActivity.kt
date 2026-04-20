package com.shihuaidexianyu.money

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shihuaidexianyu.money.ui.theme.MoneyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MoneyApplication).container
        setContent {
            val settings = container.settingsRepository.observeSettings().collectAsStateWithLifecycle(
                initialValue = com.shihuaidexianyu.money.domain.model.AppSettings(),
            )
            MoneyTheme(
                themeMode = settings.value.themeMode,
                amountColorMode = settings.value.amountColorMode,
            ) {
                MoneyApp(container = container)
            }
        }
    }
}
