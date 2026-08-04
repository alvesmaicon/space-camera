package com.spacecamera.presentation.layout

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Requirements: FR-8, NFR-4
 *
 * Em tablet cada linha de configuração se esticava por 1.280dp e a leitura ficava
 * ruim. A margem lateral centraliza o conteúdo numa largura confortável — e é zero
 * em retrato, onde a tela já é estreita, então a tela de Configurações no telefone
 * continua idêntica.
 */
class ReadingWidthTest {

    @Test
    fun `telefone em retrato não recebe margem`() {
        assertEquals(0, readingGutterDp(widthDp = 411))
    }

    @Test
    fun `tablet em paisagem centraliza o conteúdo`() {
        // 1280 - 640 = 640, metade de cada lado
        assertEquals(320, readingGutterDp(widthDp = 1280))
    }

    @Test
    fun `largura exatamente igual ao máximo não recebe margem`() {
        assertEquals(0, readingGutterDp(widthDp = READING_WIDTH_MAX_DP))
    }

    @Test
    fun `largura um pouco acima do máximo recebe margem pequena`() {
        assertEquals(30, readingGutterDp(widthDp = READING_WIDTH_MAX_DP + 60))
    }

    /** Nunca negativa: janela mais estreita que o máximo não pode empurrar conteúdo. */
    @Test
    fun `janela mais estreita que o máximo nunca gera margem negativa`() {
        assertEquals(0, readingGutterDp(widthDp = 320))
        assertEquals(0, readingGutterDp(widthDp = 0))
    }
}
