package com.sawt.voice.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sawt.voice.AppConfig
import com.sawt.voice.AppGraph
import com.sawt.voice.R
import com.sawt.voice.privacy.PrivacyManager
import com.sawt.voice.ui.components.PrimaryButton
import com.sawt.voice.ui.components.SawtCard
import com.sawt.voice.ui.components.SawtScaffold
import com.sawt.voice.ui.components.ScreenColumn
import com.sawt.voice.ui.components.SecondaryButton
import com.sawt.voice.ui.components.SectionLabel
import com.sawt.voice.ui.theme.SawtColors
import kotlinx.coroutines.launch

@Composable
fun PrivacyScreen(back: () -> Unit) {
    val s by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    SawtScaffold("الخصوصية والأمان", back) { pad ->
        ScreenColumn(pad) {
            SawtCard(accent = SawtColors.Blue) {
                Text("وضع التعرف الحالي", style = MaterialTheme.typography.titleMedium)
                Text(
                    PrivacyManager.recognitionSummary(s.offlineOnly),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PrivacyManager.sections.forEach { section ->
                SawtCard {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    Text(section.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SecondaryButton(
                text = "مسح سجل الأوامر الآن",
                modifier = Modifier.fillMaxWidth(),
                onClick = { scope.launch { PrivacyManager.clearHistory() } },
            )
            Text(
                "النص الكامل في ملف PRIVACY.md المرفق مع المشروع.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun GuideScreen(back: () -> Unit) {
    val steps = listOf(
        "فعّل الأذونات" to "من شاشة «الأذونات» اسمح بالميكروفون، ثم فعّل خدمة إمكانية الوصول لصَوْت من إعدادات النظام.",
        "ثبّت حزمة العربية للتعرف دون إنترنت" to "إعدادات Google ← الصوت ← التعرف على الكلام دون اتصال ← العربية. بدونها لن يعمل الوضع المحلي.",
        "ابدأ الاستماع" to "اضغط الزر الكبير في الصفحة الرئيسية أو الزر العائم. سيظهر إشعار دائم أثناء الاستماع.",
        "قل أمرك" to "مثل «يا صَوْت افتح الرسائل» أو «انزل» أو «ارجع». راجع «مكتبة الأوامر» لباقي الأمثلة.",
        "اضغط بالأرقام" to "قل «أظهر الأرقام» ثم «اضغط على العنصر رقم 3».",
        "أوقف الاستماع" to "اضغط الزر مجددًا، أو قل «وقف الاستماع»، أو اضغط «إيقاف» في الإشعار. يتوقف تلقائيًا عند الخمول.",
    )
    SawtScaffold("دليل الاستخدام", back) { pad ->
        ScreenColumn(pad) {
            steps.forEachIndexed { i, (title, body) ->
                SawtCard {
                    Text("${i + 1}. $title", style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun AboutScreen(back: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    }
    SawtScaffold("حول التطبيق", back) { pad ->
        ScreenColumn(pad) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(132.dp))
                Text("صَوْت | SAWT", style = MaterialTheme.typography.headlineMedium)
                Text("مساعد صوتي عربي للتحكم في الهاتف", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("الإصدار $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SawtCard {
                Text("المطوّر", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(AppConfig.DEVELOPER, style = MaterialTheme.typography.titleMedium)
            }
            SectionLabel("تواصل")
            PrimaryButton(
                text = "قناتنا على تيليجرام",
                icon = Icons.Filled.Share,
                color = SawtColors.Blue,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.TELEGRAM_URL)))
                    } catch (_: ActivityNotFoundException) {
                    }
                },
            )
            Text(
                "قيود التطبيق التقنية موثقة في ملف LIMITATIONS.md.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
