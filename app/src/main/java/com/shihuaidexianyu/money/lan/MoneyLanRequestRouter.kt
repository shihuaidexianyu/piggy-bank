package com.shihuaidexianyu.money.lan

import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.domain.model.AiLedgerRecordSnapshot
import com.shihuaidexianyu.money.domain.model.AiMutationJournalEntry
import com.shihuaidexianyu.money.domain.model.AiUndoBatchItem
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.HistoryAmountDirection
import com.shihuaidexianyu.money.domain.model.HistoryBusinessSemantic
import com.shihuaidexianyu.money.domain.model.HistoryPageCursor
import com.shihuaidexianyu.money.domain.model.HistoryRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.model.UndoLatestAiMutationResult
import com.shihuaidexianyu.money.domain.model.sync.NotePatch
import com.shihuaidexianyu.money.domain.model.sync.PatchResult
import com.shihuaidexianyu.money.domain.model.sync.SyncMirrorJson
import com.shihuaidexianyu.money.domain.usecase.AiCreateCashFlowCommand
import com.shihuaidexianyu.money.domain.usecase.AiCreateTransferCommand
import com.shihuaidexianyu.money.domain.usecase.AiMutationIdentity
import com.shihuaidexianyu.money.domain.usecase.AiUpdateCashFlowCommand
import com.shihuaidexianyu.money.domain.usecase.AiUpdateTransferCommand
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

