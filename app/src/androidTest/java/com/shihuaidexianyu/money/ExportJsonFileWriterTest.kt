package com.shihuaidexianyu.money

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shihuaidexianyu.money.data.export.ExportJsonFileWriter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportJsonFileWriterTest {
    @Test
    fun writeCreatesShareableFileProviderUri() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val writer = ExportJsonFileWriter(context, { java.time.ZoneId.of("Asia/Shanghai") })
        val result = writer.write(
            json = """{"metadata":{"schemaVersion":1}}""",
            timestamp = 1_700_000_000_000L,
        )
        val second = writer.write(
            json = """{"metadata":{"schemaVersion":1}}""",
            timestamp = 1_700_000_000_000L,
        )

        assertEquals("application/json", result.mimeType)
        assertTrue(result.fileName.startsWith("money-export-"))
        assertTrue(result.fileName.endsWith(".json"))
        assertTrue(result.fileName.matches(Regex("money-export-\\d{8}-\\d{6}-\\d{3}-[0-9a-f]{12}\\.json")))
        assertNotEquals(result.fileName, second.fileName)
        assertEquals("content", result.uri.scheme)
        assertEquals("${context.packageName}.fileprovider", result.uri.authority)

        val exportedText = context.contentResolver
            .openInputStream(result.uri)
            ?.bufferedReader()
            ?.use { it.readText() }
        assertEquals("""{"metadata":{"schemaVersion":1}}""", exportedText)
    }
}
