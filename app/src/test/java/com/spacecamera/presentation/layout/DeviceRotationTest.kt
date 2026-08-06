package com.spacecamera.presentation.layout

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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

    // ── Suavização e zona morta (Q-04) ──────────────────────────────────────

    /**
     * Requirements: NFR-1
     *
     * A razão de suavizar o **vetor** e não o ângulo. Duas leituras vizinhas em torno
     * da descontinuidade (+179° e −179° são 2° de diferença física) têm média de
     * ângulo perto de **zero** — o valor daria meia-volta em vez de convergir.
     * Passando pelo vetor, o resultado fica onde deveria: perto de ±180°.
     */
    @Test
    fun `suavizar o vetor não gira na virada de 180 graus`() {
        // y negativo = de cabeça para baixo; x troca de sinal em torno do eixo
        val xa = 0.10f; val ya = -9.81f     // ≈ +179,4°
        val xb = -0.10f; val yb = -9.81f    // ≈ -179,4°
        assertTrue(abs(rollFromGravity(xa, ya)) > 179f)
        assertTrue(abs(rollFromGravity(xb, yb)) > 179f)

        val mediaDeAngulo = (rollFromGravity(xa, ya) + rollFromGravity(xb, yb)) / 2f
        assertTrue(
            "média de ângulo colapsa para ~0 e é justamente o artefato a evitar",
            abs(mediaDeAngulo) < 1f
        )

        val suavizadoX = smoothGravity(xa, xb, alpha = 0.5f)
        val suavizadoY = smoothGravity(ya, yb, alpha = 0.5f)
        assertTrue(
            "pelo vetor, o ângulo continua perto de ±180°",
            abs(rollFromGravity(suavizadoX, suavizadoY)) > 179f
        )
    }

    /** A suavização caminha na direção da amostra sem ultrapassá-la. */
    @Test
    fun `suavização se aproxima da amostra de forma monotônica`() {
        var v = 0f
        repeat(40) { v = smoothGravity(v, 10f) }
        assertTrue("converge para a amostra, valor=$v", v > 9.5f && v <= 10f)
        assertEquals(5f, smoothGravity(0f, 10f, alpha = 0.5f), 0.001f)
    }

    /**
     * Requirements: NFR-1
     *
     * O que economiza CPU: ruído abaixo do limiar não vira escrita de estado, logo não
     * vira recomposição. Com o aparelho na mesa é este caso que vale.
     */
    @Test
    fun `variação abaixo do limiar não é publicada`() {
        val quaseParado = ROLL_MIN_DELTA * 0.9f
        assertFalse(shouldPublishRoll(published = 10f, candidate = 10f + quaseParado))
        assertFalse(shouldPublishRoll(published = 10f, candidate = 10f - quaseParado))
        assertFalse(shouldPublishRoll(published = 0f, candidate = 0f))
    }

    /** Movimento real passa, senão o nível deixaria de funcionar. */
    @Test
    fun `variação acima do limiar é publicada`() {
        assertTrue(shouldPublishRoll(published = 10f, candidate = 10f + ROLL_MIN_DELTA))
        assertTrue(shouldPublishRoll(published = 0f, candidate = 45f))
        assertTrue(shouldPublishRoll(published = -90f, candidate = 90f))
    }

    /**
     * Requirements: NFR-1
     *
     * A invariante que protege o indicador de si mesmo. A zona morta é ajustável por
     * sensação — subiu de 0,25° para 0,8° depois de observar que vibração de teclado
     * sobre a mesa ainda passava. Se um dia alguém a subir até perto de ±2°, a zona
     * morta passaria a **esconder** desnivelamento real, que é o oposto da função do
     * indicador. Este teste é o freio.
     */
    @Test
    fun `zona morta fica bem abaixo da tolerância de nivelado`() {
        assertTrue(
            "ROLL_MIN_DELTA=$ROLL_MIN_DELTA precisa ser < metade de $LEVEL_TOLERANCE",
            ROLL_MIN_DELTA < LEVEL_TOLERANCE / 2f
        )
        assertTrue(
            "LEVEL_MIN_DELTA=$LEVEL_MIN_DELTA precisa ser < metade de $LEVEL_TOLERANCE",
            LEVEL_MIN_DELTA < LEVEL_TOLERANCE / 2f
        )
    }

    /**
     * Requirements: FR-6
     *
     * A invariante que protege o arquivo gravado. O caminho de exibição pode ser
     * filtrado à vontade, mas o **físico** alimenta `captureRotation`: se alguém
     * igualar os dois "para a linha ficar mais lisa", girar o aparelho e apertar gravar
     * passa a produzir mídia com a orientação antiga.
     */
    @Test
    fun `filtro de exibição é mais forte que o físico, nunca o contrário`() {
        assertTrue(
            "LEVEL_SMOOTHING=$LEVEL_SMOOTHING deve ser < GRAVITY_SMOOTHING=$GRAVITY_SMOOTHING",
            LEVEL_SMOOTHING < GRAVITY_SMOOTHING
        )
    }

    /**
     * Quantifica o atraso que o filtro físico impõe à orientação da mídia: quantas
     * amostras para cruzar o limiar de 45° a partir do repouso, num giro de 90°.
     *
     * A ~17 amostras/s, 0,15 dá ~4,3 amostras (~250ms). O teste falha se alguém baixar
     * o filtro ao ponto de o atraso dobrar, que é onde "girei e gravei" começa a sair
     * com a orientação errada.
     */
    @Test
    fun `atraso do filtro físico até cruzar o limiar de quadrante é aceitável`() {
        var angulo = 0f
        var amostras = 0
        while (angulo < 45f && amostras < 100) {
            angulo = smoothGravity(angulo, 90f, GRAVITY_SMOOTHING)
            amostras++
        }
        assertTrue("cruzou 45° em $amostras amostras, esperado <= 6", amostras <= 6)
    }

    /**
     * O limiar mede o arco curto: 179° e −179° distam 2°, não 358°. Sem isso, girar
     * o aparelho de cabeça para baixo publicaria por um motivo errado — e pior, a
     * comparação ficaria dependente do sinal.
     */
    @Test
    fun `limiar usa o arco curto na virada de 180 graus`() {
        assertEquals(2f, rollDelta(179f, -179f), 0.01f)
        assertEquals(0f, rollDelta(180f, -180f), 0.01f)
        assertFalse(
            "0,15° de diferença física não deve publicar só por trocar de sinal",
            shouldPublishRoll(published = 179.9f, candidate = -179.95f)
        )
    }
}
