# البناء والتشغيل

## المتطلبات
- Android Studio Koala 2024.1.1 أو أحدث، JDK 17، Android SDK Platform 34.
- إنترنت أثناء أول مزامنة Gradle.

## الإصدارات المستخدمة
| المكوّن | الإصدار |
|---|---|
| Gradle | 8.9 (في `gradle-wrapper.properties`) |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 2.0.20 (مع `kotlin.plugin.compose`) |
| KSP | 2.0.20-1.0.25 |
| Compose BOM | 2024.09.00 (Material 3 1.3.x) |
| Navigation Compose | 2.8.0 |
| Room | 2.6.1 |
| compileSdk / targetSdk | 34 |
| **minSdk** | **26 (Android 8.0)** |

هذه التركيبة مختارة لتكون متوافقة نظريًا مع بعضها، لكنها **لم تُجرَّب** هنا. إن ظهر تعارض عند المزامنة فعدّل الأرقام في `build.gradle.kts` و`app/build.gradle.kts`.

### لماذا minSdk 26؟
- يغطي الغالبية العظمى من الأجهزة العاملة اليوم، ويشمل جهاز الاختبار (Android 13).
- يتيح الأيقونة التكيفية (Adaptive Icon) كاملة بلا نسخ PNG احتياطية.
- يتيح `isShowingHintText` ونوافذ `TYPE_ACCESSIBILITY_OVERLAY` وقنوات الإشعارات دون شروط إضافية.
- ميزات أحدث تُحمى بفحص الإصدار: لقطة الشاشة (API 28+)، التعرف المحلي المضمون (API 33+).

## الـ Gradle Wrapper
الملف `gradle-wrapper.properties` موجود، لكن `gradlew` و`gradle-wrapper.jar` **غير مرفقين** (لم أستطع توليدهما دون Gradle). الحلان:
- افتح المشروع من Android Studio (يستخدم Gradle 8.9 حسب `distributionUrl`)، أو
- ثبّت Gradle ونفّذ داخل المجلد: `gradle wrapper --gradle-version 8.9` فيُنشئ `gradlew` و`gradlew.bat` والـ jar.

## البناء
```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
المخرج: `app/build/outputs/apk/debug/app-debug.apk`.

## التوقيع (Release)
لا توجد مفاتيح توقيع في المشروع عمدًا. لإصدار Release أنشئ keystore خاصًا بك خارج المستودع وأضفه عبر `local.properties` أو متغيرات بيئة، ولا تضعه في Git (`*.jks` و`*.keystore` في `.gitignore`).

## تغيير الخط
يستخدم التطبيق خط النظام العربي (Noto Sans Arabic على أندرويد) لأن تنزيل خط مرخّص لم يكن ممكنًا هنا. لاستخدام خط مرفق (مثل IBM Plex Sans Arabic أو Tajawal، وكلاهما برخصة OFL):
1. ضع ملفات `.ttf` في `app/src/main/res/font/` بأسماء صغيرة (`tajawal_regular.ttf` …).
2. في `ui/theme/Type.kt` غيّر `SawtFont` إلى `FontFamily(Font(R.font.tajawal_regular, FontWeight.Normal), Font(R.font.tajawal_bold, FontWeight.Bold))`.
لا حاجة لتعديل أي شاشة.

## محرك محلي بديل (sherpa-onnx)
المنفَّذ حاليًا هو محرك النظام `SpeechRecognizer` (انظر `ARCHITECTURE.md`). لم أدمج sherpa-onnx لأنني لم أستطع التحقق من الإنترنت من توفر نموذج عربي مناسب (دعم المصرية، الحجم، الرخصة، الأداء على معالج Infinix Smart 8) ولم أرد وضع نماذج ضخمة أو غير مؤكدة الترخيص في المشروع. للدمج لاحقًا:
1. نزّل مكتبة sherpa-onnx لأندرويد (AAR أو مصادر JNI) من مستودعها الرسمي، ونموذجًا عربيًا موثوق الرخصة.
2. ضع النموذج في `filesDir` أو ينزّله المستخدم بموافقته، **لا** داخل المستودع.
3. نفّذ الواجهة `SpeechEngine` (`engine/SpeechEngine.kt`) في صنف جديد `SherpaSpeechEngine`، ثم اختره في `ListeningService.beginLoop()`.


## التحديثات التلقائية
راجع `UPDATE_SETUP.md` قبل إنشاء أول Release. يجب ضبط `UpdateConfig.REPOSITORY` على المستودع الفعلي، وإنشاء Release عام يحتوي APK، وزيادة `versionCode` و`versionName` مع كل إصدار، والتوقيع بمفتاح ثابت محفوظ خارج Git.
