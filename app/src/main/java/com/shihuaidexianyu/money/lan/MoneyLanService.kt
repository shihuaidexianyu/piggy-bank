package com.shihuaidexianyu.money.lan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.shihuaidexianyu.money.MainActivity
import com.shihuaidexianyu.money.MoneyApplication
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.sync.SyncCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground LAN service. Sessions last up to [SESSION_DURATION_MILLIS]; when a session ends
 * on its own (expiry or system timeout) the user gets a one-tap restart notification, and
 * previously paired computers resume silently through their stored device credential, so a
 * restart never means re-typing anything. While running, the service is broadcast over
 * NSD (`_moneylink._tcp.`) so computers can discover it instead of reading an IP off the screen.
 */
class MoneyLanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: MoneyLanServer? = null
    private var expiryJob: Job? = null
    private var manualStopRequested = false
    private var lastAllowWrite = true
    private var nsdManager: NsdManager? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(MoneyLanRuntime.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        scope.launch {
            MoneyLanRuntime.state.collectLatest { state ->
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
                syncPairingAlert(notificationManager, state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                manualStopRequested = true
                getSystemService(NotificationManager::class.java).cancel(STOPPED_NOTIFICATION_ID)
                stopSelf()
            }
            ACTION_START -> startServer(intent.getBooleanExtra(EXTRA_ALLOW_WRITE, true))
            ACTION_APPROVE_PAIR -> intent.getStringExtra(EXTRA_PAIR_REQUEST_ID)?.let { requestId ->
                server?.approvePairing(requestId)
            }
            ACTION_DENY_PAIR -> intent.getStringExtra(EXTRA_PAIR_REQUEST_ID)?.let { requestId ->
                server?.denyPairing(requestId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        expiryJob?.cancel()
        unregisterDiscovery()
        MoneyLanRuntime.pairingResponder = null
        val wasRunning = server != null
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
        getSystemService(NotificationManager::class.java).cancel(PAIRING_NOTIFICATION_ID)
        if (wasRunning && !manualStopRequested) {
            // The session ended on its own (expiry or system timeout): offer a one-tap restart.
            // Paired computers resume with their stored credential, so nothing has to be retyped.
            notifyStopped()
        }
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
        manualStopRequested = false
        lastAllowWrite = allowWrite
        MoneyLanRuntime.publish(
            MoneyLanRuntimeState(status = MoneyLanServerStatus.STARTING, allowWrite = allowWrite),
        )
        val now = System.currentTimeMillis()
        val expiresAt = now + SESSION_DURATION_MILLIS
        val container = (application as MoneyApplication).container
        runCatching {
            val writeRateLimiter = MoneyLanWriteRateLimiter()
            MoneyLanServer(
                scope = scope,
                router = MoneyLanRequestRouter(container, writeRateLimiter),
                pairedDeviceStore = container.lanPairedDeviceStore,
                allowWrite = allowWrite,
                startedAt = now,
                expiresAt = expiresAt,
                writeRateLimiter = writeRateLimiter,
            ).also {
                server = it
                it.start()
            }
        }.onSuccess { started ->
            MoneyLanRuntime.pairingResponder = object : MoneyLanPairingResponder {
                override fun approve(pairRequestId: String): Boolean = started.approvePairing(pairRequestId)
                override fun deny(pairRequestId: String): Boolean = started.denyPairing(pairRequestId)
                override suspend fun revokeDevice(deviceId: String) = started.revokeDevice(deviceId)
            }
            registerDiscovery(started.port)
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
        getSystemService(NotificationManager::class.java).cancel(STOPPED_NOTIFICATION_ID)
        expiryJob = scope.launch {
            delay(SESSION_DURATION_MILLIS)
            stopSelf()
        }
    }

    private fun registerDiscovery(port: Int) {
        val manager = getSystemService(NsdManager::class.java) ?: return
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = DISCOVERY_SERVICE_NAME
            serviceType = DISCOVERY_SERVICE_TYPE
            setPort(port)
            setAttribute("proto", MONEY_LAN_PROTOCOL_VERSION.toString())
            setAttribute("caps", SyncCapabilities.ALL.joinToString(","))
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                // The system may have renamed the service to resolve a conflict.
                MoneyLanRuntime.update { it.copy(discoveryName = info.serviceName) }
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                // Discovery is a convenience; manual host/port pairing remains as fallback.
                Log.w(TAG, "NSD registration failed: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        runCatching {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdManager = manager
            nsdRegistrationListener = listener
        }.onFailure { error -> Log.w(TAG, "NSD registration failed", error) }
    }

    private fun unregisterDiscovery() {
        val manager = nsdManager
        val listener = nsdRegistrationListener
        nsdManager = null
        nsdRegistrationListener = null
        if (manager != null && listener != null) {
            runCatching { manager.unregisterService(listener) }
        }
    }

    private fun ensureChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.lan_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.lan_notification_channel_description)
            setShowBadge(false)
        }
        val alertsChannel = NotificationChannel(
            ALERTS_CHANNEL_ID,
            getString(R.string.lan_notification_alerts_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.lan_notification_alerts_channel_description)
        }
        notificationManager.createNotificationChannels(listOf(serviceChannel, alertsChannel))
    }

    /** Raise (or clear) the attention-worthy pairing-request alert as pending state changes. */
    private var pairingAlertRequestId: String? = null

    private fun syncPairingAlert(notificationManager: NotificationManager, state: MoneyLanRuntimeState) {
        val pendingId = state.pendingPairRequestId
        if (pendingId == null) {
            if (pairingAlertRequestId != null) {
                notificationManager.cancel(PAIRING_NOTIFICATION_ID)
                pairingAlertRequestId = null
            }
            return
        }
        pairingAlertRequestId = pendingId
        val clientName = state.pendingPairClientName ?: return
        notificationManager.notify(
            PAIRING_NOTIFICATION_ID,
            NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.lan_notification_pair_title))
                .setContentText(getString(R.string.lan_notification_pair_request, clientName))
                .setContentIntent(openAppIntent())
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .addAction(
                    0,
                    getString(R.string.lan_notification_approve),
                    pairActionIntent(ACTION_APPROVE_PAIR, pendingId, REQUEST_APPROVE),
                )
                .addAction(
                    0,
                    getString(R.string.lan_notification_deny),
                    pairActionIntent(ACTION_DENY_PAIR, pendingId, REQUEST_DENY),
                )
                .build(),
        )
    }

    private fun notifyStopped() {
        val restartIntent = PendingIntent.getService(
            this,
            REQUEST_RESTART,
            Intent(this, MoneyLanService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ALLOW_WRITE, lastAllowWrite),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        getSystemService(NotificationManager::class.java).notify(
            STOPPED_NOTIFICATION_ID,
            NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.lan_notification_stopped_title))
                .setContentText(getString(R.string.lan_notification_stopped))
                .setContentIntent(openAppIntent())
                .setAutoCancel(true)
                .addAction(
                    0,
                    getString(R.string.lan_notification_restart),
                    restartIntent,
                )
                .build(),
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun pairActionIntent(action: String, pairRequestId: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, MoneyLanService::class.java)
                .setAction(action)
                .putExtra(EXTRA_PAIR_REQUEST_ID, pairRequestId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun buildNotification(state: MoneyLanRuntimeState): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MoneyLanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val content = when (state.status) {
            MoneyLanServerStatus.RUNNING -> when {
                state.pendingPairClientName != null ->
                    getString(R.string.lan_notification_pair_request, state.pendingPairClientName)
                state.pairedClientName == null ->
                    getString(R.string.lan_notification_waiting, state.port ?: "—")
                else -> getString(
                    R.string.lan_notification_connected,
                    state.pairedClientName,
                    getString(if (state.allowWrite) R.string.lan_mode_read_write else R.string.lan_mode_read_only),
                )
            }
            MoneyLanServerStatus.ERROR -> state.errorMessage ?: getString(R.string.lan_service_error)
            else -> getString(R.string.lan_port_starting)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.lan_notification_title))
            .setContentText(content)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.lan_notification_stop), stopIntent)
        val pendingId = state.pendingPairRequestId
        if (state.status == MoneyLanServerStatus.RUNNING && pendingId != null) {
            builder
                .addAction(
                    0,
                    getString(R.string.lan_notification_approve),
                    pairActionIntent(ACTION_APPROVE_PAIR, pendingId, REQUEST_APPROVE),
                )
                .addAction(
                    0,
                    getString(R.string.lan_notification_deny),
                    pairActionIntent(ACTION_DENY_PAIR, pendingId, REQUEST_DENY),
                )
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "MoneyLanService"
        private const val ACTION_START = "com.shihuaidexianyu.money.lan.START"
        private const val ACTION_STOP = "com.shihuaidexianyu.money.lan.STOP"
        private const val ACTION_APPROVE_PAIR = "com.shihuaidexianyu.money.lan.APPROVE_PAIR"
        private const val ACTION_DENY_PAIR = "com.shihuaidexianyu.money.lan.DENY_PAIR"
        private const val EXTRA_ALLOW_WRITE = "allow_write"
        private const val EXTRA_PAIR_REQUEST_ID = "pair_request_id"
        private const val CHANNEL_ID = "money_lan_ai"
        private const val ALERTS_CHANNEL_ID = "money_lan_ai_alerts"
        private const val NOTIFICATION_ID = 4103
        private const val PAIRING_NOTIFICATION_ID = 4104
        private const val STOPPED_NOTIFICATION_ID = 4105
        private const val REQUEST_APPROVE = 2
        private const val REQUEST_DENY = 3
        private const val REQUEST_RESTART = 4

        /** Matches the foreground-service quota budget for dataSync (6h/day) with no margin use. */
        private const val SESSION_DURATION_MILLIS = 6 * 60 * 60 * 1_000L

        private const val DISCOVERY_SERVICE_TYPE = "_moneylink._tcp."
        private val DISCOVERY_SERVICE_NAME =
            "Money AI " + Build.MODEL.filter { it.isLetterOrDigit() }.take(12).ifBlank { "phone" }

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
