package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Query

data class HistoryRecordRow(
    val recordId: Long,
    val type: String,
    val sourceOrder: Int,
    val accountId: Long,
    val relatedAccountId: Long?,
    val title: String,
    val amount: Long,
    val occurredAt: Long,
    val keywordSource: String,
    val accountName: String,
    val relatedAccountName: String?,
    /**
     * Book balance of [accountId] immediately before this record: [balanceAfter] minus this
     * row's signed contribution to the account (`amount` for cash flow / reconciliation /
     * adjustment rows, `-amount` for the outgoing transfer leg). Same full-ledger semantics as
     * [balanceAfter] — outer filters and pagination never change it.
     */
    val balanceBefore: Long?,
    /**
     * Book balance of [accountId] immediately after this record, computed over the FULL ledger —
     * outer filters and pagination never change it. For TRANSFER rows this is the FROM account's
     * balance; the receiving account's pair surfaces via [relatedBalanceBefore] /
     * [relatedBalanceAfter].
     */
    val balanceAfter: Long?,
    /**
     * Receiving account's balance right before a TRANSFER record; null for every other type and
     * when the receiving account contributes no leg (defensive only).
     */
    val relatedBalanceBefore: Long?,
    /** Receiving account's balance right after a TRANSFER record; null for non-transfer rows. */
    val relatedBalanceAfter: Long?,
)

/**
 * Shared SQL fragment: `UNION ALL` of the four ledger tables projected into the unified history
 * shape. Both [HistoryRecordDao.queryPage] and [HistoryRecordDao.count] include this fragment so
 * the 4-table projection lives in exactly one place. Keep the column order in sync with
 * [HistoryRecordRow] — Room maps result columns BY NAME to the data class properties — keep them in sync.
 *
 * Every ledger table retains soft-deleted rows, so each branch filters on `deletedAt IS NULL`.
 * Zero-delta balance checks remain stored as reconciliation evidence but are intentionally absent
 * from the user-facing history projection.
 */
internal const val HISTORY_UNION_FRAGMENT = """
    SELECT
        id AS recordId,
        'CASH_FLOW' AS type,
        4 AS sourceOrder,
        accountId AS accountId,
        NULL AS relatedAccountId,
        CASE WHEN TRIM(note) = '' THEN '未填写备注' ELSE note END AS title,
        CASE WHEN direction = 'inflow' THEN amount ELSE -amount END AS amount,
        occurredAt AS occurredAt,
        note || ' ' || CASE WHEN TRIM(note) = '' THEN '未填写备注' ELSE note END || ' ' ||
            COALESCE((SELECT name FROM accounts WHERE accounts.id = cash_flow_records.accountId), '') AS keywordSource
    FROM cash_flow_records
    WHERE deletedAt IS NULL
    UNION ALL
    SELECT
        id AS recordId,
        'TRANSFER' AS type,
        3 AS sourceOrder,
        fromAccountId AS accountId,
        toAccountId AS relatedAccountId,
        CASE WHEN TRIM(note) = '' THEN '账户间转移' ELSE note END AS title,
        amount AS amount,
        occurredAt AS occurredAt,
        note || ' ' || CASE WHEN TRIM(note) = '' THEN '账户间转移' ELSE note END || ' 转账 ' ||
            COALESCE((SELECT name FROM accounts WHERE accounts.id = transfer_records.fromAccountId), '') || ' ' ||
            COALESCE((SELECT name FROM accounts WHERE accounts.id = transfer_records.toAccountId), '') AS keywordSource
    FROM transfer_records
    WHERE deletedAt IS NULL
    UNION ALL
    SELECT
        id AS recordId,
        'BALANCE_UPDATE' AS type,
        2 AS sourceOrder,
        accountId AS accountId,
        NULL AS relatedAccountId,
        CASE WHEN delta = 0 THEN '余额核对' ELSE '对账调整' END AS title,
        delta AS amount,
        occurredAt AS occurredAt,
        CASE WHEN delta = 0 THEN '余额核对' ELSE '对账调整' END || ' ' ||
            COALESCE((SELECT name FROM accounts WHERE accounts.id = balance_update_records.accountId), '') AS keywordSource
    FROM balance_update_records
    WHERE deletedAt IS NULL AND delta != 0
    UNION ALL
    SELECT
        id AS recordId,
        'BALANCE_ADJUSTMENT' AS type,
        1 AS sourceOrder,
        accountId AS accountId,
        NULL AS relatedAccountId,
        '余额校正' AS title,
        delta AS amount,
        occurredAt AS occurredAt,
        '余额矫正 余额校正 ' || COALESCE(
            (SELECT name FROM accounts WHERE accounts.id = balance_adjustment_records.accountId),
            ''
        ) AS keywordSource
    FROM balance_adjustment_records
    WHERE deletedAt IS NULL
"""

