package com.sawt.voice.parser

/**
 * يحوّل جملة عربية (فصحى أو مصرية) إلى [Command] دون مطابقة حرفية:
 * - يزيل كلمة التنبيه «يا صوت» والحشو («لو سمحت»…).
 * - الأوامر ذات المعاملات (فتح تطبيق، كتابة، ضغط، يوتيوب، اتصال…) تُحلَّل بالبنية.
 * - الأوامر البسيطة تُطابَق كمجموعة كلمات بغض النظر عن الترتيب مع نقاط للأطول تطابقًا.
 */
class ArabicCommandParser {

    data class Parsed(val command: Command, val hadWakeWord: Boolean, val normalized: String)

    private class W(val orig: String, val norm: String)

    private class Rule(val command: Command, phrases: List<String>, exclude: List<String> = emptyList()) {
        val phraseSets: List<Set<String>> = phrases.map { ArabicNormalizer.tokens(it).toSet() }
        val excluded: Set<String> = exclude.flatMap { ArabicNormalizer.tokens(it) }.toSet()
    }

    fun parse(raw: String): Parsed {
        val words = raw.trim().split(WHITESPACE)
            .map { W(it, ArabicNormalizer.normalize(it)) }
            .filter { it.norm.isNotEmpty() }
        val (afterWake, hadWake) = stripWake(words)
        val t = afterWake.filter { it.norm !in FILLERS }
        val normalized = t.joinToString(" ") { it.norm }
        val command = if (t.isEmpty()) Command.Unknown else parseWords(t)
        return Parsed(command, hadWake, normalized)
    }

    private fun stripWake(words: List<W>): Pair<List<W>, Boolean> {
        for (wake in WAKE_WORDS) {
            if (words.size >= wake.size && wake.indices.all { words[it].norm == wake[it] }) {
                return words.drop(wake.size) to true
            }
        }
        return words to false
    }

    private fun parseWords(t: List<W>): Command {
        parseDictation(t)?.let { return it }
        parsePhoto(t)?.let { return it }
        parseYoutube(t)?.let { return it }
        parseTelegram(t)?.let { return it }
        parseWebSearch(t)?.let { return it }
        parseLike(t)?.let { return it }
        parseWhatsApp(t)?.let { return it }
        parseCall(t)?.let { return it }
        parseClick(t)?.let { return it }
        matchSimple(t.map { it.norm })?.let { return it }
        parseOpenApp(t)?.let { return it }
        return Command.Unknown
    }

    private fun parsePhoto(t: List<W>): Command? {
        val words = t.map { it.norm }.toSet()
        val capture = setOf("صور", "صورلي", "التقط", "التقاط", "خد", "خدلي", "التقطلي", "capture", "photo", "picture", "shutter")
        val cameraContext = words.any { it in setOf("كاميرا", "الكاميرا", "صوره", "تصوير") }
        return if (words.any { it in capture } && (cameraContext || words.size <= 2)) Command.TakePhoto else null
    }

    private fun parseDictation(t: List<W>): Command? {
        if (t.first().norm !in DICTATION_VERBS) return null
        val rest = t.drop(1)
        if (rest.isEmpty()) return Command.StartDictation
        val n = rest.map { it.norm }
        if (n.size >= 2 && n[0] in setOf("النص", "الكلام") && n[1] == "التالي") {
            val body = rest.drop(2)
            return if (body.isEmpty()) Command.StartDictation else Command.Type(body.joinToString(" ") { it.orig })
        }
        return Command.Type(rest.joinToString(" ") { it.orig })
    }

    private fun parseYoutube(t: List<W>): Command? {
        val ytIdx = t.indexOfFirst { base(it.norm) == "يوتيوب" }
        if (ytIdx < 0 || t.none { it.norm in SEARCH_VERBS }) return null
        val cleaned = t.filterIndexed { i, w ->
            i != ytIdx && !(i == ytIdx - 1 && w.norm in setOf("في", "علي", "ب"))
        }
        val verbIdx = cleaned.indexOfFirst { it.norm in SEARCH_VERBS }
        var rest = cleaned.drop(verbIdx + 1)
        if (rest.firstOrNull()?.norm in setOf("عن", "علي")) rest = rest.drop(1)
        if (rest.isEmpty()) return Command.YoutubeHome
        return Command.YoutubeSearch(rest.joinToString(" ") { it.orig })
    }

