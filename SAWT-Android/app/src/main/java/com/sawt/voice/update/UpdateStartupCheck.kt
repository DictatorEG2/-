package com.sawt.voice.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sawt.voice.MainActivity
import com.sawt.voice.R

object UpdateStartupCheck {
    private const val PREFS = "sawt_update_state"
    private const val LAST_CHECK = "last_check_at"
    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    fun shouldCheck(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(LAST_CHECK, 0L)
        return System.currentTimeMillis() - last >= CHECK_INTERVAL_MS
    }

    fun markChecked(context: Context, succeeded: Boolean) {
        val retryDelay = if (succeeded) CHECK_INTERVAL_MS else 60L * 60L * 1000L
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(LAST_CHECK, System.currentTimeMillis() - (CHECK_INTERVAL_MS - retryDelay))
            .apply()
    }

    fun cacheRelease(context: Context, release: GithubUpdateClient.Release) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("release_tag", release.tag)
            .putString("release_name", release.name)
            .putString("release_notes", release.notes)
            .putString("release_apk_url", release.apkUrl)
            .putString("release_page_url", release.pageUrl)
            .apply()
    }

    fun cachedRelease(context: Context): GithubUpdateClient.Release? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tag = prefs.getString("release_tag", null)?.takeIf { it.isNotBlank() } ?: return null
        val apkUrl = prefs.getString("release_apk_url", null)?.takeIf { it.startsWith("https://github.com/") } ?: return null
        val pageUrl = prefs.getString("release_page_url", null)?.takeIf { it.startsWith("https://github.com/") } ?: return null
        return GithubUpdateClient.Release(
            tag = tag,
            name = prefs.getString("release_name", tag) ?: tag,
            notes = prefs.getString("release_notes", "").orEmpty(),
            apkUrl = apkUrl,
            pageUrl = pageUrl,
        )
    }

    fun clearCachedRelease(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("release_tag")
            .remove("release_name")
            .remove("release_notes")
            .remove("release_apk_url")
            .remove("release_page_url")
            .apply()
    }

    fun notifyAvailable(context: Context, tag: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(UpdateConfig.CHANNEL_ID, "تحديثات صَوْت", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val openSettings = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_UPDATE_SETTINGS, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            4102,
            openSettings,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, UpdateConfig.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("يتوفر تحديث جديد لصَوْت")
            .setContentText("الإصدار $tag جاهز للتنزيل")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            manager.notify(4102, notification)
        } catch (_: SecurityException) {
            // Notifications may be disabled by the user.
        }
    }
}
