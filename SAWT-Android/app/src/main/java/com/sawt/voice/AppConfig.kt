package com.sawt.voice

/** إعدادات ثابتة يمكن تعديلها بسهولة من مكان واحد. */
object AppConfig {
    const val DEVELOPER = "Dictator_EG@"

    /** رابط قناة تيليجرام الرسمية. عدّله هنا فقط. */
    const val TELEGRAM_URL = "https://t.me/Dictator_channel2"

    const val HISTORY_PAGE_SIZE = 20
    const val HISTORY_MAX_ROWS = 500
    const val DEDUP_WINDOW_MS = 2000L
    const val CONFIRM_TIMEOUT_MS = 15_000L
    const val DICTATION_WINDOW_MS = 12_000L
}