    private fun parseTelegram(t: List<W>): Command? {
        val appIndex = t.indexOfFirst { base(it.norm) in TELEGRAM_WORDS }
        if (appIndex < 0 || t.none { it.norm in SEARCH_VERBS }) return null
        val cleaned = t.filterIndexed { i, w ->
            i != appIndex && !(i == appIndex - 1 && w.norm in setOf("في", "على", "ب"))
        }
        val verbIndex = cleaned.indexOfFirst { it.norm in SEARCH_VERBS }
        var query = cleaned.drop(verbIndex + 1)
        if (query.firstOrNull()?.norm in setOf("عن", "على")) query = query.drop(1)
        return query.takeIf { it.isNotEmpty() }?.let {
            Command.TelegramSearch(it.joinToString(" ") { word -> word.orig })
        }
    }

    private fun parseWebSearch(t: List<W>): Command? {
        val browserIndex = t.indexOfFirst { it.norm in WEB_SEARCH_WORDS }
        if (browserIndex < 0 || t.none { it.norm in SEARCH_VERBS }) return null
        val cleaned = t.filterIndexed { i, w ->
            i != browserIndex && !(i == browserIndex - 1 && w.norm in setOf("في", "على", "ب"))
        }
        val verbIndex = cleaned.indexOfFirst { it.norm in SEARCH_VERBS }
        var query = cleaned.drop(verbIndex + 1)
        if (query.firstOrNull()?.norm in setOf("عن", "على")) query = query.drop(1)
        return query.takeIf { it.isNotEmpty() }?.let {
            Command.WebSearch(it.joinToString(" ") { word -> word.orig })
        }
    }

    private fun parseLike(t: List<W>): Command? {
        val n = t.map { it.norm }.toSet()
        if (n.any { it in setOf("لايك", "اللايك", "لايكه", "اعجاب", "اعجبني") } &&
            n.any { it in setOf("لايك", "اللايك", "لايكه", "اعجاب", "اعجبني", "اعمل", "حط", "دوس", "اضغط") }
        ) return Command.ClickText("like")
        return null
    }

    private fun parseWhatsApp(t: List<W>): Command? {
        val appIndex = t.indexOfFirst { base(it.norm) in setOf("واتساب", "واتس", "واتسب") }
        if (appIndex < 0) return null
        val digits = digitsOf(t)
        if (digits.length >= 8) return Command.WhatsAppChat(internationalize(digits))
        val withIndex = t.indexOfFirst { it.norm in setOf("مع", "ل", "الي", "الى") }
        val nameWords = if (withIndex >= 0) {
            t.drop(withIndex + 1).takeWhile { it.norm !in setOf("في", "على", "واتساب", "واتس") }
        } else {
            t.filterIndexed { i, w ->
                i != appIndex && w.norm !in setOf("افتح", "افتحلي", "شغل", "شغلي", "رساله", "رسالة", "ابعت", "ابعث", "ارسل", "رسائل", "في", "على")
            }
        }
        val name = nameWords.joinToString(" ") { it.orig.removePrefix("ب").removePrefix("ل") }.trim()
        return name.takeIf { it.length >= 2 }?.let { Command.WhatsAppContact(it) }
    }

    private fun parseCall(t: List<W>): Command? {
        val verbIndex = t.indexOfFirst { it.norm in CALL_VERBS }
        if (verbIndex < 0) return null
        val digits = digitsOf(t)
        if (digits.length >= 3) return Command.Call(digits)
        val afterVerb = t.drop(verbIndex + 1)
        val withoutPreposition = if (afterVerb.size > 1 && afterVerb.first().norm == "علي") afterVerb.drop(1) else afterVerb
        val nameWords = withoutPreposition.filter {
            it.norm !in setOf("ب", "بال", "بي", "الي", "من", "رقم", "تليفون", "هاتف")
        }
        val name = nameWords.joinToString(" ") { it.orig.removePrefix("ب").removePrefix("ل") }.trim()
        return name.takeIf { it.length >= 2 }?.let { Command.CallContact(it) }
    }

    private fun parseClick(t: List<W>): Command? {
        val first = t.first().norm
        val hasVerb = first in CLICK_VERBS || (first in OPEN_VERBS && t.any { it.norm == "رقم" })
        val rest = if (hasVerb) t.drop(1) else t
        val cleaned = rest.filter { it.norm !in CLICK_NOISE }
        if (!hasVerb) {
            val hadNumberWord = rest.any { it.norm == "رقم" || it.norm == "العنصر" || it.norm == "عنصر" }
            val num = cleaned.singleOrNull()?.let { parseNumber(it.norm) }
            return if (hadNumberWord && num != null) Command.ClickNumber(num) else null
        }
        if (cleaned.isEmpty()) return null
        val num = if (cleaned.size == 1) parseNumber(cleaned[0].norm) else null
        return if (num != null) Command.ClickNumber(num)
        else Command.ClickText(cleaned.joinToString(" ") { it.orig })
    }

