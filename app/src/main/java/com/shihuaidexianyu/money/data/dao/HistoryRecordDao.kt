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
)

/**
 * Shared SQL fragment: `UNION ALL` of the four ledger tables projected into the unified history
 * shape. Both [HistoryRecordDao.queryPage] and [HistoryRecordDao.count] include this fragment so
 * the 4-table projection lives in exactly one place. Keep the column order in sync with
 * [HistoryRecordRow] — Room maps by position, not name, when the @Query result type is a data class.
 *
 * Every ledger table retains soft-deleted rows, so each branch filters on `deletedAt IS NULL`.
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
    WHERE deletedAt IS NULL
    UNION ALL
    SELECT
        id AS recordId,
        'BALANCE_ADJUSTMENT' AS type,
        1 AS sourceOrder,
        accountId AS accountId,
        NULL AS relatedAccountId,
        '余额矫正' AS title,
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
    @Query(
        """
        SELECT * FROM ($HISTORY_UNION_FRAGMENT)
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
}
