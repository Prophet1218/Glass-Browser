package com.example.glassbrowser

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * A böngésző saját letöltés-értesítései (a böngésző ikonjával az állapotsoron, nem a rendszer
 * letöltéskezelőjének kék ikonjával): folyamatjelző, amíg fut, és "kész" értesítés a végén,
 * amire koppintva a Letöltések nyílik meg.
 */
object DownloadNotifier {
    private const val CHANNEL = "downloads"
    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var running = false
    private val shown = HashSet<Long>()

    private fun allowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, L.t("Letöltések"), NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun openDownloads(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, DownloadsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Egy letöltés elindult: a figyelés (ha még nem fut) elindul. */
    fun started(ctx: Context) {
        app = ctx.applicationContext
        if (!running) {
            running = true
            main.post(tick)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val ctx = app ?: run { running = false; return }
            val more = update(ctx)
            if (more) main.postDelayed(this, 700L) else running = false
        }
    }

    /** Igaz, ha van még futó letöltés. */
    private fun update(ctx: Context): Boolean {
        val dm = ctx.getSystemService(DownloadManager::class.java) ?: return false
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        val ids = Downloads.ids(ctx)
        if (ids.isEmpty()) return false
        channel(ctx)
        val canPost = allowed(ctx)
        var active = false
        try {
            dm.query(DownloadManager.Query().setFilterById(*ids.take(30).toLongArray()))?.use { c ->
                val iId = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val iSt = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val iT = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val iG = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val iTitle = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val st = c.getInt(iSt)
                    val title = c.getString(iTitle).orEmpty()
                    val tag = id.toInt()
                    when (st) {
                        DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                            active = true
                            shown.add(id)
                            if (!canPost) continue
                            val total = c.getLong(iT)
                            val got = c.getLong(iG)
                            val b = NotificationCompat.Builder(ctx, CHANNEL)
                                .setSmallIcon(R.drawable.ic_stat_glass)
                                .setContentTitle(title)
                                .setContentText(if (st == DownloadManager.STATUS_PAUSED) L.t("Szüneteltetve") else L.t("Letöltés…"))
                                .setOngoing(true)
                                .setOnlyAlertOnce(true)
                                .setSilent(true)
                                .setContentIntent(openDownloads(ctx))
                            if (total > 0) b.setProgress(1000, (got * 1000 / total).toInt().coerceIn(0, 1000), false)
                            else b.setProgress(0, 0, true)
                            nm.notify(tag, b.build())
                        }
                        DownloadManager.STATUS_SUCCESSFUL, DownloadManager.STATUS_FAILED -> {
                            if (id in shown) {
                                shown.remove(id)
                                if (canPost) {
                                    val ok = st == DownloadManager.STATUS_SUCCESSFUL
                                    nm.notify(
                                        tag,
                                        NotificationCompat.Builder(ctx, CHANNEL)
                                            .setSmallIcon(R.drawable.ic_stat_glass)
                                            .setContentTitle(title)
                                            .setContentText(if (ok) L.t("A letöltés kész") else L.t("A letöltés nem sikerült"))
                                            .setAutoCancel(true)
                                            .setContentIntent(openDownloads(ctx))
                                            .build()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return false
        }
        return active
    }
}
