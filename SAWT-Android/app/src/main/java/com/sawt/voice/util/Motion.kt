package com.sawt.voice.util

import android.content.Context
import android.provider.Settings

/** يحترم إعداد «إزالة الرسوم المتحركة» في النظام (مقياس مدة الرسوم = 0). */
fun isReduceMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
