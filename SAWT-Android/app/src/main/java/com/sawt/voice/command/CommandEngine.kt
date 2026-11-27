package com.sawt.voice.command

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.sawt.voice.AppConfig
import com.sawt.voice.AppGraph
import com.sawt.voice.accessibility.SawtAccessibilityService
import com.sawt.voice.data.HistoryEntity
import com.sawt.voice.parser.Command
import com.sawt.voice.privacy.PrivacyManager
import com.sawt.voice.settings.AppSettings

data class ExecResult(
    val success: Boolean,
    val message: String,
    /** نص يُنطق بدلًا من الرسالة (مثل قراءة الشاشة). */
    val speech: String? = null,
    /** أمر تم تجاهله (تكرار، أو بدون كلمة التنبيه المطلوبة): لا يُعرض ولا يُسجَّل. */
    val ignored: Boolean = false,
    val stopListening: Boolean = false,
)

/**
 * ينفّذ الأوامر: تحليل ← اختصارات المستخدم ← تأكيد الأوامر الحساسة ← منع التكرار ← تنفيذ ← سجل.
 * يُستدعى دائمًا من الخيط الرئيسي (كوروتين الخدمة أو واجهة المستخدم).
 */
class CommandEngine(private val context: Context) {

    private var pendingConfirm: Pair<Command, Long>? = null
    private var dictationUntil = 0L
    private var lastKey = ""
    private var lastAt = 0L

    suspend fun handle(alternatives: List<String>, requireWakeWordRule: Boolean = true): ExecResult {
        if (alternatives.isEmpty()) return ignored()
        val settings = AppGraph.settings.current
        val parser = AppGraph.parser
        val now = SystemClock.elapsedRealtime()

        // وضع الكتابة الصوتية: الجملة التالية كلها نص.
        if (now < dictationUntil) {
            dictationUntil = 0L
            val first = alternatives.first()
            if (parser.parse(first).command == Command.Cancel) return ExecResult(true, "تم إلغاء الكتابة")
            return runCommand(Command.Type(first), first, settings)
        }

        var chosen = alternatives.first()
        var parsed = parser.parse(chosen)
        if (parsed.command == Command.Unknown) {
            for (alt in alternatives.drop(1)) {
                val p = parser.parse(alt)
                if (p.command != Command.Unknown) {
                    chosen = alt
                    parsed = p
                    break
                }
            }
        }

        if (requireWakeWordRule && settings.requireWakeWord && !parsed.hadWakeWord && pendingConfirm == null) return ignored()
        if (parsed.command == Command.Unknown && parsed.hadWakeWord && parsed.normalized.isEmpty()) {
            return ExecResult(true, "أنا أسمعك")
        }

        // منع تكرار تنفيذ الأمر نفسه خلال نافذة قصيرة.
        if (parsed.normalized.isNotEmpty() && parsed.normalized == lastKey && now - lastAt < AppConfig.DEDUP_WINDOW_MS) return ignored()
        lastKey = parsed.normalized
        lastAt = now

        // اختصارات المستخدم لها الأولوية.
        var command = parsed.command
        AppGraph.shortcuts.targetFor(parsed.normalized)?.let { target ->
            command = parser.parse(target).command
        }

        // تأكيد/إلغاء أمر حساس معلّق.
        val pending = pendingConfirm
        if (pending != null) {
            pendingConfirm = null
            if (now - pending.second <= AppConfig.CONFIRM_TIMEOUT_MS) {
                if (command == Command.Confirm) return runCommand(pending.first, chosen, settings)
                if (command == Command.Cancel) return ExecResult(true, "تم إلغاء الأمر")
            }
        }
        if (command == Command.Confirm || command == Command.Cancel) return ignored()

        if (command.sensitive) {
            pendingConfirm = command to now
            return ExecResult(true, confirmPrompt(command))
        }
        return runCommand(command, chosen, settings)
    }

    private fun ignored() = ExecResult(false, "", ignored = true)

    private fun confirmPrompt(c: Command): String = when (c) {
        is Command.Call -> "هل تريد الاتصال بالرقم ${c.number}؟ قل نعم للتأكيد أو إلغاء"
        else -> "هل تريد تنفيذ ${c.label}؟ قل نعم أو إلغاء"
    }

    private suspend fun runCommand(command: Command, raw: String, settings: AppSettings): ExecResult {
        val result = try {
            execute(command)
        } catch (t: Throwable) {
            AppGraph.errors.log("execute", t)
            ExecResult(false, "حدث خطأ أثناء تنفيذ الأمر")
        }
        if (settings.historyEnabled && command != Command.Unknown) {
            try {
                val dao = AppGraph.db.historyDao()
                dao.insert(
                    HistoryEntity(
                        createdAt = System.currentTimeMillis(),
                        utterance = PrivacyManager.historyText(command, raw),
                        actionLabel = command.label,
                        success = result.success,
                    ),
                )
                dao.trim(AppConfig.HISTORY_MAX_ROWS)
            } catch (t: Throwable) {
                AppGraph.errors.log("history", t)
            }
        }
        return result
    }

