package com.shihuaidexianyu.money.data.repository

import androidx.room.withTransaction
import com.shihuaidexianyu.money.data.dao.PortableSettingsDao
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.entity.PortableSettingsEntity
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.normalizeCurrencySymbol
import com.shihuaidexianyu.money.domain.model.requireValidMonthlyBudget
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PortableSettingsRepositoryImpl(
    private val database: MoneyDatabase,
    private val dao: PortableSettingsDao,
) : PortableSettingsRepository {
    override fun observe(): Flow<PortableSettings> =
        dao.observe().map { it?.toDomain() ?: PortableSettings() }

    override suspend fun query(): PortableSettings = dao.query()?.toDomain() ?: PortableSettings()

    override suspend fun updateCurrencySymbol(symbol: String) = mutate {
        copy(currencySymbol = normalizeCurrencySymbol(symbol))
    }

    override suspend fun updateAmountColorMode(mode: AmountColorMode) = mutate {
        copy(amountColorMode = mode)
    }

    override suspend fun updateMonthlyBudgetAmount(amount: Long?) {
        requireValidMonthlyBudget(amount)
        mutate { copy(monthlyBudgetAmount = amount) }
    }

    override suspend fun replace(settings: PortableSettings) {
        val normalized = settings.normalized()
        dao.upsert(normalized.toEntity())
    }

    private suspend inline fun mutate(crossinline block: PortableSettings.() -> PortableSettings) {
        database.withTransaction {
            val current = dao.query()?.toDomain() ?: PortableSettings()
            dao.upsert(current.block().normalized().toEntity())
        }
    }
}

private fun PortableSettings.normalized(): PortableSettings {
    requireValidMonthlyBudget(monthlyBudgetAmount)
    return copy(currencySymbol = normalizeCurrencySymbol(currencySymbol))
}

private fun PortableSettingsEntity.toDomain(): PortableSettings = PortableSettings(
    currencySymbol = normalizeCurrencySymbol(currencySymbol),
    amountColorMode = AmountColorMode.fromValue(amountColorMode),
    monthlyBudgetAmount = monthlyBudgetAmount?.takeIf { it > 0L },
)

private fun PortableSettings.toEntity(): PortableSettingsEntity = PortableSettingsEntity(
    id = 1,
    currencySymbol = currencySymbol,
    amountColorMode = amountColorMode.value,
    monthlyBudgetAmount = monthlyBudgetAmount,
)
