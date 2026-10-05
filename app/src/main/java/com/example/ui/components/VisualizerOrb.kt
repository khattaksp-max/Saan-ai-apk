package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.VoiceState
import com.example.ui.theme.AmberSpeakingGlow
import com.example.ui.theme.CoralSpeaking
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldListening
import com.example.ui.theme.EmeraldListeningGlow
import com.example.ui.theme.IndigoProcessing
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.VioletSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VisualizerOrb(
    state: VoiceState,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbTransition")

    // Idle breathing animation
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulse"
    )

    // Processing rotation angle
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "processingRotation"
    )

    // Ripple wave expansion
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleScale"
    )

    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleAlpha"
    )

    val primaryColor: Color
    val secondaryColor: Color
    val outerGlowColor: Color

    when (state) {
        VoiceState.IDLE -> {
            primaryColor = CyanPrimary
            secondaryColor = VioletSecondary
            outerGlowColor = CyanPrimary.copy(alpha = 0.25f)
        }
        VoiceState.LISTENING -> {
            primaryColor = EmeraldListening
            secondaryColor = EmeraldListeningGlow
            outerGlowColor = EmeraldListening.copy(alpha = 0.4f)
        }
        VoiceState.PROCESSING -> {
            primaryColor = IndigoProcessing
            secondaryColor = PurpleAccent
            outerGlowColor = PurpleAccent.copy(alpha = 0.45f)
        }
        VoiceState.SPEAKING -> {
            primaryColor = CoralSpeaking
            secondaryColor = AmberSpeakingGlow
            outerGlowColor = CoralSpeaking.copy(alpha = 0.5f)
        }
        VoiceState.ERROR -> {
            primaryColor = CrimsonError
            secondaryColor = Color(0xFFB91C1C)
            outerGlowColor = CrimsonError.copy(alpha = 0.35f)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(240.dp)
            .testTag("visualizer_orb")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 3.4f

            // Dynamic scale factor according to state and amplitude
            val dynamicScale = when (state) {
                VoiceState.IDLE -> idlePulse
                VoiceState.LISTENING -> 1.0f + (amplitude * 0.45f)
                VoiceState.PROCESSING -> idlePulse
                VoiceState.SPEAKING -> 1.0f + (amplitude * 0.4f)
                VoiceState.ERROR -> 1.0f
            }

            val radius = baseRadius * dynamicScale

            // Concentric outer ripple waves when actively speaking or listening
            if (state == VoiceState.LISTENING || state == VoiceState.SPEAKING) {
                val waveRadius1 = radius * rippleScale
                drawCircle(
                    color = primaryColor.copy(alpha = rippleAlpha * 0.7f),
                    radius = waveRadius1,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )

                val waveRadius2 = radius * (1f + (rippleScale - 1f) * 0.5f)
                drawCircle(
                    color = secondaryColor.copy(alpha = (rippleAlpha * 0.9f).coerceIn(0f, 1f)),
                    radius = waveRadius2,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Outer soft glow halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(outerGlowColor, Color.Transparent),
                    center = center,
                    radius = radius * 1.55f
                ),
                radius = radius * 1.55f,
                center = center
            )

            // Dynamic rotating gradient inside orb
            val angleRad = Math.toRadians(rotationAngle.toDouble())
            val gradOffsetX = (cos(angleRad) * radius * 0.5).toFloat()
            val gradOffsetY = (sin(angleRad) * radius * 0.5).toFloat()

            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(primaryColor, secondaryColor),
                    start = Offset(center.x - gradOffsetX, center.y - gradOffsetY),
                    end = Offset(center.x + gradOffsetX, center.y + gradOffsetY)
                ),
                radius = radius,
                center = center
            )

            // Inner gloss ring highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
                    radius = radius * 0.6f
                ),
                radius = radius * 0.7f,
                center = center
            )
        }

        // Center state icon
        val iconColor = Color.White
        when (state) {
            VoiceState.IDLE -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Start Voice",
                    tint = iconColor,
                    modifier = Modifier.size(44.dp)
                )
            }
            VoiceState.LISTENING -> {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Listening",
                    tint = iconColor,
                    modifier = Modifier.size(48.dp)
                )
            }
            VoiceState.PROCESSING -> {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Thinking",
                    tint = iconColor,
                    modifier = Modifier.size(44.dp)
                )
            }
            VoiceState.SPEAKING -> {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Speaking",
                    tint = iconColor,
                    modifier = Modifier.size(46.dp)
                )
            }
            VoiceState.ERROR -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = iconColor,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}