    private fun execute(c: Command): ExecResult = when (c) {
        Command.Back -> global(AccessibilityService.GLOBAL_ACTION_BACK, "تم الرجوع")
        Command.Home -> global(AccessibilityService.GLOBAL_ACTION_HOME, "الشاشة الرئيسية")
        Command.Recents -> global(AccessibilityService.GLOBAL_ACTION_RECENTS, "التطبيقات الأخيرة")
        Command.ScrollDown -> withA11y { ok(it.scroll(forward = true), "تم التمرير لأسفل", "لم أتمكن من التمرير") }
        Command.ScrollUp -> withA11y { ok(it.scroll(forward = false), "تم التمرير لأعلى", "لم أتمكن من التمرير") }
        Command.ReadScreen -> withA11y {
            val text = it.readScreen()
            if (text.isBlank()) ExecResult(false, "لا يوجد نص ظاهر لقراءته")
            else ExecResult(true, "قراءة الشاشة", speech = text)
        }
        Command.ShowNumbers -> withA11y {
            val n = it.showNumbers()
            if (n == 0) ExecResult(false, "لم أجد عناصر قابلة للضغط") else ExecResult(true, "ظهرت $n عنصرًا مرقمًا")
        }
        Command.HideNumbers -> withA11y { it.hideNumbers(); ExecResult(true, "تم إخفاء الأرقام") }
        is Command.ClickNumber -> withA11y {
            ok(it.clickNumber(c.number), "تم الضغط على العنصر ${c.number}", "لا يوجد عنصر بهذا الرقم. قل «أظهر الأرقام» أولًا")
        }
        is Command.ClickText -> withA11y {
            ok(it.clickText(c.text), "تم الضغط على ${c.text}", "لم أجد عنصرًا بهذا الاسم")
        }
        is Command.Type -> withA11y {
            ok(it.typeText(c.text), "تمت الكتابة", "لا يوجد حقل نص نشط، أو أن الحقل محمي")
        }
        Command.StartDictation -> {
            dictationUntil = SystemClock.elapsedRealtime() + AppConfig.DICTATION_WINDOW_MS
            ExecResult(true, "تفضل، قل النص الذي تريد كتابته")
        }
        Command.TakeScreenshot ->
            if (Build.VERSION.SDK_INT < 28) ExecResult(false, "لقطة الشاشة تتطلب Android 9 أو أحدث")
            else global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "تم التقاط لقطة الشاشة")
        is Command.OpenApp -> {
            val intent = AppGraph.resolver.resolve(c.name)
            if (intent != null && launch(intent)) ExecResult(true, "تم فتح ${c.name}")
            else ExecResult(false, "لم أجد تطبيقًا باسم ${c.name}")
        }
        Command.OpenMessages -> {
            val intent = AppGraph.resolver.messagesIntent()
            if (intent != null && launch(intent)) ExecResult(true, "تم فتح الرسائل")
            else ExecResult(false, "لم أجد تطبيق الرسائل")
        }
        Command.OpenSettings -> ok(launch(Intent(Settings.ACTION_SETTINGS)), "تم فتح الإعدادات", "تعذر فتح الإعدادات")
        Command.VolumeUp -> volume(AudioManager.ADJUST_RAISE, "تم رفع الصوت")
        Command.VolumeDown -> volume(AudioManager.ADJUST_LOWER, "تم خفض الصوت")
        Command.Mute -> volume(AudioManager.ADJUST_MUTE, "تم كتم الصوت")
        Command.BrightnessUp -> brightness(+BRIGHTNESS_STEP)
        Command.BrightnessDown -> brightness(-BRIGHTNESS_STEP)
        is Command.YoutubeSearch -> {
            val uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(c.query))
            ok(launch(Intent(Intent.ACTION_VIEW, uri)), "نتائج البحث في يوتيوب", "تعذر فتح يوتيوب")
        }
        is Command.WhatsAppChat -> {
            val uri = Uri.parse("https://wa.me/${c.number}")
            val viaApp = Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")
            val opened = launch(viaApp) || launch(Intent(Intent.ACTION_VIEW, uri))
            ok(opened, "تم فتح المحادثة", "تعذر فتح واتساب")
        }
        is Command.Call -> ok(
            launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", c.number, null))),
            "تم فتح شاشة الاتصال. اضغط زر الاتصال لإتمام المكالمة",
            "تعذر فتح تطبيق الهاتف",
        )
        Command.StopListening -> ExecResult(true, "تم إيقاف الاستماع", stopListening = true)
        Command.Unknown -> ExecResult(false, "لم أفهم الأمر")
        Command.Confirm, Command.Cancel -> ExecResult(false, "لا يوجد أمر بانتظار التأكيد", ignored = true)
    }

    private fun ok(success: Boolean, good: String, bad: String) = ExecResult(success, if (success) good else bad)

    private fun withA11y(block: (SawtAccessibilityService) -> ExecResult): ExecResult {
        val service = SawtAccessibilityService.instance
            ?: return ExecResult(false, "فعّل خدمة إمكانية الوصول لصَوْت من شاشة الأذونات أولًا")
        return block(service)
    }

    private fun global(action: Int, message: String): ExecResult = withA11y {
        ok(it.performGlobalAction(action), message, "رفض النظام تنفيذ الإجراء")
    }

    private fun volume(direction: Int, message: String): ExecResult {
        val am = context.getSystemService(AudioManager::class.java)
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return ExecResult(true, message)
    }

    private fun brightness(delta: Int): ExecResult {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
            launch(intent)
            return ExecResult(false, "اسمح لصَوْت بتعديل إعدادات النظام ثم أعد الأمر")
        }
        val resolver = context.contentResolver
        val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (current + delta).coerceIn(10, 255))
        return ExecResult(true, if (delta > 0) "تمت زيادة الإضاءة" else "تم تقليل الإضاءة")
    }

    /** نبدأ الأنشطة عبر سياق خدمة إمكانية الوصول عند توفرها (قيود تشغيل الأنشطة من الخلفية). */
    private fun launch(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ctx: Context = SawtAccessibilityService.instance ?: context
        ctx.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (t: Throwable) {
        AppGraph.errors.log("launch", t)
        false
    }

    private companion object {
        const val BRIGHTNESS_STEP = 38
    }
}