/**
 * Per-account signed contribution stream feeding the running-balance window. Unlike
 * [HISTORY_UNION_FRAGMENT] (one DISPLAY row per record), a transfer contributes TWO legs here —
 * outgoing (`-amount` on `fromAccountId`) and incoming (`+amount` on `toAccountId`) — so the
 * receiving account's later rows reflect the incoming money. The leg ordering key
 * (`occurredAt`, `sourceOrder`, `recordId`) matches the union's, keeping every display row joined
 * to exactly one balance row. Zero-delta balance checks contribute 0 and are simply never joined
 * (they are absent from the display union). Self-transfers cannot be created
 * ([com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase] rejects them); the
 * defensive zero leg keeps the key unique and the balance unchanged if one ever slips in.
 */
internal const val HISTORY_BALANCE_LEGS_FRAGMENT = """
    SELECT accountId AS accountId,
        occurredAt AS occurredAt,
        4 AS sourceOrder,
        id AS recordId,
        CASE WHEN direction = 'inflow' THEN amount ELSE -amount END AS contribution
    FROM cash_flow_records
    WHERE deletedAt IS NULL
    UNION ALL
    SELECT accountId, occurredAt, 2, id, delta
    FROM balance_update_records
    WHERE deletedAt IS NULL
    UNION ALL
    SELECT accountId, occurredAt, 1, id, delta
    FROM balance_adjustment_records
    WHERE deletedAt IS NULL
    UNION ALL
    SELECT fromAccountId, occurredAt, 3, id, -amount
    FROM transfer_records
    WHERE deletedAt IS NULL AND fromAccountId != toAccountId
    UNION ALL
    SELECT toAccountId, occurredAt, 3, id, amount
    FROM transfer_records
    WHERE deletedAt IS NULL AND fromAccountId != toAccountId
    UNION ALL
    SELECT fromAccountId, occurredAt, 3, id, 0
    FROM transfer_records
    WHERE deletedAt IS NULL AND fromAccountId = toAccountId
"""

/**
 * Running-balance window over [HISTORY_BALANCE_LEGS_FRAGMENT]: one row per leg carrying the leg's
 * own contribution plus the account's book balance right after it (`initialBalance`, 0 when the
 * account row is missing, plus every contribution up to and including the leg, accumulated in the
 * same order the pages are sorted — `occurredAt, sourceOrder, recordId` ascending here vs
 * descending for display). `balanceBefore` is derived by the caller as
 * `balanceAfter - legContribution`, which also keeps the defensive self-transfer zero leg
 * consistent (before == after). Factored out of [HISTORY_ENRICHED_FRAGMENT] so the FROM-side and
 * the TO-side joins share one window definition.
 */
internal const val HISTORY_RUNNING_BALANCE_FRAGMENT = """
    SELECT
        legs.accountId AS legsAccountId,
        legs.occurredAt AS legsOccurredAt,
        legs.sourceOrder AS legsSourceOrder,
        legs.recordId AS legsRecordId,
        legs.contribution AS legContribution,
        COALESCE((SELECT initialBalance FROM accounts WHERE accounts.id = legs.accountId), 0) +
            SUM(legs.contribution) OVER (
                PARTITION BY legs.accountId
                ORDER BY legs.occurredAt, legs.sourceOrder, legs.recordId
                ROWS UNBOUNDED PRECEDING
            ) AS balanceAfter
    FROM ($HISTORY_BALANCE_LEGS_FRAGMENT) AS legs
"""

