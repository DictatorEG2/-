package com.sawt.voice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.sawt.voice.AppGraph
import com.sawt.voice.MainActivity
import com.sawt.voice.R
import com.sawt.voice.engine.AndroidSpeechEngine
import com.sawt.voice.engine.SpeechEngine
import com.sawt.voice.engine.SpeechOutcome
import com.sawt.voice.engine.VoiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * خدمة أمامية (نوع ميكروفون) تُظهر إشعارًا دائمًا مع زر إيقاف طوال فترة الاستماع.
 * جلسة واحدة فقط في كل مرة، وتتوقف تلقائيًا بعد فترة خمول لتوفير البطارية.
 */
class ListeningService : Service() {

    companion object {
        const val ACTION_START = "com.sawt.voice.action.START"
        const val ACTION_STOP = "com.sawt.voice.action.STOP"
        private const val CHANNEL_ID = "sawt_listening"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_FAILURES = 5
        private const val LISTEN_TIMEOUT_MS = 25_000L

        @Volatile
        var instance: ListeningService? = null
            private set

        fun startIntent(context: Context): Intent =
            Intent(context, ListeningService::class.java).setAction(ACTION_START)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val running = AtomicBoolean(false)
    private val finished = AtomicBoolean(false)
    private var engine: SpeechEngine? = null
    private var sessionJob: Job? = null
    private var lastActivity = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSession()
            return START_NOT_STICKY
        }
        if (!enterForeground()) return START_NOT_STICKY
        if (running.compareAndSet(false, true)) {
            finished.set(false)
            beginLoop()
        }
        return START_NOT_STICKY
    }

    private fun enterForeground(): Boolean = try {
        ensureChannel()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        true
    } catch (t: Throwable) {
        AppGraph.errors.log("foreground", t)
        fail("منع النظام تشغيل الميكروفون من الخلفية. افتح التطبيق وابدأ الاستماع منه")
        false
    }

    private fun beginLoop() {
        val voice = AppGraph.voice
        val settings = AppGraph.settings.current
        val eng = AndroidSpeechEngine(applicationContext, settings.dialect.tag, settings.offlineOnly)
        engine = eng
        eng.availabilityProblem()?.let {
            fail(it)
            return
        }
        voice.update { it.copy(strictLocal = eng.strictLocal) }
        lastActivity = SystemClock.elapsedRealtime()

        sessionJob = scope.launch {
            var failures = 0
            while (isActive && running.get()) {
                if (AppGraph.feedback.isSpeaking) {
                    delay(250)
                    continue
                }
                voice.update { it.copy(status = VoiceStatus.LISTENING, partial = "") }
                val outcome = withTimeoutOrNull(LISTEN_TIMEOUT_MS) {
                    eng.listenOnce { p -> voice.update { s -> s.copy(partial = p) } }
                } ?: SpeechOutcome.Retry("انتهت مهلة الاستماع").also { eng.cancel() }

                when (outcome) {
                    is SpeechOutcome.Text -> {
                        failures = 0
                        lastActivity = SystemClock.elapsedRealtime()
                        process(outcome.alternatives)
                    }
                    SpeechOutcome.NoSpeech -> failures = 0
                    is SpeechOutcome.Retry -> {
                        failures++
                        if (failures > MAX_FAILURES) {
                            fail("تعذر تشغيل التعرف على الكلام (${outcome.reason})")
                            return@launch
                        }
                        delay(400L * failures)
                    }
                    is SpeechOutcome.Fatal -> {
                        fail(outcome.reason)
                        return@launch
                    }
                }
                if (!running.get()) break
                val idleLimit = AppGraph.settings.current.autoStopSeconds * 1000L
                if (SystemClock.elapsedRealtime() - lastActivity > idleLimit) {
                    voice.update { it.copy(lastResult = "توقف الاستماع تلقائيًا لتوفير البطارية") }
                    break
                }
            }
            finish()
        }
    }

    private suspend fun process(alternatives: List<String>) {
        val voice = AppGraph.voice
        val settings = AppGraph.settings.current
        voice.update { it.copy(status = VoiceStatus.PROCESSING, lastUtterance = alternatives.first()) }
        val result = AppGraph.commands.handle(alternatives)
        if (result.ignored) return
        voice.update { it.copy(lastResult = result.message) }
        if (result.success && settings.vibrate) AppGraph.feedback.vibrate()
        val say = result.speech ?: if (settings.speakFeedback) result.message else null
        if (say != null && !AppGraph.feedback.speak(say) && result.speech != null) {
            voice.update { it.copy(lastResult = "لا يتوفر صوت عربي للنطق في هذا الهاتف") }
        }
        if (result.stopListening) running.set(false)
    }

    fun stopSession() {
        running.set(false)
        engine?.cancel()
        sessionJob?.cancel()
        finish()
    }

    private fun fail(message: String) {
        AppGraph.errors.log("listening", note = message.take(80))
        AppGraph.voice.update { it.copy(status = VoiceStatus.ERROR, error = message, partial = "") }
        running.set(false)
        finish()
    }

    private fun finish() {
        if (!finished.compareAndSet(false, true)) return
        running.set(false)
        AppGraph.voice.update {
            if (it.status == VoiceStatus.ERROR) it.copy(partial = "")
            else it.copy(status = VoiceStatus.IDLE, partial = "")
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        running.set(false)
        scope.cancel()
        engine?.release()
        engine = null
        if (!finished.get()) {
            AppGraph.voice.update {
                if (it.status == VoiceStatus.ERROR) it else it.copy(status = VoiceStatus.IDLE, partial = "")
            }
        }
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun buildNotification(): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ListeningService::class.java).setAction(ACTION_STOP), flags,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notification_stop), stop)
            .build()
    }
}
