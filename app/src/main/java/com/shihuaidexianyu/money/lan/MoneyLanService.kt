package com.shihuaidexianyu.money.lan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.shihuaidexianyu.money.MainActivity
import com.shihuaidexianyu.money.MoneyApplication
import com.shihuaidexianyu.money.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MoneyLanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: MoneyLanServer? = null
    private var expiryJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(MoneyLanRuntime.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        scope.launch {
            MoneyLanRuntime.state.collectLatest { state ->
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(state))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START -> startServer(intent.getBooleanExtra(EXTRA_ALLOW_WRITE, true))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        expiryJob?.cancel()
        server?.close()
        server = null
        val terminalState = MoneyLanRuntime.state.value
        MoneyLanRuntime.publish(
            if (terminalState.status == MoneyLanServerStatus.ERROR) {
                MoneyLanRuntimeState(
                    status = MoneyLanServerStatus.ERROR,
                    errorMessage = terminalState.errorMessage,
                )
            } else {
                MoneyLanRuntimeState()
            },
        )
        scope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        MoneyLanRuntime.update {
            it.copy(
                status = MoneyLanServerStatus.ERROR,
                errorMessage = getString(R.string.lan_session_timed_out),
            )
        }
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startServer(allowWrite: Boolean) {
        if (server != null) return
        MoneyLanRuntime.publish(
            MoneyLanRuntimeState(status = MoneyLanServerStatus.STARTING, allowWrite = allowWrite),
        )
        val now = System.currentTimeMillis()
        val expiresAt = now + SESSION_DURATION_MILLIS
        val container = (application as MoneyApplication).container
        runCatching {
            MoneyLanServer(
                scope = scope,
                router = MoneyLanRequestRouter(container),
                allowWrite = allowWrite,
                startedAt = now,
                expiresAt = expiresAt,
            ).also {
                server = it
                it.start()
            }
        }.onFailure { error ->
            MoneyLanRuntime.publish(
                MoneyLanRuntimeState(
                    status = MoneyLanServerStatus.ERROR,
                    allowWrite = allowWrite,
                    errorMessage = error.message ?: getString(R.string.lan_port_start_failed),
                ),
            )
            stopSelf()
            return
        }
        expiryJob = scope.launch {
            delay(SESSION_DURATION_MILLIS)
            stopSelf()
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.lan_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.lan_notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(state: MoneyLanRuntimeState): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MoneyLanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val content = when (state.status) {
            MoneyLanServerStatus.RUNNING -> if (state.pairedClientName == null) {
                getString(R.string.lan_notification_waiting, state.port ?: "—")
            } else {
                getString(
                    R.string.lan_notification_connected,
                    state.pairedClientName,
                    getString(if (state.allowWrite) R.string.lan_mode_read_write else R.string.lan_mode_read_only),
                )
            }
            MoneyLanServerStatus.ERROR -> state.errorMessage ?: getString(R.string.lan_service_error)
            else -> getString(R.string.lan_port_starting)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.lan_notification_title))
            .setContentText(content)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.lan_notification_stop), stopIntent)
            .build()
    }

    companion object {
        private const val ACTION_START = "com.shihuaidexianyu.money.lan.START"
        private const val ACTION_STOP = "com.shihuaidexianyu.money.lan.STOP"
        private const val EXTRA_ALLOW_WRITE = "allow_write"
        private const val CHANNEL_ID = "money_lan_ai"
        private const val NOTIFICATION_ID = 4103
        private const val SESSION_DURATION_MILLIS = 4 * 60 * 60 * 1_000L

        fun start(context: Context, allowWrite: Boolean) {
            val intent = Intent(context, MoneyLanService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ALLOW_WRITE, allowWrite)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MoneyLanService::class.java).setAction(ACTION_STOP))
        }
    }
}
