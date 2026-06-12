package com.kevin.hrtracker.wear.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.kevin.hrtracker.wear.WatchState

@Composable
fun HomeScreen() {
    val bpm by WatchState.currentBpm.collectAsState()
    val isRecording by WatchState.isRecording.collectAsState()

    val heartScale = remember { Animatable(1f) }
    LaunchedEffect(bpm) {
        bpm?.let {
            heartScale.animateTo(1.35f, tween(100))
            heartScale.animateTo(1f, tween(250))
        }
    }

    Scaffold(timeText = { TimeText() }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1D1B20)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "♥",
                    fontSize = 28.sp,
                    color = Color(0xFFF4738E),
                    modifier = Modifier.scale(heartScale.value)
                )
                Text(
                    text = bpm?.toString() ?: "–",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "BPM",
                    fontSize = 14.sp,
                    color = Color(0xFF9E9E9E)
                )
                if (isRecording) {
                    Text(
                        text = "● Aufnahme läuft",
                        fontSize = 11.sp,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}