class MoneyLanRequestRouter(
    private val container: MoneyAppContainer,
    private val writeRateLimiter: MoneyLanWriteRateLimiter = MoneyLanWriteRateLimiter(),
) {
    suspend fun route(request: MoneyLanRequest, client: MoneyLanClient): JsonElement {
        if (!container.startupMigrationCoordinator.isReady) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.LEDGER_NOT_READY, "账本仍在完成启动迁移，请稍后重试")
        }
        if (request.action in writeActions && !client.allowWrite) {
            throw MoneyLanProtocolException(MoneyLanErrorCodes.WRITE_DISABLED, "本次手机会话未允许 AI 修改账目")
        }
        return when (request.action) {
            "service.context" -> serviceContext(client)
            "accounts.list" -> listAccounts(decode(request))
            "records.list" -> listRecords(decode(request))
            "records.list.detailed" -> listRecordsDetailed(decode(request))
            "records.get" -> getRecord(decode(request))
            "ledger.summary" -> ledgerSummary(decode(request))
            "journal.list" -> listJournal(decode(request))
            "journal.undo_latest" -> undoLatest(request.requestId)
            "cashflow.create" -> createCashFlow(request, client, decode(request))
            "cashflow.update" -> updateCashFlow(request, client, decode(request))
            "cashflow.delete" -> deleteCashFlow(request, client, decode(request))
            "transfer.create" -> createTransfer(request, client, decode(request))
            "transfer.update" -> updateTransfer(request, client, decode(request))
            "transfer.delete" -> deleteTransfer(request, client, decode(request))
            "sync.state" -> syncState()
            "sync.snapshot" -> syncSnapshot(decode(request))
            "sync.pull" -> syncPull(decode(request))
            "sync.push" -> syncPush(request, client, decode(request))
            else -> throw MoneyLanProtocolException(
                MoneyLanErrorCodes.UNKNOWN_ACTION,
                "不支持的 action：${request.action}",
            )
        }
    }

    private fun serviceContext(client: MoneyLanClient): JsonElement = protocolJson.encodeToJsonElement(
        ServiceContextResult(
            timeZone = ZoneId.systemDefault().id,
            currencyScale = 2,
            amountUnit = "minor_unit",
            allowWrite = client.allowWrite,
            journalPolicy = "strict_lifo",
            supportedWritableRecordKinds = listOf("cash_flow", "transfer"),
        ),
    )

    private suspend fun listAccounts(arguments: AccountsListArguments): JsonElement {
        val accounts = container.accountRepository.queryAllAccounts()
            .filter { arguments.includeClosed || !it.isClosed }
            .sortedWith(compareBy({ it.isClosed }, { it.displayOrder }, { it.id }))
        val balances = container.calculateAccountBalancesUseCase(accounts)
        return protocolJson.encodeToJsonElement(
            AccountsListResult(
                accounts = accounts.map { account ->
                    AccountResult(
                        id = account.id,
                        name = account.name,
                        balance = balances.getValue(account.id).toString(),
                        kind = account.kind.value,
                        hidden = account.isHidden,
                        closed = account.isClosed,
                        createdAt = account.createdAt,
                        updatedActivityAt = account.lastUsedAt,
                    )
                },
            ),
        )
    }

    private suspend fun listRecords(arguments: RecordsListArguments): JsonElement {
        require(arguments.limit in 1..100) { "limit 必须在 1 到 100 之间" }
        val filters = arguments.toFilters()
        val cursor = arguments.cursor?.let {
            HistoryPageCursor(it.occurredAt, it.sourceOrder, it.recordId)
        }
        val records = container.transactionRepository.queryHistoryRecords(filters, cursor, arguments.limit)
        return protocolJson.encodeToJsonElement(
            RecordsListResult(
                records = records.map(HistoryRecord::toResult),
                nextCursor = records.lastOrNull()?.cursor?.let {
                    RecordCursorResult(it.occurredAt, it.sourceOrder, it.recordId)
                },
            ),
        )
    }

    /**
     * records.list.detailed: the records.list projection plus note/updatedAt/deletedAt/operationId
     * from the stored rows. Pages are byte-shrunk towards [DETAILED_PAGE_BYTE_BUDGET] so the
     * response stays comfortably below the 256 KiB frame limit; the cursor always resumes exactly
     * after the last included row.
     */
    private suspend fun listRecordsDetailed(arguments: RecordsListArguments): JsonElement {
        require(arguments.limit in 1..100) { "limit 必须在 1 到 100 之间" }
        val filters = arguments.toFilters()
        val cursor = arguments.cursor?.let {
            HistoryPageCursor(it.occurredAt, it.sourceOrder, it.recordId)
        }
        val records = container.transactionRepository.queryHistoryRecords(filters, cursor, arguments.limit)
        val rows = records.mapNotNull { record ->
            storedDetailsOf(record)?.let { details -> record to record.toDetailedResult(details) }
        }
        var fittingCount = 0
        var bytes = 0
        for ((_, row) in rows) {
            val rowBytes = protocolJson.encodeToString(row).encodeToByteArray().size
            if (fittingCount > 0 && bytes + rowBytes > DETAILED_PAGE_BYTE_BUDGET) break
            bytes += rowBytes
            fittingCount += 1
        }
        val fitting = rows.take(fittingCount)
        return protocolJson.encodeToJsonElement(
            RecordsListDetailedResult(
                records = fitting.map { it.second },
                nextCursor = fitting.lastOrNull()?.first?.cursor?.let {
                    RecordCursorResult(it.occurredAt, it.sourceOrder, it.recordId)
                },
            ),
        )
    }

    private class StoredRecordDetails(
        val note: String?,
        val updatedAt: Long,
        val deletedAt: Long?,
        val operationId: String,
    )

    private suspend fun storedDetailsOf(record: HistoryRecord): StoredRecordDetails? = when (record.type) {
        HistoryRecordType.CASH_FLOW -> container.transactionRepository
            .queryStoredCashFlowRecordById(record.recordId)
            ?.let { StoredRecordDetails(it.note, it.updatedAt, it.deletedAt, it.operationId) }
        HistoryRecordType.TRANSFER -> container.transactionRepository
            .queryStoredTransferRecordById(record.recordId)
            ?.let { StoredRecordDetails(it.note, it.updatedAt, it.deletedAt, it.operationId) }
        HistoryRecordType.BALANCE_UPDATE -> container.transactionRepository
            .queryStoredBalanceUpdateRecordById(record.recordId)
            ?.let { StoredRecordDetails(null, it.updatedAt, it.deletedAt, it.operationId) }
        HistoryRecordType.BALANCE_ADJUSTMENT -> container.transactionRepository
            .queryStoredBalanceAdjustmentRecordById(record.recordId)
            ?.let { StoredRecordDetails(null, it.updatedAt, it.deletedAt, it.operationId) }
    }

    private fun HistoryRecord.toDetailedResult(details: StoredRecordDetails) = DetailedHistoryRecordResult(
        recordId = recordId,
        type = type.name.lowercase(),
        accountId = accountId,
        accountName = accountName,
        relatedAccountId = relatedAccountId,
        relatedAccountName = relatedAccountName,
        title = title,
        amount = amount.toString(),
        occurredAt = occurredAt,
        balanceBefore = balanceBefore?.toString(),
        balanceAfter = balanceAfter?.toString(),
        note = details.note,
        updatedAt = details.updatedAt,
        deletedAt = details.deletedAt,
        operationId = details.operationId,
    )

    private suspend fun syncState(): JsonElement {
        val state = container.getSyncStateUseCase()
        return protocolJson.encodeToJsonElement(
            SyncStateResult(
                datasetId = state.datasetId,
                revision = state.revision,
                minAvailableRevision = state.minAvailableRevision,
                serverTime = state.serverTime,
            ),
        )
    }

    private suspend fun syncSnapshot(arguments: SyncSnapshotArguments): JsonElement {
        val page = container.exportSyncSnapshotPageUseCase(
            expectedDatasetId = arguments.datasetId,
            snapshotRevision = arguments.snapshotRevision,
            cursor = arguments.cursor,
        )
        return protocolJson.encodeToJsonElement(
            SyncSnapshotResult(
                datasetId = page.datasetId,
                snapshotRevision = page.snapshotRevision,
                rows = page.rows.map { it.toWireJson() },
                nextCursor = page.nextCursor,
                done = page.done,
            ),
        )
    }

    private suspend fun syncPull(arguments: SyncPullArguments): JsonElement {
        val page = container.pullSyncChangesUseCase(
            expectedDatasetId = arguments.datasetId,
            afterRevision = arguments.afterRevision,
            limit = arguments.limit,
        )
        return protocolJson.encodeToJsonElement(
            SyncPullResult(
                datasetId = page.datasetId,
                fromRevision = page.fromRevision,
                toRevision = page.toRevision,
                changes = page.changes.map { it.toWireJson() },
                hasMore = page.hasMore,
            ),
        )
    }

    private suspend fun syncPush(
        request: MoneyLanRequest,
        client: MoneyLanClient,
        arguments: SyncPushArguments,
    ): JsonElement {
        // Idempotent replays return the stored batch results without consuming rate budget.
        val stored = container.pushSyncPatchesUseCase.findStoredResults(request.requestId)
        if (stored != null) {
            return protocolJson.encodeToJsonElement(SyncPushResult(stored.map { it.toResult() }))
        }
        writeRateLimiter.chargeSyncPush()
        val result = container.pushSyncPatchesUseCase(
            identity = request.identity(client),
            expectedDatasetId = arguments.datasetId,
            patches = arguments.patches.map { patch ->
                NotePatch(
                    patchId = patch.patchId,
                    entityKind = patch.entityKind,
                    recordId = patch.recordId,
                    expectedUpdatedAt = patch.expectedUpdatedAt,
                    changes = patch.changes,
                )
            },
        )
        return protocolJson.encodeToJsonElement(SyncPushResult(result.results.map { it.toResult() }))
    }

    private fun PatchResult.toResult() = SyncPushPatchResult(
        patchId = patchId,
        status = status.value,
        revision = revision,
        serverUpdatedAt = serverUpdatedAt,
        serverPayload = serverPayloadJson?.let { SyncMirrorJson.parseToJsonElement(it).jsonObject },
        error = errorCode?.let { code -> SyncPushPatchError(code, errorMessage ?: "补丁无效") },
    )

    private suspend fun getRecord(arguments: RecordGetArguments): JsonElement {
        val result = when (arguments.kind.lowercase()) {
            "cash_flow" -> container.transactionRepository
                .queryStoredCashFlowRecordById(arguments.recordId)
                ?.let { record ->
                    StoredRecordResult(
                        kind = "cash_flow",
                        recordId = record.id,
                        accountId = record.accountId,
                        direction = record.direction,
                        amount = record.amount.toString(),
                        note = record.note,
                        occurredAt = record.occurredAt,
                        createdAt = record.createdAt,
                        updatedAt = record.updatedAt,
                        deletedAt = record.deletedAt,
                        operationId = record.operationId,
                    )
                }
            "transfer" -> container.transactionRepository
                .queryStoredTransferRecordById(arguments.recordId)
                ?.let { record ->
                    StoredRecordResult(
                        kind = "transfer",
                        recordId = record.id,
                        fromAccountId = record.fromAccountId,
                        toAccountId = record.toAccountId,
                        amount = record.amount.toString(),
                        note = record.note,
                        occurredAt = record.occurredAt,
                        createdAt = record.createdAt,
                        updatedAt = record.updatedAt,
                        deletedAt = record.deletedAt,
                        operationId = record.operationId,
                    )
                }
            "balance_update" -> container.transactionRepository
                .queryStoredBalanceUpdateRecordById(arguments.recordId)
                ?.let { record ->
                    StoredRecordResult(
                        kind = "balance_update",
                        recordId = record.id,
                        accountId = record.accountId,
                        amount = record.delta.toString(),
                        occurredAt = record.occurredAt,
                        createdAt = record.createdAt,
                        updatedAt = record.updatedAt,
                        deletedAt = record.deletedAt,
                        operationId = record.operationId,
                    )
                }
            "balance_adjustment" -> container.transactionRepository
                .queryStoredBalanceAdjustmentRecordById(arguments.recordId)
                ?.let { record ->
                    StoredRecordResult(
                        kind = "balance_adjustment",
                        recordId = record.id,
                        accountId = record.accountId,
                        amount = record.delta.toString(),
                        occurredAt = record.occurredAt,
                        createdAt = record.createdAt,
                        updatedAt = record.updatedAt,
                        deletedAt = record.deletedAt,
                        operationId = record.operationId,
                    )
                }
            else -> throw MoneyLanProtocolException(MoneyLanErrorCodes.VALIDATION_FAILED, "kind 无效")
        } ?: throw MoneyLanProtocolException(MoneyLanErrorCodes.NOT_FOUND, "未找到指定账本记录")
        return protocolJson.encodeToJsonElement(result)
    }

    private suspend fun ledgerSummary(arguments: LedgerSummaryArguments): JsonElement {
        require(
            arguments.startInclusive == null ||
                arguments.endExclusive == null ||
                arguments.startInclusive < arguments.endExclusive,
        ) { "开始时间必须早于结束时间" }
        val filters = HistoryRecordFilters(
            accountId = arguments.accountId,
            dateStartAt = arguments.startInclusive,
            dateEndAt = arguments.endExclusive,
            businessSemantic = parseBusinessSemantic(arguments.businessSemantic),
        )
        val summary = container.transactionRepository.queryHistoryFilterSummary(filters)
        val count = container.transactionRepository.countHistoryRecords(filters)
        val accounts = container.accountRepository.queryAllAccounts()
        val balances = container.calculateAccountBalancesUseCase(accounts)
        return protocolJson.encodeToJsonElement(
            LedgerSummaryResult(
                recordCount = count,
                cashInflow = summary.cashInflow.toString(),
                cashOutflow = summary.cashOutflow.toString(),
                netChange = summary.netChange.toString(),
                totalAssets = balances.values.fold(0L, Math::addExact).toString(),
                accountBalances = accounts.sortedBy { it.displayOrder }.map { account ->
                    AccountBalanceResult(account.id, account.name, balances.getValue(account.id).toString())
                },
            ),
        )
    }

    private suspend fun listJournal(arguments: JournalListArguments): JsonElement {
        require(arguments.limit in 1..100) { "limit 必须在 1 到 100 之间" }
        val entries = container.aiMutationJournalRepository.queryRecent(arguments.limit)
        return protocolJson.encodeToJsonElement(
            JournalListResult(entries.map(AiMutationJournalEntry::toResult)),
        )
    }

    private suspend fun undoLatest(requestId: String): JsonElement = when (
        val result = container.aiJournaledLedgerUseCase.undoLatest(requestId)
    ) {
        UndoLatestAiMutationResult.Empty -> protocolJson.encodeToJsonElement(
            UndoResult(status = "empty", message = "没有可撤销的 AI 操作"),
        )
        is UndoLatestAiMutationResult.Conflict -> protocolJson.encodeToJsonElement(
            UndoResult(
                status = "conflict",
                message = result.message,
                entry = result.entry.toResult(),
            ),
        )
        is UndoLatestAiMutationResult.Undone -> protocolJson.encodeToJsonElement(
            UndoResult(
                status = "undone",
                message = if (result.replayed) "该撤销请求已经完成" else "已撤销最近一步 AI 操作",
                entry = result.entry.toResult(),
                replayed = result.replayed,
            ),
        )
        is UndoLatestAiMutationResult.BatchUndone -> protocolJson.encodeToJsonElement(
            UndoResult(
                status = "undone",
                message = if (result.replayed) "该撤销请求已经完成" else "已撤销最近一次 AI 批量操作",
                entry = result.entry.toResult(),
                replayed = result.replayed,
                entryType = result.entry.entryType.value,
                items = result.items.map(AiUndoBatchItem::toResult),
            ),
        )
        is UndoLatestAiMutationResult.BatchConflict -> protocolJson.encodeToJsonElement(
            UndoResult(
                status = "conflict",
                message = result.message,
                entry = result.entry.toResult(),
                entryType = result.entry.entryType.value,
                items = result.items.map(AiUndoBatchItem::toResult),
            ),
        )
    }

    private suspend fun createCashFlow(
        request: MoneyLanRequest,
        client: MoneyLanClient,
        arguments: CreateCashFlowArguments,
    ): JsonElement {
        val receipt = container.aiJournaledLedgerUseCase.createCashFlow(
            AiCreateCashFlowCommand(
                identity = request.identity(client),
                accountId = arguments.accountId,
                direction = parseDirection(arguments.direction),
                amount = parsePositiveAmount(arguments.amount),
                note = arguments.note,
                occurredAt = arguments.occurredAt,
            ),
        )
        return receiptResult(receipt.entry, receipt.replayed)
    }

    private suspend fun updateCashFlow(
        request: MoneyLanRequest,
        client: MoneyLanClient,
        arguments: UpdateCashFlowArguments,
    ): JsonElement {
        val receipt = container.aiJournaledLedgerUseCase.updateCashFlow(
            AiUpdateCashFlowCommand(
                identity = request.identity(client),
                recordId = arguments.recordId,
                accountId = arguments.accountId,
                direction = parseDirection(arguments.direction),
                amount = parsePositiveAmount(arguments.amount),
                note = arguments.note,
                occurredAt = arguments.occurredAt,
                expectedUpdatedAt = arguments.expectedUpdatedAt,
            ),
        )
        return receiptResult(receipt.entry, receipt.replayed)
    }

    private suspend fun deleteCashFlow(
        request: MoneyLanRequest,
        client: MoneyLanClient,
        arguments: DeleteRecordArguments,
    ): JsonElement {
        val receipt = container.aiJournaledLedgerUseCase.deleteCashFlow(
            identity = request.identity(client),
            recordId = arguments.recordId,
            expectedUpdatedAt = arguments.expectedUpdatedAt,
        )
        return receiptResult(receipt.entry, receipt.replayed)
    }

    private suspend fun createTransfer(
        request: MoneyLanRequest,
        client: MoneyLanClient,
        arguments: CreateTransferArguments,
    ): JsonElement {
        val receipt = container.aiJournaledLedgerUseCase.createTransfer(
            AiCreateTransferCommand(
                identity = request.identity(client),
                fromAccountId = arguments.fromAccountId,
                toAccountId = arguments.toAccountId,
                amount = parsePositiveAmount(arguments.amount),
                note = arguments.note,
                occurredAt = arguments.occurredAt,
            ),
        )
        return receiptResult(receipt.entry, receipt.replayed)
    }

    private suspend fun updateTransfer(
        request: MoneyLanRequest,
        client: MoneyLanClient,
        arguments: UpdateTransferArguments,
    ): JsonElement {
        val receipt = container.aiJournaledLedgerUseCase.updateTransfer(
            AiUpdateTransferCommand(
                identity = request.identity(client),
                recordId = arguments.recordId,
                fromAccountId = arguments.fromAccountId,
                toAccountId = arguments.toAccountId,
                amount = parsePositiveAmount(arguments.amount),
                note = arguments.note,
                occurredAt = arguments.occurredAt,
                expectedUpdatedAt = arguments.expectedUpdatedAt,
            ),
        )
        return receiptResult(receipt.entry, receipt.replayed)
    }

    private suspend fun deleteTransfer(
        request: MoneyLanRequest,
        client: MoneyLanClient,
        arguments: DeleteRecordArguments,
    ): JsonElement {
        val receipt = container.aiJournaledLedgerUseCase.deleteTransfer(
            identity = request.identity(client),
            recordId = arguments.recordId,
            expectedUpdatedAt = arguments.expectedUpdatedAt,
        )
        return receiptResult(receipt.entry, receipt.replayed)
    }

    private fun receiptResult(entry: AiMutationJournalEntry, replayed: Boolean): JsonElement =
        protocolJson.encodeToJsonElement(
            MutationResult(
                journal = entry.toResult(),
                record = protocolJson.decodeFromString(requireNotNull(entry.afterSnapshotJson)),
                replayed = replayed,
            ),
        )

    private inline fun <reified T> decode(request: MoneyLanRequest): T = try {
        protocolJson.decodeFromJsonElement(request.arguments)
    } catch (error: IllegalArgumentException) {
        throw MoneyLanProtocolException(MoneyLanErrorCodes.VALIDATION_FAILED, error.message ?: "请求参数无效")
    }

    private fun RecordsListArguments.toFilters(): HistoryRecordFilters = HistoryRecordFilters(
        keyword = keyword,
        recordTypes = recordTypes.map { value ->
            runCatching { HistoryRecordType.valueOf(value.uppercase()) }
                .getOrElse {
                    throw MoneyLanProtocolException(
                        MoneyLanErrorCodes.VALIDATION_FAILED,
                        "recordTypes 包含无效值：$value",
                    )
                }
        }.toSet(),
        accountId = accountId,
        dateStartAt = startInclusive,
        dateEndAt = endExclusive,
        minAmount = minAmount?.let(::parseNonNegativeAmount),
        maxAmount = maxAmount?.let(::parseNonNegativeAmount),
        amountDirection = runCatching { HistoryAmountDirection.valueOf(amountDirection.uppercase()) }
            .getOrElse {
                throw MoneyLanProtocolException(MoneyLanErrorCodes.VALIDATION_FAILED, "amountDirection 无效")
            },
        businessSemantic = parseBusinessSemantic(businessSemantic),
    )

    private fun parseBusinessSemantic(value: String): HistoryBusinessSemantic =
        HistoryBusinessSemantic.entries.firstOrNull { it.value == value.lowercase() }
            ?: throw MoneyLanProtocolException(
                MoneyLanErrorCodes.VALIDATION_FAILED,
                "businessSemantic 必须是 all、daily_expense、investment_pnl、investment_gain 或 investment_loss",
            )

    private fun parseDirection(value: String): CashFlowDirection =
        CashFlowDirection.entries.firstOrNull { it.value == value.lowercase() }
            ?: throw MoneyLanProtocolException(
                MoneyLanErrorCodes.VALIDATION_FAILED,
                "direction 必须是 inflow 或 outflow",
            )

    private fun parsePositiveAmount(value: String): Long = parseNonNegativeAmount(value).also {
        require(it > 0L) { "amount 必须大于 0" }
    }

    private fun parseNonNegativeAmount(value: String): Long = value.toLongOrNull()?.takeIf { it >= 0L }
        ?: throw MoneyLanProtocolException(
            MoneyLanErrorCodes.VALIDATION_FAILED,
            "金额必须是非负的最小货币单位整数字符串",
        )

    private fun MoneyLanRequest.identity(client: MoneyLanClient) = AiMutationIdentity(
        requestId = requestId,
        sessionId = client.sessionId,
        clientName = client.name,
    )

    private companion object {
        /** records.list.detailed page target; the outer frame limit is 256 KiB. */
        const val DETAILED_PAGE_BYTE_BUDGET = 192 * 1024

        val protocolJson = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
            encodeDefaults = true
        }

        // sync.push is a write for the WRITE_DISABLED gate; its rate budget is charged after the
        // replay check inside the syncPush handler, not by the server's pre-routing pass.
        val writeActions = setOf(
            "journal.undo_latest",
            "cashflow.create",
            "cashflow.update",
            "cashflow.delete",
            "transfer.create",
            "transfer.update",
            "transfer.delete",
            "sync.push",
        )
    }
}

