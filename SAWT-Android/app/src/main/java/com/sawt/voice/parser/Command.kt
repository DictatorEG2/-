package com.sawt.voice.parser

/** كل الأوامر التي يفهمها النظام. هذا الملف لا يعتمد على Android ليمكن اختباره على JVM. */
sealed interface Command {
    val label: String
    val sensitive: Boolean get() = false

    data object Back : Command { override val label = "رجوع" }
    data object Home : Command { override val label = "الشاشة الرئيسية" }
    data object Recents : Command { override val label = "التطبيقات الأخيرة" }
    data object ScrollDown : Command { override val label = "تمرير لأسفل" }
    data object ScrollUp : Command { override val label = "تمرير لأعلى" }
    data object VolumeUp : Command { override val label = "رفع الصوت" }
    data object VolumeDown : Command { override val label = "خفض الصوت" }
    data object Mute : Command { override val label = "كتم الصوت" }
    data object TorchOn : Command { override val label = "تشغيل الكشاف" }
    data object TorchOff : Command { override val label = "إيقاف الكشاف" }
    data object BrightnessUp : Command { override val label = "زيادة الإضاءة" }
    data object BrightnessDown : Command { override val label = "تقليل الإضاءة" }
    data object ReadScreen : Command { override val label = "قراءة الشاشة" }
    data object ShowNumbers : Command { override val label = "إظهار أرقام العناصر" }
    data object HideNumbers : Command { override val label = "إخفاء الأرقام" }
    data object OpenSettings : Command { override val label = "فتح الإعدادات" }
    data object OpenMessages : Command { override val label = "فتح الرسائل" }
    data object TakeScreenshot : Command { override val label = "لقطة شاشة" }
    data object TakePhoto : Command { override val label = "التقاط صورة" }
    data object StartDictation : Command { override val label = "بدء الكتابة الصوتية" }
    data object YoutubeHome : Command { override val label = "فتح يوتيوب" }
    data object StopListening : Command { override val label = "إيقاف الاستماع" }
    data object Confirm : Command { override val label = "تأكيد" }
    data object Cancel : Command { override val label = "إلغاء" }
    data object Unknown : Command { override val label = "أمر غير مفهوم" }

    data class OpenApp(val name: String) : Command {
        override val label: String get() = "فتح تطبيق: $name"
    }
    data class Type(val text: String) : Command {
        override val label: String get() = "كتابة نص"
    }
    data class ClickNumber(val number: Int) : Command {
        override val label: String get() = "ضغط على العنصر $number"
    }
    data class ClickText(val text: String) : Command {
        override val label: String get() = "ضغط على: $text"
    }
    data class YoutubeSearch(val query: String) : Command {
        override val label: String get() = "بحث في يوتيوب: $query"
    }
    data class WebSearch(val query: String) : Command {
        override val label: String get() = "بحث في المتصفح: $query"
    }
    data class TelegramSearch(val query: String) : Command {
        override val label: String get() = "بحث في تليجرام: $query"
    }
    data class WhatsAppChat(val number: String) : Command {
        override val label: String get() = "فتح محادثة واتساب"
    }
    data class CallContact(val name: String) : Command {
        override val label: String get() = "الاتصال بجهة اتصال: $name"
        override val sensitive: Boolean get() = true
    }
    data class WhatsAppContact(val name: String) : Command {
        override val label: String get() = "فتح محادثة واتساب مع: $name"
    }
    data class Call(val number: String) : Command {
        override val label: String get() = "اتصال هاتفي"
        override val sensitive: Boolean get() = true
    }
}
