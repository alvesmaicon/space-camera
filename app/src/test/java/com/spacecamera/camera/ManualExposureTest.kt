package com.spacecamera.camera

import org.junit.Test
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

    @Test
    fun `ISO automático não pede nada - o AE do aparelho segue no comando`() {
        assertNull(ManualExposure.request(iso = null, frozenExposureNs = 16_000_000L, exposureRange = exposicao))
    }

    @Test
    fun `ISO manual congela o tempo de exposição automático`() {
        // Camera2 não tem "só ISO manual": desligar o AE exige fixar os dois.
        val pedido = ManualExposure.request(iso = 800, frozenExposureNs = 8_000_000L, exposureRange = exposicao)!!

        assertEquals(800, pedido.sensitivity)
        assertEquals(8_000_000L, pedido.exposureTimeNs)
    }

    @Test
    fun `sem leitura do AE usa 1 sobre 60 dentro da faixa`() {
        val pedido = ManualExposure.request(iso = 800, frozenExposureNs = null, exposureRange = exposicao)!!

        assertEquals(16_666_667L, pedido.exposureTimeNs)
    }

    @Test
    fun `tempo congelado fora da faixa é limitado`() {
        val curta = 1_000L..5_000_000L
        val pedido = ManualExposure.request(iso = 800, frozenExposureNs = 30_000_000L, exposureRange = curta)!!

        assertEquals(5_000_000L, pedido.exposureTimeNs)
    }

    @Test
    fun `campos de telemetria só têm números`() {
        // NFR-7.
        val campos = TelemetryFields.manualFields(ManualExposure.request(800, 8_000_000L, exposicao))
        assertEquals("iso=800 exposure_ns=8000000", campos)
        assertEquals("iso=auto", TelemetryFields.manualFields(null))
        assertTrue(Regex("^[a-z_]+=[a-z0-9]+( [a-z_]+=[a-z0-9]+)*$").matches(campos))
    }
}
