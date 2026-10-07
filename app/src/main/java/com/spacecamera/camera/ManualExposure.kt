package com.spacecamera.camera

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * O que vai para o Camera2 quando o ISO está manual: as duas grandezas que, juntas,
 * substituem o AE.
 */
data class ManualRequest(val sensitivity: Int, val exposureTimeNs: Long)

/**
 * Requirements: FR-9, AC-9.1
 * Decisions: ADR-007, Q-11
 *
 * A parte pura do ISO manual. Sem CameraX e sem tipos `android.*`: quem escreve as
 * chaves do `CaptureRequest` é o [ManualExposureControls], verificado em aparelho.
 */
object ManualExposure {

    /** 1/60 s — o tempo usado se ainda não houve leitura do AE para congelar. */
    private const val FALLBACK_EXPOSURE_NS = 16_666_667L

    /** O ISO pedido, limitado à faixa que o aparelho reportou (AC-9.1). */
    fun clampIso(requested: Int, range: IntRange): Int = requested.coerceIn(range)

    /**
     * O ISO numa posição do slider, de 0 (mínimo) a 1 (máximo). A escala é
     * logarítmica porque ISO é multiplicativo: cada stop (o dobro da luz) ocupa o
     * mesmo espaço, como nas câmeras.
     */
    fun isoAt(position: Float, range: IntRange): Int {
        val p = position.coerceIn(0f, 1f).toDouble()
        val iso = range.first * (range.last.toDouble() / range.first).pow(p)
        return clampIso(iso.roundToInt(), range)
    }

    /** A posição do slider para um ISO — o inverso de [isoAt]. */
    fun positionOf(iso: Int, range: IntRange): Float {
        if (range.last <= range.first) return 0f
        val p = ln(clampIso(iso, range).toDouble() / range.first) / ln(range.last.toDouble() / range.first)
        return p.toFloat()
    }

    /**
     * O pedido ao Camera2, ou `null` com o ISO automático — aí nada é pedido e o AE do
     * aparelho segue no comando.
     *
     * O Camera2 não tem "só ISO manual": desligar o AE exige fixar também o tempo de
     * exposição. O design (§4.3) manda manter o que o AE estava usando — congelado
     * no momento em que o ISO virou manual —, limitado à faixa do aparelho.
     */
    fun request(iso: Int?, frozenExposureNs: Long?, exposureRange: LongRange): ManualRequest? {
        iso ?: return null
        val exposicao = (frozenExposureNs ?: FALLBACK_EXPOSURE_NS).coerceIn(exposureRange)
        return ManualRequest(sensitivity = iso, exposureTimeNs = exposicao)
    }
}
