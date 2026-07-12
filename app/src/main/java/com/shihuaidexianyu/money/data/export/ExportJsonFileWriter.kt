package com.shihuaidexianyu.money.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import com.shihuaidexianyu.money.domain.repository.BackupJsonEncoder
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExportShareFile(
    val uri: Uri,
    val fileName: String,
    val mimeType: String = "application/json",
)

class ExportJsonFileWriter(
    private val context: Context,
    private val zoneIdProvider: ZoneIdProvider,
    private val backupJsonEncoder: BackupJsonEncoder,
    private val randomSuffix: () -> String = ::secureSuffix,
) {
    suspend fun write(snapshot: MoneyBackupSnapshot, timestamp: Long): ExportShareFile = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, EXPORT_DIR_NAME).apply { mkdirs() }
        require(exportDir.isDirectory) { "无法创建导出目录" }
        val dateText = FILE_TIME_FORMATTER.format(
            Instant.ofEpochMilli(timestamp).atZone(zoneIdProvider.zoneId()),
        )
        val exportFile = generateSequence {
            File(exportDir, "money-export-$dateText-${randomSuffix()}.json")
        }.first { it.createNewFile() }
        FileOutputStream(exportFile).use { output ->
            backupJsonEncoder.encodeToStream(snapshot, output)
            output.fd.sync()
        }
        ExportShareFile(
            uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile,
            ),
            fileName = exportFile.name,
        )
    }

    private companion object {
        const val EXPORT_DIR_NAME = "exports"
        val FILE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(
            "yyyyMMdd-HHmmss-SSS",
            Locale.ROOT,
        )
    }
}

private fun secureSuffix(): String = ByteArray(6).also(SecureRandom()::nextBytes)
    .joinToString(separator = "") { "%02x".format(it) }
