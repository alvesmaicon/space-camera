package com.spacecamera.presentation.layout

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Requirements: FR-6, NFR-4
 * Decisions: Q-01
 *
 * Duas grandezas distintas saem do mesmo `rollDegrees`, e confundi-las é o defeito
 * medido em 2026-08-04:
 *
 * - **rotação de captura** — orientação física do aparelho, o que o CameraX precisa
 *   para gravar a mídia de pé. Só depende do acelerômetro.
 * - **compensação da UI** — quanto girar ícones e nível *dentro da janela*. Depende
 *   de quanto o compositor já girou: com a janela livre ele já deixou tudo de pé, e
 *   girar de novo produz ícone deitado.
 *
 * A invariante que amarra as duas: quando a janela acompanha o aparelho,
 * `Display.getRotation()` **é** a rotação física (medido 1:1 nos quatro estados), e
 * a compensação tem de ser zero.
 */
class DeviceRotationTest {

    // ── Rotação de captura — comportamento de hoje, agora testável na JVM ──────

    @Test
    fun `aparelho em pé grava sem rotação`() {
        assertEquals(Surface.ROTATION_0, captureRotation(0f))
    }

    @Test
    fun `aparelho girado no sentido anti-horário grava em ROTATION_90`() {
        assertEquals(Surface.ROTATION_90, captureRotation(90f))
    }

    @Test
    fun `aparelho girado no sentido horário grava em ROTATION_270`() {
        assertEquals(Surface.ROTATION_270, captureRotation(-90f))
    }

    @Test
    fun `aparelho de cabeça para baixo grava em ROTATION_180`() {
        assertEquals(Surface.ROTATION_180, captureRotation(180f))
    }

    /**
     * NFR-1: os limiares têm de continuar exatamente onde estavam. 45° cai em
     * retrato e 135° cai em 180° — a extração não pode ter deslocado a fronteira.
     */
    @Test
    fun `limiares de quadrante ficam onde estavam`() {
        assertEquals(Surface.ROTATION_0, captureRotation(45f))
        assertEquals(Surface.ROTATION_90, captureRotation(45.1f))
        assertEquals(Surface.ROTATION_180, captureRotation(135f))
        assertEquals(Surface.ROTATION_0, captureRotation(-45f))
        assertEquals(Surface.ROTATION_180, captureRotation(-135f))
    }

    /** A rotação da janela não entra na captura — é a conclusão da Q-01. */
    @Test
    fun `rotação de captura não depende da janela`() {
        assertEquals(Surface.ROTATION_90, captureRotation(90f))
    }

    // ── Compensação da UI ─────────────────────────────────────────────────────

    /**
     * As 16 combinações: 4 rotações físicas × 4 rotações de janela.
     * A diagonal é zero — janela livre acompanha o aparelho, nada a compensar.
     */
    @Test
    fun `compensação da UI nas dezesseis combinações`() {
        val rolls = listOf(0f, 90f, 180f, -90f)
        val janelas = listOf(
            Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270
        )
        val esperado = listOf(
            //        R0      R90     R180    R270
            listOf(   0f,    -90f,   180f,    90f),  // aparelho em pé
            listOf(  90f,      0f,   -90f,   180f),  // girado anti-horário
            listOf( 180f,     90f,     0f,   -90f),  // de cabeça para baixo
            listOf( -90f,    180f,    90f,     0f)   // girado horário
        )
        for ((i, roll) in rolls.withIndex()) {
            for ((j, janela) in janelas.withIndex()) {
                assertEquals(
                    "roll=$roll janela=$janela",
                    esperado[i][j],
                    uiRotation(roll, janela)
                )
            }
        }
    }

    /**
     * O defeito medido no tablet: janela em retrato (ROTATION_90) com o aparelho
     * girado 90°. O compositor já deixou a UI de pé; girar o ícone de novo o deita.
     */
    @Test
    fun `janela que acompanha o aparelho não gira o ícone de novo`() {
        assertEquals(0f, uiRotation(90f, Surface.ROTATION_90))
        assertEquals(0f, uiRotation(-90f, Surface.ROTATION_270))
        assertEquals(0f, uiRotation(180f, Surface.ROTATION_180))
    }

    /**
     * NFR-1 / AC-6.3: telefone travado em retrato tem `Display.getRotation()` fixo
     * em ROTATION_0, e o comportamento de hoje precisa sair intacto.
     */
    @Test
    fun `janela travada em retrato preserva o comportamento atual`() {
        assertEquals(0f, uiRotation(0f, Surface.ROTATION_0))
        assertEquals(90f, uiRotation(90f, Surface.ROTATION_0))
        assertEquals(-90f, uiRotation(-90f, Surface.ROTATION_0))
        assertEquals(180f, uiRotation(180f, Surface.ROTATION_0))
    }

    /** Rotação de janela desconhecida cai em "não compensar" — NFR-1. */
    @Test
    fun `rotação de janela inválida não compensa`() {
        assertEquals(90f, uiRotation(90f, 99))
    }

    // ── Inclinação relativa à janela — para o nível de horizonte ───────────────

    /**
     * O nível desenha uma linha contínua, não travada em quadrantes: precisa da
     * inclinação residual dentro da janela, senão fica 90° fora numa janela girada.
     */
    @Test
    fun `inclinação relativa desconta o que a janela já girou`() {
        assertEquals(10f, windowRelativeRoll(100f, Surface.ROTATION_90), 0.01f)
        assertEquals(-5f, windowRelativeRoll(85f, Surface.ROTATION_90), 0.01f)
        assertEquals(3f, windowRelativeRoll(3f, Surface.ROTATION_0), 0.01f)
    }

    /** O resultado fica em (-180, 180] para a animação pegar o caminho curto. */
    @Test
    fun `inclinação relativa é normalizada`() {
        assertEquals(-90f, windowRelativeRoll(180f, Surface.ROTATION_270), 0.01f)
        assertEquals(90f, windowRelativeRoll(-90f, Surface.ROTATION_180), 0.01f)
        assertEquals(180f, windowRelativeRoll(0f, Surface.ROTATION_180), 0.01f)
    }
}
