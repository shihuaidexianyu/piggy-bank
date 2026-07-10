package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.migration.LegacySourceRecoveryExporter
import kotlin.test.assertTrue
import org.junit.Test

class LegacySourceRecoveryExporterContractTest {
    @Test
    fun `exporter exposes URI creation inside its injectable cleanup boundary`() {
        assertTrue(
            LegacySourceRecoveryExporter::class.java.declaredConstructors
                .any { it.parameterCount == 5 },
        )
    }
}
