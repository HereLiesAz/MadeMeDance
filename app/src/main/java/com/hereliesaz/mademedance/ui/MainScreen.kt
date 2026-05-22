package com.hereliesaz.mademedance.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    movementStatus: String,
    audioStatus: String,
    systemStatus: String,
    hasAudioPermission: Boolean,
    movementBpm: Float?,
    audioBpm: Float?,
    onPermissionClick: () -> Unit,
    onClipListClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Made Me Dance") },
                actions = {
                    TextButton(onClick = onClipListClick) {
                        Text("Clips")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Pulse ring visualization
            PulseRing(
                movementBpm = movementBpm,
                audioBpm = audioBpm
            )

            Spacer(modifier = Modifier.height(24.dp))

            // BPM displays
            Text(
                text = movementStatus,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = audioStatus,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Match proximity bar
            MatchProximityBar(movementBpm = movementBpm, audioBpm = audioBpm)

            Spacer(modifier = Modifier.height(24.dp))

            // System status
            Text(
                text = systemStatus,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Permission button or listening indicator
            if (!hasAudioPermission) {
                Button(onClick = onPermissionClick) {
                    Text("Enable Microphone")
                }
            } else {
                Text(
                    text = "Listening for music...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PulseRing(
    movementBpm: Float?,
    audioBpm: Float?
) {
    val proximity = if (movementBpm != null && audioBpm != null) {
        val diff = abs(movementBpm - audioBpm)
        (1f - (diff / 30f).coerceIn(0f, 1f)) // Closer = higher value
    } else {
        0f
    }

    val ringColor by animateColorAsState(
        targetValue = when {
            proximity > 0.83f -> Color(0xFF4CAF50) // Green — near match
            proximity > 0.5f -> Color(0xFFFFC107)  // Amber — getting close
            movementBpm != null -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(300),
        label = "ringColor"
    )

    // Pulse animation based on movement BPM
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseDurationMs = if (movementBpm != null && movementBpm > 0f) {
        (60_000f / movementBpm).toInt().coerceIn(300, 2000)
    } else {
        1000
    }
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val scale = if (movementBpm != null) pulseScale else 0.92f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(160.dp)
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val radius = (size.minDimension / 2f) * scale
            drawCircle(
                color = ringColor,
                radius = radius,
                style = Stroke(width = 6.dp.toPx())
            )
            // Inner glow ring when close to match
            if (proximity > 0.5f) {
                drawCircle(
                    color = ringColor.copy(alpha = proximity * 0.3f),
                    radius = radius * 0.85f,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        // BPM text inside the ring
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = movementBpm?.let { "%.0f".format(it) } ?: "--",
                style = MaterialTheme.typography.headlineLarge,
                color = ringColor
            )
            Text(
                text = "BPM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MatchProximityBar(
    movementBpm: Float?,
    audioBpm: Float?
) {
    val progress by animateFloatAsState(
        targetValue = if (movementBpm != null && audioBpm != null) {
            val diff = abs(movementBpm - audioBpm)
            (1f - (diff / 10f)).coerceIn(0f, 1f) // 0 BPM diff = 100%, 10+ BPM diff = 0%
        } else {
            0f
        },
        animationSpec = tween(300),
        label = "matchProgress"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (movementBpm != null && audioBpm != null) {
                "Match: ${"%.0f".format(progress * 100)}%"
            } else {
                "Waiting for rhythm..."
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = when {
                progress > 0.5f -> Color(0xFF4CAF50)
                progress > 0.25f -> Color(0xFFFFC107)
                else -> MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