@Serializable
private data class ServiceContextResult(
    val timeZone: String,
    val currencyScale: Int,
    val amountUnit: String,
    val allowWrite: Boolean,
    val journalPolicy: String,
    val supportedWritableRecordKinds: List<String>,
)

@Serializable
private data class AccountsListArguments(val includeClosed: Boolean = false)

@Serializable
private data class AccountResult(
    val id: Long,
    val name: String,
    val balance: String,
    val kind: String,
    val hidden: Boolean,
    val closed: Boolean,
    val createdAt: Long,
    val updatedActivityAt: Long?,
)

@Serializable
private data class AccountsListResult(val accounts: List<AccountResult>)

@Serializable
private data class RecordsListArguments(
    val keyword: String = "",
    val recordTypes: List<String> = emptyList(),
    val accountId: Long? = null,
    val startInclusive: Long? = null,
    val endExclusive: Long? = null,
    val minAmount: String? = null,
    val maxAmount: String? = null,
    val amountDirection: String = "all",
    val businessSemantic: String = "all",
    val cursor: RecordCursorResult? = null,
    val limit: Int = 50,
)

@Serializable
private data class HistoryRecordResult(
    val recordId: Long,
    val type: String,
    val accountId: Long,
    val accountName: String,
    val relatedAccountId: Long?,
    val relatedAccountName: String?,
    val title: String,
    val amount: String,
    val occurredAt: Long,
    val balanceBefore: String?,
    val balanceAfter: String?,
)

