package com.shihuaidexianyu.money.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android URI adapter. All subsequent preview/confirm reads are by opaque stage id. */
class BackupFileReader(
    private val context: Context,
    private val stagedBackupStore: StagedBackupStore,
) {
    suspend fun stage(uri: Uri, now: Long): StagedBackupHandle = withContext(Dispatchers.IO) {
        stagedBackupStore.cleanupExpired(now)
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取所选文件" }
        input.use { stagedBackupStore.stage(it, now) }
    }
}
