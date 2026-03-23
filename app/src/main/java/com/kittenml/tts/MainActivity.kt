package com.kittenml.tts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kittenml.tts.ui.screen.TTSScreen
import com.kittenml.tts.ui.theme.KittenTTSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KittenTTSTheme {
                TTSScreen()
            }
        }
    }
}
