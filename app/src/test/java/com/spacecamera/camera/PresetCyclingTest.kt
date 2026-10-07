package com.spacecamera.camera

import com.spacecamera.camera.mode.ModeArrangement
import com.spacecamera.camera.mode.ModeRegistry
import com.spacecamera.camera.mode.PhotoMode
import com.spacecamera.camera.mode.VideoMode
import com.spacecamera.presentation.viewmodels.RecordingDelay
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Vários controles da barra superior avançam por toque, ciclando entre valores.
 * O que quebra nesse tipo de código é a volta ao início — o `% size` no último
 * item — e o passo para trás em índice zero, que vira negativo sem o `+ size`.
 *
 * Roda sob Robolectric porque `PhotoQualityPreset` guarda um `android.util.Size`:
 * na JVM pura o android.jar stub devolve 0 em width/height e as asserções de
 * tamanho passariam sem testar nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PresetCyclingTest {

    // O ciclo de modos saiu do enum `CameraMode` para o arranjo do registro
    // (Tarefa 8, Q-10). Mesmos valores esperados, mesmos riscos: a volta no último
    // item e o índice negativo no primeiro.
    private val modos = ModeRegistry.arranged(ModeArrangement.DEFAULT, emptySet())

    @Test
    fun `modo da camera alterna entre video e foto`() {
        assertEquals(PhotoMode, modos.step(VideoMode, by = +1))
        assertEquals(VideoMode, modos.step(PhotoMode, by = +1))
    }

    @Test
    fun `passo para tras no primeiro item vai para o ultimo`() {
        // Sem o `+ size` antes do módulo isto daria índice -1.
        assertEquals(PhotoMode, modos.step(VideoMode, by = -1))
    }

    @Test
    fun `atraso de gravacao cicla off 3 5 10 e volta`() {
        val esperado = listOf(
            RecordingDelay.THREE, RecordingDelay.FIVE,
            RecordingDelay.TEN, RecordingDelay.OFF
        )
        var atual = RecordingDelay.OFF
        esperado.forEach { proximo ->
            atual = atual.next()
            assertEquals(proximo, atual)
        }
    }

    @Test
    fun `segundos do atraso batem com o rotulo`() {
        assertEquals(0, RecordingDelay.OFF.seconds)
        assertEquals(3, RecordingDelay.THREE.seconds)
        assertEquals(5, RecordingDelay.FIVE.seconds)
        assertEquals(10, RecordingDelay.TEN.seconds)
    }

    @Test
    fun `qualidade de foto cicla e volta ao inicio`() {
        var atual = PhotoQualityPreset.MAXIMA
        repeat(PhotoQualityPreset.entries.size) { atual = atual.next() }
        assertEquals(PhotoQualityPreset.MAXIMA, atual, "uma volta completa retorna ao início")
    }

    @Test
    fun `apenas a qualidade maxima nao fixa um tamanho alvo`() {
        // MAXIMA = usar o sensor inteiro; as demais reduzem para o alvo declarado.
        assertEquals(null, PhotoQualityPreset.MAXIMA.targetSize)
        PhotoQualityPreset.entries.filter { it != PhotoQualityPreset.MAXIMA }.forEach {
            assertEquals(true, it.targetSize != null, "${it.name} precisa de targetSize")
        }
    }

    @Test
    fun `tamanhos alvo caem em ordem decrescente`() {
        val megapixels = listOf(PhotoQualityPreset.ALTA, PhotoQualityPreset.MEDIA, PhotoQualityPreset.BAIXA)
            .map { it.targetSize!!.width.toLong() * it.targetSize!!.height }
        assertEquals(megapixels.sortedDescending(), megapixels)
    }
}
