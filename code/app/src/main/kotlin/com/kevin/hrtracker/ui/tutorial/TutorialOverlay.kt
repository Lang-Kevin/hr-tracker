package com.kevin.hrtracker.ui.tutorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kevin.hrtracker.ui.theme.PrimaryPurple

data class TutorialStep(val key: String, val title: String, val text: String)

/** Tracks on-screen bounds of tagged UI elements so the overlay can spotlight them. */
class TutorialAnchors {
    private val rects = mutableStateMapOf<String, Rect>()

    fun modifierFor(key: String): Modifier = Modifier.onGloballyPositioned { coords ->
        rects[key] = coords.boundsInRoot()
    }

    fun rectOf(key: String): Rect? = rects[key]
}

@Composable
fun rememberTutorialAnchors() = remember { TutorialAnchors() }

fun Modifier.tutorialAnchor(anchors: TutorialAnchors, key: String): Modifier =
    this.then(anchors.modifierFor(key))

/**
 * Dims the screen and punches a hole around the currently highlighted element,
 * with an explanation card pinned to the bottom. One step at a time.
 */
@Composable
fun TutorialOverlay(
    steps: List<TutorialStep>,
    anchors: TutorialAnchors,
    visible: Boolean,
    onFinish: () -> Unit
) {
    if (!visible || steps.isEmpty()) return
    var index by remember(visible) { mutableIntStateOf(0) }
    val step = steps[index]
    val rect = anchors.rectOf(step.key)

    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenHeightPx = with(density) { maxHeight.toPx() }
        // Card is ~180dp tall; put it on whichever half the highlighted rect doesn't occupy.
        val cardHeightPx = with(density) { 180.dp.toPx() }
        val cardAtTop = rect != null && rect.bottom > screenHeightPx - cardHeightPx

        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(Color.Black.copy(alpha = 0.78f))
            rect?.let {
                val pad = 8.dp.toPx()
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(it.left - pad, it.top - pad),
                    size = Size(it.width + pad * 2, it.height + pad * 2),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    blendMode = BlendMode.Clear
                )
            }
        }

        Card(
            modifier = Modifier
                .align(if (cardAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryPurple)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(step.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(step.text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onFinish) {
                        Text("Überspringen", color = Color.White.copy(alpha = 0.8f))
                    }
                    Button(
                        onClick = { if (index < steps.lastIndex) index++ else onFinish() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = PrimaryPurple)
                    ) {
                        Text(if (index < steps.lastIndex) "Weiter (${index + 1}/${steps.size})" else "Fertig")
                    }
                }
            }
        }
    }
}
