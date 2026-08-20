package com.shihuaidexianyu.money.domain.model

enum class AmountSurface {
    IN_APP,
    NOTIFICATION,
}

enum class AmountVisibility {
    VISIBLE,
    MASKED,
}

data class AmountPrivacy(
    val maskAmountsInApp: Boolean,
    val hideNotificationAmounts: Boolean,
) {
    fun visibilityFor(surface: AmountSurface): AmountVisibility = when (surface) {
        AmountSurface.IN_APP -> maskAmountsInApp
        AmountSurface.NOTIFICATION -> hideNotificationAmounts
    }.toVisibility()

    companion object {
        val Visible = AmountPrivacy(
            maskAmountsInApp = false,
            hideNotificationAmounts = false,
        )

        fun from(preferences: DevicePreferences): AmountPrivacy = AmountPrivacy(
            maskAmountsInApp = preferences.maskAmountsInApp,
            hideNotificationAmounts = preferences.hideNotificationAmounts,
        )
    }
}

private fun Boolean.toVisibility(): AmountVisibility =
    if (this) AmountVisibility.MASKED else AmountVisibility.VISIBLE