private fun HistoryRecord.toResult() = HistoryRecordResult(
    recordId = recordId,
    type = type.name.lowercase(),
    accountId = accountId,
    accountName = accountName,
    relatedAccountId = relatedAccountId,
    relatedAccountName = relatedAccountName,
    title = title,
    amount = amount.toString(),
    occurredAt = occurredAt,
    balanceBefore = balanceBefore?.toString(),
    balanceAfter = balanceAfter?.toString(),
)

@Serializable
private data class RecordsListResult(
    val records: List<HistoryRecordResult>,
    val nextCursor: RecordCursorResult?,
)

@Serializable
private data class RecordGetArguments(val kind: String, val recordId: Long)

@Serializable
private data class StoredRecordResult(
    val kind: String,
    val recordId: Long,
    val accountId: Long? = null,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val direction: String? = null,
    val amount: String,
    val note: String? = null,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val operationId: String,
)

@Serializable
private data class LedgerSummaryArguments(
    val accountId: Long? = null,
    val startInclusive: Long? = null,
    val endExclusive: Long? = null,
    val businessSemantic: String = "all",
)

@Serializable
private data class AccountBalanceResult(val accountId: Long, val accountName: String, val balance: String)

@Serializable
private data class LedgerSummaryResult(
    val recordCount: Int,
    val cashInflow: String,
    val cashOutflow: String,
    val netChange: String,
    val totalAssets: String,
    val accountBalances: List<AccountBalanceResult>,
)

