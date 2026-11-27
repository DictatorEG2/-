package com.sawt.voice.command

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.sawt.voice.parser.ArabicNormalizer

/** يحوّل اسم تطبيق منطوق إلى Intent إطلاق. قائمة التطبيقات تُخزَّن مؤقتًا 5 دقائق. */
class AppResolver(private val context: Context) {

    private class Entry(val norm: String, val latin: String, val pkg: String)
    private class Alias(val packages: List<String>, val labels: List<String>)

    @Volatile private var cache: List<Entry> = emptyList()
    @Volatile private var cachedAt = 0L

    private fun entries(): List<Entry> {
        val now = SystemClock.elapsedRealtime()
        if (cache.isNotEmpty() && now - cachedAt < CACHE_TTL_MS) return cache
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
            .flatMap { info ->
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(pm).toString()
                listOf(
                    Entry(ArabicNormalizer.normalize(label), transliterate(ArabicNormalizer.normalize(label)), pkg),
                    Entry(ArabicNormalizer.normalize(pkg.substringAfterLast('.').replace('_', ' ')), transliterate(ArabicNormalizer.normalize(pkg.substringAfterLast('.').replace('_', ' '))), pkg),
                )
            }
            .distinctBy { "${it.norm}|${it.pkg}" }
        cache = list
        cachedAt = now
        return list
    }

    fun resolve(spoken: String): Intent? {
        val tokens = ArabicNormalizer.tokens(spoken).map(::stripAl).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val pm = context.packageManager
        val candidates = listOf(tokens.joinToString(" ")) + tokens
        for (c in candidates) {
            val alias = ALIASES[c] ?: continue
            for (pkg in alias.packages) pm.getLaunchIntentForPackage(pkg)?.let { return it }
            for (label in alias.labels) findByLabel(label)?.let { return it }
        }
        val query = tokens.joinToString(" ")
        findByLabel(query)?.let { return it }
        val normalizedQuery = stripAl(query)
        if (normalizedQuery.length >= 3) {
            val latinQuery = transliterate(normalizedQuery)
            val best = entries().map { it to maxOf(similarity(normalizedQuery, stripAl(it.norm)), similarity(latinQuery, it.latin)) }
                .filter { it.second >= if (normalizedQuery.length <= 4) 0.88 else 0.76 }
                .maxByOrNull { it.second }?.first
            best?.let { context.packageManager.getLaunchIntentForPackage(it.pkg) }?.let { return it }
        }
        return null
    }

    private fun findByLabel(query: String): Intent? {
        val key = stripAl(ArabicNormalizer.normalize(query))
        if (key.length < 2) return null
        val all = entries()
        val latinKey = transliterate(key)
        val hit = all.firstOrNull { stripAl(it.norm) == key || it.latin == latinKey }
            ?: all.firstOrNull { stripAl(it.norm).startsWith(key) || it.latin.startsWith(latinKey) }
            ?: if (key.length >= 3) all.firstOrNull { it.norm.contains(key) || it.latin.contains(latinKey) } else null
            ?: if (key.length >= 4) all.map { it to maxOf(similarity(key, stripAl(it.norm)), similarity(latinKey, it.latin)) }
                .filter { it.second >= 0.70 }
                .maxByOrNull { it.second }?.first else null
        return hit?.let { context.packageManager.getLaunchIntentForPackage(it.pkg) }
    }

    /** تطبيق الرسائل الافتراضي (SMS) إن وُجد. */
    fun messagesIntent(): Intent? {
        val i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
        return if (i.resolveActivity(context.packageManager) != null) i else null
    }