/**
 * [HISTORY_UNION_FRAGMENT] enriched with the per-row running balances and resolved account names.
 * The window runs INSIDE this subquery — over the complete, unfiltered legs stream — so the
 * outer [HISTORY_FILTER_FRAGMENT] (keyword/account/date/amount) and keyset pagination cannot
 * change any row's balance pair. Every display row INNER JOINs its own leg on
 * (accountId, occurredAt, sourceOrder, recordId); TRANSFER rows additionally LEFT JOIN the
 * receiving account's leg (same key pinned to `relatedAccountId`) to surface both accounts'
 * before/after balances. The LEFT JOIN key stays unique because a transfer's two legs differ in
 * `accountId`; non-transfer rows have a NULL `relatedAccountId` and simply get NULLs.
 */
internal const val HISTORY_ENRICHED_FRAGMENT = """
    SELECT
        unioned.*,
        COALESCE((SELECT name FROM accounts WHERE accounts.id = unioned.accountId), '') AS accountName,
        (SELECT name FROM accounts WHERE accounts.id = unioned.relatedAccountId) AS relatedAccountName,
        running.balanceAfter - running.legContribution AS balanceBefore,
        running.balanceAfter AS balanceAfter,
        relatedRunning.balanceAfter - relatedRunning.legContribution AS relatedBalanceBefore,
        relatedRunning.balanceAfter AS relatedBalanceAfter
    FROM ($HISTORY_UNION_FRAGMENT) AS unioned
    INNER JOIN ($HISTORY_RUNNING_BALANCE_FRAGMENT) AS running
        ON running.legsAccountId = unioned.accountId
        AND running.legsOccurredAt = unioned.occurredAt
        AND running.legsSourceOrder = unioned.sourceOrder
        AND running.legsRecordId = unioned.recordId
    LEFT JOIN ($HISTORY_RUNNING_BALANCE_FRAGMENT) AS relatedRunning
        ON relatedRunning.legsAccountId = unioned.relatedAccountId
        AND relatedRunning.legsOccurredAt = unioned.occurredAt
        AND relatedRunning.legsSourceOrder = unioned.sourceOrder
        AND relatedRunning.legsRecordId = unioned.recordId
"""

/**
 * Shared `WHERE` clause applied to the union result. Both query methods use this so filter
 * semantics stay in sync. Note: `LIKE ... ESCAPE '\'` requires the caller to escape `\`, `%`,
 * and `_` in [keyword]/[excludeKeyword] — see [com.shihuaidexianyu.money.data.repository.escapeHistoryLikeLiteral].
 */
internal const val HISTORY_FILTER_FRAGMENT = """
    (:keyword = '' OR LOWER(keywordSource) LIKE '%' || :keyword || '%' ESCAPE '\')
        AND (:excludeKeyword = '' OR LOWER(keywordSource) NOT LIKE '%' || :excludeKeyword || '%' ESCAPE '\')
        AND (
            :allTypes
            OR (:includeCashFlow AND type = 'CASH_FLOW')
            OR (:includeTransfer AND type = 'TRANSFER')
            OR (:includeBalanceUpdate AND type = 'BALANCE_UPDATE')
            OR (:includeBalanceAdjustment AND type = 'BALANCE_ADJUSTMENT')
        )
        AND (:accountId IS NULL OR accountId = :accountId OR relatedAccountId = :accountId)
        AND (:dateStartAt IS NULL OR occurredAt >= :dateStartAt)
        AND (:dateEndAt IS NULL OR occurredAt < :dateEndAt)
        AND (:minAmount IS NULL OR amount >= :minAmount OR amount <= -:minAmount)
        AND (:maxAmount IS NULL OR (amount >= -:maxAmount AND amount <= :maxAmount))
        AND (
            :amountDirection = 'ALL'
            OR (:amountDirection = 'INCREASE' AND amount > 0 AND type != 'TRANSFER')
            OR (:amountDirection = 'DECREASE' AND amount < 0 AND type != 'TRANSFER')
        )
"""