    private fun matchSimple(n: List<String>): Command? {
        val set = n.toSet()
        var best: Rule? = null
        var bestScore = 0
        for (r in RULES) {
            if (r.excluded.any { it in set }) continue
            val s = r.phraseSets.filter { set.containsAll(it) }.maxOfOrNull { it.size } ?: continue
            if (n.size - s > 3) continue
            if (s > bestScore) {
                best = r
                bestScore = s
            }
        }
        return best?.command
    }

    private fun parseOpenApp(t: List<W>): Command? {
        if (t.first().norm !in OPEN_VERBS) return null
        val rest = t.drop(1).filter { it.norm !in APP_NOISE }
        if (rest.isEmpty()) return null
        return Command.OpenApp(rest.joinToString(" ") { it.norm })
    }

    private fun parseNumber(tok: String): Int? =
        tok.toIntOrNull()?.takeIf { it in 1..99 } ?: NUMBER_WORDS[tok]

    private fun digitsOf(t: List<W>): String =
        t.map { it.norm }.filter { tok -> tok.isNotEmpty() && tok.all { it in '0'..'9' } }.joinToString("")

    /** يحوّل رقم مصري محلي (01xxxxxxxxx) إلى صيغة دولية لرابط واتساب. */
    private fun internationalize(d: String): String = when {
        d.startsWith("00") -> d.drop(2)
        d.startsWith("01") && d.length == 11 -> "20" + d.drop(1)
        else -> d
    }

    private fun base(tok: String) = if (tok.startsWith("ال") && tok.length > 3) tok.drop(2) else tok

