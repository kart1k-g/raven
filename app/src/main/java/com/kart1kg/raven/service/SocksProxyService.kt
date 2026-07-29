package com.kart1kg.raven.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kart1kg.raven.MainActivity
import com.kart1kg.raven.R
import com.kart1kg.raven.engine.Socks5Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "SocksProxyService"
private const val CHANNEL_ID = "raven_proxy_channel"
private const val NOTIFICATION_ID = 1
private const val WAKE_LOCK_TAG = "Raven::ProxyWakeLock"

/**
 * Foreground service that keeps the SOCKS5 proxy server alive.
 *
 * Uses a partial wake lock to prevent the CPU from sleeping while
 * clients are connected and actively transferring data.
 */
class SocksProxyService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        /** Shared server instance, accessible from the ViewModel. */
        val server = Socks5Server()

        const val EXTRA_PORT = "extra_port"
        const val ACTION_STOP = "com.kart1kg.raven.STOP_PROXY"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()

        // Update notification as connection count changes
        serviceScope.launch {
            server.serverState.collectLatest { state ->
                if (state.isRunning) {
                    updateNotification(state.activeConnections, state.port)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopProxy()
            return START_NOT_STICKY
        }

        val port = intent?.getIntExtra(EXTRA_PORT, 1080) ?: 1080
        startForeground(NOTIFICATION_ID, buildNotification(0, port))
        server.start(port)
        Log.i(TAG, "Proxy service started on port $port")

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopProxy()
        super.onDestroy()
    }

    private fun stopProxy() {
        server.stop()
        releaseWakeLock()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Proxy service destroyed")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notification
    // ──────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Raven Proxy",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when the SOCKS5 proxy is active"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(activeConnections: Int, port: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPending = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SocksProxyService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Raven Proxy Active")
            .setContentText("Port $port · $activeConnections active connection(s)")
            .setSmallIcon(R.drawable.launcher_icon_foreground)
            .setOngoing(true)
            .setContentIntent(openAppPending)
            .addAction(0, "Stop", stopPending)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(activeConnections: Int, port: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(activeConnections, port))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Wake Lock
    // ──────────────────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
