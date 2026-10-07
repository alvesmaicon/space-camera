package com.spacecamera.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spacecamera.camera.VideoOption
import com.spacecamera.presentation.icons.TimerIcon10
import com.spacecamera.presentation.icons.TimerIcon3
import com.spacecamera.presentation.icons.TimerIcon5
import com.spacecamera.presentation.screens.OFF_CONTROL_ALPHA
import com.spacecamera.presentation.viewmodels.RecordingDelay

/*
 * Os botões da barra superior, usados por [TopBarControl]. Separados dele só para que
 * nenhum dos dois passe de 400 linhas (NFR-3); vieram da `CameraScreen` sem mudança.
 */

// Resolution two-line button (original — video mode)
@Composable
internal fun ResolutionTopBarButton(
    option: com.spacecamera.camera.VideoOption,
    isActive: Boolean,
    enabled: Boolean = true,
    rotationDeg: Float = 0f,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                if (isActive && enabled) Color.White.copy(alpha = 0.22f) else Color.Transparent,
                RoundedCornerShape(7.dp)
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((-2).dp),
            modifier = Modifier.rotate(rotationDeg)
        ) {
            Text(
                option.qualityLabel,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.28f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp
            )
            Text(
                "${option.fps}",
                color = if (enabled) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.2f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 8.sp
            )
        }
    }
}

// Resolution button — photo mode (single label like "MAX", "12MP")
@Composable
internal fun ResolutionTopBarButton(
    displayLabel: String,
    isActive: Boolean,
    enabled: Boolean = true,
    rotationDeg: Float = 0f,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                if (isActive && enabled) Color.White.copy(alpha = 0.22f) else Color.Transparent,
                RoundedCornerShape(7.dp)
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            displayLabel,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.28f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.rotate(rotationDeg)
        )
    }
}

// Text-only toggle (EIS) — no background, white when on, gray when off
@Composable
internal fun TopBarTextToggle(
    label: String,
    isOn: Boolean,
    enabled: Boolean = true,
    rotationDeg: Float = 0f,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = when {
                !enabled -> Color.White.copy(alpha = 0.2f)
                isOn -> Color.White
                else -> Color.White.copy(alpha = OFF_CONTROL_ALPHA)
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.rotate(rotationDeg)
        )
    }
}

@Composable
internal fun TopBarIconToggle(
    isOn: Boolean,
    iconOn: ImageVector,
    iconOff: ImageVector,
    desc: String,
    enabled: Boolean = true,
    iconSize: Dp = 26.dp,
    rotationDeg: Float = 0f,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(46.dp)
    ) {
        Icon(
            if (isOn) iconOn else iconOff,
            contentDescription = desc,
            tint = when {
                !enabled -> Color.White.copy(alpha = 0.2f)
                isOn -> Color.White
                else -> Color.White.copy(alpha = OFF_CONTROL_ALPHA)
            },
            modifier = Modifier.size(iconSize).rotate(rotationDeg)
        )
    }
}

@Composable
internal fun TopBarTimerButton(
    delay: RecordingDelay,
    enabled: Boolean = true,
    rotationDeg: Float = 0f,
    onClick: () -> Unit
) {
    val iconTint = when {
        !enabled -> Color.White.copy(alpha = 0.2f)
        delay != RecordingDelay.OFF -> Color.White
        else -> Color.White.copy(alpha = OFF_CONTROL_ALPHA)
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(46.dp)
    ) {
        when (delay) {
            RecordingDelay.OFF -> Icon(
                Icons.Outlined.TimerOff,
                contentDescription = "Timer desativado",
                tint = iconTint,
                modifier = Modifier.size(26.dp).rotate(rotationDeg)
            )
            RecordingDelay.THREE -> Icon(
                TimerIcon3,
                contentDescription = "Timer 3s",
                tint = iconTint,
                modifier = Modifier.size(26.dp).rotate(rotationDeg)
            )
            RecordingDelay.FIVE -> Icon(
                TimerIcon5,
                contentDescription = "Timer 5s",
                tint = iconTint,
                modifier = Modifier.size(26.dp).rotate(rotationDeg)
            )
            RecordingDelay.TEN -> Icon(
                TimerIcon10,
                contentDescription = "Timer 10s",
                tint = iconTint,
                modifier = Modifier.size(26.dp).rotate(rotationDeg)
            )
        }
    }
}
