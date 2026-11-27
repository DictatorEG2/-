package com.sawt.voice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sawt.voice.AppGraph
import com.sawt.voice.R
import com.sawt.voice.engine.VoiceStatus
import com.sawt.voice.engine.VoiceUiState
import com.sawt.voice.ui.components.PrimaryButton
import com.sawt.voice.ui.components.SawtCard
import com.sawt.voice.ui.components.SawtScaffold
import com.sawt.voice.ui.components.ScreenColumn
import com.sawt.voice.ui.components.SecondaryButton
import com.sawt.voice.ui.components.SectionLabel
import com.sawt.voice.ui.components.StatusPill
import com.sawt.voice.ui.components.WaveformBars
import com.sawt.voice.ui.components.IconBadge
import com.sawt.voice.ui.components.rememberResumeTick
import com.sawt.voice.ui.components.sawtTextFieldColors
import com.sawt.voice.ui.nav.Routes
import com.sawt.voice.ui.theme.SawtColors
import com.sawt.voice.util.Permissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** زر تشغيل/إيقاف الاستماع. إن لم تُمنح صلاحية الميكروفون ننقل المستخدم لشاشة الأذونات (حيث الشرح). */
@Composable
fun rememberMicToggle(onNeedPermission: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val need = rememberUpdatedState(onNeedPermission)
    return remember {
        {
            if (!Permissions.hasMic(context)) need.value() else AppGraph.voice.toggle()
        }
    }
}

private fun isActive(s: VoiceUiState) =
    s.status == VoiceStatus.STARTING || s.status == VoiceStatus.LISTENING || s.status == VoiceStatus.PROCESSING

private fun statusLabel(s: VoiceStatus): String = when (s) {
    VoiceStatus.IDLE -> "المساعد متوقف"
    VoiceStatus.STARTING -> "جارٍ التشغيل"
    VoiceStatus.LISTENING -> "أستمع إليك"
    VoiceStatus.PROCESSING -> "أنفّذ الأمر"
    VoiceStatus.ERROR -> "تعذر التشغيل"
}

