package com.spacecamera.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.spacecamera.camera.ManualExposure
import com.spacecamera.camera.mode.ManualSensorRanges
import com.spacecamera.camera.mode.OverlayId
import com.spacecamera.presentation.viewmodels.CameraViewModel

/**
 * Requirements: FR-1, NFR-2
 * Decisions: ADR-001
 *
 * Resolve o [OverlayId] que o modo declara no composable que o desenha. É aqui —
 * e não na `CameraScreen` — que um modo novo com overlay acrescenta seu ramo: o
 * NFR-2 exige zero edições na tela para um modo novo.
 *
 * Os identificadores que este `when` conhece têm de estar em
 * [ModeSurfaces.overlays]; o teste de completude cobra que todo overlay declarado
 * por algum modo esteja lá.
 */
@Composable
internal fun BoxScope.ModeOverlay(
    overlay: OverlayId,
    viewModel: CameraViewModel,
    isWide: Boolean,
    enabled: Boolean,
    rotationDeg: Float
) {
    when (overlay) {
        ModeSurfaces.PRO_SCALES -> {
            val faixas by viewModel.manualSensorRanges.collectAsState()
            val exposicao by viewModel.manualExposure.collectAsState()
            faixas?.let {
                // Na lateral direita: obturador e ISO lado a lado, ISO junto à borda.
                // Em janela larga a coluna de controles ocupa essa borda, então as
                // escalas se afastam dela (a Tarefa 9 revê o eixo).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = if (isWide) 112.dp else 12.dp)
                ) {
                    EscalaDoObturador(it, exposicao.exposureNs, enabled, rotationDeg, viewModel::setShutter)
                    EscalaDeIso(it, exposicao.iso, enabled, rotationDeg, viewModel::setIso)
                }
            }
        }
    }
}

/** Quanto tempo o aviso de modo recusado fica na tela. */
private const val NOTICE_MS = 2_500L
private const val NOTICE_SCRIM_ALPHA = 0.6f

/**
 * Requirements: FR-2
 * Decisions: ADR-002
 *
 * O "aviso discreto" do fluxo 4.2 do design: o aparelho recusou a combinação de use
 * cases do modo, e a sessão voltou ao modo anterior. Some sozinho.
 */
@Composable
internal fun BoxScope.ModeRejectedNotice(viewModel: CameraViewModel) {
    var aviso by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.modeRejected.collect { recusado ->
            aviso = "Modo ${recusado.label} indisponível neste aparelho"
            delay(NOTICE_MS)
            aviso = null
        }
    }
    AnimatedVisibility(visible = aviso != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
        Text(
            aviso.orEmpty(),
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .background(Color.Black.copy(alpha = NOTICE_SCRIM_ALPHA), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun EscalaDeIso(faixas: ManualSensorRanges, iso: Int?, enabled: Boolean, rotationDeg: Float, onIso: (Int?) -> Unit) {
    val faixa = faixas.iso
    ProScaleSlider(
        valueLabel = iso?.toString() ?: "ISO",
        minLabel = faixa.first.toString(),
        position = iso?.let { ManualExposure.positionOf(it, faixa) },
        stops = generateSequence(faixa.first) { it * 2 }.takeWhile { it <= faixa.last }
            .map { ManualExposure.positionOf(it, faixa) }.toList(),
        digitalFrom = faixas.maxAnalogIso?.let { ManualExposure.positionOf(it, faixa) },
        description = "Escala de ISO",
        enabled = enabled,
        rotationDeg = rotationDeg,
        onPosition = { onIso(ManualExposure.isoAt(it, faixa)) },
        onAuto = { onIso(null) }
    )
}

@Composable
private fun EscalaDoObturador(faixas: ManualSensorRanges, ns: Long?, enabled: Boolean, rotationDeg: Float, onNs: (Long?) -> Unit) {
    val faixa = ManualExposure.userShutterRange(faixas.exposureTimeNs)
    ProScaleSlider(
        valueLabel = ns?.let(ManualExposure::shutterLabel) ?: "OBT",
        minLabel = ManualExposure.shutterLabel(faixa.first),
        position = ns?.let { ManualExposure.positionOfExposure(it, faixa) },
        stops = generateSequence(faixa.first) { it * 2 }.takeWhile { it <= faixa.last }
            .map { ManualExposure.positionOfExposure(it, faixa) }.toList(),
        digitalFrom = null,
        description = "Escala do obturador",
        enabled = enabled,
        rotationDeg = rotationDeg,
        onPosition = { onNs(ManualExposure.exposureAt(it, faixa)) },
        onAuto = { onNs(null) }
    )
}
