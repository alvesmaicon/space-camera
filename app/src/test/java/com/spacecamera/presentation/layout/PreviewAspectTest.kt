package com.spacecamera.presentation.layout

import com.spacecamera.camera.mode.AspectRatioRule.FIXED_16_9
import com.spacecamera.camera.mode.AspectRatioRule.USER_SELECTED
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Requirements: FR-3, FR-4, NFR-1
 * Decisions: Q-09
 *
 * A proporção da caixa de pré-visualização, que antes era um `when` sobre
 * `CameraMode` dentro da `CameraScreen`. A tabela abaixo é a do código de antes,
 * caso a caso — é ela que garante o iso-comportamento da extração.
 */
class PreviewAspectTest {

    @Test
    fun `vídeo ignora a proporção da foto, inclusive Full`() {
        assertEquals(PreviewAspect("9:16", fillsScreen = false), previewAspect(FIXED_16_9, "Full", isWide = false))
        assertEquals(PreviewAspect("9:16", fillsScreen = false), previewAspect(FIXED_16_9, "3:4", isWide = false))
        assertEquals(PreviewAspect("16:9", fillsScreen = false), previewAspect(FIXED_16_9, "3:4", isWide = true))
    }

    @Test
    fun `foto em Full preenche a tela em qualquer janela`() {
        assertEquals(PreviewAspect("Full", fillsScreen = true), previewAspect(USER_SELECTED, "Full", isWide = false))
        assertEquals(PreviewAspect("Full", fillsScreen = true), previewAspect(USER_SELECTED, "Full", isWide = true))
    }

    @Test
    fun `foto em 9 por 16 e 3 por 4 viram paisagem em janela larga`() {
        assertEquals(PreviewAspect("9:16", fillsScreen = false), previewAspect(USER_SELECTED, "9:16", isWide = false))
        assertEquals(PreviewAspect("16:9", fillsScreen = false), previewAspect(USER_SELECTED, "9:16", isWide = true))
        assertEquals(PreviewAspect("3:4", fillsScreen = false), previewAspect(USER_SELECTED, "3:4", isWide = false))
        assertEquals(PreviewAspect("4:3", fillsScreen = false), previewAspect(USER_SELECTED, "3:4", isWide = true))
    }
}