private fun statusColor(s: VoiceStatus): Color = when (s) {
    VoiceStatus.IDLE -> SawtColors.TextSecondary
    VoiceStatus.STARTING, VoiceStatus.PROCESSING -> SawtColors.BlueLight
    VoiceStatus.LISTENING -> SawtColors.CrimsonLight
    VoiceStatus.ERROR -> SawtColors.CrimsonLight
}

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(900)
        onDone()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(168.dp))
            Text("صَوْت", style = MaterialTheme.typography.displaySmall)
            Text("SAWT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class Quick(val title: String, val subtitle: String, val icon: ImageVector, val route: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(go: (String) -> Unit) {
    val context = LocalContext.current
    val state by AppGraph.voice.state.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    val hasMic = remember(tick) { Permissions.hasMic(context) }
    val a11y = remember(tick) { Permissions.isAccessibilityEnabled(context) }
    val toggle = rememberMicToggle { go(Routes.PERMISSIONS) }
    val active = isActive(state)

    SawtScaffold(title = "صَوْت | SAWT", onBack = null) { pad ->
        ScreenColumn(pad) {
            SawtCard(accent = if (state.status == VoiceStatus.LISTENING) SawtColors.Crimson else null) {
                StatusPill(statusLabel(state.status), statusColor(state.status))
                val line = when {
                    state.error != null -> state.error ?: ""
                    state.lastResult.isNotBlank() -> state.lastResult
                    active -> if (state.strictLocal) "تعرف محلي: لا يُرسَل الصوت إلى خوادم" else "التعرف المحلي مفضّل وقد تُستخدم خدمة الاتصال"
                    else -> "اضغط الزر الكبير وقل أمرك، مثل «افتح الرسائل»"
                }
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val desc = if (active) "إيقاف الاستماع" else "بدء الاستماع"
                Surface(
                    onClick = toggle,
                    shape = CircleShape,
                    color = if (active) SawtColors.Crimson else SawtColors.SurfaceHigh,
                    border = BorderStroke(2.dp, if (active) SawtColors.CrimsonLight else SawtColors.Blue),
                    modifier = Modifier.size(132.dp).semantics { contentDescription = desc },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (active) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = null,
                            tint = SawtColors.TextPrimary,
                            modifier = Modifier.size(52.dp),
                        )
                    }
                }
                Text(
                    if (active) "اضغط للإيقاف" else "اضغط للتحدث",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!hasMic || !a11y) {
                SawtCard(accent = SawtColors.Crimson, onClick = { go(Routes.PERMISSIONS) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = SawtColors.CrimsonLight)
                        Text("أكمل الإعداد", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        buildString {
                            if (!hasMic) append("صلاحية الميكروفون مطلوبة للاستماع. ")
                            if (!a11y) append("فعّل خدمة إمكانية الوصول ليتمكن صَوْت من التحكم بالشاشة.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionLabel("الوصول السريع")
            val quick = listOf(
                Quick("الاستماع والتحكم", "راقب الأوامر مباشرة", Icons.Filled.GraphicEq, Routes.LISTEN),
                Quick("مكتبة الأوامر", "ما يمكنك قوله", Icons.Filled.Book, Routes.LIBRARY),
                Quick("الاختصارات", "عباراتك المخصصة", Icons.Filled.Bolt, Routes.SHORTCUTS),
                Quick("سجل الأوامر", "آخر ما نُفّذ", Icons.Filled.History, Routes.HISTORY),
                Quick("الزر العائم", "تحكم من أي تطبيق", Icons.Filled.TouchApp, Routes.FLOATING),
                Quick("الخصوصية", "ما يُحفظ وما لا يُحفظ", Icons.Filled.Shield, Routes.PRIVACY),
            )
            quick.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { item ->
                        SawtCard(modifier = Modifier.weight(1f).fillMaxHeight(), onClick = { go(item.route) }) {
                            IconBadge(item.icon)
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            SectionLabel("المزيد")
            listOf(
                Quick("إعدادات الصوت والتعرف", "", Icons.Filled.Tune, Routes.VOICE_SETTINGS),
                Quick("الأذونات", "", Icons.Filled.Accessibility, Routes.PERMISSIONS),
                Quick("دليل الاستخدام", "", Icons.Filled.Help, Routes.GUIDE),
                Quick("حول التطبيق", "", Icons.Filled.Info, Routes.ABOUT),
            ).forEach { item ->
                SawtCard(onClick = { go(item.route) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(item.icon)
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningScreen(back: () -> Unit, go: (String) -> Unit) {
    val state by AppGraph.voice.state.collectAsStateWithLifecycle()
    val active = isActive(state)
    val toggle = rememberMicToggle { go(Routes.PERMISSIONS) }
    val scope = rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    var typedResult by remember { mutableStateOf<String?>(null) }

    SawtScaffold("الاستماع والتحكم", back) { pad ->
        ScreenColumn(pad) {
            SawtCard(accent = if (active) SawtColors.Crimson else null) {
                StatusPill(statusLabel(state.status), statusColor(state.status))
                WaveformBars(active = state.status == VoiceStatus.LISTENING, modifier = Modifier.fillMaxWidth().height(72.dp))
                Text(
                    if (state.partial.isNotBlank()) state.partial else if (active) "قل أمرك الآن…" else "اضغط «بدء الاستماع»",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            PrimaryButton(
                text = if (active) "إيقاف الاستماع" else "بدء الاستماع",
                onClick = toggle,
                icon = if (active) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                color = if (active) SawtColors.Crimson else SawtColors.Blue,
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { err ->
                SawtCard(accent = SawtColors.Crimson) {
                    Text("تنبيه", style = MaterialTheme.typography.labelMedium, color = SawtColors.CrimsonLight)
                    Text(err, style = MaterialTheme.typography.bodyMedium)
                }
            }

            SawtCard {
                Text("آخر أمر", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.lastUtterance.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
                Text("النتيجة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.lastResult.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "يُحفَظ النص في الذاكرة المؤقتة فقط أثناء عرضه هنا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SawtCard {
                Text("جرّب أمرًا بالكتابة", style = MaterialTheme.typography.titleMedium)
                Text(
                    "ينفّذ الأمر المكتوب بنفس محرك الأوامر الصوتية، مفيد للاختبار دون ميكروفون.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("مثال: افتح الرسائل") },
                    colors = sawtTextFieldColors(),
                    shape = MaterialTheme.shapes.medium,
                )
                SecondaryButton(
                    text = "نفّذ",
                    enabled = typed.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val text = typed
                        typed = ""
                        scope.launch { typedResult = AppGraph.commands.handle(listOf(text), requireWakeWordRule = false).message }
                    },
                )
                typedResult?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = SawtColors.BlueLight)
                }
            }
        }
    }
}
