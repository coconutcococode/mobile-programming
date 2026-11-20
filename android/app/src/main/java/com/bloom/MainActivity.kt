package com.bloom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloom.core.datastore.AppSettings
import com.bloom.core.datastore.SettingsDataStore
import com.bloom.core.designsystem.theme.BloomTheme
import com.bloom.core.navigation.BloomApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsDataStore.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            BloomTheme(darkTheme = settings.darkMode ?: isSystemInDarkTheme()) {
                BloomApp()
            }
        }
    }
}
