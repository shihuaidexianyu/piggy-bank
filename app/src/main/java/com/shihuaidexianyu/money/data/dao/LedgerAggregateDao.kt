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

data class HomePeriodLedgerSummaryProjection(
    val cashInflow: Long,
    val cashOutflow: Long,
    val reconciliationIncrease: Long,
    val reconciliationDecrease: Long,
    val manualAdjustmentIncrease: Long,
    val manualAdjustmentDecrease: Long,
    val cashFlowRecordCount: Long,
    val transferRecordCount: Long,
    val manualAdjustmentRecordCount: Long,
)

@Dao
interface LedgerAggregateDao {
    @Query(
        """
        SELECT
            COALESCE(SUM(cashInflow), 0) AS cashInflow,
            COALESCE(SUM(cashOutflow), 0) AS cashOutflow,
            COALESCE(SUM(reconciliationIncrease), 0) AS reconciliationIncrease,
            COALESCE(SUM(reconciliationDecrease), 0) AS reconciliationDecrease,
            COALESCE(SUM(manualAdjustmentIncrease), 0) AS manualAdjustmentIncrease,
            COALESCE(SUM(manualAdjustmentDecrease), 0) AS manualAdjustmentDecrease,
            COALESCE(SUM(cashFlowRecordCount), 0) AS cashFlowRecordCount,
            COALESCE(SUM(transferRecordCount), 0) AS transferRecordCount,
            COALESCE(SUM(manualAdjustmentRecordCount), 0) AS manualAdjustmentRecordCount
        FROM (
            SELECT
                CASE WHEN direction = :inflowDirection THEN amount ELSE 0 END AS cashInflow,
                CASE WHEN direction = :outflowDirection THEN amount ELSE 0 END AS cashOutflow,
                0 AS reconciliationIncrease, 0 AS reconciliationDecrease,
                0 AS manualAdjustmentIncrease, 0 AS manualAdjustmentDecrease,
                1 AS cashFlowRecordCount, 0 AS transferRecordCount, 0 AS manualAdjustmentRecordCount
            FROM cash_flow_records
            WHERE deletedAt IS NULL AND occurredAt >= :startInclusive AND occurredAt < :endExclusive

            UNION ALL

            SELECT 0, 0, 0, 0, 0, 0, 0, 1, 0
            FROM transfer_records
            WHERE deletedAt IS NULL AND occurredAt >= :startInclusive AND occurredAt < :endExclusive

            UNION ALL

            SELECT 0, 0,
                CASE WHEN delta > 0 THEN delta ELSE 0 END,
                CASE WHEN delta < 0 THEN delta ELSE 0 END,
                0, 0, 0, 0, 0
            FROM balance_update_records
            WHERE deletedAt IS NULL AND occurredAt >= :startInclusive AND occurredAt < :endExclusive

            UNION ALL

            SELECT 0, 0, 0, 0,
                CASE WHEN delta > 0 THEN delta ELSE 0 END,
                CASE WHEN delta < 0 THEN delta ELSE 0 END,
                0, 0, 1
            FROM balance_adjustment_records
            WHERE deletedAt IS NULL AND occurredAt >= :startInclusive AND occurredAt < :endExclusive
        ) period_ledger
        """,
    )
    suspend fun queryHomePeriodLedgerSummary(
        startInclusive: Long,
        endExclusive: Long,
        inflowDirection: String,
        outflowDirection: String,
    ): HomePeriodLedgerSummaryProjection

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
