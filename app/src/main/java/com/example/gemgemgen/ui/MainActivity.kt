package com.example.gemgemgen.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gemgemgen.ui.android.AndroidAppContainer
import com.example.gemgemgen.ui.android.AndroidAutomationHost
import com.example.gemgemgen.ui.theme.GemgemgenTheme

class MainActivity : ComponentActivity() {
    private val appContainer by lazy {
        AndroidAppContainer(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val palette by appContainer.themePaletteStore.currentPalette.collectAsStateWithLifecycle()
            val themeMode by appContainer.themePaletteStore.currentMode.collectAsStateWithLifecycle()
            GemgemgenTheme(palette = palette, themeMode = themeMode) {
                AndroidAutomationHost(appContainer)
            }
        }
    }
}

