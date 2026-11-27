package com.sawt.voice.engine

import android.content.Context
import androidx.core.content.ContextCompat
import com.sawt.voice.AppGraph
import com.sawt.voice.service.ListeningService
import com.sawt.voice.util.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class VoiceStatus { IDLE, STARTING, LISTENING, PROCESSING, ERROR }

data class VoiceUiState(
    val status: VoiceStatus = VoiceStatus.IDLE,
    val partial: String = "",
    val lastUtterance: String = "",
    val lastResult: String = "",
    val error: String? = null,
    val strictLocal: Boolean = true,
)

/** نقطة التحكم الوحيدة ببدء/إيقاف الاستماع. تمنع تشغيل أكثر من جلسة في الوقت نفسه. */
class VoiceController(private val app: Context) {
    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    val isActive: Boolean
        get() = _state.value.status.let {
            it == VoiceStatus.STARTING || it == VoiceStatus.LISTENING || it == VoiceStatus.PROCESSING
        }

    fun update(block: (VoiceUiState) -> VoiceUiState) = _state.update(block)

    fun start(): Boolean {
        if (isActive) return true
        if (!Permissions.hasMic(app)) {
            update { it.copy(status = VoiceStatus.ERROR, error = "صلاحية الميكروفون غير ممنوحة") }
            return false
        }
        update { it.copy(status = VoiceStatus.STARTING, error = null, partial = "", lastResult = "") }
        return try {
            ContextCompat.startForegroundService(app, ListeningService.startIntent(app))
            true
        } catch (t: Throwable) {
            AppGraph.errors.log("start-service", t)
            update { it.copy(status = VoiceStatus.ERROR, error = "تعذر بدء خدمة الاستماع") }
            false
        }
    }

    fun stop() {
        val service = ListeningService.instance
        if (service != null) service.stopSession()
        else update { it.copy(status = VoiceStatus.IDLE, partial = "") }
    }

    fun toggle() {
        if (isActive) stop() else start()
    }
}
