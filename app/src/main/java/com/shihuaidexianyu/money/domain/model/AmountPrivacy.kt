package com.shihuaidexianyu.money.domain.model

enum class AmountSurface {
    IN_APP,
    WIDGET,
    NOTIFICATION,
}

enum class AmountVisibility {
    VISIBLE,
    MASKED,
}

data class AmountPrivacy(
    val maskAmountsInApp: Boolean,
    val hideWidgetAmounts: Boolean,
    val hideNotificationAmounts: Boolean,
) {
    fun visibilityFor(surface: AmountSurface): AmountVisibility = when (surface) {
        AmountSurface.IN_APP -> maskAmountsInApp
        AmountSurface.WIDGET -> hideWidgetAmounts
        AmountSurface.NOTIFICATION -> hideNotificationAmounts
    }.toVisibility()

    companion object {
        val Visible = AmountPrivacy(
            maskAmountsInApp = false,
            hideWidgetAmounts = false,
            hideNotificationAmounts = false,
        )

        fun from(preferences: DevicePreferences): AmountPrivacy = AmountPrivacy(
            maskAmountsInApp = preferences.maskAmountsInApp,
            hideWidgetAmounts = preferences.hideWidgetAmounts,
            hideNotificationAmounts = preferences.hideNotificationAmounts,
        )
    }
}

private fun Boolean.toVisibility(): AmountVisibility =
    if (this) AmountVisibility.MASKED else AmountVisibility.VISIBLE
