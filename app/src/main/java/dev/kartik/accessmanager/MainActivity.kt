package dev.kartik.accessmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.kartik.accessmanager.presentation.navigation.AccessManagerNavHost
import dev.kartik.accessmanager.presentation.theme.AccessManagerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AccessManagerTheme {
                AccessManagerNavHost()
            }
        }
    }
}
