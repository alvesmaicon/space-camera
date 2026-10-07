package com.spacecamera.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Trecho da régua acima do ganho analógico: ali o ISO é amplificação digital. */
private const val DIGITAL_GAIN_RGB = 0xFFFFB300
private val DigitalGainColor = Color(DIGITAL_GAIN_RGB)
private const val INACTIVE_ALPHA = 0.55f
private const val TRACK_ALPHA = 0.45f
private const val SCRIM_ALPHA = 0.35f

/**
 * Requirements: FR-9, FR-10, FR-11, AC-5.2
 * Decisions: ADR-007, Q-11, Q-13
 *
 * Uma escala vertical do modo Pro — a mesma para ISO e obturador (decisão do usuário,
 * 2026-10-07): "AUTO" no topo, o valor atual, e a régua. O topo da régua é o lado
 * mais claro (ISO maior, tempo maior). A régua só conhece posições de 0 a 1; quem
 * converte para ISO ou tempo, sempre em escala logarítmica, é quem a usa.
 *
 * @param position posição do valor manual, ou `null` em automático (sem cursor).
 * @param stops posições das marcas (um stop cada).
 * @param digitalFrom a partir de que posição o ganho é digital (só ISO), ou `null`.
 */
@Composable
internal fun ProScaleSlider(
    valueLabel: String,
    minLabel: String,
    position: Float?,
    stops: List<Float>,
    digitalFrom: Float?,
    description: String,
    enabled: Boolean,
    rotationDeg: Float,
    onPosition: (Float) -> Unit,
    onAuto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val automatico = position == null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .width(56.dp)
            .background(Color.Black.copy(alpha = SCRIM_ALPHA), RoundedCornerShape(28.dp))
            .padding(vertical = 12.dp)
    ) {
        Text(
            "AUTO",
            color = if (automatico) Color.White else Color.White.copy(alpha = INACTIVE_ALPHA),
            fontSize = 11.sp,
            fontWeight = if (automatico) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onAuto)
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .rotate(rotationDeg)
        )
        Text(valueLabel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.rotate(rotationDeg))
        Regua(
            position = position,
            stops = stops,
            digitalFrom = digitalFrom,
            enabled = enabled,
            onPosition = onPosition,
            modifier = Modifier
                .width(40.dp)
                .height(220.dp)
                .semantics { contentDescription = description }
        )
        Text(minLabel, color = Color.White.copy(alpha = INACTIVE_ALPHA), fontSize = 10.sp, modifier = Modifier.rotate(rotationDeg))
    }
}

/** A régua: topo é a posição 1, base a 0. Toque ou arraste escolhe. */
@Composable
private fun Regua(
    position: Float?,
    stops: List<Float>,
    digitalFrom: Float?,
    enabled: Boolean,
    onPosition: (Float) -> Unit,
    modifier: Modifier
) {
    fun posicaoNaAltura(y: Float, altura: Int) = 1f - (y / altura).coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { onPosition(posicaoNaAltura(it.y, size.height)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    onPosition(posicaoNaAltura(change.position.y, size.height))
                }
            }
    ) {
        val x = size.width / 2
        fun yDe(p: Float) = size.height * (1f - p)
        val yDigital = digitalFrom?.takeIf { it < 1f }?.let(::yDe)
        drawLine(Color.White.copy(alpha = TRACK_ALPHA), Offset(x, yDigital ?: 0f), Offset(x, size.height), 3.dp.toPx())
        if (yDigital != null) {
            drawLine(DigitalGainColor, Offset(x, 0f), Offset(x, yDigital), 3.dp.toPx())
            drawLine(DigitalGainColor, Offset(x - 8.dp.toPx(), yDigital), Offset(x + 8.dp.toPx(), yDigital), 2.dp.toPx())
        }
        stops.forEach { p ->
            val y = yDe(p)
            drawLine(Color.White.copy(alpha = TRACK_ALPHA), Offset(x - 5.dp.toPx(), y), Offset(x + 5.dp.toPx(), y), 1.dp.toPx())
        }
        if (position != null) drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(x, yDe(position)))
    }
}
