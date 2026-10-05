package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VoiceState
import com.example.ui.theme.CoralSpeaking
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldListening
import com.example.ui.theme.IndigoProcessing
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun StateBadge(
    state: VoiceState,
    isContinuousActive: Boolean,
    modifier: Modifier = Modifier
) {
    val (dotColor, titleText, subtitleText) = when (state) {
        VoiceState.IDLE -> Triple(
            CyanPrimary,
            "IDLE",
            "Tap to start continuous conversation"
        )
        VoiceState.LISTENING -> Triple(
            EmeraldListening,
            "LISTENING",
            "SANA is listening… speak naturally"
        )
        VoiceState.PROCESSING -> Triple(
            IndigoProcessing,
            "THINKING",
            "Gemini is reasoning & formulating voice"
        )
        VoiceState.SPEAKING -> Triple(
            CoralSpeaking,
            "SPEAKING",
            "Real Gemini Native Audio output"
        )
        VoiceState.ERROR -> Triple(
            CrimsonError,
            "ERROR",
            "Tap retry or check settings"
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.testTag("state_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF131A29))
                .border(1.dp, dotColor.copy(alpha = 0.35f), RoundedCornerShape(32.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = titleText,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            if (isContinuousActive) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "• LIVE",
                    color = EmeraldListening,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subtitleText,
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
