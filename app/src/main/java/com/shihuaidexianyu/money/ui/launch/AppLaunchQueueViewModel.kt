package com.shihuaidexianyu.money.ui.launch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.shihuaidexianyu.money.domain.launch.AppLaunchRequest
import com.shihuaidexianyu.money.domain.launch.AppLaunchRequestQueue
import java.util.ArrayList

class AppLaunchQueueViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val queue = AppLaunchRequestQueue(
        initialPending = savedStateHandle.get<ArrayList<AppLaunchRequest>>(PENDING_KEY).orEmpty(),
        initialAcknowledged = savedStateHandle
            .get<ArrayList<String>>(ACKNOWLEDGED_KEY)
            .orEmpty()
            .toSet(),
        onChanged = { pending, acknowledged ->
            savedStateHandle[PENDING_KEY] = ArrayList(pending)
            savedStateHandle[ACKNOWLEDGED_KEY] = ArrayList(acknowledged)
        },
    )

    val pending = queue.pending

    fun offer(request: AppLaunchRequest) = queue.offer(request)

    fun acknowledge(token: String) = queue.acknowledge(token)

    private companion object {
        const val PENDING_KEY = "app_launch_pending"
        const val ACKNOWLEDGED_KEY = "app_launch_acknowledged"
    }
}
