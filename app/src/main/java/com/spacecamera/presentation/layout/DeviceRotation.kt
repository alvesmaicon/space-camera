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
 * Peso da amostra nova na suavização do vetor de gravidade. Ver [smoothGravity].
 *
 * Alimenta a inclinação **física**: [captureRotation], [uiRotation] e o verde de
 * nivelado. Não pode ser muito baixo porque atrasa a orientação do arquivo gravado —
 * medido: para cruzar o limiar de 45° a partir do repouso são ~4,3 amostras (~250ms) a
 * 0,15, contra ~8,3 (~490ms) a 0,08. Girar o aparelho e apertar gravar dentro desse
 * intervalo sairia com a orientação antiga. FR-6 é Critical.
 */
internal const val GRAVITY_SMOOTHING = 0.15f

/**
 * Peso da amostra nova na suavização usada **só para desenhar** a linha do nível.
 *
 * Bem mais forte que [GRAVITY_SMOOTHING] porque aqui atraso não custa nada: uma linha
 * de horizonte que converge em meio segundo é melhor que uma que persegue vibração. É a
 * mesma separação da Q-01 — grandeza física e grandeza de exibição saem do mesmo sensor
 * mas têm exigências opostas.
 *
 * Motivo de existir, em vez de simplesmente aumentar a zona morta: zona morta
 * **converte** tremor contínuo em saltos, porque só publica nos picos. Menos frequente,
 * porém cada salto maior. Vibração de teclado sobre a mesa é alta frequência, e o
 * instrumento para isso é filtro.
 */
internal const val LEVEL_SMOOTHING = 0.05f

/**
 * Zona morta da linha do nível, menor que [ROLL_MIN_DELTA].
 *
 * Pode ser menor porque [LEVEL_SMOOTHING] já derrubou a amplitude do ruído antes: o
 * papel que sobra é só o de parar de publicar quando o valor convergiu.
 */
internal const val LEVEL_MIN_DELTA = 0.25f

/**
 * Menor variação de inclinação que vale publicar, em graus. Ver [shouldPublishRoll].
 *
 * Começou em 0,25°, calculado para ser imperceptível (sobre a meia-linha de ~110px do
 * nível, desloca a ponta menos de meio pixel). **Insuficiente na prática:** com o
 * telefone na mesa e alguém digitando no teclado ao lado, a vibração ainda passava e a
 * linha tremia. 0,8° é o valor ajustado a partir dessa observação.
 *
 * Fica deliberadamente abaixo do limiar de ±2° que acende o verde de "nivelado"
 * ([LEVEL_TOLERANCE]) — senão a zona morta poderia esconder um desnivelamento real,
 * que é justamente o que o indicador existe para mostrar.
 */
internal const val ROLL_MIN_DELTA = 0.8f

/**
 * Tolerância em graus para considerar o enquadramento nivelado.
 *
 * Existe como constante para amarrar a invariante do teste: [ROLL_MIN_DELTA] tem de
 * ser bem menor que isto.
 */
internal const val LEVEL_TOLERANCE = 2f

/**
 * Suavização exponencial de **uma componente** do vetor de gravidade.
 *
 * Suaviza-se o vetor e só depois se tira o ângulo, e não o contrário: média de ângulo
 * atravessa a descontinuidade de ±180° pelo lado errado, fazendo o valor girar em vez
 * de convergir. Suavizar x e y é livre desse artefato porque são grandezas contínuas.
 */
internal fun smoothGravity(previous: Float, sample: Float, alpha: Float = GRAVITY_SMOOTHING): Float =
    previous * (1f - alpha) + sample * alpha

/** Inclinação em graus a partir do vetor de gravidade já suavizado. */
internal fun rollFromGravity(x: Float, y: Float): Float =
    Math.toDegrees(kotlin.math.atan2(x.toDouble(), y.toDouble())).toFloat()

/** Diferença entre dois ângulos pelo arco curto, sempre positiva. */
internal fun rollDelta(a: Float, b: Float): Float = abs(normalizeDegrees(a - b))

/**
 * Requirements: NFR-1
 *
 * Se vale publicar [candidate] como nova inclinação, dado o último valor publicado.
 *
 * Serve para **estabilidade visual** e, em segundo lugar, para CPU: o acelerômetro
 * entrega ~17 amostras/s e nunca repete valor, nem com o aparelho imóvel na mesa,
 * então sem a zona morta o nível de horizonte tremia continuamente e cada amostra
 * invalidava o composable de ~1.100 linhas da câmera.
 *
 * Medido em aparelho com A/B alternado (duas rodadas, mediana de 6 amostras cada):
 * 117% e 122% de CPU sem a zona morta, contra 96,5% e 92,8% com ela. Os valores
 * absolutos variam muito com a cena, então o que sustenta a conclusão é a separação
 * consistente entre as rodadas, não o número.
 *
 * **Não** é a causa dominante do consumo: sobram ~95%, que estão no caminho da
 * pré-visualização (`TextureView`, por `ImplementationMode.COMPATIBLE`) e são
 * indiferentes ao sensor. Ver Q-04.
 *
 * Aumentar [minDelta] é preferível a fortalecer [smoothGravity] quando o objetivo é
 * rejeitar vibração: a zona morta não adiciona atraso nenhum a uma rotação real, que
 * a excede de imediato, enquanto o filtro atrasa **tudo** — inclusive
 * [captureRotation], que decide a orientação do arquivo gravado.
 */
internal fun shouldPublishRoll(
    published: Float,
    candidate: Float,
    minDelta: Float = ROLL_MIN_DELTA
): Boolean = rollDelta(published, candidate) >= minDelta

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
