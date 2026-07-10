package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.PortableSettings
import kotlinx.coroutines.flow.Flow

interface PortableSettingsRepository {
    fun observe(): Flow<PortableSettings>
    suspend fun query(): PortableSettings
    suspend fun updateCurrencySymbol(symbol: String)
    suspend fun updateAmountColorMode(mode: AmountColorMode)
    suspend fun updateMonthlyBudgetAmount(amount: Long?)
    suspend fun replace(settings: PortableSettings)
}
