package com.shihuaidexianyu.money.lan

/**
 * Stable wire error codes for the Money Link Protocol. Codes are part of the cross-repo contract
 * (docs/protocol/sync-v1/fixtures): never rename or repurpose an existing code.
 */
object MoneyLanErrorCodes {
    const val FRAME_TOO_LARGE = "FRAME_TOO_LARGE"
    const val INVALID_REQUEST = "INVALID_REQUEST"
    const val UNSUPPORTED_VERSION = "UNSUPPORTED_VERSION"
    const val SESSION_EXPIRED = "SESSION_EXPIRED"
    const val PAIRING_EXPIRED = "PAIRING_EXPIRED"
    const val PAIRING_LOCKED = "PAIRING_LOCKED"
    const val ALREADY_PAIRED = "ALREADY_PAIRED"
    const val PAIRING_FAILED = "PAIRING_FAILED"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val CONFLICT = "CONFLICT"
    const val VALIDATION_FAILED = "VALIDATION_FAILED"
    const val AMOUNT_OVERFLOW = "AMOUNT_OVERFLOW"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val RESPONSE_TOO_LARGE = "RESPONSE_TOO_LARGE"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val LEDGER_NOT_READY = "LEDGER_NOT_READY"
    const val WRITE_DISABLED = "WRITE_DISABLED"
    const val UNKNOWN_ACTION = "UNKNOWN_ACTION"
    const val NOT_FOUND = "NOT_FOUND"

    // sync v1
    const val DATASET_MISMATCH = "DATASET_MISMATCH"
    const val RESYNC_REQUIRED = "RESYNC_REQUIRED"
    const val INVALID_PATCH = "INVALID_PATCH"
    const val UNSUPPORTED_CAPABILITY = "UNSUPPORTED_CAPABILITY"
}
