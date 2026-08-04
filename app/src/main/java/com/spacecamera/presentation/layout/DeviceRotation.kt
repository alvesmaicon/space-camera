package com.spacecamera.presentation.layout

import android.view.Surface
import kotlin.math.abs

/**
 * Requirements: FR-6
 * Decisions: Q-01
 *
 * Duas grandezas saem do mesmo `rollDegrees` do acelerômetro, e confundi-las é o
 * defeito medido em 2026-08-04 (Q-01 em decisions.md):
 *
 * - [captureRotation] — orientação **física** do aparelho. É o que o CameraX precisa
 *   para gravar a mídia de pé, e não depende da janela: com a janela livre,
 *   `Display.getRotation()` **é** a rotação física (medido 1:1 nos quatro estados),
 *   então compor as duas dobraria a rotação e torceria o arquivo.
 * - [uiRotation] e [windowRelativeRoll] — quanto girar ícones e nível **dentro da
 *   janela**. Aí a rotação da janela importa: o compositor já girou o conteúdo, e
 *   girar de novo deita o ícone.
 *
 * Ambas ficam aqui, e não em `camera/`, porque compartilham a normalização e só
 * fazem sentido lado a lado — a distinção entre elas é a razão de o arquivo existir.
 *
 * Funções puras, para rodar na JVM sem emulador (NFR-4).
 */

private const val FULL_TURN = 360f
private const val HALF_TURN = 180f
private const val QUARTER_TURN = 90f

/** Meio caminho entre dois quadrantes: onde o ângulo travado vira o próximo. */
private const val QUADRANT_EDGE = 45f
private const val UPSIDE_DOWN_EDGE = HALF_TURN - QUADRANT_EDGE

/** Traz o ângulo para (-180, 180], para a animação pegar o caminho curto. */
private fun normalizeDegrees(degrees: Float): Float {
    var d = degrees % FULL_TURN
    if (d > HALF_TURN) d -= FULL_TURN
    if (d <= -HALF_TURN) d += FULL_TURN
    return d
}

/**
 * Trava o ângulo no quadrante mais próximo: 0, ±90 ou 180.
 *
 * Os limiares são os mesmos de antes desta extração — o teste de fronteira existe
 * para garantir que não se deslocaram (NFR-1).
 */
private fun snapToQuadrant(degrees: Float): Float {
    val d = normalizeDegrees(degrees)
    return when {
        d > QUADRANT_EDGE && d < UPSIDE_DOWN_EDGE -> QUARTER_TURN
        d < -QUADRANT_EDGE && d > -UPSIDE_DOWN_EDGE -> -QUARTER_TURN
        abs(d) >= UPSIDE_DOWN_EDGE -> HALF_TURN
        else -> 0f
    }
}

/** Quanto o compositor já girou o conteúdo da janela, na convenção de `rollDegrees`. */
private fun windowRotationDegrees(displayRotation: Int): Float = when (displayRotation) {
    Surface.ROTATION_90 -> QUARTER_TURN
    Surface.ROTATION_180 -> HALF_TURN
    Surface.ROTATION_270 -> -QUARTER_TURN
    // Inclui ROTATION_0 e qualquer valor inesperado: não compensar preserva o
    // comportamento de hoje, que é o caminho default exigido pelo NFR-1.
    else -> 0f
}

/**
 * Rotação alvo do CameraX para a mídia gravada, como constante `Surface.ROTATION_*`.
 *
 * Depende **só** do acelerômetro. Comportamento idêntico ao anterior a esta
 * extração; o ganho é ser testável na JVM.
 */
internal fun captureRotation(rollDegrees: Float): Int = when (snapToQuadrant(rollDegrees)) {
    QUARTER_TURN -> Surface.ROTATION_90
    -QUARTER_TURN -> Surface.ROTATION_270
    HALF_TURN -> Surface.ROTATION_180
    else -> Surface.ROTATION_0
}

/**
 * Inclinação do aparelho **relativa à janela**, contínua e normalizada.
 *
 * Para o nível de horizonte, que desenha um ângulo livre. Numa janela girada, usar
 * `rollDegrees` cru deixaria a linha 90° fora.
 */
internal fun windowRelativeRoll(rollDegrees: Float, displayRotation: Int): Float =
    normalizeDegrees(rollDegrees - windowRotationDegrees(displayRotation))

/**
 * Quanto girar um ícone para ele aparecer de pé dentro da janela.
 *
 * Com a janela travada em retrato (telefone, `ROTATION_0`) devolve exatamente o que
 * o código devolvia antes. Com a janela acompanhando o aparelho devolve zero — o
 * compositor já resolveu.
 */
internal fun uiRotation(rollDegrees: Float, displayRotation: Int): Float =
    snapToQuadrant(windowRelativeRoll(rollDegrees, displayRotation))