@Serializable
private data class JournalListArguments(val limit: Int = 50)

@Serializable
private data class JournalEntryResult(
    val id: Long,
    val requestId: String,
    val sessionId: String,
    val clientName: String,
    val action: String,
    val recordKind: String? = null,
    val recordId: Long? = null,
    val summary: String,
    val status: String,
    val createdAt: Long,
    val resolvedAt: Long?,
    val entryType: String,
    val itemCount: Int? = null,
    val appliedCount: Int? = null,
    val conflictCount: Int? = null,
)

private fun AiMutationJournalEntry.toResult() = JournalEntryResult(
    id = id,
    requestId = requestId,
    sessionId = sessionId,
    clientName = clientName,
    action = action.name.lowercase(),
    recordKind = recordKind?.name?.lowercase(),
    recordId = recordId,
    summary = summary,
    status = status.value,
    createdAt = createdAt,
    resolvedAt = resolvedAt,
    entryType = entryType.value,
    itemCount = itemCount,
    appliedCount = appliedCount,
    conflictCount = conflictCount,
)

@Serializable
private data class JournalListResult(val entries: List<JournalEntryResult>)

@Serializable
private data class UndoBatchItemResult(
    val recordKind: String,
    val recordId: Long,
    val restored: Boolean,
    val message: String? = null,
)

