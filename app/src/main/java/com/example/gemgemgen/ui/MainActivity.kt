package com.example.gemgemgen.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            GemgemgenTheme {
                AndroidAutomationHost(appContainer)
            }
        }
    }
}

