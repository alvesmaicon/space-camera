package com.spacecamera.presentation.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spacecamera.camera.mode.ArrangedModes
import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.presentation.layout.AxisContainer
import com.spacecamera.presentation.layout.AxisScope

/** Opacidade dos rótulos não selecionados — o `OFF_CONTROL_ALPHA` da `CameraScreen`. */
private const val INACTIVE_ALPHA = 0.48f

/** Largura de cada item do carrossel; o deslocamento anima em múltiplos dela. */
private const val ITEM_WIDTH_DP = 88f

/**
 * Requirements: FR-6, FR-17, AC-1.1, AC-6.1
 * Decisions: ADR-001, ADR-004
 *
 * O seletor de modos, montado a partir do registro: os modos do plano, o modo
 * ativo se ele veio da gaveta, e o item "Mais", que abre a gaveta. O item aparece
 * mesmo com a gaveta vazia — é ali que mora o atalho da personalização (FR-17),
 * decisão do usuário em 2026-10-07.
 *
 * Em retrato é um carrossel que recentra o modo ativo e rola; em janela larga, uma
 * coluna.
 * Extraído da `CameraScreen` na Tarefa 8 (NFR-3).
 */
@Composable
internal fun ModeSelector(
    modes: ArrangedModes,
    active: CameraModeDefinition,
    vertical: Boolean,
    scrimColor: Color,
    onModeSelect: (CameraModeDefinition) -> Unit,
    onOpenDrawer: () -> Unit
) {
    val carrossel = modes.carousel(active)
    val indiceAtivo = carrossel.indexOf(active).coerceAtLeast(0)
    val itens: @Composable AxisScope.() -> Unit = {
        carrossel.forEach { modo ->
            ItemDoSeletor(texto = modo.label, selecionado = modo === active) { onModeSelect(modo) }
        }
        ItemDoSeletor(texto = "Mais", selecionado = false, onClick = onOpenDrawer)
    }
    if (vertical) {
        // Em janela larga, uma coluna sem deslizamento. Quantos modos cabem no eixo
        // é a Tarefa 9 (FR-14).
        AxisContainer(
            vertical = true,
            modifier = Modifier.background(scrimColor, RoundedCornerShape(12.dp)),
            content = itens
        )
    } else {
        CarrosselCentrado(indiceAtivo = indiceAtivo, itens = itens)
    }
}

/**
 * Faixa horizontal que centraliza o item ativo e **rola**: com o ativo no centro, o
 * "Mais" fica dois itens à direita, e em tela de 360dp ele já sairia da vista. Rolar
 * mantém todo item alcançável, como nos apps de referência.
 *
 * O preenchimento lateral de meia tela menos meio item é o que deixa o primeiro e o
 * último itens chegarem ao centro. Na primeira exibição posiciona sem animar, como
 * o seletor de antes; nas trocas, anima em 300 ms.
 */
@Composable
private fun CarrosselCentrado(indiceAtivo: Int, itens: @Composable AxisScope.() -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val larguraItem = ITEM_WIDTH_DP.dp
        val margem = ((maxWidth - larguraItem) / 2).coerceAtLeast(0.dp)
        val rolagem = rememberScrollState()
        val alvoPx = with(LocalDensity.current) { (larguraItem * indiceAtivo).roundToPx() }
        var posicionado by remember { mutableStateOf(false) }
        LaunchedEffect(alvoPx) {
            if (posicionado) rolagem.animateScrollTo(alvoPx, tween(durationMillis = 300))
            else rolagem.scrollTo(alvoPx).also { posicionado = true }
        }
        AxisContainer(
            vertical = false,
            modifier = Modifier.horizontalScroll(rolagem).padding(horizontal = margem),
            content = itens
        )
    }
}

@Composable
private fun ItemDoSeletor(texto: String, selecionado: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.width(ITEM_WIDTH_DP.dp), contentAlignment = Alignment.Center) {
        Text(
            text = texto,
            color = if (selecionado) Color.White else Color.White.copy(alpha = INACTIVE_ALPHA),
            fontSize = if (selecionado) 15.sp else 14.sp,
            fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick
                )
                .padding(vertical = 8.dp)
        )
    }
}
