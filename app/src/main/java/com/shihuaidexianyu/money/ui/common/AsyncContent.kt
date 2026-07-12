package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.R

enum class EmptyKind {
    COMPLETELY_EMPTY,
    FILTERED_EMPTY,
}

sealed interface AsyncContent<out T> {
    data object Loading : AsyncContent<Nothing>

    data class Data<T>(val value: T) : AsyncContent<T>

    data class Refreshing<T>(val value: T) : AsyncContent<T>

    data class Empty(val kind: EmptyKind) : AsyncContent<Nothing>

    data class Error(
        val message: String,
        val retryToken: String?,
    ) : AsyncContent<Nothing>
}

fun <T> formAsyncContent(
    value: T,
    isLoading: Boolean,
    errorMessage: String?,
    retryToken: String?,
): AsyncContent<T> = when {
    errorMessage != null -> AsyncContent.Error(errorMessage, retryToken)
    isLoading -> AsyncContent.Loading
    else -> AsyncContent.Data(value)
}

/**
 * Renders exactly one asynchronous branch. A refresh deliberately keeps the last committed
 * value visible; an error never falls through to an empty or zero-valued content branch.
 */
@Composable
fun <T> AsyncContentRenderer(
    content: AsyncContent<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { DefaultAsyncLoading() },
    empty: @Composable (EmptyKind) -> Unit = { DefaultAsyncEmpty(it) },
    data: @Composable (value: T, refreshing: Boolean) -> Unit,
) {
    Box(modifier = modifier) {
        when (content) {
            AsyncContent.Loading -> loading()
            is AsyncContent.Data -> data(content.value, false)
            is AsyncContent.Refreshing -> data(content.value, true)
            is AsyncContent.Empty -> empty(content.kind)
            is AsyncContent.Error -> DefaultAsyncError(
                message = content.message,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun DefaultAsyncEmpty(kind: EmptyKind) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when (kind) {
                EmptyKind.COMPLETELY_EMPTY -> stringResource(R.string.async_empty)
                EmptyKind.FILTERED_EMPTY -> stringResource(R.string.async_filtered_empty)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DefaultAsyncLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DefaultAsyncError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.action_retry))
        }
    }
}
