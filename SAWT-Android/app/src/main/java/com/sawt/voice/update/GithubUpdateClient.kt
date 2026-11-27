package com.sawt.voice.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** يقرأ إصدارات GitHub العامة فقط، ولا يرسل بيانات التطبيق أو بيانات اعتماد. */
object GithubUpdateClient {
    data class Release(
        val tag: String,
        val name: String,
        val notes: String,
        val apkUrl: String,
        val pageUrl: String,
    )

    sealed interface CheckResult {
        data class Available(val release: Release) : CheckResult
        data object UpToDate : CheckResult
        data object NoRelease : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    fun check(context: Context): CheckResult {
        val endpoint = "https://api.github.com/repos/${UpdateConfig.REPOSITORY}/releases/latest"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SAWT-Android-Updater")
        }

        return try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                if (status == HttpURLConnection.HTTP_NOT_FOUND) return CheckResult.NoRelease
                return CheckResult.Failed("تعذر فحص التحديثات (HTTP $status). حاول مرة أخرى لاحقًا.")
            }
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(payload)
            val tag = json.optString("tag_name").trim()
            val pageUrl = json.optString("html_url").trim()
            val assets = json.optJSONArray("assets")
            val apkUrl = (0 until (assets?.length() ?: 0))
                .asSequence()
                .mapNotNull { assets?.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
                .orEmpty()

            if (tag.isBlank() || !apkUrl.startsWith("https://github.com/") || !pageUrl.startsWith("https://github.com/")) {
                return CheckResult.Failed("الإصدار الأخير لا يحتوي على رابط GitHub وملف APK صالحين.")
            }

            val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            val comparison = VersionComparator.compare(tag, currentVersion)
                ?: return CheckResult.Failed("تعذر مقارنة أرقام الإصدارات. استخدم وسومًا رقمية مثل v0.2.0.")
            if (comparison <= 0) CheckResult.UpToDate
            else CheckResult.Available(
                Release(
                    tag = tag,
                    name = json.optString("name").ifBlank { tag },
                    notes = json.optString("body").take(1200),
                    apkUrl = apkUrl,
                    pageUrl = pageUrl,
                ),
            )
        } catch (_: Exception) {
            CheckResult.Failed("تعذر الاتصال بـ GitHub. حاول مرة أخرى عند توفر الإنترنت.")
        } finally {
            connection.disconnect()
        }
    }

    fun download(context: Context, release: Release): Long {
        require(release.apkUrl.startsWith("https://github.com/")) { "Unsupported download host" }
        val safeTag = release.tag.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val request = DownloadManager.Request(Uri.parse(release.apkUrl)).apply {
            setTitle("تحديث صَوْت ${release.tag}")
            setDescription("جارٍ تنزيل تحديث التطبيق")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "sawt-$safeTag.apk")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }
        val downloadId = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        check(downloadId >= 0L) { "DownloadManager rejected the request" }
        return downloadId
    }

}
