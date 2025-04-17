package de.timklge.karooheadwind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import de.timklge.karooheadwind.screens.MainScreen
import de.timklge.karooheadwind.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                MainScreen() {
                    finish()
                }
            }
        }
    }
}
