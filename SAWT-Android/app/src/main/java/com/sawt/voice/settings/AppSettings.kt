package com.sawt.voice.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Dialect(val tag: String, val title: String) {
    EGYPT("ar-EG", "اللهجة المصرية"),
    MSA("ar-SA", "الفصحى"),
}

data class AppSettings(
    val dialect: Dialect = Dialect.EGYPT,
    /** الوضع المحلي: لا يُسمح بأي استخدام للشبكة أثناء التعرف (مضمون على Android 13+ فقط). */
    val offlineOnly: Boolean = true,
    val requireWakeWord: Boolean = false,
    val speakFeedback: Boolean = true,
    val vibrate: Boolean = true,
    val historyEnabled: Boolean = true,
    val autoStopSeconds: Int = 90,
    val floatingEnabled: Boolean = false,
    val floatSizeDp: Int = 56,
    val floatAlpha: Float = 0.9f,
    val floatX: Int = -1,
    val floatY: Int = -1,
)

/** تخزين الإعدادات في SharedPreferences مع تدفق StateFlow. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("sawt_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    @Synchronized
    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(_settings.value)
        _settings.value = next
        save(next)
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            dialect = runCatching { Dialect.valueOf(prefs.getString("dialect", d.dialect.name) ?: d.dialect.name) }
                .getOrDefault(d.dialect),
            offlineOnly = prefs.getBoolean("offlineOnly", d.offlineOnly),
            requireWakeWord = prefs.getBoolean("requireWakeWord", d.requireWakeWord),
            speakFeedback = prefs.getBoolean("speakFeedback", d.speakFeedback),
            vibrate = prefs.getBoolean("vibrate", d.vibrate),
            historyEnabled = prefs.getBoolean("historyEnabled", d.historyEnabled),
            autoStopSeconds = prefs.getInt("autoStopSeconds", d.autoStopSeconds),
            floatingEnabled = prefs.getBoolean("floatingEnabled", d.floatingEnabled),
            floatSizeDp = prefs.getInt("floatSizeDp", d.floatSizeDp),
            floatAlpha = prefs.getFloat("floatAlpha", d.floatAlpha),
            floatX = prefs.getInt("floatX", d.floatX),
            floatY = prefs.getInt("floatY", d.floatY),
        )
    }

    private fun save(s: AppSettings) {
        prefs.edit()
            .putString("dialect", s.dialect.name)
            .putBoolean("offlineOnly", s.offlineOnly)
            .putBoolean("requireWakeWord", s.requireWakeWord)
            .putBoolean("speakFeedback", s.speakFeedback)
            .putBoolean("vibrate", s.vibrate)
            .putBoolean("historyEnabled", s.historyEnabled)
            .putInt("autoStopSeconds", s.autoStopSeconds)
            .putBoolean("floatingEnabled", s.floatingEnabled)
            .putInt("floatSizeDp", s.floatSizeDp)
            .putFloat("floatAlpha", s.floatAlpha)
            .putInt("floatX", s.floatX)
            .putInt("floatY", s.floatY)
            // SharedPreferences.Editor.apply(): حفظ غير متزامن؛ ليس استدعاءً لـ Kotlin scope function.
            .apply()
    }
}
