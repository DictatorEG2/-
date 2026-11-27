package com.sawt.voice.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * سجل أخطاء تقني محلي صغير (حتى 100 كيلوبايت). لا يُسجَّل فيه أي صوت أو نص منطوق أو محتوى شاشة،
 * فقط وسم المكوّن ونوع الاستثناء ورسالته المقتطعة.
 */
class ErrorLog(context: Context) {
    private val file = File(context.filesDir, "logs/errors.log")
    private val executor = Executors.newSingleThreadExecutor()

    fun log(tag: String, throwable: Throwable? = null, note: String? = null) {
        val now = System.currentTimeMillis()
        val kind = throwable?.javaClass?.simpleName
        val msg = throwable?.message?.take(160)
        executor.execute {
            try {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))
                val line = buildString {
                    append(stamp).append(" [").append(tag).append("]")
                    if (note != null) append(' ').append(note)
                    if (kind != null) append(' ').append(kind)
                    if (msg != null) append(": ").append(msg)
                    append('\n')
                }
                file.parentFile?.mkdirs()
                if (file.exists() && file.length() > MAX_BYTES) file.delete()
                file.appendText(line)
            } catch (_: Throwable) {
                // لا نفشل بسبب السجل
            }
        }
    }

    fun clear() {
        executor.execute { runCatching { file.delete() } }
    }

    private companion object {
        const val MAX_BYTES = 100_000L
    }
}
