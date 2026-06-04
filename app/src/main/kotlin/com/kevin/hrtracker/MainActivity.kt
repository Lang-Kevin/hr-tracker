package com.kevin.hrtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kevin.hrtracker.ui.scan.ScanScreen
import com.kevin.hrtracker.ui.theme.HRTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HRTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScanScreen()
                }
            }
        }
    }
}
