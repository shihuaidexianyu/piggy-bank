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

@Dao
interface HistoryRecordDao {
    @Query(
        """
        SELECT * FROM (
            SELECT
                id AS recordId,
                'CASH_FLOW' AS type,
                4 AS sourceOrder,
                accountId AS accountId,
                NULL AS relatedAccountId,
                CASE WHEN TRIM(purpose) = '' THEN '未填写用途' ELSE purpose END AS title,
                CASE WHEN direction = 'inflow' THEN amount ELSE -amount END AS amount,
                occurredAt AS occurredAt,
                purpose AS keywordSource
            FROM cash_flow_records
            WHERE isDeleted = 0
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
                note AS keywordSource
            FROM transfer_records
            WHERE isDeleted = 0
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
                '' AS keywordSource
            FROM balance_update_records
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
                '' AS keywordSource
            FROM balance_adjustment_records
        )
        WHERE (:keyword = '' OR LOWER(keywordSource) LIKE '%' || :keyword || '%')
            AND (:excludeKeyword = '' OR LOWER(keywordSource) NOT LIKE '%' || :excludeKeyword || '%')
            AND (:accountId IS NULL OR accountId = :accountId OR relatedAccountId = :accountId)
            AND (:dateStartAt IS NULL OR occurredAt >= :dateStartAt)
            AND (:dateEndAt IS NULL OR occurredAt <= :dateEndAt)
            AND (:minAmount IS NULL OR ABS(amount) >= :minAmount)
            AND (:maxAmount IS NULL OR ABS(amount) <= :maxAmount)
            AND (
                :amountDirection = 'ALL'
                OR (:amountDirection = 'INCREASE' AND amount > 0 AND type != 'TRANSFER')
                OR (:amountDirection = 'DECREASE' AND amount < 0 AND type != 'TRANSFER')
            )
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
        SELECT COUNT(*) FROM (
            SELECT
                id AS recordId,
                'CASH_FLOW' AS type,
                4 AS sourceOrder,
                accountId AS accountId,
                NULL AS relatedAccountId,
                CASE WHEN direction = 'inflow' THEN amount ELSE -amount END AS amount,
                occurredAt AS occurredAt,
                purpose AS keywordSource
            FROM cash_flow_records
            WHERE isDeleted = 0
            UNION ALL
            SELECT
                id AS recordId,
                'TRANSFER' AS type,
                3 AS sourceOrder,
                fromAccountId AS accountId,
                toAccountId AS relatedAccountId,
                amount AS amount,
                occurredAt AS occurredAt,
                note AS keywordSource
            FROM transfer_records
            WHERE isDeleted = 0
            UNION ALL
            SELECT
                id AS recordId,
                'BALANCE_UPDATE' AS type,
                2 AS sourceOrder,
                accountId AS accountId,
                NULL AS relatedAccountId,
                delta AS amount,
                occurredAt AS occurredAt,
                '' AS keywordSource
            FROM balance_update_records
            UNION ALL
            SELECT
                id AS recordId,
                'BALANCE_ADJUSTMENT' AS type,
                1 AS sourceOrder,
                accountId AS accountId,
                NULL AS relatedAccountId,
                delta AS amount,
                occurredAt AS occurredAt,
                '' AS keywordSource
            FROM balance_adjustment_records
        )
        WHERE (:keyword = '' OR LOWER(keywordSource) LIKE '%' || :keyword || '%')
            AND (:excludeKeyword = '' OR LOWER(keywordSource) NOT LIKE '%' || :excludeKeyword || '%')
            AND (:accountId IS NULL OR accountId = :accountId OR relatedAccountId = :accountId)
            AND (:dateStartAt IS NULL OR occurredAt >= :dateStartAt)
            AND (:dateEndAt IS NULL OR occurredAt <= :dateEndAt)
            AND (:minAmount IS NULL OR ABS(amount) >= :minAmount)
            AND (:maxAmount IS NULL OR ABS(amount) <= :maxAmount)
            AND (
                :amountDirection = 'ALL'
                OR (:amountDirection = 'INCREASE' AND amount > 0 AND type != 'TRANSFER')
                OR (:amountDirection = 'DECREASE' AND amount < 0 AND type != 'TRANSFER')
            )
        """,
    )
    suspend fun count(
        keyword: String,
        excludeKeyword: String,
        accountId: Long?,
        dateStartAt: Long?,
        dateEndAt: Long?,
        minAmount: Long?,
        maxAmount: Long?,
        amountDirection: String,
    ): Int
}
