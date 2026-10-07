package com.spacecamera.camera.mode

import com.spacecamera.presentation.components.ModeSurfaces
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Requirements: FR-2, FR-5, FR-9, FR-12, AC-2.1, AC-5.1, AC-5.2
 * Decisions: ADR-001, ADR-007, Q-11
 *
 * O modo Pro declarado — o primeiro modo que entra **só** pela costura: um arquivo e
 * uma linha no registro.
 */
class ProModeTest {

    private val comManual = setOf(Capability.MANUAL_SENSOR)

    @Test
    fun `Pro liga só pré-visualização e foto`() {
        // AC-2.1: sem VideoCapture na sessão.
        assertEquals(setOf(AppUseCase.PREVIEW, AppUseCase.IMAGE_CAPTURE), ProMode.useCases)
    }

    @Test
    fun `Pro exige controle manual do sensor`() {
        assertEquals(Capability.MANUAL_SENSOR, ProMode.requiredCapability)
    }

    @Test
    fun `Pro não aparece em aparelho sem MANUAL_SENSOR`() {
        // AC-5.1: nem no plano, nem na gaveta.
        val arranjo = ModeRegistry.arranged(ModeArrangement.DEFAULT, emptySet())
        assertFalse(ProMode in arranjo.pinned + arranjo.drawer)
    }

    @Test
    fun `Pro vem na gaveta em aparelho com MANUAL_SENSOR`() {
        // AC-5.2 + decisão do usuário (2026-10-07): por padrão, gaveta.
        // Afirma o lugar do Pro, não a lista inteira: outro modo na gaveta não é erro.
        val arranjo = ModeRegistry.arranged(ModeArrangement.DEFAULT, comManual)
        assertTrue(ProMode in arranjo.drawer)
        assertFalse(ProMode in arranjo.pinned)
    }

    @Test
    fun `Pro é modo de foto, com o disparador da foto`() {
        // ADR-007 / Q5: controles manuais só para foto.
        assertEquals(CaptureOutput.PHOTO, ProMode.output)
        assertEquals(ShutterAction.CapturePhoto, ProMode.shutterAction(CaptureState(recording = false, countdownActive = false)))
        assertEquals(ShutterAction.CancelCountdown, ProMode.shutterAction(CaptureState(recording = false, countdownActive = true)))
        assertEquals(AspectRatioRule.USER_SELECTED, ProMode.aspectRatio)
        assertEquals(StabilizationRule.OFF, ProMode.stabilization)
    }

    @Test
    fun `Pro não oferece flash nem HDR`() {
        // Q-11: com o AE desligado o flash automático não dispara de forma confiável,
        // e o scene mode HDR anula a exposição manual.
        assertEquals(FlashBehavior.Unavailable, ProMode.flashBehavior())
        assertFalse(ControlId.FLASH in ProMode.controls)
        assertFalse(ControlId.HDR in ProMode.moreControls)
    }

    @Test
    fun `Pro desenha as escalas, e a tabela da UI as conhece`() {
        // A partir daqui o teste de completude do registro deixa de passar vazio.
        assertEquals(ModeSurfaces.PRO_SCALES, ProMode.overlay)
        assertTrue(ProMode.overlay in ModeSurfaces.overlays)
    }

    @Test
    fun `os modos base não ganharam overlay`() {
        assertNull(VideoMode.overlay)
        assertNull(PhotoMode.overlay)
    }
}
