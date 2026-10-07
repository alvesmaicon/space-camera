// O assunto do arquivo é a função TopBarControl; TopBarContext é só o pacote de parâmetros dela.
@file:Suppress("MatchingDeclarationName")

package com.spacecamera.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HdrOff
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.camera.mode.ControlId
import com.spacecamera.camera.mode.FlashBehavior
import com.spacecamera.presentation.screens.OFF_CONTROL_ALPHA
import com.spacecamera.presentation.viewmodels.CameraViewModel
import com.spacecamera.presentation.viewmodels.PhotoFlashMode

/** O que todo controle da barra precisa da tela, num lugar só. */
internal class TopBarContext(
    val viewModel: CameraViewModel,
    val barDisabled: Boolean,
    val rotationDeg: Float,
    val resolutionMenuOpen: Boolean,
    val onToggleResolutionMenu: () -> Unit,
    val expanded: Boolean,
    val onToggleExpanded: () -> Unit,
    val onOpenSettings: () -> Unit
)

/**
 * Requirements: FR-1, NFR-2, NFR-3
 * Decisions: ADR-001, Q-09
 *
 * Um controle da barra superior, pelo [ControlId] que o modo declara em `controls` ou
 * `moreControls`. É a tabela identificador → composable dos controles: o `when` é
 * exaustivo, então controle novo sem desenho não compila — e quem acrescenta um
 * controle edita este arquivo, não a `CameraScreen`. Saiu dela na Tarefa 13 (NFR-3).
 *
 * O `when` só despacha; cada controle é um composable pequeno que lê o próprio estado.
 *
 * @param mode o modo do conteúdo animado, não o atual: na troca de modo as duas barras
 *   aparecem juntas, e a que sai não pode mudar de estilo no caminho.
 */
@Composable
internal fun TopBarControl(id: ControlId, mode: CameraModeDefinition, c: TopBarContext) {
    when (id) {
        ControlId.RESOLUTION -> Resolucao(c)
        ControlId.STABILIZATION -> Estabilizacao(c)
        ControlId.PHOTO_QUALITY -> QualidadeDaFoto(c)
        ControlId.ASPECT_RATIO -> Proporcao(c)
        ControlId.FLASH -> Flash(mode, c)
        ControlId.TIMER -> Timer(c)
        ControlId.MORE_OPTIONS -> MaisOpcoes(c)
        ControlId.NOISE_REDUCTION -> ReducaoDeRuido(c)
        ControlId.IMAGE_ENHANCEMENT -> Melhoria(c)
        ControlId.HDR -> Hdr(c)
        ControlId.MICROPHONE -> Microfone(c)
        ControlId.GRID -> Grade(c)
        ControlId.SETTINGS -> Configuracoes(c)
    }
}

@Composable
private fun Resolucao(c: TopBarContext) {
    val opcao by c.viewModel.selectedVideoOption.collectAsState()
    ResolutionTopBarButton(
        option = opcao,
        isActive = c.resolutionMenuOpen,
        enabled = !c.barDisabled,
        rotationDeg = c.rotationDeg,
        onClick = c.onToggleResolutionMenu
    )
}

@Composable
private fun Estabilizacao(c: TopBarContext) {
    val ligado by c.viewModel.isStabilizationEnabled.collectAsState()
    val suportado by c.viewModel.isEisSupported.collectAsState()
    TopBarTextToggle(
        label = "EIS",
        isOn = ligado,
        enabled = !c.barDisabled && suportado,
        rotationDeg = c.rotationDeg,
        onClick = { c.viewModel.toggleStabilization() }
    )
}

@Composable
private fun QualidadeDaFoto(c: TopBarContext) {
    val preset by c.viewModel.photoQualityPreset.collectAsState()
    val contagem by c.viewModel.countdownSeconds.collectAsState()
    ResolutionTopBarButton(
        displayLabel = preset.displayLabel,
        isActive = false,
        enabled = contagem == 0,
        rotationDeg = c.rotationDeg,
        onClick = { c.viewModel.setPhotoQualityPreset(preset.next()) }
    )
}

@Composable
private fun Proporcao(c: TopBarContext) {
    val proporcao by c.viewModel.selectedAspectRatio.collectAsState()
    val contagem by c.viewModel.countdownSeconds.collectAsState()
    TopBarTextToggle(
        label = proporcao,
        isOn = true,
        enabled = contagem == 0,
        rotationDeg = c.rotationDeg,
        onClick = {
            val photoRatios = listOf("Full", "9:16", "3:4")
            val idx = photoRatios.indexOf(proporcao).let { if (it < 0) 0 else it }
            c.viewModel.setAspectRatio(photoRatios[(idx + 1) % photoRatios.size])
        }
    )
}

