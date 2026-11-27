package com.sawt.voice.ui.theme

import androidx.compose.ui.graphics.Color

/** لوحة الألوان الوحيدة للتطبيق. لا تُختار ألوان منفصلة داخل الشاشات. */
object SawtColors {
    // خلفيات وأسطح
    val Black = Color(0xFF07070A)
    val Surface = Color(0xFF111116)
    val SurfaceHigh = Color(0xFF1A1A22)
    val Outline = Color(0xFF2A2A34)

    // قرمزي غامق: العناصر المهمة والتنبيهات النشطة
    val Crimson = Color(0xFF9E1B32)
    val CrimsonLight = Color(0xFFD6405A) // للنص والأيقونات فوق الخلفية الداكنة

    // أزرق كهربائي داكن: التفاعل والتقنية
    val Blue = Color(0xFF2A4BD7)
    val BlueLight = Color(0xFF7C93F2)

    // نصوص
    val TextPrimary = Color(0xFFF2F2F5)
    val TextSecondary = Color(0xFF9A9AA8)
    val TextDisabled = Color(0xFF63636F)
}
