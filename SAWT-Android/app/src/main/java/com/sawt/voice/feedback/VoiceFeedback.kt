package com.sawt.voice.feedback

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** نطق النتائج بالعربية (TextToSpeech) واهتزاز اختياري. */
class VoiceFeedback(private val context: Context) {
    private var tts: TextToSpeech? = null
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var ready = false
    @Volatile private var arabicAvailable = false
    @Volatile var isSpeaking: Boolean = false
        private set
    private var pending: String? = null

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("ar"))
                arabicAvailable = r != null && r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { isSpeaking = false }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { isSpeaking = false }
                })
                ready = true
                pending?.let { p ->
                    pending = null
                    speak(p)
                }
            }
        }
    }

    /** يعيد false إذا لم يتوفر صوت عربي في الهاتف. */
    fun speak(text: String): Boolean {
        ensureTts()
        if (!ready) {
            pending = text
            return true
        }
        if (!arabicAvailable) return false
        isSpeaking = true
        val result = tts?.speak(text.take(MAX_CHARS), TextToSpeech.QUEUE_FLUSH, null, "sawt-${System.nanoTime()}")
        if (result != TextToSpeech.SUCCESS) {
            isSpeaking = false
            return false
        }
        main.postDelayed({ isSpeaking = false }, SAFETY_TIMEOUT_MS)
        return true
    }

    fun vibrate(ms: Long = 40) {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Throwable) {
            // الاهتزاز اختياري
        }
    }

    fun stop() {
        isSpeaking = false
        tts?.stop()
    }

    private companion object {
        const val MAX_CHARS = 900
        const val SAFETY_TIMEOUT_MS = 25_000L
    }
}
