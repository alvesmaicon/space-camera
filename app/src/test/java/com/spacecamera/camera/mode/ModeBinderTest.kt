package com.spacecamera.camera.mode

import com.spacecamera.camera.mode.ModeBinder.Outcome
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * Requirements: FR-2, NFR-1
 * Decisions: ADR-002
 *
 * O tradutor de use cases e a regra de falha do fluxo 4.2 do design, testados
 * sem CameraX: o `ModeBinder` é genérico no tipo do use case e da câmera, e quem
 * fornece os objetos de verdade é o `CameraManager`. Aqui eles são texto.
 */
class ModeBinderTest {

    private val disponiveis = mapOf(
        AppUseCase.PREVIEW to "preview",
        AppUseCase.VIDEO_CAPTURE to "video",
        AppUseCase.IMAGE_CAPTURE to "image"
    )

    /** Modo de foto sem gravação, como o Pro da Tarefa 11. */
    private val soFoto = object : CameraModeDefinition() {
        override val id = CameraModeId("so_foto")
        override val label = "Só foto"
        override val useCases = setOf(AppUseCase.PREVIEW, AppUseCase.IMAGE_CAPTURE)
        override val requiredCapability: Capability? = null
        override val controls = emptyList<ControlId>()
        override val overlay: OverlayId? = null
        override val pinnedByDefault = false
        override fun shutterAction(state: CaptureState) = ShutterAction.CapturePhoto
        override fun flashBehavior() = FlashBehavior.PhotoCycle
    }

    private val recusa = IllegalArgumentException("combinação não suportada")

    // ── Tradução: AppUseCase → use case do controller ───────────────────────

    @Test
    fun `vídeo liga os três na ordem de antes da refatoração`() {
        // A ordem dos argumentos do bindToLifecycle não é contrato do CameraX, mas
        // é o que existia — e iso-comportamento (NFR-1) não aposta no que "não deveria"
        // importar.
        assertEquals(listOf("preview", "video", "image"), ModeBinder.resolve(VideoMode.useCases, disponiveis))
    }

    @Test
    fun `liga exatamente o conjunto declarado e nenhum outro`() {
        // FR-2 / AC-2.1: um modo sem VIDEO_CAPTURE não recebe gravação na sessão.
        assertEquals(listOf("preview", "image"), ModeBinder.resolve(soFoto.useCases, disponiveis))
    }

    @Test
    fun `use case declarado sem construtor falha alto`() {
        // Se o AppUseCase ganhar um valor e ninguém ensinar o controller a construí-lo,
        // o modo ligaria menos do que declarou, calado. Melhor quebrar no bind.
        val semImagem = disponiveis - AppUseCase.IMAGE_CAPTURE
        assertFailsWith<IllegalStateException> { ModeBinder.resolve(VideoMode.useCases, semImagem) }
    }

    // ── Fluxo 4.2: nunca deixar a sessão sem bind ───────────────────────────

    @Test
    fun `bind aceito liga o modo pedido`() {
        val tentativas = mutableListOf<CameraModeDefinition>()

        val r = ModeBinder.bind(requested = PhotoMode, previous = VideoMode) { tentativas += it; "camera" }

        assertEquals(Outcome.Bound("camera", PhotoMode), r)
        assertEquals(listOf<CameraModeDefinition>(PhotoMode), tentativas)
    }

    @Test
    fun `combinação recusada restaura o conjunto do modo anterior`() {
        val tentativas = mutableListOf<CameraModeDefinition>()

        val r = ModeBinder.bind(requested = soFoto, previous = VideoMode) { modo ->
            tentativas += modo
            if (modo === soFoto) throw recusa
            "camera-restaurada"
        }

        val restaurado = assertIs<Outcome.Restored<String>>(r)
        assertEquals("camera-restaurada", restaurado.camera)
        assertSame(VideoMode, restaurado.mode)
        assertSame(soFoto, restaurado.rejected)
        assertSame(recusa, restaurado.cause)
        assertEquals(listOf(soFoto, VideoMode), tentativas)
    }

    @Test
    fun `primeiro bind sem modo anterior falha sem tentar de novo`() {
        var chamadas = 0

        val r = ModeBinder.bind(requested = VideoMode, previous = null) { chamadas++; throw recusa }

        assertEquals(Outcome.Failed(recusa), r)
        assertEquals(1, chamadas)
    }

    @Test
    fun `rebind do mesmo modo que falha não repete a mesma tentativa`() {
        // Troca de proporção ou de resolução religa o mesmo modo. Se falhou, o
        // "anterior" é ele mesmo — repetir só dobraria o tempo de tela preta.
        var chamadas = 0

        val r = ModeBinder.bind(requested = VideoMode, previous = VideoMode) { chamadas++; throw recusa }

        assertEquals(Outcome.Failed(recusa), r)
        assertEquals(1, chamadas)
    }

    @Test
    fun `se a restauração também falha, a causa reportada é a original`() {
        // A primeira recusa é a que explica o problema; a segunda é consequência.
        val segunda = IllegalStateException("câmera fechada")

        val r = ModeBinder.bind(requested = soFoto, previous = VideoMode) { modo ->
            throw if (modo === soFoto) recusa else segunda
        }

        val falha = assertIs<Outcome.Failed>(r)
        assertSame(recusa, falha.cause)
        assertEquals(listOf<Throwable>(segunda), falha.cause.suppressed.toList())
    }
}
