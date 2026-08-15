package com.shihuaidexianyu.money.domain.usecase

/**
 * Single source of truth for user-facing validation error messages.
 *
 * Use cases and validators throw `require(...)`/`ValidationException` with these exact texts, and
 * the UI layer (`ui/common/FormError.kt`) recognizes the same constants to map caught exceptions
 * back to structured field errors. Referencing this object from both sides keeps the mapping from
 * silently degrading to `FormError.Unknown` when wording changes.
 *
 * Prefix/suffix constants are recognized by pattern: [CLOSED_ACCOUNT_PREFIX] is followed by the
 * rejected action, [NOT_FOUND_SUFFIX] is preceded by the missing entity name.
 */
object ValidationErrorText {
    const val ACCOUNT_REQUIRED = "请选择账户"
    const val AMOUNT_REQUIRED = "金额不能为空"
    const val ACCOUNT_NAME_REQUIRED = "账户名称不能为空"
    const val REMINDER_NAME_REQUIRED = "请输入名称"
    const val AMOUNT_INVALID_PREFIX = "请输入有效金额"
    const val AMOUNT_INVALID = "$AMOUNT_INVALID_PREFIX，最多保留两位小数"
    const val AMOUNT_MUST_BE_POSITIVE = "金额必须大于 0"
    const val OCCURRED_AT_IN_FUTURE = "时间不能晚于当前时间"
    const val OCCURRED_AT_BEFORE_ACCOUNT_CREATION = "时间不能早于账户创建时间"
    const val DUPLICATE_ACCOUNT_NAME = "已存在同名账户"
    const val CLOSED_ACCOUNT_PREFIX = "关闭账户不能"
    const val SAME_TRANSFER_ACCOUNTS = "请选择不同的转出和转入账户"
    const val NOT_FOUND_SUFFIX = "不存在"
    const val OPERATION_FAILED = "操作失败"

    // Reminder form schedule/anchor parsing messages (ui/reminder form helpers).
    const val REMINDER_MONTH_DAY_INVALID = "请输入有效的每月日期"
    const val REMINDER_MONTH_INVALID = "请输入有效的月份"
    const val REMINDER_DAY_INVALID = "请输入有效的日期"
    const val REMINDER_INTERVAL_DAYS_INVALID = "请输入有效的间隔天数"
    const val REMINDER_ANCHOR_DATE_FORMAT = "首次日期格式应为 YYYY-MM-DD"
    const val REMINDER_ANCHOR_TIME_FORMAT = "首次时间格式应为 HH:mm"
    const val REMINDER_INTERVAL_DAYS_RANGE = "间隔天数必须在 1 到 3650 之间"

    /** Ledger note length violation; parameterized so the limit stays owned by the caller. */
    fun noteTooLong(maxLength: Int): String = "备注不能超过 $maxLength 个字符"
}
