package com.shihuaidexianyu.money.util

import com.shihuaidexianyu.money.domain.usecase.ValidationErrorText

object RecordValidator {
    fun requireAmount(amountText: String): Long {
        val amount = AmountInputParser.parseUnsignedToMinor(amountText)
            ?: throw ValidationException(invalidAmountMessage(amountText))
        if (amount <= 0) {
            throw ValidationException(ValidationErrorText.AMOUNT_MUST_BE_POSITIVE)
        }
        return amount
    }

    fun requireSignedAmount(amountText: String): Long {
        return AmountInputParser.parseSignedToMinor(amountText)
            ?: throw ValidationException(invalidAmountMessage(amountText))
    }

    fun requireOccurredAt(occurredAt: Long) {
        if (occurredAt > System.currentTimeMillis()) {
            throw ValidationException(ValidationErrorText.OCCURRED_AT_IN_FUTURE)
        }
    }

    fun requireAccountId(accountId: Long?): Long {
        return accountId ?: throw ValidationException(ValidationErrorText.ACCOUNT_REQUIRED)
    }

    fun requireTransferAccounts(fromId: Long?, toId: Long?): Pair<Long, Long> {
        val from = fromId ?: throw ValidationException(ValidationErrorText.ACCOUNT_REQUIRED)
        val to = toId ?: throw ValidationException(ValidationErrorText.ACCOUNT_REQUIRED)
        if (from == to) {
            throw ValidationException(ValidationErrorText.SAME_TRANSFER_ACCOUNTS)
        }
        return from to to
    }

    fun requireReminderName(name: String) {
        if (name.isBlank()) {
            throw ValidationException(ValidationErrorText.REMINDER_NAME_REQUIRED)
        }
    }

    class ValidationException(override val message: String) : Exception(message)

    private fun invalidAmountMessage(amountText: String): String {
        return if (amountText.isBlank()) {
            ValidationErrorText.AMOUNT_REQUIRED
        } else {
            ValidationErrorText.AMOUNT_INVALID
        }
    }
}
