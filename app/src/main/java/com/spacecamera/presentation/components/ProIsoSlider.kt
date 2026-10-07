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
import com.spacecamera.camera.ManualExposure

/** Trecho da régua acima do ganho analógico: ali o ISO é amplificação digital. */
private const val DIGITAL_GAIN_RGB = 0xFFFFB300
private val DigitalGainColor = Color(DIGITAL_GAIN_RGB)
private const val INACTIVE_ALPHA = 0.55f
private const val TRACK_ALPHA = 0.45f
private const val SCRIM_ALPHA = 0.35f

/**
 * Requirements: FR-9, AC-5.2, AC-9.1
 * Decisions: ADR-007, Q-11
 *
 * O slider vertical de ISO do modo Pro, na lateral da pré-visualização (decisão do
 * usuário, 2026-10-07): "AUTO" no topo, o valor atual, e a régua em escala
 * logarítmica — cada stop ocupa o mesmo espaço. O trecho acima do limite analógico
 * fica em âmbar: ali o ganho é digital e o ruído cresce.
 *
 * As faixas são exatamente as do HAL (AC-5.2); o valor que sai já está dentro delas.
 *
 * @param iso ISO manual em vigor, ou `null` para automático.
 * @param onIsoChange recebe o ISO escolhido, ou `null` ao tocar em "AUTO".
 */
@Composable
internal fun ProIsoSlider(
    range: IntRange,
    analogMax: Int?,
    iso: Int?,
    enabled: Boolean,
    rotationDeg: Float,
    onIsoChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
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
            color = if (iso == null) Color.White else Color.White.copy(alpha = INACTIVE_ALPHA),
            fontSize = 11.sp,
            fontWeight = if (iso == null) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { onIsoChange(null) }
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .rotate(rotationDeg)
        )
        Text(
            iso?.toString() ?: "ISO",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.rotate(rotationDeg)
        )
        Regua(
            range = range,
            analogMax = analogMax,
            iso = iso,
            enabled = enabled,
            onIsoChange = onIsoChange,
            modifier = Modifier
                .width(40.dp)
                .height(220.dp)
                .semantics { contentDescription = "Escala de ISO" }
        )
        Text(
            range.first.toString(),
            color = Color.White.copy(alpha = INACTIVE_ALPHA),
            fontSize = 10.sp,
            modifier = Modifier.rotate(rotationDeg)
        )
    }
}

/** A régua: topo é o ISO máximo, base o mínimo. Toque ou arraste escolhe o valor. */
@Composable
private fun Regua(
    range: IntRange,
    analogMax: Int?,
    iso: Int?,
    enabled: Boolean,
    onIsoChange: (Int?) -> Unit,
    modifier: Modifier
) {
    // Posição vertical (0 = topo) → ISO. A régua é invertida: o máximo fica em cima.
    fun isoNaAltura(y: Float, altura: Int) = ManualExposure.isoAt(1f - (y / altura).coerceIn(0f, 1f), range)
    Canvas(
        modifier = modifier
            .pointerInput(range, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { onIsoChange(isoNaAltura(it.y, size.height)) }
            }
            .pointerInput(range, enabled) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    onIsoChange(isoNaAltura(change.position.y, size.height))
                }
            }
    ) {
        val x = size.width / 2
        val trilho = 3.dp.toPx()
        fun yDe(valor: Int) = size.height * (1f - ManualExposure.positionOf(valor, range))
        val yAnalogico = analogMax?.takeIf { it < range.last }?.let { yDe(it) }

        // Trilho: âmbar acima do limite analógico, branco abaixo.
        drawLine(Color.White.copy(alpha = TRACK_ALPHA), Offset(x, yAnalogico ?: 0f), Offset(x, size.height), trilho)
        if (yAnalogico != null) {
            drawLine(DigitalGainColor, Offset(x, 0f), Offset(x, yAnalogico), trilho)
            drawLine(DigitalGainColor, Offset(x - 8.dp.toPx(), yAnalogico), Offset(x + 8.dp.toPx(), yAnalogico), 2.dp.toPx())
        }
        // Marcas de stop (cada dobro do ISO), começando no mínimo.
        var stop = range.first
        while (stop <= range.last) {
            val y = yDe(stop)
            drawLine(Color.White.copy(alpha = TRACK_ALPHA), Offset(x - 5.dp.toPx(), y), Offset(x + 5.dp.toPx(), y), 1.dp.toPx())
            stop *= 2
        }
        // Cursor só com ISO manual — no automático quem decide é o AE.
        if (iso != null) {
            drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(x, yDe(iso)))
        }
    }
}
