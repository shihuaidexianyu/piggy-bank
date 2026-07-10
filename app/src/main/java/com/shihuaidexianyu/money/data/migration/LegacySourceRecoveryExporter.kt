package com.shihuaidexianyu.money.data.migration

import android.content.Context
import androidx.core.content.FileProvider
import com.shihuaidexianyu.money.domain.time.ClockProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LegacySourceRecoveryExporter(
    private val context: Context,
    private val sourceFile: File,
    private val clockProvider: ClockProvider,
    private val copySource: (source: File, destination: File) -> Unit = { source, destination ->
        source.inputStream().use { input ->
            destination.outputStream().use(input::copyTo)
        }
    },
) {
    suspend fun export(): LegacySourceExport = withContext(Dispatchers.IO) {
        require(sourceFile.isFile) { "未找到可导出的旧账本源文件" }
        val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
        check(exportDirectory.isDirectory) { "无法创建旧账本导出目录" }
        val randomSuffix = UUID.randomUUID().toString().replace("-", "").take(12)
        val fileName = "legacy-money-store-${clockProvider.nowMillis()}-$randomSuffix.json"
        val destination = File(exportDirectory, fileName)
        try {
            copySource(sourceFile, destination)
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
        LegacySourceExport(
            contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destination,
            ).toString(),
            fileName = fileName,
        )
    }
}
