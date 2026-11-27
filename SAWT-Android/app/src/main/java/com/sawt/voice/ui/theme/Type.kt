package com.sawt.voice.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * عائلة خط واحدة فقط. يستخدم النظام خط Noto Sans Arabic الواضح على أندرويد.
 * لاستبداله بخط مرفق (مثل IBM Plex Sans Arabic أو Tajawal) ضع ملفات .ttf في res/font
 * وغيّر [SawtFont] فقط (انظر BUILD.md).
 */
val SawtFont: FontFamily = FontFamily.SansSerif

val SawtTypography = Typography(
    displaySmall = TextStyle(fontFamily = SawtFont, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = SawtFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = SawtFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = SawtFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontFamily = SawtFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = SawtFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontFamily = SawtFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = SawtFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = SawtFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)
