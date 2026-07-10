package com.shihuaidexianyu.money.data.dao

import androidx.room.Dao
import androidx.room.Query

data class AccountLedgerAggregateProjection(
    val accountId: Long,
    val inflow: Long,
    val outflow: Long,
    val transferIn: Long,
    val transferOut: Long,
    val manualAdjustment: Long,
    val reconciliation: Long,
)

data class AccountActivityMaximaProjection(
    val maxActiveOccurredAt: Long?,
    val maxBalanceUpdateOccurredAt: Long?,
)

@Dao
interface LedgerAggregateDao {
    @Query(
        """
        SELECT
            accountId,
            SUM(inflow) AS inflow,
            SUM(outflow) AS outflow,
            SUM(transferIn) AS transferIn,
            SUM(transferOut) AS transferOut,
            SUM(manualAdjustment) AS manualAdjustment,
            SUM(reconciliation) AS reconciliation
        FROM (
            SELECT
                r.accountId AS accountId,
                CASE WHEN r.direction = :inflowDirection THEN r.amount ELSE 0 END AS inflow,
                CASE WHEN r.direction = :outflowDirection THEN r.amount ELSE 0 END AS outflow,
                0 AS transferIn, 0 AS transferOut, 0 AS manualAdjustment, 0 AS reconciliation
            FROM cash_flow_records r
            INNER JOIN accounts a ON a.id = r.accountId
            WHERE r.deletedAt IS NULL
              AND r.accountId IN (:accountIds)
              AND r.occurredAt >= a.createdAt - ((a.createdAt % 60000 + 60000) % 60000)
              AND ((:inclusiveEnd = 1 AND r.occurredAt <= :boundary)
                OR (:inclusiveEnd = 0 AND r.occurredAt < :boundary))

            UNION ALL

            SELECT r.toAccountId, 0, 0, r.amount, 0, 0, 0
            FROM transfer_records r
            INNER JOIN accounts a ON a.id = r.toAccountId
            WHERE r.deletedAt IS NULL
              AND r.toAccountId IN (:accountIds)
              AND r.occurredAt >= a.createdAt - ((a.createdAt % 60000 + 60000) % 60000)
              AND ((:inclusiveEnd = 1 AND r.occurredAt <= :boundary)
                OR (:inclusiveEnd = 0 AND r.occurredAt < :boundary))

            UNION ALL

            SELECT r.fromAccountId, 0, 0, 0, r.amount, 0, 0
            FROM transfer_records r
            INNER JOIN accounts a ON a.id = r.fromAccountId
            WHERE r.deletedAt IS NULL
              AND r.fromAccountId IN (:accountIds)
              AND r.occurredAt >= a.createdAt - ((a.createdAt % 60000 + 60000) % 60000)
              AND ((:inclusiveEnd = 1 AND r.occurredAt <= :boundary)
                OR (:inclusiveEnd = 0 AND r.occurredAt < :boundary))

            UNION ALL

            SELECT r.accountId, 0, 0, 0, 0, r.delta, 0
            FROM balance_adjustment_records r
            INNER JOIN accounts a ON a.id = r.accountId
            WHERE r.deletedAt IS NULL
              AND r.accountId IN (:accountIds)
              AND r.occurredAt >= a.createdAt - ((a.createdAt % 60000 + 60000) % 60000)
              AND ((:inclusiveEnd = 1 AND r.occurredAt <= :boundary)
                OR (:inclusiveEnd = 0 AND r.occurredAt < :boundary))

            UNION ALL

            SELECT r.accountId, 0, 0, 0, 0, 0, r.delta
            FROM balance_update_records r
            INNER JOIN accounts a ON a.id = r.accountId
            WHERE r.deletedAt IS NULL
              AND r.accountId IN (:accountIds)
              AND (:excludingBalanceUpdateId IS NULL OR r.id != :excludingBalanceUpdateId)
              AND r.occurredAt >= a.createdAt - ((a.createdAt % 60000 + 60000) % 60000)
              AND ((:inclusiveEnd = 1 AND r.occurredAt <= :boundary)
                OR (:inclusiveEnd = 0 AND r.occurredAt < :boundary))
        ) ledger_components
        GROUP BY accountId
        """,
    )
    suspend fun queryAccountAggregates(
        accountIds: List<Long>,
        boundary: Long,
        inclusiveEnd: Boolean,
        excludingBalanceUpdateId: Long?,
        inflowDirection: String,
        outflowDirection: String,
    ): List<AccountLedgerAggregateProjection>

    @Query(
        """
        SELECT
            MAX(occurredAt) AS maxActiveOccurredAt,
            MAX(CASE WHEN isBalanceUpdate = 1 THEN occurredAt ELSE NULL END) AS maxBalanceUpdateOccurredAt
        FROM (
            SELECT occurredAt, 0 AS isBalanceUpdate
            FROM cash_flow_records
            WHERE deletedAt IS NULL AND accountId = :accountId
            UNION ALL
            SELECT occurredAt, 0 FROM transfer_records
            WHERE deletedAt IS NULL AND fromAccountId = :accountId
            UNION ALL
            SELECT occurredAt, 0 FROM transfer_records
            WHERE deletedAt IS NULL AND toAccountId = :accountId
            UNION ALL
            SELECT occurredAt, 1 FROM balance_update_records
            WHERE deletedAt IS NULL AND accountId = :accountId
            UNION ALL
            SELECT occurredAt, 0 FROM balance_adjustment_records
            WHERE deletedAt IS NULL AND accountId = :accountId
        ) active_ledger_activity
        """,
    )
    suspend fun queryActivityMaxima(accountId: Long): AccountActivityMaximaProjection
}
