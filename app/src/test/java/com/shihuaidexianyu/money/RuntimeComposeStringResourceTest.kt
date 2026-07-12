package com.shihuaidexianyu.money

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

class RuntimeComposeStringResourceTest {
    @Test
    fun `runtime compose copy is stored in Android resources`() {
        val sourceRoot = File("src/main/java/com/shihuaidexianyu/money")
        val uiFiles = File(sourceRoot, "ui").walkTopDown()
            .filter(File::isFile)
            .filter { file ->
                file.name != "ComponentPreviews.kt" &&
                    listOf("Screen.kt", "Components.kt", "Dialog.kt").any(file.name::endsWith)
            }
        val navigationFiles = sequenceOf(
            File(sourceRoot, "navigation/MoneyNavGraph.kt"),
            File(sourceRoot, "navigation/AdaptiveTopLevelNavigation.kt"),
        )
        val indirectRuntimeCopy = sequenceOf(
            File(sourceRoot, "ui/common/AsyncContent.kt"),
            File(sourceRoot, "ui/history/HistoryViewModel.kt"),
        )
        val widgetFiles = File(sourceRoot, "widget").walkTopDown().filter(File::isFile)
        val chineseString = Regex("\"[^\"\\r\\n]*[\\p{IsHan}][^\"\\r\\n]*\"")
        val violations = (uiFiles + navigationFiles + indirectRuntimeCopy + widgetFiles)
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//")
                    if ("require(" !in code && chineseString.containsMatchIn(code)) {
                        "${file.relativeTo(sourceRoot)}:${index + 1}: ${code.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertTrue(
            violations.isEmpty(),
            "运行时 Compose 中文文案必须使用 strings.xml/plurals.xml：\n${violations.joinToString("\n")}",
        )
    }
}