@Dao
interface HistoryRecordDao {
    // The page query reads the enriched union: the running balance is windowed over the complete
    // ledger inside the subquery, so the filter fragment and the keyset cursor only choose WHICH
    // rows appear, never their balanceAfter values.
    @Query(
        """
        SELECT * FROM ($HISTORY_ENRICHED_FRAGMENT)
        WHERE $HISTORY_FILTER_FRAGMENT
            AND (
                :cursorOccurredAt IS NULL
                OR occurredAt < :cursorOccurredAt
                OR (occurredAt = :cursorOccurredAt AND sourceOrder < :cursorSourceOrder)
                OR (occurredAt = :cursorOccurredAt AND sourceOrder = :cursorSourceOrder AND recordId < :cursorRecordId)
            )
        ORDER BY occurredAt DESC, sourceOrder DESC, recordId DESC
        LIMIT :limit
        """,
    )
    suspend fun queryPage(
        keyword: String,
        excludeKeyword: String,
        allTypes: Boolean,
        includeCashFlow: Boolean,
        includeTransfer: Boolean,
        includeBalanceUpdate: Boolean,
        includeBalanceAdjustment: Boolean,
        accountId: Long?,
        dateStartAt: Long?,
        dateEndAt: Long?,
        minAmount: Long?,
        maxAmount: Long?,
        amountDirection: String,
        cursorOccurredAt: Long?,
        cursorSourceOrder: Int,
        cursorRecordId: Long,
        limit: Int,
    ): List<HistoryRecordRow>

    @Query(
        """
        SELECT COUNT(*) FROM ($HISTORY_UNION_FRAGMENT)
        WHERE $HISTORY_FILTER_FRAGMENT
        """,
    )
    suspend fun count(
        keyword: String,
        excludeKeyword: String,
        allTypes: Boolean,
        includeCashFlow: Boolean,
        includeTransfer: Boolean,
        includeBalanceUpdate: Boolean,
        includeBalanceAdjustment: Boolean,
        accountId: Long?,
        dateStartAt: Long?,
        dateEndAt: Long?,
        minAmount: Long?,
        maxAmount: Long?,
        amountDirection: String,
    ): Int

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'CASH_FLOW' AND amount > 0 THEN amount ELSE 0 END), 0) AS cashInflow,
            COALESCE(SUM(CASE WHEN type = 'CASH_FLOW' AND amount < 0 THEN -amount ELSE 0 END), 0) AS cashOutflow,
            COALESCE(SUM(
                CASE WHEN type = 'TRANSFER' THEN
                    CASE
                        WHEN :accountId IS NULL THEN 0
                        WHEN relatedAccountId = :accountId AND accountId = :accountId THEN 0
                        WHEN relatedAccountId = :accountId THEN amount
                        WHEN accountId = :accountId THEN -amount
                        ELSE 0
                    END
                ELSE amount END
            ), 0) AS netChange
        FROM ($HISTORY_UNION_FRAGMENT)
        WHERE $HISTORY_FILTER_FRAGMENT
        """,
    )
    suspend fun summarize(
        keyword: String,
        excludeKeyword: String,
        allTypes: Boolean,
        includeCashFlow: Boolean,
        includeTransfer: Boolean,
        includeBalanceUpdate: Boolean,
        includeBalanceAdjustment: Boolean,
        accountId: Long?,
        dateStartAt: Long?,
        dateEndAt: Long?,
        minAmount: Long?,
        maxAmount: Long?,
        amountDirection: String,
    ): HistoryFilterSummaryProjection
}

/** Property names must match the SELECT aliases — Room maps data-class results by column name. */
data class HistoryFilterSummaryProjection(
    val cashInflow: Long,
    val cashOutflow: Long,
    val netChange: Long,
)
