package com.spacecamera.presentation.layout

import com.spacecamera.camera.mode.AspectRatioRule

/**
 * Requirements: FR-3, FR-4
 * Decisions: Q-09
 *
 * A caixa de pré-visualização: o rótulo de proporção (que também vai para o
 * `evt=bind`) e se ela ocupa a tela inteira.
 */
internal data class PreviewAspect(val label: String, val fillsScreen: Boolean)

/**
 * Antes era um `when` sobre `CameraMode` dentro da `CameraScreen`; agora depende só
 * da regra que o modo declara. Em janela larga a proporção retrato vira paisagem
 * (FR-3 do layout adaptativo) — o 3:4 da foto acompanha, porque uma caixa alta numa
 * janela larga desperdiça a tela.
 */
internal fun previewAspect(rule: AspectRatioRule, selectedRatio: String, isWide: Boolean): PreviewAspect {
    val segueEscolha = rule == AspectRatioRule.USER_SELECTED
    return when {
        segueEscolha && selectedRatio == "Full" -> PreviewAspect("Full", fillsScreen = true)
        !segueEscolha || selectedRatio == "9:16" -> PreviewAspect(if (isWide) "16:9" else "9:16", fillsScreen = false)
        else -> PreviewAspect(if (isWide) "4:3" else "3:4", fillsScreen = false)
    }
}
