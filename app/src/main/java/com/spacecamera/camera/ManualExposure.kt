package com.spacecamera.camera

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * O que vai para o Camera2 quando o ISO está manual: as duas grandezas que, juntas,
 * substituem o AE.
 */
data class ManualRequest(val sensitivity: Int, val exposureTimeNs: Long)

/** O que o AE estava usando quando a exposição virou manual — o que se congela. */
data class AutoExposure(val exposureNs: Long?, val iso: Int?)

/**
 * Requirements: FR-9, AC-9.1
 * Decisions: ADR-007, Q-11
 *
 * A parte pura do ISO manual. Sem CameraX e sem tipos `android.*`: quem escreve as
 * chaves do `CaptureRequest` é o [ManualExposureControls], verificado em aparelho.
 */
object ManualExposure {

    /** 1/60 s e ISO 400 — usados se ainda não houve leitura do AE para congelar. */
    private const val FALLBACK_EXPOSURE_NS = 16_666_667L
    private const val FALLBACK_ISO = 400

    /** Teto prático do obturador manual (Q-13): 1/4 s. */
    private const val MAX_USER_EXPOSURE_NS = 250_000_000L

    /** Abaixo disso o tempo se escreve como fração (1/4); a partir dele, em segundos. */
    private const val FRACTION_LIMIT_S = 0.4

    /** A partir de 10 s, ou quando já é quase inteiro, o tempo sai sem casa decimal. */
    private const val WHOLE_SECONDS_FROM_S = 10.0
    private const val WHOLE_TOLERANCE_S = 0.05
    private const val NANOS = 1e9

    /** O ISO pedido, limitado à faixa que o aparelho reportou (AC-9.1). */
    fun clampIso(requested: Int, range: IntRange): Int = requested.coerceIn(range)

    /**
     * O ISO numa posição do slider, de 0 (mínimo) a 1 (máximo). A escala é
     * logarítmica porque ISO é multiplicativo: cada stop (o dobro da luz) ocupa o
     * mesmo espaço, como nas câmeras.
     */
    fun isoAt(position: Float, range: IntRange): Int =
        clampIso(logAt(position, range.first.toDouble(), range.last.toDouble()).roundToInt(), range)

    /** O valor numa posição de uma escala logarítmica entre [min] e [max]. */
    private fun logAt(position: Float, min: Double, max: Double): Double =
        min * (max / min).pow(position.coerceIn(0f, 1f).toDouble())

    /** A posição do slider para um ISO — o inverso de [isoAt]. */
    fun positionOf(iso: Int, range: IntRange): Float {
        if (range.last <= range.first) return 0f
        val p = ln(clampIso(iso, range).toDouble() / range.first) / ln(range.last.toDouble() / range.first)
        return p.toFloat()
    }

    /**
     * O pedido ao Camera2, ou `null` com ISO **e** obturador automáticos — aí nada é
     * pedido e o AE do aparelho volta ao comando (FR-11, AC-11.1).
     *
     * O Camera2 não tem "só ISO manual" nem "só obturador manual": desligar o AE exige
     * fixar os dois. O que estiver em automático fica com o valor que o AE usava —
     * congelado no momento em que a exposição virou manual (design §4.3) —, e os dois
     * são limitados às faixas do aparelho (AC-9.1, AC-10.1).
     */
    fun request(
        iso: Int?,
        exposureNs: Long?,
        frozen: AutoExposure,
        isoRange: IntRange,
        exposureRange: LongRange
    ): ManualRequest? {
        if (iso == null && exposureNs == null) return null
        return ManualRequest(
            sensitivity = clampIso(iso ?: frozen.iso ?: FALLBACK_ISO, isoRange),
            exposureTimeNs = (exposureNs ?: frozen.exposureNs ?: FALLBACK_EXPOSURE_NS).coerceIn(exposureRange)
        )
    }

    /**
     * A faixa de tempo que o **usuário** pode escolher: a do aparelho, com teto de 1/4 s
     * (Q-13). Medido no Redmi Note 10: 1/4 s captura em ~4 s, 1 s em ~23 s, 4 s em ~34 s,
     * e 28,8 s falha (`ERROR_CAPTURE_FAILED`) depois de ~4 min. O tempo congelado do AE
     * não passa por aqui — o AE já escolhe tempos que capturam.
     */
    fun userShutterRange(reported: LongRange): LongRange =
        reported.first..minOf(reported.last, MAX_USER_EXPOSURE_NS)

    /** O tempo de exposição numa posição do slider — logarítmico, como o ISO. */
    fun exposureAt(position: Float, range: LongRange): Long =
        logAt(position, range.first.toDouble(), range.last.toDouble()).roundToLong().coerceIn(range)

    /** A posição do slider para um tempo — o inverso de [exposureAt]. */
    fun positionOfExposure(ns: Long, range: LongRange): Float {
        if (range.last <= range.first) return 0f
        return (ln(ns.coerceIn(range).toDouble() / range.first) / ln(range.last.toDouble() / range.first)).toFloat()
    }

    /**
     * O tempo como as câmeras escrevem: fração abaixo de ~1/3 s (`1/250`), segundos
     * acima (`0,5″` em português, `0.5″` em inglês, `2″`). O separador decimal segue o
     * idioma do aparelho.
     */
    fun shutterLabel(ns: Long, locale: Locale = Locale.getDefault()): String {
        val s = ns / NANOS
        return when {
            s < FRACTION_LIMIT_S -> "1/${(1 / s).roundToInt()}"
            s >= WHOLE_SECONDS_FROM_S || abs(s - s.roundToInt()) < WHOLE_TOLERANCE_S -> "${s.roundToInt()}\""
            else -> "%.1f\"".format(locale, s)
        }
    }
}
