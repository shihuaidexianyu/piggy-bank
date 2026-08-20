package com.shihuaidexianyu.money

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

/**
 * WHY THIS GUARD EXISTS: all user-facing copy must live in strings.xml/plurals.xml so it stays
 * greppable and consistent (AGENTS.md: UI text is Chinese, code is English). A hardcoded Chinese
 * literal in runtime Compose code silently bypasses that. This regex scan is a proxy, not a
 * compiler: it strips `//` comments but NOT KDoc, so a Chinese example inside a doc comment will
 * false-positive — reword the doc, don't weaken the scan. If this fires on a genuine new file
 * category (e.g. a new naming suffix), extend the file filters rather than inlining the string.
 */
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
        val chineseString = Regex("\"[^\"\\r\\n]*[\\p{IsHan}][^\"\\r\\n]*\"")
        val violations = (uiFiles + navigationFiles + indirectRuntimeCopy)
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
