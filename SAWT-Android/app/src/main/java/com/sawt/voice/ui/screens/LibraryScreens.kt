package com.sawt.voice.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sawt.voice.AppConfig
import com.sawt.voice.AppGraph
import com.sawt.voice.command.CommandCatalog
import com.sawt.voice.data.HistoryEntity
import com.sawt.voice.data.ShortcutEntity
import com.sawt.voice.parser.Command
import com.sawt.voice.ui.components.PrimaryButton
import com.sawt.voice.ui.components.SawtCard
import com.sawt.voice.ui.components.SawtScaffold
import com.sawt.voice.ui.components.SecondaryButton
import com.sawt.voice.ui.components.SettingSwitchRow
import com.sawt.voice.ui.components.sawtTextFieldColors
import com.sawt.voice.ui.theme.SawtColors
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommandLibraryScreen(back: () -> Unit) {
    SawtScaffold("مكتبة الأوامر", back) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "لا يشترط قول الجملة حرفيًا: النظام يفهم المرادفات واختلاف ترتيب الكلمات، ويمكنك البدء بـ «يا صَوْت».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(CommandCatalog.entries, key = { it.title }) { entry ->
                SawtCard {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                    entry.examples.forEach {
                        Text("«$it»", style = MaterialTheme.typography.bodyMedium, color = SawtColors.BlueLight)
                    }
                    entry.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ShortcutsScreen(back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<ShortcutEntity>() }
    var total by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }

    fun loadMore() {
        scope.launch {
            items.addAll(AppGraph.shortcuts.page(AppConfig.HISTORY_PAGE_SIZE, items.size))
            total = AppGraph.shortcuts.count()
        }
    }
    LaunchedEffect(Unit) { loadMore() }

    SawtScaffold("الاختصارات المخصصة", back) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "اربط عبارة تقولها بأي أمر مدعوم. مثال: العبارة «شغّل الأخبار» ← الأمر «ابحث في يوتيوب عن الأخبار».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                PrimaryButton("إضافة اختصار", onClick = { showAdd = true }, icon = Icons.Filled.Add, modifier = Modifier.fillMaxWidth())
            }
            if (items.isEmpty()) {
                item { SawtCard { Text("لا توجد اختصارات بعد.", style = MaterialTheme.typography.bodyMedium) } }
            }
            items(items, key = { it.id }) { s ->
                SawtCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("«${s.displayPhrase}»", style = MaterialTheme.typography.titleMedium)
                            Text("← ${s.target}", style = MaterialTheme.typography.bodyMedium, color = SawtColors.BlueLight)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                AppGraph.shortcuts.remove(s.id)
                                items.remove(s)
                                total = AppGraph.shortcuts.count()
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف الاختصار", tint = SawtColors.CrimsonLight)
                        }
                    }
                }
            }
            if (items.size < total) {
                item { SecondaryButton("عرض المزيد", onClick = { loadMore() }, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }

    if (showAdd) {
        var phrase by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("اختصار جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = phrase, onValueChange = { phrase = it }, singleLine = true,
                        label = { Text("العبارة التي ستقولها") }, colors = sawtTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = target, onValueChange = { target = it }, singleLine = true,
                        label = { Text("الأمر الذي سيُنفَّذ") }, colors = sawtTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = SawtColors.CrimsonLight, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsedPhrase = AppGraph.parser.parse(phrase)
                    val parsedTarget = AppGraph.parser.parse(target).command
                    when {
                        parsedPhrase.normalized.isEmpty() -> error = "اكتب العبارة"
                        parsedTarget == Command.Unknown || parsedTarget == Command.Confirm || parsedTarget == Command.Cancel ->
                            error = "الأمر الهدف غير مفهوم. جرّب صياغة من مكتبة الأوامر"
                        else -> scope.launch {
                            AppGraph.shortcuts.add(parsedPhrase.normalized, phrase.trim(), target.trim())
                            items.clear()
                            showAdd = false
                            loadMore()
                        }
                    }
                }) { Text("حفظ", color = SawtColors.BlueLight) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        )
    }
}

@Composable
fun HistoryScreen(back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle()
    val items = remember { mutableStateListOf<HistoryEntity>() }
    var total by remember { mutableIntStateOf(0) }
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("ar")) }

    fun loadMore() {
        scope.launch {
            val dao = AppGraph.db.historyDao()
            items.addAll(dao.page(AppConfig.HISTORY_PAGE_SIZE, items.size))
            total = dao.count()
        }
    }
    LaunchedEffect(Unit) { loadMore() }

    SawtScaffold("سجل الأوامر", back) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SawtCard {
                    SettingSwitchRow(
                        title = "تسجيل الأوامر",
                        subtitle = "محفوظ على جهازك فقط. لا يُحفظ النص المكتوب بالصوت ولا أرقام الهواتف.",
                        checked = settings.historyEnabled,
                        onChange = { v -> AppGraph.settings.update { it.copy(historyEnabled = v) } },
                    )
                }
            }
            item {
                SecondaryButton(
                    "مسح السجل",
                    enabled = items.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            AppGraph.db.historyDao().clear()
                            items.clear()
                            total = 0
                        }
                    },
                )
            }
            if (items.isEmpty()) {
                item { SawtCard { Text("لا توجد أوامر مسجلة.", style = MaterialTheme.typography.bodyMedium) } }
            }
            items(items, key = { it.id }) { h ->
                SawtCard(accent = if (h.success) null else SawtColors.Crimson) {
                    Text(h.actionLabel, style = MaterialTheme.typography.titleMedium)
                    Text(h.utterance, style = MaterialTheme.typography.bodyMedium, color = SawtColors.BlueLight)
                    Text(
                        (if (h.success) "نجح" else "لم ينجح") + " · " + formatter.format(Date(h.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (items.size < total) {
                item { SecondaryButton("عرض المزيد", onClick = { loadMore() }, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}
