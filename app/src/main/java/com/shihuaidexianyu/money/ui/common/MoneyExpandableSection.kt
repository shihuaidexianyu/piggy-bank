package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R

/** Optional settings stay discoverable without interrupting the primary task. */
@Composable
fun MoneyExpandableSection(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MoneyDimens.SpacingMd)) {
        MoneyListRow(
            title = title,
            subtitle = summary,
            showChevron = false,
            onClick = { expanded = !expanded },
            accessory = {
                Text(
                    stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            },
        )
        if (expanded) content()
    }
}
