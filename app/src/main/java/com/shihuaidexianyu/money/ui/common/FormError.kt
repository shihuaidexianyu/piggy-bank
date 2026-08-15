package com.shihuaidexianyu.money.ui.common

import com.shihuaidexianyu.money.domain.usecase.ValidationErrorText

/**
 * Structured form-validation error. UI can `when` on the type to render field-specific feedback
 * (e.g. highlight the amount field red for [InvalidAmount]) instead of relying on string matching.
 *
 * The [message] texts come from [ValidationErrorText], the same constants thrown by `require(...)`
 * in use cases and by [com.shihuaidexianyu.money.util.RecordValidator], so the snackbar behavior is
 * unchanged when VMs map caught exceptions to [FormError].
 */
sealed interface FormError {
    val message: String

    /** A required field was left blank or unselected. */
    data class MissingField(val field: Field) : FormError {
        override val message: String = when (field) {
            Field.ACCOUNT -> ValidationErrorText.ACCOUNT_REQUIRED
            Field.AMOUNT -> ValidationErrorText.AMOUNT_REQUIRED
            Field.NAME -> ValidationErrorText.ACCOUNT_NAME_REQUIRED
            Field.REMINDER_NAME -> ValidationErrorText.REMINDER_NAME_REQUIRED
        }
    }

    /** The entered amount is zero, negative, or has too many decimal places. */
    data object InvalidAmount : FormError {
        override val message: String = ValidationErrorText.AMOUNT_INVALID
    }

    /** The amount is valid but not positive (e.g. 0). */
    data object AmountMustBePositive : FormError {
        override val message: String = ValidationErrorText.AMOUNT_MUST_BE_POSITIVE
    }

    /** The occurredAt timestamp is in the future. */
    data object FutureTimestamp : FormError {
        override val message: String = ValidationErrorText.OCCURRED_AT_IN_FUTURE
    }

    /** The record's occurredAt is before the account's creation. */
    data class BeforeAccountCreation(val accountName: String?) : FormError {
        override val message: String = ValidationErrorText.OCCURRED_AT_BEFORE_ACCOUNT_CREATION
    }

    /** A duplicate name was rejected by the uniqueness check. */
    data object DuplicateName : FormError {
        override val message: String = ValidationErrorText.DUPLICATE_ACCOUNT_NAME
    }

    /** The account is closed and cannot be mutated. */
    data class ClosedAccount(val action: String) : FormError {
        override val message: String = "${ValidationErrorText.CLOSED_ACCOUNT_PREFIX}$action"
    }

    /** The referenced entity (account, record, reminder) does not exist. */
    data class NotFound(val what: String) : FormError {
        override val message: String = "$what${ValidationErrorText.NOT_FOUND_SUFFIX}"
    }

    /** Transfer from- and to- accounts are the same. */
    data object SameTransferAccounts : FormError {
        override val message: String = ValidationErrorText.SAME_TRANSFER_ACCOUNTS
    }

    /** Catch-all for unexpected failures (e.g. DB error). */
    data class Unknown(override val message: String) : FormError

    enum class Field { ACCOUNT, AMOUNT, NAME, REMINDER_NAME }
}

/**
 * Maps a caught [Throwable] to a [FormError]. Recognizes the [ValidationErrorText] messages thrown
 * by `require(...)` in use cases and [com.shihuaidexianyu.money.util.RecordValidator] — if the
 * message matches a known pattern, the structured type is returned; otherwise [FormError.Unknown]
 * preserves the original message.
 */
fun Throwable.toFormError(): FormError {
    val msg = message ?: ""
    return when {
        msg == ValidationErrorText.ACCOUNT_REQUIRED -> FormError.MissingField(FormError.Field.ACCOUNT)
        msg == ValidationErrorText.AMOUNT_REQUIRED -> FormError.MissingField(FormError.Field.AMOUNT)
        msg == ValidationErrorText.ACCOUNT_NAME_REQUIRED -> FormError.MissingField(FormError.Field.NAME)
        msg == ValidationErrorText.REMINDER_NAME_REQUIRED -> FormError.MissingField(FormError.Field.REMINDER_NAME)
        msg == ValidationErrorText.AMOUNT_MUST_BE_POSITIVE -> FormError.AmountMustBePositive
        msg.startsWith(ValidationErrorText.AMOUNT_INVALID_PREFIX) -> FormError.InvalidAmount
        msg == ValidationErrorText.OCCURRED_AT_IN_FUTURE -> FormError.FutureTimestamp
        msg == ValidationErrorText.OCCURRED_AT_BEFORE_ACCOUNT_CREATION -> FormError.BeforeAccountCreation(null)
        msg == ValidationErrorText.DUPLICATE_ACCOUNT_NAME -> FormError.DuplicateName
        msg.startsWith(ValidationErrorText.CLOSED_ACCOUNT_PREFIX) ->
            FormError.ClosedAccount(msg.removePrefix(ValidationErrorText.CLOSED_ACCOUNT_PREFIX))
        msg == ValidationErrorText.SAME_TRANSFER_ACCOUNTS -> FormError.SameTransferAccounts
        msg.endsWith(ValidationErrorText.NOT_FOUND_SUFFIX) ->
            FormError.NotFound(msg.removeSuffix(ValidationErrorText.NOT_FOUND_SUFFIX))
        else -> FormError.Unknown(msg.ifEmpty { ValidationErrorText.OPERATION_FAILED })
    }
}
