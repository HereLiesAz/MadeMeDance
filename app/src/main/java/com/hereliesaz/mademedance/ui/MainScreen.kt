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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
    isServiceRunning: Boolean,
    movementBpm: Float?,
    audioBpm: Float?,
    sensitivity: Float,
    batteryDrainPerHour: Float?,
    powerSaving: Boolean,
    onSensitivityChange: (Float) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
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
            PulseRing(
                movementBpm = movementBpm,
                audioBpm = audioBpm,
                active = isServiceRunning
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(text = movementStatus, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = audioStatus, style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(16.dp))
            MatchProximityBar(movementBpm = movementBpm, audioBpm = audioBpm)
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = systemStatus,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            SensitivityControl(
                sensitivity = sensitivity,
                powerSaving = powerSaving,
                batteryDrainPerHour = batteryDrainPerHour,
                onSensitivityChange = onSensitivityChange
            )

            Spacer(modifier = Modifier.height(24.dp))

            when {
                !hasAudioPermission -> {
                    Button(onClick = onPermissionClick) {
                        Text("Enable Microphone & Start")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "MadeMeDance listens in the background. You can close the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                isServiceRunning -> {
                    OutlinedButton(onClick = onStopClick) {
                        Text("Stop listening")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Running in the background. Close the app — it'll keep listening.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    Button(onClick = onStartClick) {
                        Text("Start listening")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap to start. The app runs as a background service — you don't need to keep it open.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SensitivityControl(
    sensitivity: Float,
    powerSaving: Boolean,
    batteryDrainPerHour: Float?,
    onSensitivityChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Sensitivity: ${"%.0f".format(sensitivity * 100)}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = sensitivity,
            onValueChange = onSensitivityChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Text(
            text = "Lower for vigorous dancing, higher for subtle movement.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (batteryDrainPerHour != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (powerSaving) {
                    "Battery: ~${"%.0f".format(batteryDrainPerHour)}%/hr — power-saving, sensitivity reduced"
                } else {
                    "Battery: ~${"%.0f".format(batteryDrainPerHour)}%/hr"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (powerSaving) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PulseRing(
    movementBpm: Float?,
    audioBpm: Float?,
    active: Boolean
) {
    val proximity = if (movementBpm != null && audioBpm != null) {
        val diff = abs(movementBpm - audioBpm)
        (1f - (diff / 30f).coerceIn(0f, 1f))
    } else 0f

    val ringColor by animateColorAsState(
        targetValue = when {
            !active -> MaterialTheme.colorScheme.outlineVariant
            proximity > 0.83f -> Color(0xFF4CAF50)
            proximity > 0.5f -> Color(0xFFFFC107)
            movementBpm != null -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(300),
        label = "ringColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseDurationMs = if (movementBpm != null && movementBpm > 0f) {
        (60_000f / movementBpm).toInt().coerceIn(300, 2000)
    } else 1000
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val scale = if (active && movementBpm != null) pulseScale else 0.92f

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
            if (proximity > 0.5f) {
                drawCircle(
                    color = ringColor.copy(alpha = proximity * 0.3f),
                    radius = radius * 0.85f,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
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
            (1f - (diff / 10f)).coerceIn(0f, 1f)
        } else 0f,
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