private fun AiUndoBatchItem.toResult() = UndoBatchItemResult(
    recordKind = recordKind.name.lowercase(),
    recordId = recordId,
    restored = restored,
    message = message,
)

@Serializable
private data class UndoResult(
    val status: String,
    val message: String,
    val entry: JournalEntryResult? = null,
    val replayed: Boolean = false,
    val entryType: String? = null,
    val items: List<UndoBatchItemResult>? = null,
)

@Serializable
private data class CreateCashFlowArguments(
    val accountId: Long,
    val direction: String,
    val amount: String,
    val note: String = "",
    val occurredAt: Long? = null,
)

@Serializable
private data class UpdateCashFlowArguments(
    val recordId: Long,
    val accountId: Long,
    val direction: String,
    val amount: String,
    val note: String = "",
    val occurredAt: Long,
    val expectedUpdatedAt: Long? = null,
)

@Serializable
private data class CreateTransferArguments(
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: String,
    val note: String = "",
    val occurredAt: Long? = null,
)

@Serializable
private data class UpdateTransferArguments(
    val recordId: Long,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: String,
    val note: String = "",
    val occurredAt: Long,
    val expectedUpdatedAt: Long? = null,
)

@Serializable
private data class DeleteRecordArguments(
    val recordId: Long,
    val expectedUpdatedAt: Long? = null,
)

@Serializable
private data class MutationResult(
    val journal: JournalEntryResult,
    val record: AiLedgerRecordSnapshot,
    val replayed: Boolean,
)
