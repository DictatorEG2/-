package com.sawt.voice

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.sawt.voice.ui.nav.Routes
import com.sawt.voice.ui.nav.SawtNavHost
import com.sawt.voice.ui.theme.SawtTheme
import com.sawt.voice.update.GithubUpdateClient
import com.sawt.voice.update.UpdateStartupCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_OPEN_UPDATE_SETTINGS = "open_update_settings" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val openUpdateSettings = intent.getBooleanExtra(EXTRA_OPEN_UPDATE_SETTINGS, false)
        setContent {
            SawtTheme { SawtNavHost(if (openUpdateSettings) Routes.VOICE_SETTINGS else Routes.SPLASH) }
        }
        checkForUpdatesInBackground()
    }

    private fun checkForUpdatesInBackground() {
        if (!UpdateStartupCheck.shouldCheck(this)) return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = GithubUpdateClient.check(this@MainActivity)
            UpdateStartupCheck.markChecked(this@MainActivity, result !is GithubUpdateClient.CheckResult.Failed)
            if (result is GithubUpdateClient.CheckResult.Available) {
                UpdateStartupCheck.notifyAvailable(this@MainActivity, result.release.tag)
            }
        }
    }
}
