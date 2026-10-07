package com.spacecamera.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spacecamera.camera.mode.CameraModeDefinition

/** Largura do `Switch` do Material 3 — o espaço reservado nas linhas sem ele. */
private val SWITCH_WIDTH = 52.dp

/**
 * Requirements: FR-7, FR-16, FR-17, AC-7.1, AC-7.2, AC-16.1
 * Decisions: ADR-004, Q-12
 *
 * A seção "Modos da câmera" de Configurações: os modos que este aparelho oferece, na
 * ordem do usuário, com setas para reordenar e um interruptor para levar ao seletor
 * ou à gaveta. Vídeo e Foto não têm interruptor — a ação não é oferecida (AC-7.1).
 *
 * Reordenar é por setas, não por arrastar: o Compose 1.5 não tem arrastar em lista
 * pronto, e setas são acessíveis e testáveis (Q-12).
 *
 * @param modes os disponíveis, na ordem do usuário (plano e gaveta intercalados).
 * @param pinned os que estão no plano.
 */
@Composable
internal fun ModeSettingsSection(
    modes: List<CameraModeDefinition>,
    pinned: Set<CameraModeDefinition>,
    isRemovable: (CameraModeDefinition) -> Boolean,
    onMove: (CameraModeDefinition, Int) -> Unit,
    onPinnedChange: (CameraModeDefinition, Boolean) -> Unit,
    onRestoreDefault: () -> Unit
) {
    Column {
        modes.forEachIndexed { i, modo ->
            val noPlano = modo in pinned
            val removivel = isRemovable(modo)
            ListItem(
                headlineContent = { Text(modo.label) },
                supportingContent = {
                    Text(
                        when {
                            !removivel -> "Sempre no seletor"
                            noPlano -> "No seletor"
                            else -> "Em Mais modos"
                        }
                    )
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onMove(modo, -1) }, enabled = i > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir ${modo.label}")
                        }
                        IconButton(onClick = { onMove(modo, +1) }, enabled = i < modes.lastIndex) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Descer ${modo.label}")
                        }
                        if (removivel) {
                            Switch(
                                checked = noPlano,
                                onCheckedChange = { onPinnedChange(modo, it) },
                                modifier = Modifier.semantics { contentDescription = "${modo.label} no seletor" }
                            )
                        } else {
                            // O lugar do interruptor fica reservado, para as setas de todas
                            // as linhas formarem uma coluna só.
                            Spacer(Modifier.width(SWITCH_WIDTH))
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
        TextButton(onClick = onRestoreDefault, modifier = Modifier.padding(start = 8.dp)) {
            Text("Restaurar padrão")
        }
    }
}
