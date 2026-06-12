package com.kevin.hrtracker.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.material.MaterialTheme
import com.kevin.hrtracker.wear.comms.PhoneMessenger
import com.kevin.hrtracker.wear.sensor.HrSensorManager
import com.kevin.hrtracker.wear.ui.HomeScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val hrSensorManager by lazy { HrSensorManager(this) }
    private val phoneMessenger by lazy { PhoneMessenger(this) }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startHrCollection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HomeScreen()
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startHrCollection()
        } else {
            requestPermission.launch(Manifest.permission.BODY_SENSORS)
        }
    }

    private fun startHrCollection() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                hrSensorManager.hrFlow().collect { bpm ->
                    WatchState.setBpm(bpm)
                    phoneMessenger.sendHrSample(bpm)
                }
            }
        }
    }
}
