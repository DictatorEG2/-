package com.sawt.voice.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * محرك التعرف المبني على SpeechRecognizer في أندرويد.
 * - Android 13+ والوضع المحلي: createOnDeviceSpeechRecognizer (لا اتصال بالشبكة).
 * - غير ذلك: createSpeechRecognizer مع EXTRA_PREFER_OFFLINE عند الوضع المحلي (تفضيل بلا ضمان).
 */
class AndroidSpeechEngine(
    private val context: Context,
    private val languageTag: String,
    private val offlineOnly: Boolean,
) : SpeechEngine {

    override val id = "android-speech-recognizer"
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    private fun onDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override val strictLocal: Boolean get() = offlineOnly && onDeviceAvailable()

    override fun availabilityProblem(): String? {
        if (offlineOnly && Build.VERSION.SDK_INT >= 33) {
            return if (onDeviceAvailable()) null
            else "التعرف المحلي غير متاح على هذا الهاتف. ثبّت حزمة العربية للتعرف دون إنترنت من إعدادات Google أو فعّل وضع الاتصال."
        }
        return if (SpeechRecognizer.isRecognitionAvailable(context)) null
        else "خدمة التعرف على الكلام غير متوفرة على هذا الهاتف."
    }

    private fun obtain(): SpeechRecognizer {
        recognizer?.let { return it }
        val r = if (Build.VERSION.SDK_INT >= 33 && offlineOnly && onDeviceAvailable()) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = r
        return r
    }

    private fun reset() {
        try { recognizer?.destroy() } catch (_: Throwable) {}
        recognizer = null
    }

    override suspend fun listenOnce(onPartial: (String) -> Unit): SpeechOutcome =
        suspendCancellableCoroutine { cont ->
            fun finish(o: SpeechOutcome) {
                if (cont.isActive) cont.resume(o)
            }
            val rec = try {
                obtain()
            } catch (_: Throwable) {
                finish(SpeechOutcome.Fatal("تعذر إنشاء محرك التعرف على الكلام"))
                return@suspendCancellableCoroutine
            }
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onError(error: Int) {
                    finish(mapError(error))
                }
                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty().filter { it.isNotBlank() }
                    finish(if (list.isEmpty()) SpeechOutcome.NoSpeech else SpeechOutcome.Text(list))
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.let(onPartial)
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offlineOnly)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            cont.invokeOnCancellation {
                main.post { try { rec.cancel() } catch (_: Throwable) {} }
            }
            try {
                rec.startListening(intent)
            } catch (_: Throwable) {
                reset()
                finish(SpeechOutcome.Retry("تعذر بدء جلسة الاستماع"))
            }
        }

    private fun mapError(code: Int): SpeechOutcome = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechOutcome.NoSpeech
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechOutcome.Fatal("صلاحية الميكروفون مرفوضة")
        12, 13 -> SpeechOutcome.Fatal("اللغة العربية غير متاحة لمحرك التعرف. ثبّت حزمة العربية من إعدادات Google")
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
            if (offlineOnly) SpeechOutcome.Fatal("التعرف المحلي يحتاج حزمة العربية دون إنترنت، وهي غير مثبتة. ثبّتها أو فعّل وضع الاتصال")
            else SpeechOutcome.Retry("مشكلة اتصال")
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT, 11 -> {
            reset()
            SpeechOutcome.Retry("المحرك مشغول")
        }
        else -> SpeechOutcome.Retry("خطأ رقم $code")
    }

    override fun cancel() {
        main.post { try { recognizer?.cancel() } catch (_: Throwable) {} }
    }

    override fun release() {
        reset()
    }
}
