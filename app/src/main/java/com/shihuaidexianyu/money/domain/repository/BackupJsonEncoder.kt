package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import java.io.OutputStream

/**
 * Port for serializing a [MoneyBackupSnapshot] to a JSON string.
 * Implemented in the data layer; consumed by domain use cases to
 * keep `domain/` free of `data.*` imports.
 */
fun interface BackupJsonEncoder {
    fun encode(snapshot: MoneyBackupSnapshot): String

    fun encodeToStream(snapshot: MoneyBackupSnapshot, output: OutputStream) {
        output.writer(Charsets.UTF_8).apply {
            write(encode(snapshot))
            flush()
        }
    }
}
