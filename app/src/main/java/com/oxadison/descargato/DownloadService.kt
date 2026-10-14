// Código creado por Oxadison con amor para Suidelame ♥

package com.oxadison.descargato

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(
            NOTIF_ID,
            buildNotification("Preparando descarga…", 0, indeterminate = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        observer?.cancel()
        observer = scope.launch {
            DownloadEngine.state.collect { s ->
                if (!s.busy) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    notifyFinal(s)
                    stopSelf()
                } else {
                    val pct = s.progress.toInt().coerceIn(0, 100)
                    val text = if (s.phase == Phase.DOWNLOADING && s.progress > 0f) {
                        "Descargando… $pct%"
                    } else {
                        s.status.ifBlank { "Descargando…" }
                    }
                    notificationManager().notify(
                        NOTIF_ID,
                        buildNotification(text, pct, indeterminate = s.progress <= 0f)
                    )
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Descargas", NotificationManager.IMPORTANCE_LOW)
        notificationManager().createNotificationChannel(channel)
    }

    private fun buildNotification(text: String, progress: Int, indeterminate: Boolean): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("DescarGato")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, indeterminate)
            .build()

    private fun notifyFinal(s: UiState) {
        val text = when (s.phase) {
            Phase.DONE -> "Descarga completada"
            Phase.ERROR -> "La descarga falló"
            Phase.CANCELLED -> return
            else -> return
        }
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("DescarGato")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        notificationManager().notify(NOTIF_ID + 1, n)
    }

    private companion object {
        const val CHANNEL_ID = "descargas"
        const val NOTIF_ID = 1001
    }
}
