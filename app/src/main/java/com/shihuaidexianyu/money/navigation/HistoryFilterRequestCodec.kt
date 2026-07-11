package com.shihuaidexianyu.money.navigation

import com.shihuaidexianyu.money.domain.model.HistoryAmountDirection
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.requireValidAmountBounds
import java.util.UUID

internal object HistoryFilterRequestCodec {
    private val allowedKeys = setOf(
        "keyword",
        "exclude",
        "types",
        "accountId",
        "transferFrom",
        "transferTo",
        "start",
        "end",
        "min",
        "max",
        "direction",
    )

    fun encode(filters: HistoryRecordFilters): String = buildList {
        if (filters.keyword.isNotEmpty()) add("keyword=${NavigationQueryCodec.encode(filters.keyword)}")
        if (filters.excludeKeyword.isNotEmpty()) add("exclude=${NavigationQueryCodec.encode(filters.excludeKeyword)}")
        if (filters.recordTypes.isNotEmpty()) {
            val types = HistoryRecordType.entries.filter(filters.recordTypes::contains).joinToString(",") { it.name }
            add("types=$types")
        }
        filters.accountId?.let { add("accountId=$it") }
        filters.transferFromAccountId?.let { add("transferFrom=$it") }
        filters.transferToAccountId?.let { add("transferTo=$it") }
        filters.dateStartAt?.let { add("start=$it") }
        filters.dateEndAt?.let { add("end=$it") }
        filters.minAmount?.let { add("min=$it") }
        filters.maxAmount?.let { add("max=$it") }
        if (filters.amountDirection != HistoryAmountDirection.ALL) add("direction=${filters.amountDirection.name}")
    }.joinToString("&")

    fun decode(query: String): HistoryRecordFilters {
        if (query.isBlank()) return HistoryRecordFilters()
        val values = buildMap {
            query.split('&').forEach { part ->
                val separator = part.indexOf('=')
                require(separator > 0) { "Malformed history filter entry" }
                val key = part.substring(0, separator)
                require(key in allowedKeys) { "Unknown history filter key: $key" }
                require(!containsKey(key)) { "Duplicate history filter key: $key" }
                put(key, part.substring(separator + 1))
            }
        }
        val recordTypes = values["types"]?.let { encodedTypes ->
            require(encodedTypes.isNotBlank()) { "History record types cannot be empty" }
            encodedTypes.split(',').mapTo(linkedSetOf()) { value ->
                HistoryRecordType.entries.firstOrNull { it.name == value }
                    ?: throw IllegalArgumentException("Unknown history record type: $value")
            }
        }.orEmpty()
        val direction = values["direction"]?.let { value ->
            HistoryAmountDirection.entries.firstOrNull { it.name == value }
                ?: throw IllegalArgumentException("Unknown history amount direction: $value")
        } ?: HistoryAmountDirection.ALL
        return HistoryRecordFilters(
            keyword = values["keyword"]?.let(NavigationQueryCodec::decode).orEmpty(),
            excludeKeyword = values["exclude"]?.let(NavigationQueryCodec::decode).orEmpty(),
            recordTypes = recordTypes,
            accountId = values.strictLongOrNull("accountId"),
            transferFromAccountId = values.strictLongOrNull("transferFrom"),
            transferToAccountId = values.strictLongOrNull("transferTo"),
            dateStartAt = values.strictLongOrNull("start"),
            dateEndAt = values.strictLongOrNull("end"),
            minAmount = values.strictLongOrNull("min"),
            maxAmount = values.strictLongOrNull("max"),
            amountDirection = direction,
        ).also { filters ->
            filters.requireValidAmountBounds()
            require(filters.accountId == null || filters.accountId > 0L) { "History account id must be positive" }
            require(filters.transferFromAccountId == null || filters.transferFromAccountId > 0L) {
                "History transfer source id must be positive"
            }
            require(filters.transferToAccountId == null || filters.transferToAccountId > 0L) {
                "History transfer destination id must be positive"
            }
            require(
                filters.transferFromAccountId == null || filters.transferToAccountId == null ||
                    filters.transferFromAccountId != filters.transferToAccountId,
            ) { "History transfer path cannot target the same account" }
            require(filters.dateStartAt == null || filters.dateStartAt >= 0L) { "History start must be non-negative" }
            require(filters.dateEndAt == null || filters.dateEndAt >= 0L) { "History end must be non-negative" }
            require(
                filters.dateStartAt == null || filters.dateEndAt == null || filters.dateStartAt < filters.dateEndAt,
            ) { "History date range must be non-empty and ordered" }
        }
    }

    private fun Map<String, String>.strictLongOrNull(key: String): Long? {
        val raw = this[key] ?: return null
        return raw.toLongOrNull() ?: throw IllegalArgumentException("Invalid long history filter: $key")
    }
}

internal object HistoryFilterNavigationRequest {
    fun create(filters: HistoryRecordFilters, token: String = UUID.randomUUID().toString()): String {
        require(token.isNotBlank() && ':' !in token) { "Invalid history filter request token" }
        return "$token:${HistoryFilterRequestCodec.encode(filters)}"
    }

    fun decode(request: String): HistoryRecordFilters {
        val separator = request.indexOf(':')
        require(separator > 0) { "Malformed history filter navigation request" }
        return HistoryFilterRequestCodec.decode(request.substring(separator + 1))
    }
}
