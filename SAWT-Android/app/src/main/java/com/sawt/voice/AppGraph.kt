package com.sawt.voice

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.sawt.voice.command.AppResolver
import com.sawt.voice.command.CommandEngine
import com.sawt.voice.data.AppDatabase
import com.sawt.voice.data.ErrorLog
import com.sawt.voice.data.ShortcutRepository
import com.sawt.voice.engine.VoiceController
import com.sawt.voice.feedback.VoiceFeedback
import com.sawt.voice.parser.ArabicCommandParser
import com.sawt.voice.settings.SettingsRepository

/** حاوية تبعيات بسيطة (بدون Hilt) لتسهيل البناء والاختبار. تُهيَّأ مرة واحدة في Application. */
object AppGraph {
    lateinit var appContext: Context
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var errors: ErrorLog
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var parser: ArabicCommandParser
        private set
    lateinit var resolver: AppResolver
        private set
    lateinit var feedback: VoiceFeedback
        private set
    lateinit var shortcuts: ShortcutRepository
        private set
    lateinit var commands: CommandEngine
        private set
    lateinit var voice: VoiceController
        private set

    fun init(application: Application) {
        appContext = application.applicationContext
        settings = SettingsRepository(appContext)
        errors = ErrorLog(appContext)
        db = Room.databaseBuilder(appContext, AppDatabase::class.java, "sawt.db").build()
        parser = ArabicCommandParser()
        resolver = AppResolver(appContext)
        feedback = VoiceFeedback(appContext)
        shortcuts = ShortcutRepository(db.shortcutDao())
        commands = CommandEngine(appContext)
        voice = VoiceController(appContext)
    }
}
