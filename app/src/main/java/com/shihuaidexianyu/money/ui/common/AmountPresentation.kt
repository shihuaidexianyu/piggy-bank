package com.shihuaidexianyu.money.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.shihuaidexianyu.money.domain.model.AmountPrivacy
import com.shihuaidexianyu.money.domain.model.AmountSurface
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.util.AmountFormatter

val LocalAmountPrivacy = compositionLocalOf { AmountPrivacy.Visible }

@Composable
fun formatInAppAmount(
    amountInMinor: Long,
    settings: PortableSettings,
): String = AmountFormatter.format(
    amountInMinor = amountInMinor,
    settings = settings,
    visibility = LocalAmountPrivacy.current.visibilityFor(AmountSurface.IN_APP),
)
