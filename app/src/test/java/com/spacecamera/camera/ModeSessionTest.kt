package com.spacecamera.camera

import com.spacecamera.camera.mode.ModeLabel
import com.spacecamera.camera.mode.AppUseCase
import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.camera.mode.CameraModeId
import com.spacecamera.camera.mode.Capability
import com.spacecamera.camera.mode.CaptureState
import com.spacecamera.camera.mode.AspectRatioRule
import com.spacecamera.camera.mode.CaptureOutput
import com.spacecamera.camera.mode.StabilizationRule
import com.spacecamera.camera.mode.ControlId
import com.spacecamera.camera.mode.FlashBehavior
import com.spacecamera.camera.mode.OverlayId
import com.spacecamera.camera.mode.PhotoMode
import com.spacecamera.camera.mode.ShutterAction
import com.spacecamera.camera.mode.VideoMode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Requirements: FR-2, FR-13
 * Decisions: ADR-002
 *
 * A contabilidade entre o modo **pedido** e o modo **ligado** na sessão. É o que
 * decide quando sai `evt=mode` (só na troca efetiva, não a cada rebind de
 * proporção) e o que o ViewModel ouve quando o aparelho recusa um modo.
 */
class ModeSessionTest {

    private val soFoto = object : CameraModeDefinition() {
        override val id = CameraModeId("so_foto")
        override val label = ModeLabel.Literal("Só foto")
        override val useCases = setOf(AppUseCase.PREVIEW, AppUseCase.IMAGE_CAPTURE)
        override val requiredCapability: Capability? = null
        override val controls = emptyList<ControlId>()
        override val moreControls = emptyList<ControlId>()
        override val aspectRatio = AspectRatioRule.USER_SELECTED
        override val stabilization = StabilizationRule.OFF
        override val output = CaptureOutput.PHOTO
        override val overlay: OverlayId? = null
        override val pinnedByDefault = false
        override fun shutterAction(state: CaptureState) = ShutterAction.CapturePhoto
        override fun flashBehavior() = FlashBehavior.PhotoCycle
    }

    private val recusa = IllegalArgumentException("combinação não suportada")

    @Test
    fun `pedir o modo que já está pedido não muda nada`() {
        val sessao = ModeSession(VideoMode)

        assertFalse(sessao.request(VideoMode))
        assertTrue(sessao.request(PhotoMode))
        assertSame(PhotoMode, sessao.current)
    }

    @Test
    fun `evt=mode sai na primeira ligação e depois só quando o modo ligado muda`() {
        val sessao = ModeSession(VideoMode)
        val trocas = mutableListOf<Pair<CameraModeId?, CameraModeId>>()
        sessao.onModeChanged = { de, para, _, _ -> trocas += de to para }

        sessao.bind(onRestored = {}) { "camera" }
        sessao.reportIfChanged(elapsedMs = 97)
        sessao.bind(onRestored = {}) { "camera" }   // rebind por proporção
        sessao.reportIfChanged(elapsedMs = 40)
        sessao.request(PhotoMode)
        sessao.bind(onRestored = {}) { "camera" }
        sessao.reportIfChanged(elapsedMs = 41)

        assertEquals(listOf(null to VideoMode.id, VideoMode.id to PhotoMode.id), trocas)
    }

    @Test
    fun `modo recusado volta o pedido para o restaurado e avisa quem ouve`() = runTest {
        val sessao = ModeSession(VideoMode)
        sessao.bind(onRestored = {}) { "camera" }
        sessao.request(soFoto)
        var rebindAgendado = false
        // Sem replay de propósito: um coletor novo não pode receber recusa velha e
        // reverter um modo que já está certo. Por isso ouve antes do bind.
        val aviso = async(start = CoroutineStart.UNDISPATCHED) { sessao.rejections.first() }

        val camera = sessao.bind(onRestored = { rebindAgendado = true }) { modo ->
            if (modo === soFoto) throw recusa
            "camera-restaurada"
        }

        assertEquals("camera-restaurada", camera)
        assertSame(VideoMode, sessao.current)
        assertTrue(rebindAgendado, "os use cases foram montados para o modo recusado; precisam ser refeitos")
        assertEquals(ModeRejection(rejected = soFoto.id, active = VideoMode.id), aviso.await())
    }

    @Test
    fun `restauração não conta como troca de modo`() {
        val sessao = ModeSession(VideoMode)
        val trocas = mutableListOf<CameraModeId>()
        sessao.onModeChanged = { _, para, _, _ -> trocas += para }
        sessao.bind(onRestored = {}) { "camera" }
        sessao.reportIfChanged(1)
        sessao.request(soFoto)

        sessao.bind(onRestored = {}) { modo -> if (modo === soFoto) throw recusa else "camera" }
        sessao.reportIfChanged(1)

        assertEquals(listOf(VideoMode.id), trocas, "o modo ligado continua o vídeo")
    }

    @Test
    fun `nada ligado propaga a recusa e mantém o modo ligado anterior`() {
        val sessao = ModeSession(VideoMode)

        val erro = assertFailsWith<IllegalArgumentException> {
            sessao.bind(onRestored = {}) { throw recusa }
        }

        assertSame(recusa, erro)
    }
}
