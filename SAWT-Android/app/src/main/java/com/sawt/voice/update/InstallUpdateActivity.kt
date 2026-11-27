package com.sawt.voice.update

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** لا يبدأ تثبيت التحديث إلا عبر مثبت Android وبموافقة المستخدم. */
class InstallUpdateActivity : ComponentActivity() {
    private var pendingDownloadId: Long = -1L

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (Build.VERSION.SDK_INT >= 26 && packageManager.canRequestPackageInstalls()) {
            openInstaller()
        } else {
            finishWithMessage("لم يتم السماح بتثبيت التحديث")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDownloadId = intent.getLongExtra(UpdateConfig.DOWNLOAD_ID_EXTRA, -1L)
        if (pendingDownloadId < 0L) {
            finishWithMessage("ملف التحديث غير معروف")
            return
        }

        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, "اسمح لصَوْت بتثبيت التحديثات من هذا المصدر للمتابعة", Toast.LENGTH_LONG).show()
            installPermissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
            )
            return
        }
        openInstaller()
    }

    private fun openInstaller() {
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val apkUri = downloadManager.getUriForDownloadedFile(pendingDownloadId)
        if (apkUri == null) {
            finishWithMessage("تعذر العثور على ملف التحديث")
            return
        }
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
            finish()
        } catch (_: Exception) {
            finishWithMessage("تعذر فتح مثبت الحزم")
        }
    }

    private fun finishWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
}