    private fun transliterate(value: String): String {
        val map = mapOf(
            'ا' to "a", 'ب' to "b", 'ت' to "t", 'ث' to "th", 'ج' to "g", 'ح' to "h",
            'خ' to "kh", 'د' to "d", 'ذ' to "z", 'ر' to "r", 'ز' to "z", 'س' to "s",
            'ش' to "sh", 'ص' to "s", 'ض' to "d", 'ط' to "t", 'ظ' to "z", 'ع' to "a",
            'غ' to "gh", 'ف' to "f", 'ق' to "q", 'ك' to "k", 'ل' to "l", 'م' to "m",
            'ن' to "n", 'ه' to "h", 'و' to "w", 'ي' to "y", 'ى' to "a"
        )
        return value.map { map[it] ?: it.toString() }.joinToString("")
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0
        val longer = maxOf(a.length, b.length)
        return 1.0 - levenshtein(a, b).toDouble() / longer
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
            }
            previous = current
        }
        return previous[b.length]
    }

    private fun stripAl(s: String) = if (s.startsWith("ال") && s.length > 3) s.drop(2) else s

    private companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L

        // المفاتيح بصيغتها المطبّعة وبدون «ال».
        val ALIASES: Map<String, Alias> = mapOf(
            "شات جي بي تي" to Alias(listOf("com.openai.chatgpt"), listOf("chatgpt", "chat gpt", "شات جي بي تي")),
            "شات جيبتي" to Alias(listOf("com.openai.chatgpt"), listOf("chatgpt", "chat gpt")),
            "شات جبت" to Alias(listOf("com.openai.chatgpt"), listOf("chatgpt", "chat gpt")),
            "chatgpt" to Alias(listOf("com.openai.chatgpt"), listOf("chatgpt", "chat gpt")),
            "chat gpt" to Alias(listOf("com.openai.chatgpt"), listOf("chatgpt", "chat gpt")),
            "تشات جي بي تي" to Alias(listOf("com.openai.chatgpt"), listOf("chatgpt", "chat gpt")),
            "شاتجبت" to Alias(listOf("com.openai.chatgpt"), listOf("chatgpt", "chat gpt")),
            "ترجمان" to Alias(emptyList(), listOf("tarjuman", "tarjuman app", "ترجمان", "ترجمات")),
            "الترجمان" to Alias(emptyList(), listOf("tarjuman", "ترجمان", "ترجمات")),
            "ترجمات" to Alias(emptyList(), listOf("tarjuman", "ترجمان", "ترجمات")),
            "ترجمه" to Alias(emptyList(), listOf("tarjuman", "ترجمان", "ترجمات")),
            "tarjuman" to Alias(emptyList(), listOf("tarjuman", "ترجمان", "ترجمات")),
            "ترجمانا" to Alias(emptyList(), listOf("tarjuman", "ترجمان")),
            "تحميل" to Alias(emptyList(), listOf("tahmel", "تحميل", "downloader", "downloads")),
            "التحميل" to Alias(emptyList(), listOf("tahmel", "تحميل", "downloader", "downloads")),
            "تحمل" to Alias(emptyList(), listOf("tahmel", "تحميل", "downloader")),
            "داونلود" to Alias(emptyList(), listOf("tahmel", "downloader", "تحميل", "downloads")),
            "downloader" to Alias(emptyList(), listOf("tahmel", "downloader", "تحميل", "downloads")),
            "tahmel" to Alias(emptyList(), listOf("tahmel", "downloader", "تحميل")),
            "تيك توك" to Alias(listOf("com.zhiliaoapp.musically"), listOf("tiktok", "tik tok")),
            "تيكتوك" to Alias(listOf("com.zhiliaoapp.musically"), listOf("tiktok", "tik tok")),
            "سناب شات" to Alias(listOf("com.snapchat.android"), listOf("snapchat")),
            "جيميني" to Alias(listOf("com.google.android.apps.bard"), listOf("gemini")),
            "سبوتيفاي" to Alias(listOf("com.spotify.music"), listOf("spotify")),
            "ديسكورد" to Alias(listOf("com.discord"), listOf("discord")),
            "زوم" to Alias(listOf("us.zoom.videomeetings"), listOf("zoom")),
            "تويتر" to Alias(listOf("com.twitter.android"), listOf("x", "twitter")),
            "اكس" to Alias(listOf("com.twitter.android"), listOf("x", "twitter")),
            "لينكد ان" to Alias(listOf("com.linkedin.android"), listOf("linkedin")),
            "نتفلكس" to Alias(listOf("com.netflix.mediaclient"), listOf("netflix")),
            "فيسبوك" to Alias(listOf("com.facebook.katana", "com.facebook.lite"), listOf("facebook")),
            "فيس" to Alias(listOf("com.facebook.katana", "com.facebook.lite"), listOf("facebook")),
            "فيسبك" to Alias(listOf("com.facebook.katana", "com.facebook.lite"), listOf("facebook")),
            "واتساب" to Alias(listOf("com.whatsapp", "com.whatsapp.w4b"), listOf("whatsapp")),
            "واتس اب" to Alias(listOf("com.whatsapp", "com.whatsapp.w4b"), listOf("whatsapp")),
            "كلاود" to Alias(emptyList(), listOf("cloud", "cloudflare")),
            "جوجل درايف" to Alias(listOf("com.google.android.apps.docs"), listOf("google drive", "drive")),
            "درايف" to Alias(listOf("com.google.android.apps.docs"), listOf("google drive", "drive")),
            "واتس" to Alias(listOf("com.whatsapp", "com.whatsapp.w4b"), listOf("whatsapp")),
            "يوتيوب" to Alias(listOf("com.google.android.youtube"), listOf("youtube")),
            "كروم" to Alias(listOf("com.android.chrome"), listOf("chrome")),
            "تيليجرام" to Alias(listOf("org.telegram.messenger"), listOf("telegram")),
            "تلجرام" to Alias(listOf("org.telegram.messenger"), listOf("telegram")),
            "تليجرام" to Alias(listOf("org.telegram.messenger"), listOf("telegram")),
            "انستجرام" to Alias(listOf("com.instagram.android"), listOf("instagram")),
            "انستا" to Alias(listOf("com.instagram.android"), listOf("instagram")),
            "كاميرا" to Alias(emptyList(), listOf("camera", "كاميرا")),
            "هاتف" to Alias(listOf("com.google.android.dialer", "com.android.dialer"), listOf("phone", "هاتف")),
            "تليفون" to Alias(listOf("com.google.android.dialer", "com.android.dialer"), listOf("phone")),
            "جيميل" to Alias(listOf("com.google.android.gm"), listOf("gmail")),
            "خرائط" to Alias(listOf("com.google.android.apps.maps"), listOf("maps")),
            "صور" to Alias(listOf("com.google.android.apps.photos"), listOf("photos", "gallery")),
            "معرض" to Alias(listOf("com.google.android.apps.photos"), listOf("gallery", "photos")),
            "ماسنجر" to Alias(listOf("com.facebook.orca"), listOf("messenger")),
            "حاسبه" to Alias(emptyList(), listOf("calculator", "حاسبة")),
            "ساعه" to Alias(emptyList(), listOf("clock", "ساعة")),
            "متجر" to Alias(listOf("com.android.vending"), listOf("play store")),
            "بلاي" to Alias(listOf("com.android.vending"), listOf("play store")),
        )
    }
}
