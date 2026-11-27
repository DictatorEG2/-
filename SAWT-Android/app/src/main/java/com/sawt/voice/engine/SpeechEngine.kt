package com.sawt.voice.engine

sealed interface SpeechOutcome {
    data class Text(val alternatives: List<String>) : SpeechOutcome
    data object NoSpeech : SpeechOutcome
    data class Retry(val reason: String) : SpeechOutcome
    data class Fatal(val reason: String) : SpeechOutcome
}

/**
 * واجهة محرك التعرف. المنفَّذ حاليًا: [AndroidSpeechEngine] (SpeechRecognizer).
 * لإضافة sherpa-onnx لاحقًا نفّذ هذه الواجهة نفسها (راجع ARCHITECTURE.md) دون تغيير بقية التطبيق.
 */
interface SpeechEngine {
    val id: String

    /** هل التعرف محلي مضمونًا بالكامل؟ */
    val strictLocal: Boolean

    /** يعيد رسالة عربية توضح لماذا لا يمكن التشغيل، أو null إذا كان المحرك جاهزًا. */
    fun availabilityProblem(): String?

    /** جلسة استماع واحدة. يجب استدعاؤها من الخيط الرئيسي. */
    suspend fun listenOnce(onPartial: (String) -> Unit): SpeechOutcome

    fun cancel()
    fun release()
}