@Composable
private fun Flash(mode: CameraModeDefinition, c: TopBarContext) {
    val frontal by c.viewModel.isFrontCamera.collectAsState()
    val contagem by c.viewModel.countdownSeconds.collectAsState()
    val habilitado = !frontal && contagem == 0
    when (mode.flashBehavior()) {
        FlashBehavior.Torch -> {
            val aceso by c.viewModel.isFlashOn.collectAsState()
            TopBarIconToggle(
                isOn = aceso,
                iconOn = Icons.Default.FlashOn,
                iconOff = Icons.Default.FlashOff,
                desc = "Flash",
                enabled = habilitado,
                rotationDeg = c.rotationDeg,
                onClick = { c.viewModel.toggleFlash() },
                iconSize = 26.dp
            )
        }
        FlashBehavior.PhotoCycle -> {
            val ciclo by c.viewModel.photoFlashMode.collectAsState()
            IconButton(onClick = { c.viewModel.toggleFlash() }, enabled = habilitado, modifier = Modifier.size(46.dp)) {
                Icon(
                    imageVector = when (ciclo) {
                        PhotoFlashMode.AUTO -> Icons.Default.FlashAuto
                        PhotoFlashMode.ON -> Icons.Default.FlashOn
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash",
                    tint = when {
                        !habilitado -> Color.White.copy(alpha = 0.2f)
                        ciclo != PhotoFlashMode.OFF -> Color.White
                        else -> Color.White.copy(alpha = OFF_CONTROL_ALPHA)
                    },
                    modifier = Modifier.size(26.dp).rotate(c.rotationDeg)
                )
            }
        }
        FlashBehavior.Unavailable -> Unit
    }
}

@Composable
private fun Timer(c: TopBarContext) {
    val atraso by c.viewModel.recordingDelay.collectAsState()
    TopBarTimerButton(
        delay = atraso,
        enabled = !c.barDisabled,
        rotationDeg = c.rotationDeg,
        onClick = { c.viewModel.cycleRecordingDelay() }
    )
}

@Composable
private fun MaisOpcoes(c: TopBarContext) {
    IconButton(onClick = c.onToggleExpanded, modifier = Modifier.size(46.dp)) {
        Icon(
            if (c.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (c.expanded) "Recolher" else "Mais opções",
            tint = if (c.expanded) Color.White else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp).rotate(c.rotationDeg)
        )
    }
}

@Composable
private fun ReducaoDeRuido(c: TopBarContext) {
    val ligado by c.viewModel.isNoiseReductionEnabled.collectAsState()
    TopBarTextToggle(
        label = "NR",
        isOn = ligado,
        enabled = !c.barDisabled,
        rotationDeg = c.rotationDeg,
        onClick = { c.viewModel.toggleNoiseReduction() }
    )
}

@Composable
private fun Melhoria(c: TopBarContext) {
    val ligado by c.viewModel.isImageEnhancementEnabled.collectAsState()
    val contagem by c.viewModel.countdownSeconds.collectAsState()
    TopBarIconToggle(
        isOn = ligado,
        iconOn = Icons.Default.AutoAwesome,
        iconOff = Icons.Default.AutoAwesome,
        desc = "Melhoria",
        enabled = contagem == 0,
        rotationDeg = c.rotationDeg,
        onClick = { c.viewModel.toggleImageEnhancement() },
        iconSize = 24.dp
    )
}

@Composable
private fun Hdr(c: TopBarContext) {
    val ligado by c.viewModel.isHdrEnabled.collectAsState()
    val suportado by c.viewModel.isHdrSupported.collectAsState()
    TopBarIconToggle(
        isOn = ligado,
        iconOn = Icons.Default.HdrOn,
        iconOff = Icons.Default.HdrOff,
        desc = "HDR",
        enabled = !c.barDisabled && suportado,
        rotationDeg = c.rotationDeg,
        onClick = { c.viewModel.toggleHdr() },
        iconSize = 26.dp
    )
}

@Composable
private fun Microfone(c: TopBarContext) {
    val mudo by c.viewModel.isMicMuted.collectAsState()
    TopBarIconToggle(
        isOn = !mudo,
        iconOn = Icons.Default.Mic,
        iconOff = Icons.Default.MicOff,
        desc = "Microfone",
        enabled = !c.barDisabled,
        rotationDeg = c.rotationDeg,
        onClick = { c.viewModel.toggleMic() },
        iconSize = 26.dp
    )
}

@Composable
private fun Grade(c: TopBarContext) {
    val ligada by c.viewModel.isGridEnabled.collectAsState()
    TopBarIconToggle(
        isOn = ligada,
        iconOn = Icons.Default.GridOn,
        iconOff = Icons.Default.GridOff,
        desc = "Grade",
        enabled = !c.barDisabled,
        rotationDeg = c.rotationDeg,
        onClick = { c.viewModel.toggleGrid() },
        iconSize = 26.dp
    )
}

@Composable
private fun Configuracoes(c: TopBarContext) {
    IconButton(onClick = c.onOpenSettings, modifier = Modifier.size(46.dp)) {
        Icon(
            Icons.Default.VideoSettings,
            contentDescription = "Configurações",
            tint = Color.White,
            modifier = Modifier.size(26.dp).rotate(c.rotationDeg)
        )
    }
}
