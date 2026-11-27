package com.sawt.voice.update

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sawt.voice.R

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId < 0L) return

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = manager.query(query) ?: return
        val successful = cursor.use {
            it.moveToFirst() && it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
        }
        if (!successful) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            notificationManager.createNotificationChannel(
                NotificationChannel(UpdateConfig.CHANNEL_ID, "تحديثات صَوْت", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val installIntent = Intent(context, InstallUpdateActivity::class.java).apply {
            putExtra(UpdateConfig.DOWNLOAD_ID_EXTRA, downloadId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            downloadId.toInt(),
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, UpdateConfig.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("تحديث صَوْت جاهز")
            .setContentText("اضغط لمراجعة التثبيت")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            notificationManager.notify(downloadId.toInt(), notification)
        } catch (_: SecurityException) {
            // Android 13+: if notifications are denied, DownloadManager still keeps its own download record.
        }
    }
}
