package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.domain.model.PortableSettings

/**
 * Type alias for backward compatibility. Existing tests that imported `TestSettingsRepository`
 * will keep working; new tests should use [InMemoryPortableSettingsRepository] directly.
 */
typealias TestSettingsRepository = InMemoryPortableSettingsRepository

/**
 * Factory for the portable-settings test repository.
 */
fun testSettingsRepository(initial: PortableSettings = PortableSettings()): TestSettingsRepository =
    InMemoryPortableSettingsRepository(initial)
