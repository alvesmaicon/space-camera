package com.spacecamera.presentation.layout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Requirements: FR-1, NFR-4
 *
 * O predicado é a fonte única de verdade do layout adaptativo (ADR-002). Por ser
 * função pura de dois inteiros, roda na JVM sem emulador — que é o que o NFR-4
 * cobra. O desempate do caso quadrado não é detalhe: garante que qualquer valor
 * inesperado leve ao layout de retrato, o único com cobertura de teste.
 */
class WindowAxisTest {

    /** AC-1.1 — telefone típico em retrato. */
    @Test
    fun `janela de telefone em retrato não é larga`() {
        assertFalse(isWideWindow(widthDp = 411, heightDp = 891))
    }

    /** AC-1.2 — tablet em paisagem. */
    @Test
    fun `janela de tablet em paisagem é larga`() {
        assertTrue(isWideWindow(widthDp = 1280, heightDp = 800))
    }

    /** AC-1.3 — o empate favorece retrato. */
    @Test
    fun `janela quadrada não é larga`() {
        assertFalse(isWideWindow(widthDp = 800, heightDp = 800))
    }

    @Test
    fun `um dp de diferença já classifica como larga`() {
        assertTrue(isWideWindow(widthDp = 801, heightDp = 800))
    }

    @Test
    fun `um dp abaixo do empate continua em retrato`() {
        assertFalse(isWideWindow(widthDp = 799, heightDp = 800))
    }

    /** Telefone girado com a janela livre — também é janela larga. */
    @Test
    fun `janela de telefone em paisagem é larga`() {
        assertTrue(isWideWindow(widthDp = 891, heightDp = 411))
    }

    /**
     * Configuração degenerada (dimensões zeradas ou ainda não medidas) tem de cair
     * em retrato: NFR-1 exige que qualquer falha na detecção preserve o layout atual.
     */
    @Test
    fun `janela com dimensões zeradas cai em retrato`() {
        assertFalse(isWideWindow(widthDp = 0, heightDp = 0))
    }
}
