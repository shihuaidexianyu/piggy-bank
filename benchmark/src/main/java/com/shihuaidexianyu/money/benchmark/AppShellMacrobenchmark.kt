package com.shihuaidexianyu.money.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppShellMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    @Test
    fun openHistoryFirstFrame() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = { startActivityAndWait() },
    ) {
        val history = device.wait(Until.findObject(By.text("明细")), 5_000L)
            ?: error("History destination was not visible")
        history.click()
        device.waitForIdle()
    }

    @Test
    fun homeTenThousandRows() = measureHome(10_000)

    @Test
    fun homeOneHundredThousandRows() = measureHome(100_000)

    @Test
    fun historyTenThousandRows() = measureHistory(10_000)

    @Test
    fun historyOneHundredThousandRows() = measureHistory(100_000)

    private fun measureHome(recordCount: Int) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            seed(recordCount)
            pressHome()
        },
    ) {
        startActivityAndWait()
        device.waitForIdle()
    }

    private fun measureHistory(recordCount: Int) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = {
            seed(recordCount)
            startActivityAndWait()
        },
    ) {
        val history = device.wait(Until.findObject(By.text("明细")), 5_000L)
            ?: error("History destination was not visible")
        history.click()
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.seed(recordCount: Int) {
        device.executeShellCommand(
            "am broadcast -a $SEED_ACTION -p $TARGET_PACKAGE --ei record_count $recordCount",
        )
    }

    private companion object {
        const val TARGET_PACKAGE = "com.shihuaidexianyu.money"
        const val SEED_ACTION = "com.shihuaidexianyu.money.SEED_PERFORMANCE_FIXTURE"
    }
}
