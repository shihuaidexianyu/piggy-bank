package com.shihuaidexianyu.money.ui.common

/**
 * Structured form-validation error. UI can `when` on the type to render field-specific feedback
 * (e.g. highlight the amount field red for [InvalidAmount]) instead of relying on string matching.
 *
 * The [message] is the user-facing Chinese text — kept consistent with the existing
 * `require(...)` messages in use cases so the snackbar behavior is unchanged when VMs map
 * caught exceptions to [FormError].
 */
sealed interface FormError {
    val message: String

    /** A required field was left blank or unselected. */
    data class MissingField(val field: Field) : FormError {
        override val message: String = when (field) {
            Field.ACCOUNT -> "请选择账户"
            Field.AMOUNT -> "金额不能为空"
            Field.NAME -> "账户名称不能为空"
            Field.REMINDER_NAME -> "请输入名称"
        }
    }

    /** The entered amount is zero, negative, or has too many decimal places. */
    data object InvalidAmount : FormError {
        override val message: String = "请输入有效金额，最多保留两位小数"
    }

    /** The amount is valid but not positive (e.g. 0). */
    data object AmountMustBePositive : FormError {
        override val message: String = "金额必须大于 0"
    }

    /** The occurredAt timestamp is in the future. */
    data object FutureTimestamp : FormError {
        override val message: String = "时间不能晚于当前时间"
    }

    /** The record's occurredAt is before the account's creation. */
    data class BeforeAccountCreation(val accountName: String?) : FormError {
        override val message: String = "时间不能早于账户创建时间"
    }

    /** A duplicate name was rejected by the uniqueness check. */
    data object DuplicateName : FormError {
        override val message: String = "已存在同名账户"
    }

    /** The account is archived and cannot be mutated. */
    data class ArchivedAccount(val action: String) : FormError {
        override val message: String = "归档账户不能$action"
    }

    /** The referenced entity (account, record, reminder) does not exist. */
    data class NotFound(val what: String) : FormError {
        override val message: String = "${what}不存在"
    }

    /** Transfer from- and to- accounts are the same. */
    data object SameTransferAccounts : FormError {
        override val message: String = "请选择不同的转出和转入账户"
    }

    /** Catch-all for unexpected failures (e.g. DB error). */
    data class Unknown(override val message: String) : FormError

    enum class Field { ACCOUNT, AMOUNT, NAME, REMINDER_NAME }
}

/**
 * Maps a caught [Throwable] to a [FormError]. Recognizes the Chinese messages thrown by
 * `require(...)` in use cases and [com.shihuaidexianyu.money.util.RecordValidator] — if the
 * message matches a known pattern, the structured type is returned; otherwise [FormError.Unknown]
 * preserves the original message.
 */
fun Throwable.toFormError(): FormError {
    val msg = message ?: ""
    return when {
        msg == "请选择账户" -> FormError.MissingField(FormError.Field.ACCOUNT)
        msg == "金额不能为空" -> FormError.MissingField(FormError.Field.AMOUNT)
        msg == "账户名称不能为空" -> FormError.MissingField(FormError.Field.NAME)
        msg == "请输入名称" -> FormError.MissingField(FormError.Field.REMINDER_NAME)
        msg == "金额必须大于 0" -> FormError.AmountMustBePositive
        msg.startsWith("请输入有效金额") -> FormError.InvalidAmount
        msg == "时间不能晚于当前时间" -> FormError.FutureTimestamp
        msg == "时间不能早于账户创建时间" -> FormError.BeforeAccountCreation(null)
        msg == "已存在同名账户" -> FormError.DuplicateName
        msg.startsWith("归档账户不能") -> FormError.ArchivedAccount(msg.removePrefix("归档账户不能"))
        msg == "请选择不同的转出和转入账户" -> FormError.SameTransferAccounts
        msg.endsWith("不存在") -> FormError.NotFound(msg.removeSuffix("不存在"))
        else -> FormError.Unknown(msg.ifEmpty { "操作失败" })
    }
}
