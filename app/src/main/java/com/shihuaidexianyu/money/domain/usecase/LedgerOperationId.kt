package com.shihuaidexianyu.money.domain.usecase

import java.util.UUID

fun interface LedgerOperationIdFactory {
    fun create(): String
}

object UuidLedgerOperationIdFactory : LedgerOperationIdFactory {
    override fun create(): String = UUID.randomUUID().toString()
}

internal fun savedOperationId(
    existing: String?,
    factory: LedgerOperationIdFactory,
): String {
    if (existing != null) {
        require(existing.isNotBlank()) { "已保存的操作标识不能为空" }
        return existing
    }
    return factory.create().also { generated ->
        require(generated.isNotBlank()) { "生成的操作标识不能为空" }
    }
}
