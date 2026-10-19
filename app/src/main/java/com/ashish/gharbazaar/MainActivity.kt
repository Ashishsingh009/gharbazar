package com.ashish.gharbazaar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ashish.gharbazaar.shell.GharBazaarShell
import com.ashish.gharbazaar.ui.theme.GharBazaarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GharBazaarTheme {
                GharBazaarShell((application as GharBazaarApplication).graph)
            }
        }
    }
}