package com.spacecamera.camera.mode

import com.spacecamera.presentation.components.ModeSurfaces
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Requirements: FR-1, FR-2, FR-3, FR-4, NFR-5
 * Decisions: ADR-001, ADR-002
 *
 * O que cada modo **declara**. Estas asserções reproduzem, em forma de teste, o
 * comportamento que hoje vive espalhado em `when` e `if` por três arquivos grandes —
 * é a rede que permite remigrar Vídeo e Foto sem mudar o que o usuário vê.
 *
 * JVM pura: nenhum destes carrega Compose ou CameraX.
 */
class CameraModeDefinitionTest {

    // ── FR-2: conjunto de use cases declarado por modo ──────────────────────

    @Test
    fun `vídeo liga preview, gravação e captura de imagem`() {
        // AC-2.2 — é o conjunto que o bind de hoje já usa, e não pode mudar.
        assertEquals(
            setOf(AppUseCase.PREVIEW, AppUseCase.VIDEO_CAPTURE, AppUseCase.IMAGE_CAPTURE),
            VideoMode.useCases
        )
    }

    @Test
    fun `foto liga o mesmo conjunto de hoje`() {
        // A Foto hoje liga os três porque `bindCameraUseCases()` é cego ao modo.
        // Remigrar sem mudar comportamento significa declarar exatamente isso —
        // enxugar para Preview+ImageCapture seria mudança observável, e é o Pro
        // (Tarefa 11) que existe para exercitar o conjunto reduzido.
        assertEquals(
            setOf(AppUseCase.PREVIEW, AppUseCase.VIDEO_CAPTURE, AppUseCase.IMAGE_CAPTURE),
            PhotoMode.useCases
        )
    }

    @Test
    fun `nenhum dos dois modos base exige capacidade de hardware`() {
        assertEquals(null, VideoMode.requiredCapability)
        assertEquals(null, PhotoMode.requiredCapability)
    }

    // ── Comportamento: o disparador ─────────────────────────────────────────

    @Test
    fun `disparador do vídeo em repouso inicia a gravação`() {
        assertEquals(
            ShutterAction.StartRecording,
            VideoMode.shutterAction(CaptureState(recording = false, countdownActive = false))
        )
    }

    @Test
    fun `disparador do vídeo gravando para a gravação`() {
        assertEquals(
            ShutterAction.StopRecording,
            VideoMode.shutterAction(CaptureState(recording = true, countdownActive = false))
        )
    }

    @Test
    fun `disparador do vídeo durante a contagem regressiva cancela`() {
        // Hoje: `isRecording -> stop` vem **antes** de `countdown > 0 -> cancel`,
        // então gravando ganha do timer. A ordem importa e está reproduzida aqui.
        assertEquals(
            ShutterAction.CancelCountdown,
            VideoMode.shutterAction(CaptureState(recording = false, countdownActive = true))
        )
        assertEquals(
            ShutterAction.StopRecording,
            VideoMode.shutterAction(CaptureState(recording = true, countdownActive = true))
        )
    }

    @Test
    fun `disparador da foto captura, e cancela durante a contagem`() {
        assertEquals(
            ShutterAction.CapturePhoto,
            PhotoMode.shutterAction(CaptureState(recording = false, countdownActive = false))
        )
        assertEquals(
            ShutterAction.CancelCountdown,
            PhotoMode.shutterAction(CaptureState(recording = false, countdownActive = true))
        )
    }

    // ── Comportamento: o flash ──────────────────────────────────────────────

    @Test
    fun `flash é tocha no vídeo e ciclo de três estados na foto`() {
        // O caso que decidiu o ADR-001 pela forma híbrida: isto é comportamento,
        // não escolha de conjunto fechado.
        assertEquals(FlashBehavior.Torch, VideoMode.flashBehavior())
        assertEquals(FlashBehavior.PhotoCycle, PhotoMode.flashBehavior())
    }

    // ── Guarda-corpos do ADR-001 ────────────────────────────────────────────

    @Test
    fun `definição de modo não guarda estado mutável`() {
        // Varre por reflexão: campo não-final na definição é estado, e estado aqui
        // faria do modo um segundo ViewModel — o teste de unidade deixaria de ser
        // determinístico e o registro sairia do alcance da JVM pura.
        ModeRegistry.all.forEach { modo ->
            modo.javaClass.declaredFields
                .filterNot { it.isSynthetic }
                .forEach { campo ->
                    assertTrue(
                        java.lang.reflect.Modifier.isFinal(campo.modifiers),
                        "${modo.id.value}: campo mutável '${campo.name}' viola o ADR-001"
                    )
                }
        }
    }

    @Test
    fun `definição de modo não conhece o controller nem o CameraX`() {
        // Se o tipo de algum membro vier de `androidx.camera` ou do controller, a
        // dependência inverteu e o registro deixou de ser testável sem aparelho.
        ModeRegistry.all.forEach { modo ->
            val tipos = modo.javaClass.declaredFields.map { it.type.name } +
                modo.javaClass.declaredMethods.map { it.returnType.name }
            tipos.forEach { t ->
                assertFalse(
                    t.startsWith("androidx.camera") || t == "com.spacecamera.camera.CameraController",
                    "${modo.id.value}: membro do tipo $t viola o ADR-001"
                )
            }
        }
    }

    // ── O contrapeso da indireção identificador → composable ────────────────

    @Test
    fun `todo overlay e controle declarado resolve na tabela da UI`() {
        // AC da Tarefa 2, e a razão de o ADR-001 ter sido aceito apesar da
        // indireção: sem este teste, declarar um identificador sem correspondente
        // troca erro de compilação por tela quebrada na mão do usuário.
        //
        // Passa vazio enquanto nenhum modo declara overlay — e é a Tarefa 11, com o
        // PRO_SCALES, que o exercita de fato. Existe desde já de propósito: a rede
        // tem de estar no lugar antes do salto.
        val overlaysDeclarados = ModeRegistry.all.mapNotNull { it.overlay }.toSet()
        val naoResolvidos = overlaysDeclarados - ModeSurfaces.overlays
        assertTrue(naoResolvidos.isEmpty(), "overlays sem composable: $naoResolvidos")

        val controlesDeclarados = ModeRegistry.all.flatMap { it.controls }.toSet()
        val controlesOrfaos = controlesDeclarados - ModeSurfaces.controls
        assertTrue(controlesOrfaos.isEmpty(), "controles sem composable: $controlesOrfaos")
    }

    @Test
    fun `a tabela da UI não tem entrada que ninguém declara`() {
        // O outro sentido: entrada órfã é código morto que ninguém percebe, como o
        // ramo "1:1" do CameraManager que a Q-01 encontrou.
        val sobrando = ModeSurfaces.overlays - ModeRegistry.all.mapNotNull { it.overlay }.toSet()
        assertTrue(sobrando.isEmpty(), "overlays registrados que nenhum modo usa: $sobrando")
    }
}
