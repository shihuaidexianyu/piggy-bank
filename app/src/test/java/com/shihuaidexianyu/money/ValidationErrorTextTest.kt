package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.usecase.ValidationErrorText
import com.shihuaidexianyu.money.ui.common.FormError
import com.shihuaidexianyu.money.ui.common.toFormError
import kotlin.test.assertEquals
import org.junit.Test

/**
 * Pins [ValidationErrorText] contents and verifies that every recognized message maps to the
 * structured [FormError] type. The pinning guards against silent wording drift: `toFormError`
 * matches these exact texts, so an accidental edit here would otherwise degrade field-level error
 * highlighting to [FormError.Unknown] without any test noticing.
 */
class ValidationErrorTextTest {

    // region Content pinning — edit only with a deliberate copy review.
    @Test
    fun `constant contents are pinned`() {
        assertEquals("请选择账户", ValidationErrorText.ACCOUNT_REQUIRED)
        assertEquals("金额不能为空", ValidationErrorText.AMOUNT_REQUIRED)
        assertEquals("账户名称不能为空", ValidationErrorText.ACCOUNT_NAME_REQUIRED)
        assertEquals("请输入名称", ValidationErrorText.REMINDER_NAME_REQUIRED)
        assertEquals("请输入有效金额", ValidationErrorText.AMOUNT_INVALID_PREFIX)
        assertEquals("请输入有效金额，最多保留两位小数", ValidationErrorText.AMOUNT_INVALID)
        assertEquals("金额必须大于 0", ValidationErrorText.AMOUNT_MUST_BE_POSITIVE)
        assertEquals("时间不能晚于当前时间", ValidationErrorText.OCCURRED_AT_IN_FUTURE)
        assertEquals("时间不能早于账户创建时间", ValidationErrorText.OCCURRED_AT_BEFORE_ACCOUNT_CREATION)
        assertEquals("已存在同名账户", ValidationErrorText.DUPLICATE_ACCOUNT_NAME)
        assertEquals("关闭账户不能", ValidationErrorText.CLOSED_ACCOUNT_PREFIX)
        assertEquals("请选择不同的转出和转入账户", ValidationErrorText.SAME_TRANSFER_ACCOUNTS)
        assertEquals("不存在", ValidationErrorText.NOT_FOUND_SUFFIX)
        assertEquals("操作失败", ValidationErrorText.OPERATION_FAILED)
        assertEquals("请输入有效的每月日期", ValidationErrorText.REMINDER_MONTH_DAY_INVALID)
        assertEquals("请输入有效的月份", ValidationErrorText.REMINDER_MONTH_INVALID)
        assertEquals("请输入有效的日期", ValidationErrorText.REMINDER_DAY_INVALID)
        assertEquals("请输入有效的间隔天数", ValidationErrorText.REMINDER_INTERVAL_DAYS_INVALID)
        assertEquals("首次日期格式应为 YYYY-MM-DD", ValidationErrorText.REMINDER_ANCHOR_DATE_FORMAT)
        assertEquals("首次时间格式应为 HH:mm", ValidationErrorText.REMINDER_ANCHOR_TIME_FORMAT)
        assertEquals("间隔天数必须在 1 到 3650 之间", ValidationErrorText.REMINDER_INTERVAL_DAYS_RANGE)
        assertEquals("备注不能超过 200 个字符", ValidationErrorText.noteTooLong(200))
    }
    // endregion

    @Test
    fun `every recognized message maps to its structured FormError`() {
        val cases: List<Pair<String, FormError>> = listOf(
            ValidationErrorText.ACCOUNT_REQUIRED to FormError.MissingField(FormError.Field.ACCOUNT),
            ValidationErrorText.AMOUNT_REQUIRED to FormError.MissingField(FormError.Field.AMOUNT),
            ValidationErrorText.ACCOUNT_NAME_REQUIRED to FormError.MissingField(FormError.Field.NAME),
            ValidationErrorText.REMINDER_NAME_REQUIRED to FormError.MissingField(FormError.Field.REMINDER_NAME),
            ValidationErrorText.AMOUNT_MUST_BE_POSITIVE to FormError.AmountMustBePositive,
            ValidationErrorText.AMOUNT_INVALID to FormError.InvalidAmount,
            ValidationErrorText.OCCURRED_AT_IN_FUTURE to FormError.FutureTimestamp,
            ValidationErrorText.OCCURRED_AT_BEFORE_ACCOUNT_CREATION to FormError.BeforeAccountCreation(null),
            ValidationErrorText.DUPLICATE_ACCOUNT_NAME to FormError.DuplicateName,
            ValidationErrorText.SAME_TRANSFER_ACCOUNTS to FormError.SameTransferAccounts,
        )
        cases.forEach { (message, expected) ->
            assertEquals(expected, IllegalArgumentException(message).toFormError(), "message: $message")
        }
    }

    @Test
    fun `closed account prefix maps to ClosedAccount keeping the action suffix`() {
        val error = IllegalArgumentException("${ValidationErrorText.CLOSED_ACCOUNT_PREFIX}修改账户").toFormError()
        assertEquals(FormError.ClosedAccount("修改账户"), error)
    }

    @Test
    fun `not-found suffix maps to NotFound keeping the entity prefix`() {
        val error = IllegalArgumentException("账户${ValidationErrorText.NOT_FOUND_SUFFIX}").toFormError()
        assertEquals(FormError.NotFound("账户"), error)
    }

    @Test
    fun `FormError messages are sourced from the same constants`() {
        assertEquals(ValidationErrorText.ACCOUNT_REQUIRED, FormError.MissingField(FormError.Field.ACCOUNT).message)
        assertEquals(ValidationErrorText.AMOUNT_REQUIRED, FormError.MissingField(FormError.Field.AMOUNT).message)
        assertEquals(ValidationErrorText.ACCOUNT_NAME_REQUIRED, FormError.MissingField(FormError.Field.NAME).message)
        assertEquals(ValidationErrorText.REMINDER_NAME_REQUIRED, FormError.MissingField(FormError.Field.REMINDER_NAME).message)
        assertEquals(ValidationErrorText.AMOUNT_INVALID, FormError.InvalidAmount.message)
        assertEquals(ValidationErrorText.AMOUNT_MUST_BE_POSITIVE, FormError.AmountMustBePositive.message)
        assertEquals(ValidationErrorText.OCCURRED_AT_IN_FUTURE, FormError.FutureTimestamp.message)
        assertEquals(ValidationErrorText.OCCURRED_AT_BEFORE_ACCOUNT_CREATION, FormError.BeforeAccountCreation(null).message)
        assertEquals(ValidationErrorText.DUPLICATE_ACCOUNT_NAME, FormError.DuplicateName.message)
        assertEquals(ValidationErrorText.SAME_TRANSFER_ACCOUNTS, FormError.SameTransferAccounts.message)
        assertEquals(
            "${ValidationErrorText.CLOSED_ACCOUNT_PREFIX}修改账户",
            FormError.ClosedAccount("修改账户").message,
        )
        assertEquals("账户${ValidationErrorText.NOT_FOUND_SUFFIX}", FormError.NotFound("账户").message)
    }
}
