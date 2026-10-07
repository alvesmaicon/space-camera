package com.spacecamera.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spacecamera.camera.mode.CameraModeDefinition

/** Fundo e opacidade do painel — os mesmos da linha "mais opções" da barra superior. */
private const val PANEL_RGB = 0xFF1C1C1E
private const val PANEL_ALPHA = 0.97f
private val PanelColor = Color(PANEL_RGB).copy(alpha = PANEL_ALPHA)

/**
 * Requirements: FR-6, FR-17, AC-6.1
 * Decisions: ADR-004
 *
 * O painel "Mais modos": os modos que não estão no plano, e o "Editar" que leva à
 * personalização (FR-17 — sem esse atalho, ninguém descobriria a função em
 * Configurações). Vazio, explica por quê e mantém o "Editar".
 *
 * Fechar ao tocar fora e no "voltar" é responsabilidade de quem o mostra.
 */
@Composable
internal fun ModeDrawer(
    modes: List<CameraModeDefinition>,
    active: CameraModeDefinition,
    onModeSelect: (CameraModeDefinition) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(PanelColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mais modos", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Editar", color = Color.White, fontSize = 14.sp)
            }
        }
        if (modes.isEmpty()) {
            Text(
                "Todos os modos já estão no seletor.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modes.forEach { modo -> BlocoDeModo(modo, ativo = modo === active) { onModeSelect(modo) } }
            }
        }
    }
}

@Composable
private fun BlocoDeModo(modo: CameraModeDefinition, ativo: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 88.dp, height = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (ativo) 0.22f else 0.10f))
            .then(if (ativo) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
    ) {
        Text(
            modo.label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = if (ativo) FontWeight.Bold else FontWeight.Normal
        )
    }
}
