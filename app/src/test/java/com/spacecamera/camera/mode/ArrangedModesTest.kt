package com.spacecamera.camera.mode

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Requirements: FR-6, FR-14
 * Decisions: ADR-004
 *
 * O carrossel do seletor e o passo do gesto de deslizar, sobre o arranjo de modos.
 * Substituem `CameraMode.entries` e `CameraMode.next()/previous()`.
 */
class ArrangedModesTest {

    private val pro = modoDeTeste("pro", "Pro")
    private val arranjo = ArrangedModes(pinned = listOf(VideoMode, PhotoMode), drawer = listOf(pro))

    @Test
    fun `carrossel é o plano quando o modo ativo está nele`() {
        assertEquals(listOf(VideoMode, PhotoMode), arranjo.carousel(active = PhotoMode))
    }

    @Test
    fun `modo escolhido na gaveta aparece no carrossel enquanto ativo`() {
        // O modo ativo nunca some da tela (FR-14): sem isto, escolher o Pro na
        // gaveta deixaria o seletor sem nada destacado.
        assertEquals(listOf(VideoMode, PhotoMode, pro), arranjo.carousel(active = pro))
    }

    @Test
    fun `deslizar anda pelo carrossel e dá a volta, como o enum fazia`() {
        // Iso-comportamento: CameraMode.next() era cíclico — de Foto, "próximo"
        // voltava para Vídeo.
        assertSame(PhotoMode, arranjo.step(active = VideoMode, by = +1))
        assertSame(VideoMode, arranjo.step(active = PhotoMode, by = +1))
        assertSame(PhotoMode, arranjo.step(active = VideoMode, by = -1))
        assertSame(VideoMode, arranjo.step(active = PhotoMode, by = -1))
    }

    @Test
    fun `deslizar não entra na gaveta`() {
        // A gaveta é para o que o usuário tirou do plano; o gesto não a percorre.
        assertSame(VideoMode, arranjo.step(active = PhotoMode, by = +1))
    }

    @Test
    fun `deslizar a partir de um modo da gaveta volta ao plano`() {
        assertSame(VideoMode, arranjo.step(active = pro, by = +1))
        assertSame(PhotoMode, arranjo.step(active = pro, by = -1))
    }
}
