package com.spacecamera.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
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
                ProIsoSlider(
                    range = it.iso,
                    analogMax = it.maxAnalogIso,
                    iso = exposicao.iso,
                    enabled = enabled,
                    rotationDeg = rotationDeg,
                    onIsoChange = viewModel::setIso,
                    // Na lateral direita. Em janela larga a coluna de controles ocupa
                    // essa borda, então o slider se afasta dela (a Tarefa 9 revê o eixo).
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = if (isWide) 112.dp else 12.dp)
                )
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
