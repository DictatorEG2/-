# البنية المعمارية

بنية بسيطة بلا Hilt: حاوية تبعيات `AppGraph` تُهيَّأ في `SawtApp`. الواجهة (Compose) لا تحتوي منطق أوامر؛ تقرأ حالة من `StateFlow` وتستدعي دوال في الطبقات الأدنى.

## الوحدات (الحزم) ومقابلها في المواصفات
| الوحدة المطلوبة | الحزمة | أهم الملفات |
|---|---|---|
| VoiceEngine | `engine/` | `SpeechEngine` (واجهة)، `AndroidSpeechEngine`، `VoiceController` |
| ArabicCommandParser | `parser/` | `ArabicNormalizer`، `Command`، `ArabicCommandParser` (Kotlin نقي بلا Android) |
| CommandEngine | `command/` | `CommandEngine`، `AppResolver`، `CommandCatalog` |
| AccessibilityEngine + ScreenReader + FloatingControls | `accessibility/` | `SawtAccessibilityService`، `ScreenReader`، `FloatingButton`، `NumberOverlay` |
| VoiceFeedback | `feedback/` | `VoiceFeedback` (نطق واهتزاز) |
| PrivacyManager | `privacy/` | `PrivacyManager` |
| Settings | `settings/` | `AppSettings`، `SettingsRepository` |
| LocalStorage | `data/` | Room: `HistoryEntity`، `ShortcutEntity`، `ShortcutRepository`، `ErrorLog` |
| UpdateManager | `update/` | `GithubUpdateClient`، `UpdateStartupCheck`، `DownloadCompleteReceiver`، `InstallUpdateActivity` |
| خدمة الاستماع | `service/` | `ListeningService` (خدمة أمامية نوع ميكروفون) |
| الواجهة | `ui/` | `theme/` (Color, Type, Shape, Theme)، `components/`، `screens/`، `nav/` |

## تدفق أمر صوتي
```
زر التطبيق / الزر العائم / الإشعار
        │
VoiceController.start()  ← يمنع جلسة ثانية، يتحقق من الميكروفون
        │
ListeningService (خدمة أمامية + إشعار دائم بزر إيقاف)
        │  حلقة: جلسة استماع واحدة ← نتيجة ← معالجة ← جلسة جديدة
AndroidSpeechEngine.listenOnce()   (SpeechRecognizer؛ محلي على Android 13+)
        │ نص + بدائل
CommandEngine.handle()
   ├─ وضع الكتابة الصوتية؟ → Type
   ├─ ArabicCommandParser.parse()  (تطبيع، كلمة التنبيه، حشو، قواعد)
   ├─ اختصارات المستخدم (Room + ذاكرة مؤقتة)
   ├─ أمر حساس؟ → طلب تأكيد (نعم / إلغاء) خلال 15 ثانية
   ├─ منع التكرار (نافذة ثانيتين)
   └─ execute()
        ├─ SawtAccessibilityService  (رجوع، تمرير، ضغط، كتابة، قراءة، أرقام)
        ├─ Intents (فتح تطبيق، رسائل، إعدادات، يوتيوب، واتساب، شاشة الاتصال)
        └─ AudioManager / Settings.System (صوت، إضاءة)
        │
سجل الأوامر (Room، مع حجب النصوص الحساسة) + نطق النتيجة + اهتزاز اختياري
```

## الخيوط والحالة
- كل المنطق يعمل على الخيط الرئيسي عبر كوروتين الخدمة (`SpeechRecognizer` وخدمة إمكانية الوصول يشترطان ذلك). Room تنقل عملياتها خارج الخيط الرئيسي تلقائيًا (دوال suspend).
- الحالة المشتركة في `VoiceController.state` و`SettingsRepository.settings` (StateFlow). الزر العائم يشترك فيهما داخل الخدمة.

## كيف تضيف أمرًا جديدًا
1. أضف صنفًا في `parser/Command.kt` (وحدد `sensitive` إن لزم تأكيد).
2. أضف قاعدة في `ArabicCommandParser` (`RULES` للأوامر البسيطة، أو دالة `parseX` للأوامر ذات المعاملات).
3. نفّذ الفرع في `CommandEngine.execute()`.
4. أضف مثالًا في `CommandCatalog` و`COMMANDS.md`.
5. أضف اختبارًا في `ArabicCommandParserTest`.

## كيف تضيف محرك تعرف جديد
نفّذ `SpeechEngine` وبدّل إنشاءه في `ListeningService.beginLoop()`. لا شيء آخر يتغير.


## التحديثات
- `UpdateStartupCheck` ينظم الفحص عند بدء التطبيق (24 ساعة بين المحاولات الناجحة، وإعادة المحاولة بعد ساعة عند الفشل).
- `GithubUpdateClient` يقرأ أحدث GitHub Release العام ويقبل APK من رابط HTTPS على `github.com` فقط.
- `DownloadManager` يدير التنزيل، و`DownloadCompleteReceiver` يعرض إشعار اكتماله.
- `InstallUpdateActivity` يفتح مثبت Android بعد موافقة المستخدم؛ لا يوجد تثبيت صامت.
- إعداد المستودع في `update/UpdateConfig.kt`، وتفاصيل النشر والتوقيع في `UPDATE_SETUP.md`.
