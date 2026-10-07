package com.spacecamera.camera

import com.spacecamera.camera.mode.AppUseCase
import com.spacecamera.camera.mode.CameraModeId
import com.spacecamera.camera.mode.PhotoMode
import com.spacecamera.camera.mode.VideoMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Requirements: FR-13, NFR-1, NFR-7
 *
 * O `evt=mode` novo e o campo `mode=` do `evt=bind`, que passa a vir do
 * identificador do modo e precisa continuar saindo igual ao de antes.
 */
class CameraTelemetryModeTest {

    @Test
    fun `evt=bind mantém VIDEO e PHOTO vindos do identificador estável`() {
        // NFR-1: é campo preexistente, comparado entre os dois commits. Trocar a
        // fonte (enum → registro) não pode trocar o valor.
        assertEquals("VIDEO", TelemetryFields.bindModeName(VideoMode.id))
        assertEquals("PHOTO", TelemetryFields.bindModeName(PhotoMode.id))
    }

    @Test
    fun `evt=mode traz origem, destino, use cases e latência`() {
        val campos = TelemetryFields.modeFields(
            from = VideoMode.id,
            to = PhotoMode.id,
            useCases = PhotoMode.useCases,
            elapsedMs = 40
        )

        assertEquals(
            "from=video to=photo use_cases=preview,video_capture,image_capture elapsed_ms=40",
            campos
        )
    }

    @Test
    fun `primeiro bind não tem modo de origem`() {
        val campos = TelemetryFields.modeFields(null, VideoMode.id, VideoMode.useCases, 97)

        assertTrue(campos.startsWith("from=- to=video "), campos)
    }

    @Test
    fun `use cases saem sempre na mesma ordem, qualquer que seja a do conjunto`() {
        // Set não tem ordem garantida; a linha precisa ser estável para o grep.
        val embaralhado = linkedSetOf(AppUseCase.IMAGE_CAPTURE, AppUseCase.PREVIEW)

        val campos = TelemetryFields.modeFields(null, CameraModeId("pro"), embaralhado, 1)

        assertTrue("use_cases=preview,image_capture " in campos, campos)
    }

    @Test
    fun `campo de latência reaproveita o nome que já existe`() {
        // Q-04: a telemetria usa elapsed_ms; a spec falava em latency_ms. Um
        // terceiro nome só pioraria o grep.
        val campos = TelemetryFields.modeFields(VideoMode.id, PhotoMode.id, PhotoMode.useCases, 5)

        assertTrue(campos.endsWith(" elapsed_ms=5"), campos)
    }
}
