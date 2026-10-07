package com.spacecamera.camera

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Requirements: FR-9, AC-9.1
 * Decisions: ADR-007, Q-11
 *
 * A parte pura do ISO manual: limitar à faixa do aparelho, a escala logarítmica do
 * slider, e o pedido que vai para o Camera2. Sem CameraX — as chaves do
 * `CaptureRequest` são escritas por um adaptador à parte, verificado em aparelho.
 */
class ManualExposureTest {

    private val faixa = 50..3200
    private val exposicao = 100_000L..400_000_000L

    // ── AC-9.1: limitar à faixa reportada ───────────────────────────────────

    @Test
    fun `ISO acima da faixa vira o máximo`() {
        assertEquals(3200, ManualExposure.clampIso(6400, faixa))
    }

    @Test
    fun `ISO abaixo da faixa vira o mínimo`() {
        assertEquals(50, ManualExposure.clampIso(10, faixa))
    }

    @Test
    fun `ISO dentro da faixa passa intacto`() {
        assertEquals(800, ManualExposure.clampIso(800, faixa))
    }

    // ── Escala do slider ────────────────────────────────────────────────────

    @Test
    fun `os extremos do slider são os extremos da faixa`() {
        assertEquals(50, ManualExposure.isoAt(0f, faixa))
        assertEquals(3200, ManualExposure.isoAt(1f, faixa))
    }

    @Test
    fun `a escala é logarítmica - cada stop ocupa o mesmo espaço`() {
        // 50 → 3200 são 6 stops; o meio da régua é 400 (3 stops acima de 50).
        assertEquals(400, ManualExposure.isoAt(0.5f, faixa))
    }

    @Test
    fun `posição e ISO são inversos`() {
        listOf(50, 100, 400, 1600, 3200).forEach { iso ->
            assertEquals(iso, ManualExposure.isoAt(ManualExposure.positionOf(iso, faixa), faixa))
        }
    }

    // ── Pedido ao Camera2 ───────────────────────────────────────────────────

    /** Atalho: o pedido com o AE congelado em [congeladoNs]/[congeladoIso]. */
    private fun pedido(
        iso: Int?,
        exposicaoNs: Long? = null,
        congeladoNs: Long? = null,
        congeladoIso: Int? = null,
        faixaExposicao: LongRange = exposicao
    ) = ManualExposure.request(
        iso = iso,
        exposureNs = exposicaoNs,
        frozen = AutoExposure(exposureNs = congeladoNs, iso = congeladoIso),
        isoRange = faixa,
        exposureRange = faixaExposicao
    )

    @Test
    fun `os dois em automático não pedem nada - o AE volta ao comando`() {
        // AC-11.1: é a ausência de pedido que devolve o AE.
        assertNull(pedido(iso = null, exposicaoNs = null, congeladoNs = 16_000_000L, congeladoIso = 400))
    }

    @Test
    fun `ISO manual congela o tempo de exposição automático`() {
        // Camera2 não tem "só ISO manual": desligar o AE exige fixar os dois.
        val p = pedido(iso = 800, congeladoNs = 8_000_000L)!!

        assertEquals(800, p.sensitivity)
        assertEquals(8_000_000L, p.exposureTimeNs)
    }

    @Test
    fun `obturador manual congela o ISO automático`() {
        // FR-10, o simétrico do anterior.
        val p = pedido(iso = null, exposicaoNs = 250_000_000L, congeladoIso = 640)!!

        assertEquals(640, p.sensitivity)
        assertEquals(250_000_000L, p.exposureTimeNs)
    }

    @Test
    fun `ISO congelado acima da faixa declarada é limitado`() {
        // O AE deste aparelho passa da faixa que o sensor declara (ISO 6400 > 3200).
        assertEquals(3200, pedido(iso = null, exposicaoNs = 1_000_000L, congeladoIso = 6400)!!.sensitivity)
    }

    @Test
    fun `obturador acima da faixa vira o máximo reportado`() {
        // AC-10.1: faixa de 1/8000 s a 1/4 s, pede 1 s, aplica 1/4 s.
        val faixaCurta = 125_000L..250_000_000L
        assertEquals(250_000_000L, pedido(iso = 400, exposicaoNs = 1_000_000_000L, faixaExposicao = faixaCurta)!!.exposureTimeNs)
    }

    @Test
    fun `sem leitura do AE usa 1 sobre 60 e ISO 400 dentro da faixa`() {
        assertEquals(16_666_667L, pedido(iso = 800)!!.exposureTimeNs)
        assertEquals(400, pedido(iso = null, exposicaoNs = 1_000_000L)!!.sensitivity)
    }

    @Test
    fun `tempo congelado fora da faixa é limitado`() {
        val curta = 1_000L..5_000_000L
        assertEquals(5_000_000L, pedido(iso = 800, congeladoNs = 30_000_000L, faixaExposicao = curta)!!.exposureTimeNs)
    }

    // ── Teto prático do obturador (Q-13) ────────────────────────────────────

    @Test
    fun `escala do obturador vai até 1 sobre 4 mesmo que o sensor aceite mais`() {
        // Medido no Redmi: 1 s levou 23 s para capturar; 28,8 s falhou depois de ~4 min.
        assertEquals(65_424L..250_000_000L, ManualExposure.userShutterRange(65_424L..30_071_705_440L))
    }

    @Test
    fun `sensor com máximo menor que o teto mantém o próprio máximo`() {
        assertEquals(100_000L..100_000_000L, ManualExposure.userShutterRange(100_000L..100_000_000L))
    }

    // ── Escala e rótulo do obturador ────────────────────────────────────────

    @Test
    fun `escala do obturador também é logarítmica`() {
        val faixaObt = 1_000_000L..1_000_000_000L  // 1/1000 s a 1 s
        assertEquals(1_000_000L, ManualExposure.exposureAt(0f, faixaObt))
        assertEquals(1_000_000_000L, ManualExposure.exposureAt(1f, faixaObt))
        assertEquals(31_622_777L, ManualExposure.exposureAt(0.5f, faixaObt))  // média geométrica
        assertEquals(0.5f, ManualExposure.positionOfExposure(31_622_777L, faixaObt), 0.001f)
    }

    @Test
    fun `rótulo do obturador como nas câmeras`() {
        val pt = Locale("pt", "BR")
        assertEquals("1/8000", ManualExposure.shutterLabel(125_000L, pt))
        assertEquals("1/250", ManualExposure.shutterLabel(4_000_000L, pt))
        assertEquals("1/30", ManualExposure.shutterLabel(33_333_333L, pt))
        assertEquals("1/4", ManualExposure.shutterLabel(250_000_000L, pt))
        assertEquals("0,5\"", ManualExposure.shutterLabel(500_000_000L, pt))
        assertEquals("2\"", ManualExposure.shutterLabel(2_000_000_000L, pt))
        assertEquals("30\"", ManualExposure.shutterLabel(30_071_705_440L, pt))
    }

    @Test
    fun `separador decimal do obturador segue o idioma`() {
        assertEquals("0.5\"", ManualExposure.shutterLabel(500_000_000L, Locale.ENGLISH))
    }

    @Test
    fun `campos de telemetria só têm números`() {
        // NFR-7.
        val campos = TelemetryFields.manualFields(pedido(iso = 800, congeladoNs = 8_000_000L))
        assertEquals("iso=800 exposure_ns=8000000", campos)
        assertEquals("iso=auto", TelemetryFields.manualFields(null))
        assertTrue(Regex("^[a-z_]+=[a-z0-9]+( [a-z_]+=[a-z0-9]+)*$").matches(campos))
    }
}