    companion object {
        private val WHITESPACE = Regex("\\s+")

        private val WAKE_WORDS = listOf(
            listOf("يا", "صوت"), listOf("يا", "ساوت"), listOf("ياصوت"), listOf("صوت"),
        )

        // كلها بصيغتها بعد التطبيع.
        private val FILLERS = setOf(
            "لو", "سمحت", "فضلك", "ممكن", "عايز", "عاوز", "اريد", "الان", "بقي",
            "خلاص", "يعني", "ياريت", "معلش", "طيب", "لي",
        )
        private val OPEN_VERBS = setOf("افتح", "افتحلي", "افتح لي", "شغل", "شغلي", "شغللي", "ادخل", "هات", "هاتلي", "روح", "وريني")
        private val APP_NOISE = setOf("علي", "الي", "تطبيق", "برنامج", "لتطبيق", "ل")
        private val CLICK_VERBS = setOf("اضغط", "دوس", "اختار", "انقر", "اضغطي", "دوسي", "اختر")
        private val CLICK_NOISE = setOf("علي", "الي", "العنصر", "عنصر", "رقم", "زر", "الزر", "الخيار", "خيار")
        private val DICTATION_VERBS = setOf("اكتب", "اكتبلي", "املي")
        private val SEARCH_VERBS = setOf("ابحث", "ابحثلي", "دور", "دورلي")
        private val TELEGRAM_WORDS = setOf("تليجرام", "تلجرام", "تيليجرام")
        private val WEB_SEARCH_WORDS = setOf("متصفح", "المتصفح", "جوجل", "كروم", "انترنت", "الانترنت")
        private val CALL_VERBS = setOf("اتصل", "كلم", "رن", "اطلب")

        private val NUMBER_WORDS = mapOf(
            "واحد" to 1, "واحده" to 1, "وحده" to 1,
            "اثنين" to 2, "اتنين" to 2, "اثنان" to 2,
            "ثلاثه" to 3, "تلاته" to 3, "ثلاث" to 3, "تلات" to 3,
            "اربعه" to 4, "اربع" to 4,
            "خمسه" to 5, "خمس" to 5,
            "سته" to 6, "ست" to 6,
            "سبعه" to 7, "سبع" to 7,
            "ثمانيه" to 8, "تمانيه" to 8, "ثمان" to 8, "تمان" to 8,
            "تسعه" to 9, "تسع" to 9,
            "عشره" to 10, "عشر" to 10,
        )

        private val RULES: List<Rule> = listOf(
            Rule(
                Command.Back,
                listOf("ارجع", "رجوع", "للخلف", "خلف", "ارجعلي", "الشاشة السابقة", "رجعني"),
                exclude = listOf("الرئيسية", "للرئيسية", "رئيسيه", "هوم", "الهوم"),
            ),
            Rule(
                Command.Home,
                listOf("الرئيسية", "للرئيسية", "هوم", "الهوم", "الصفحة الرئيسية", "شاشة البداية"),
            ),
            Rule(
                Command.Recents,
                listOf("التطبيقات الأخيرة", "الأخيرة", "ريسنت", "المهام الأخيرة", "التطبيقات المفتوحة"),
            ),
            Rule(Command.ScrollDown, listOf("انزل", "انزل لتحت", "انزل تحت", "تحت", "لتحت", "مرر لأسفل", "مرر للأسفل", "اسحب لتحت", "اسحب لاسفل", "نزل", "قلب الصفحة", "اقلب الصفحة", "قلب", "اقلب", "الصفحة اللي بعدها", "اللي بعده")),
            Rule(Command.ScrollUp, listOf("اطلع", "اطلع لفوق", "فوق", "لفوق", "مرر لأعلى", "مرر للأعلى", "اسحب لفوق", "اسحب لاعلى", "الصفحة اللي قبلها", "اللي قبله")),
            Rule(Command.VolumeUp, listOf("ارفع الصوت", "علي الصوت", "علّي الصوت", "زود الصوت", "رفع الصوت", "اعلي الصوت", "كبر الصوت", "زودلي الصوت", "عليلي الصوت", "ارفعلي الصوت", "الصوت اعلى", "خلي الصوت اعلى")),
            Rule(Command.VolumeDown, listOf("وطي الصوت", "وطّي الصوت", "وط الصوت", "وطى الصوت", "اخفض الصوت", "خفض الصوت", "وطي", "وطيلي الصوت", "قلل الصوت", "نزل الصوت", "خفضلي الصوت", "الصوت اوطى", "خلي الصوت اوطى", "قلل مستوى الصوت")),
            Rule(Command.Mute, listOf("اكتم الصوت", "كتم الصوت", "اكتم", "وضع الصامت", "اسكت الصوت", "اقفل الصوت")),
            Rule(Command.TorchOn, listOf("افتح الكشاف", "شغل الكشاف", "افتح الفلاش", "شغل الفلاش", "ولع الفلاش", "نور الكشاف", "شغل ضوء الهاتف", "شغل ضوء الفلاش")),
            Rule(Command.TorchOff, listOf("اقفل الكشاف", "اطفي الكشاف", "طفي الكشاف", "اقفل الفلاش", "اطفي الفلاش", "طفي الفلاش", "اقفل ضوء الهاتف")),
            Rule(Command.BrightnessUp, listOf("زود الإضاءة", "ارفع الإضاءة", "علي الإضاءة", "زود السطوع", "ارفع السطوع")),
            Rule(Command.BrightnessDown, listOf("قلل الإضاءة", "وطي الإضاءة", "اخفض الإضاءة", "قلل السطوع", "اخفض السطوع")),
            Rule(
                Command.ReadScreen,
                listOf("اقرأ الشاشة", "اقرأ لي الشاشة", "اقرأ ما على الشاشة", "اقرأ اللي على الشاشة", "اقرأ النص", "اقرأ"),
            ),
            Rule(
                Command.ShowNumbers,
                listOf("أظهر الأرقام", "أظهرلي الأرقام", "اعرض الأرقام", "اعرضلي الأرقام", "وريني الأرقام", "أرقام العناصر", "رقم العناصر", "أظهر الاعداد"),
            ),
            Rule(
                Command.HideNumbers,
                listOf("أخف الأرقام", "اخفي الأرقام", "إخفاء الأرقام", "اقفل الأرقام", "شيل الأرقام", "إلغاء الأرقام"),
            ),
            Rule(
                Command.OpenSettings,
                listOf("الإعدادات", "اعدادات", "اعدادات الهاتف"),
            ),
            Rule(Command.OpenMessages, listOf("الرسائل", "رسائل", "الرسالة", "تطبيق الرسائل")),
            Rule(
                Command.TakeScreenshot,
                listOf("لقطة شاشة", "لقطة الشاشة", "التقط لقطة", "صور الشاشة", "سكرين شوت", "سكرينشوت", "اسكرين شوت"),
            ),
            Rule(
                Command.StopListening,
                listOf("وقف الاستماع", "ايقاف الاستماع", "توقف عن الاستماع", "اسكت", "كفاية", "وقف"),
            ),
            Rule(
                Command.Confirm,
                listOf("نعم", "ايوه", "ايوا", "تأكيد", "اكد", "أكيد", "موافق", "تمام", "ماشي", "نفذ"),
            ),
            Rule(Command.Cancel, listOf("لا", "إلغاء", "الغي", "ايقاف", "بلاش", "لا شكرا")),
        )
    }
}
