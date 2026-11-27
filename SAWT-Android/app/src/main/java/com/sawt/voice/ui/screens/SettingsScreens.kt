package com.sawt.voice.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sawt.voice.AppGraph
import com.sawt.voice.privacy.PrivacyManager
import com.sawt.voice.settings.Dialect
import com.sawt.voice.ui.components.PrimaryButton
import com.sawt.voice.ui.components.SawtCard
import com.sawt.voice.ui.components.SawtScaffold
import com.sawt.voice.ui.components.ScreenColumn
import com.sawt.voice.ui.components.SecondaryButton
import com.sawt.voice.ui.components.SectionLabel
import com.sawt.voice.ui.components.SettingSliderRow
import com.sawt.voice.ui.components.SettingSwitchRow
import com.sawt.voice.ui.components.StatusPill
import com.sawt.voice.ui.components.rememberResumeTick
import com.sawt.voice.ui.theme.SawtColors
import com.sawt.voice.util.Permissions
import com.sawt.voice.update.GithubUpdateClient
import com.sawt.voice.update.UpdateStartupCheck
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(back: () -> Unit) {
    val s by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cachedRelease = remember(context) { UpdateStartupCheck.cachedRelease(context) }
    var updateStatus by remember {
        mutableStateOf(
            cachedRelease?.let { "يتوفر إصدار ${it.tag}: ${it.name}" }
                ?: "يُفحص التحديث تلقائيًا مرة كل 24 ساعة. يمكنك الفحص يدويًا."
        )
    }
    var availableRelease by remember { mutableStateOf<GithubUpdateClient.Release?>(cachedRelease) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var autoDownloadedTag by remember { mutableStateOf(context.getSharedPreferences("sawt_update_state", android.content.Context.MODE_PRIVATE).getString("auto_download_tag", null)) }

    val checkForUpdates: () -> Unit = {
        if (!checkingUpdate) {
            checkingUpdate = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { GithubUpdateClient.check(context) }
                UpdateStartupCheck.markChecked(context, result !is GithubUpdateClient.CheckResult.Failed)
                when (result) {
                    is GithubUpdateClient.CheckResult.Available -> {
                        availableRelease = result.release
                        UpdateStartupCheck.cacheRelease(context, result.release)
                        updateStatus = "يتوفر إصدار ${result.release.tag}: ${result.release.name}"
                        val updatePrefs = context.getSharedPreferences("sawt_update_state", android.content.Context.MODE_PRIVATE)
                        if (autoDownloadedTag != result.release.tag) {
                            downloadingUpdate = true
                            scope.launch {
                                val download = withContext(Dispatchers.IO) { runCatching { GithubUpdateClient.download(context, result.release) } }
                                if (download.isSuccess) {
                                    updatePrefs.edit().putString("auto_download_tag", result.release.tag).apply()
                                    autoDownloadedTag = result.release.tag
                                    updateStatus = "بدأ تنزيل التحديث تلقائيًا. عند اكتماله سيظهر إشعار لفتح مثبت Android والموافقة على التثبيت."
                                } else {
                                    updateStatus = "تعذر تنزيل التحديث تلقائيًا. يمكنك المحاولة من زر التنزيل."
                                }
                                downloadingUpdate = false
                            }
                        }
                    }
                    GithubUpdateClient.CheckResult.UpToDate -> {
                        availableRelease = null
                        UpdateStartupCheck.clearCachedRelease(context)
                        updateStatus = "أنت تستخدم أحدث إصدار متاح."
                    }
                    GithubUpdateClient.CheckResult.NoRelease -> {
                        availableRelease = null
                        UpdateStartupCheck.clearCachedRelease(context)
                        updateStatus = "لم يُنشر إصدار على GitHub Releases بعد. سيظهر التحديث هنا عند نشر أول APK موقّع."
                    }
                    is GithubUpdateClient.CheckResult.Failed -> {
                        updateStatus = result.message
                    }
                }
                checkingUpdate = false
            }
        }
    }
    LaunchedEffect(Unit) {
        if (availableRelease == null && UpdateStartupCheck.shouldCheck(context)) checkForUpdates()
    }

    SawtScaffold("إعدادات الصوت والتعرف", back) { pad ->
        ScreenColumn(pad) {
            SectionLabel("اللغة واللهجة")
            SawtCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Dialect.values().forEach { d ->
                        FilterChip(
                            selected = s.dialect == d,
                            onClick = { AppGraph.settings.update { it.copy(dialect = d) } },
                            label = { Text(d.title) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SawtColors.Blue,
                                selectedLabelColor = SawtColors.TextPrimary,
                            ),
                        )
                    }
                }
                Text(
                    "اللهجة تؤثر على نموذج التعرف المستخدم في هاتفك (ar-EG أو ar-SA).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel("التعرف على الكلام")
            SawtCard {
                SettingSwitchRow(
                    title = "الوضع المحلي (دون إنترنت)",
                    subtitle = PrivacyManager.recognitionSummary(s.offlineOnly),
                    checked = s.offlineOnly,
                    onChange = { v -> AppGraph.settings.update { it.copy(offlineOnly = v) } },
                )
                SettingSwitchRow(
                    title = "اشتراط كلمة «يا صَوْت»",
                    subtitle = "عند التفعيل لا تُنفَّذ إلا الأوامر التي تبدأ بها.",
                    checked = s.requireWakeWord,
                    onChange = { v -> AppGraph.settings.update { it.copy(requireWakeWord = v) } },
                )
            }

            SectionLabel("التغذية الراجعة")
            SawtCard {
                SettingSwitchRow(
                    title = "نطق النتيجة صوتيًا",
                    checked = s.speakFeedback,
                    onChange = { v -> AppGraph.settings.update { it.copy(speakFeedback = v) } },
                )
                SettingSwitchRow(
                    title = "اهتزاز عند نجاح الأمر",
                    checked = s.vibrate,
                    onChange = { v -> AppGraph.settings.update { it.copy(vibrate = v) } },
                )
            }

            SectionLabel("البطارية")
            SawtCard {
                SettingSliderRow(
                    title = "إيقاف الاستماع تلقائيًا بعد الخمول",
                    valueLabel = "${s.autoStopSeconds} ثانية",
                    value = s.autoStopSeconds.toFloat(),
                    range = 30f..300f,
                    steps = 8,
                    onChange = { v -> AppGraph.settings.update { it.copy(autoStopSeconds = (v / 30f).roundToInt() * 30) } },
                )
            }

            SectionLabel("تحديث التطبيق")
            SawtCard(accent = SawtColors.Blue) {
                Text("التحديثات الرسمية", style = MaterialTheme.typography.titleMedium)
                if (availableRelease != null) {
                    StatusPill("تحديث متاح", SawtColors.BlueLight)
                }
                Text(
                    updateStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                availableRelease?.let { release ->
                    if (release.notes.isNotBlank()) {
                        Text(release.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PrimaryButton(
                        text = when { downloadingUpdate -> "جارٍ التنزيل…"; autoDownloadedTag == release.tag -> "تم بدء تنزيل هذا التحديث"; else -> "تنزيل التحديث ${release.tag}" },
                        enabled = !downloadingUpdate && autoDownloadedTag != release.tag,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (!downloadingUpdate) {
                                downloadingUpdate = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { GithubUpdateClient.download(context, release) }
                                    }
                                    updateStatus = if (result.isSuccess) {
                                        "بدأ التنزيل. ستصلك رسالة عند اكتماله، ثم توافق على التثبيت من Android."
                                    } else {
                                        "تعذر بدء التنزيل. تحقق من الاتصال وحاول مجددًا."
                                    }
                                    downloadingUpdate = false
                                }
                            }
                        },
                    )
                }
                SecondaryButton(
                    text = if (checkingUpdate) "جارٍ الفحص…" else "فحص التحديثات الآن",
                    enabled = !checkingUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = checkForUpdates,
                )
                Text(
                    "يُستخدم الإنترنت فقط للتواصل مع GitHub وفحص الإصدار وتنزيل ملف APK. لا يُرسل التطبيق تسجيلات أو أوامر أو بيانات شخصية.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun FloatingSettingsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val s by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    val a11y = remember(tick) { Permissions.isAccessibilityEnabled(context) }

    SawtScaffold("إعدادات الزر العائم", back) { pad ->
        ScreenColumn(pad) {
            SawtCard {
                Text("زر عائم للتحكم بالاستماع", style = MaterialTheme.typography.titleMedium)
                Text(
                    "ضغطة واحدة تشغّل الاستماع وأخرى توقفه، ويمكن سحبه لأي مكان. يتلوّن بالقرمزي عند استخدام الميكروفون. " +
                        "يُعرض عبر خدمة إمكانية الوصول، لذلك لا يحتاج صلاحية «الظهور فوق التطبيقات».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingSwitchRow(
                    title = "إظهار الزر العائم",
                    subtitle = if (a11y) null else "يتطلب تفعيل خدمة إمكانية الوصول أولًا (من شاشة الأذونات).",
                    checked = s.floatingEnabled && a11y,
                    enabled = a11y,
                    onChange = { v -> AppGraph.settings.update { it.copy(floatingEnabled = v) } },
                )
            }
            SawtCard {
                SettingSliderRow(
                    title = "الحجم",
                    valueLabel = "${s.floatSizeDp} dp",
                    value = s.floatSizeDp.toFloat(),
                    range = 40f..80f,
                    steps = 7,
                    onChange = { v -> AppGraph.settings.update { it.copy(floatSizeDp = v.roundToInt()) } },
                )
                SettingSliderRow(
                    title = "الشفافية",
                    valueLabel = "${(s.floatAlpha * 100).roundToInt()}%",
                    value = s.floatAlpha,
                    range = 0.3f..1f,
                    onChange = { v -> AppGraph.settings.update { it.copy(floatAlpha = v) } },
                )
            }
            SecondaryButton(
                text = "إعادة الزر إلى موضعه الافتراضي",
                modifier = Modifier.fillMaxWidth(),
                onClick = { AppGraph.settings.update { it.copy(floatX = -1, floatY = -1) } },
            )
        }
    }
}

@Composable
fun PermissionsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val tick = rememberResumeTick()
    var refresh by remember { mutableIntStateOf(0) }
    val camera = remember(tick, refresh) { Permissions.hasCamera(context) }
    val contacts = remember(tick, refresh) { Permissions.hasContacts(context) }
    val mic = remember(tick, refresh) { Permissions.hasMic(context) }
    val notif = remember(tick, refresh) { Permissions.hasNotifications(context) }
    val a11y = remember(tick, refresh) { Permissions.isAccessibilityEnabled(context) }
    val write = remember(tick, refresh) { Permissions.canWriteSettings(context) }
    var showDisclosure by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val callLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    SawtScaffold("الأذونات", back) { pad ->
        ScreenColumn(pad) {
            Text(
                "صَوْت يطلب الأذونات عند الحاجة فقط. إذن جهات الاتصال اختياري ولا يُستخدم إلا عند طلب الاتصال بشخص بالاسم أو فتح محادثة واتساب معه.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PermissionCard(
                title = "الميكروفون",
                reason = "لسماع أوامرك الصوتية. لا يبدأ الاستماع إلا بضغطك، ويظهر إشعار دائم بزر إيقاف أثناءه. لا تُحفظ أي تسجيلات.",
                granted = mic,
                actionLabel = "السماح بالميكروفون",
            ) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

            PermissionCard(
                title = "الكاميرا (للكشاف فقط)",
                reason = "يستخدم صَوْت إذن الكاميرا حصريًا لتشغيل فلاش الهاتف أو إيقافه بأمرك. لا يلتقط صورًا ولا يسجل فيديو.",
                granted = camera,
                actionLabel = "السماح بالكشاف",
            ) { cameraLauncher.launch(Manifest.permission.CAMERA) }

            PermissionCard(
                title = "الاتصال المباشر (اختياري)",
                reason = "بعد قول «اتصل بأحمد» ثم تأكيد «نعم»، يمكن لصَوْت بدء المكالمة مباشرة بدل فتح شاشة الاتصال. لن يبدأ الاتصال قبل التأكيد الصوتي.",
                granted = context.checkSelfPermission(Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                actionLabel = "السماح بالاتصال المباشر",
            ) { callLauncher.launch(Manifest.permission.CALL_PHONE) }

            PermissionCard(
                title = "جهات الاتصال (اختياري)",
                reason = "للتعرّف على أسماء مثل «اتصل بعلي» أو «افتح محادثة واتساب مع علي» وتحويلها إلى رقم. لا تُرسل جهات الاتصال إلى أي خادم.",
                granted = contacts,
                actionLabel = "السماح بجهات الاتصال",
            ) { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }

            PermissionCard(
                title = "خدمة إمكانية الوصول",
                reason = "للتحكم بالشاشة نيابةً عنك: الضغط والتمرير والرجوع والكتابة وقراءة النص الظاهر وعرض الأرقام والزر العائم. تُفعَّل يدويًا من إعدادات النظام.",
                granted = a11y,
                actionLabel = "تفعيل الخدمة",
            ) { showDisclosure = true }

            if (Build.VERSION.SDK_INT >= 33) {
                PermissionCard(
                    title = "الإشعارات",
                    reason = "لإظهار مؤشر الاستماع وزر الإيقاف الفوري. بدونه قد لا يظهر المؤشر في شريط الإشعارات.",
                    granted = notif,
                    actionLabel = "السماح بالإشعارات",
                ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }

            PermissionCard(
                title = "تعديل إعدادات النظام (اختياري)",
                reason = "فقط لأوامر الإضاءة («زوّد الإضاءة»). يمكنك تجاهله دون أن يتأثر أي شيء آخر.",
                granted = write,
                actionLabel = "فتح الإعداد",
            ) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("إفصاح عن خدمة إمكانية الوصول") },
            text = {
                Text(
                    "ستنقلك الخطوة التالية إلى إعدادات إمكانية الوصول في هاتفك لتفعيل «صَوْت».\n\n" +
                        "• تُستخدم الخدمة لتنفيذ ما تطلبه صوتيًا أو من الزر العائم فقط.\n" +
                        "• تقرأ نص الشاشة عند طلب «اقرأ الشاشة» أو «أظهر الأرقام» فقط، ولا تخزّنه ولا ترسله.\n" +
                        "• لا تقرأ حقول كلمات المرور ولا تكتب فيها.\n" +
                        "• يمكنك إيقافها في أي وقت من نفس الشاشة.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("أوافق وأفتح الإعدادات", color = SawtColors.BlueLight) }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) {
                    Text("ليس الآن", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

@Composable
private fun PermissionCard(title: String, reason: String, granted: Boolean, actionLabel: String, onAction: () -> Unit) {
    SawtCard(accent = if (granted) SawtColors.Blue else null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (granted) StatusPill("مفعّل", SawtColors.BlueLight) else StatusPill("غير مفعّل", SawtColors.CrimsonLight)
        }
        Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!granted) PrimaryButton(actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth())
    }
}
