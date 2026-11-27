package com.sawt.voice

import android.app.Application

class SawtApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
