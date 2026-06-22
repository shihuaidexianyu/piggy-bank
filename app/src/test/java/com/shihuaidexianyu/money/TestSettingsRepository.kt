package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemorySettingsRepository
import com.shihuaidexianyu.money.domain.model.AppSettings

/**
 * Type alias for backward compatibility. Existing tests that imported `TestSettingsRepository`
 * will keep working; new tests should use [InMemorySettingsRepository] directly.
 */
typealias TestSettingsRepository = InMemorySettingsRepository

/**
 * Factory for [TestSettingsRepository] preserves the original constructor signature
 * `TestSettingsRepository(initial: AppSettings = AppSettings())`.
 */
fun testSettingsRepository(initial: AppSettings = AppSettings()): TestSettingsRepository =
    InMemorySettingsRepository(initial)
