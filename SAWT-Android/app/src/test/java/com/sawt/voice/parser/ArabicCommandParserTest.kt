package com.sawt.voice.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicCommandParserTest {
    private val parser = ArabicCommandParser()
    private fun cmd(s: String) = parser.parse(s).command

    @Test fun coreShortcutAndSynonyms() {
        val p = parser.parse("يا صَوْت افتح الرسائل")
        assertEquals(Command.OpenMessages, p.command)
        assertTrue(p.hadWakeWord)
        listOf("افتح الرسائل", "ادخل على الرسائل", "شغّل تطبيق الرسائل", "هات الرسائل").forEach {
            assertEquals(it, Command.OpenMessages, cmd(it))
        }
    }

    @Test fun navigation() {
        listOf("ارجع", "رجوع", "للخلف").forEach { assertEquals(Command.Back, cmd(it)) }
        assertEquals(Command.Home, cmd("ارجع للرئيسية"))
        assertEquals(Command.Recents, cmd("افتح التطبيقات الأخيرة"))
    }

    @Test fun scrolling() {
        listOf("انزل", "تحت", "مرر لأسفل").forEach { assertEquals(Command.ScrollDown, cmd(it)) }
        listOf("اطلع", "فوق", "مرر لأعلى").forEach { assertEquals(Command.ScrollUp, cmd(it)) }
    }

    @Test fun volume() {
        assertEquals(Command.VolumeUp, cmd("ارفع الصوت"))
        assertEquals(Command.VolumeUp, cmd("علي الصوت"))
        assertEquals(Command.VolumeDown, cmd("وطي الصوت"))
        assertEquals(Command.Mute, cmd("اكتم الصوت"))
    }

    @Test fun wordOrderAndFillersDoNotMatter() {
        assertEquals(Command.VolumeUp, cmd("الصوت ارفع"))
        assertEquals(Command.OpenMessages, cmd("لو سمحت افتح الرسائل"))
    }

    @Test fun openApps() {
        assertEquals(Command.OpenApp("فيسبوك"), cmd("افتح فيسبوك"))
        assertEquals(Command.OpenApp("الفيس"), cmd("شغل الفيس"))
        assertEquals(Command.OpenApp("الفيس"), cmd("ادخل على الفيس"))
        assertEquals(Command.OpenApp("واتساب"), cmd("افتح واتساب"))
    }

    @Test fun settingsAndReading() {
        assertEquals(Command.OpenSettings, cmd("افتح الإعدادات"))
        assertEquals(Command.ReadScreen, cmd("اقرأ الشاشة"))
        assertEquals(Command.TakeScreenshot, cmd("التقط لقطة شاشة"))
    }

    @Test fun clickByNumber() {
        assertEquals(Command.ClickNumber(3), cmd("اضغط على العنصر رقم 3"))
        assertEquals(Command.ClickNumber(3), cmd("اضغط على العنصر رقم ٣"))
        assertEquals(Command.ClickNumber(3), cmd("اضغط على رقم ثلاثة"))
        assertEquals(Command.ClickNumber(5), cmd("العنصر رقم 5"))
        assertEquals(Command.ShowNumbers, cmd("أظهر الأرقام"))
        assertEquals(Command.HideNumbers, cmd("إلغاء الأرقام"))
    }

    @Test fun clickByText() {
        assertEquals(Command.ClickText("إرسال"), cmd("اضغط على إرسال"))
    }

    @Test fun dictation() {
        assertEquals(Command.Type("مرحبا بك"), cmd("اكتب مرحبا بك"))
        assertEquals(Command.StartDictation, cmd("اكتب النص التالي"))
    }

    @Test fun youtubeSearch() {
        assertEquals(Command.YoutubeSearch("القرآن الكريم"), cmd("ابحث في يوتيوب عن القرآن الكريم"))
        assertEquals(Command.YoutubeSearch("القرآن الكريم"), cmd("ابحث عن القرآن الكريم في يوتيوب"))
    }

    @Test fun expandedEgyptianVolumeAndYoutubeCommands() {
        listOf("عليلي الصوت", "كبر الصوت", "خلي الصوت اعلى").forEach {
            assertEquals(Command.VolumeUp, cmd(it))
        }
        listOf("وطيلي الصوت", "قلل الصوت", "نزل الصوت").forEach {
            assertEquals(Command.VolumeDown, cmd(it))
        }
        assertEquals(Command.YoutubeHome, cmd("ابحث في يوتيوب"))
        assertEquals(Command.ClickText("like"), cmd("حط لايك"))
        assertEquals(Command.ClickText("like"), cmd("اعمل لايك"))
        assertEquals(Command.TorchOn, cmd("افتح الكشاف"))
        assertEquals(Command.TorchOn, cmd("شغل الفلاش"))
        assertEquals(Command.TorchOff, cmd("اطفي الفلاش"))
    }

    @Test fun contactNameCommands() {
        val call = cmd("اتصل بعلي")
        assertEquals(Command.CallContact("علي"), call)
        assertTrue(call.sensitive)
        assertEquals(Command.WhatsAppContact("علي"), cmd("ابعت رسالة لعلي في الواتساب"))
    }

    @Test fun callIsSensitiveAndWhatsAppNumberIsInternational() {
        val call = cmd("اتصل بالرقم 01001234567")
        assertEquals(Command.Call("01001234567"), call)
        assertTrue(call.sensitive)
        assertEquals(Command.WhatsAppChat("201001234567"), cmd("افتح محادثة واتساب مع الرقم 01001234567"))
    }

    @Test fun unknownAndWakeOnly() {
        assertEquals(Command.Unknown, cmd("بسبسة كلام غير مفهوم تماما هنا"))
        val wakeOnly = parser.parse("يا صوت")
        assertEquals(Command.Unknown, wakeOnly.command)
        assertTrue(wakeOnly.hadWakeWord)
        assertFalse(parser.parse("افتح الرسائل").hadWakeWord)
    }
}
