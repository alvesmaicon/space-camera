package com.spacecamera.camera

import androidx.camera.video.Quality
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O bitrate escolhido aqui é o que determina tamanho de arquivo e se o encoder de
 * hardware aceita ou rejeita a configuração. É conta pura — dá para fixar em teste.
 */
class VideoBitratePresetTest {

    @Test
    fun `bitrate segue largura x altura x fps x bpp`() {
        val fhd30 = VideoOption(Quality.FHD, 30)
        // 1920 * 1080 * 30 * 0.12 = 7.464.960
        assertEquals(7_464_960, VideoBitratePreset.MEDIUM.bitrateFor(fhd30))
    }

    @Test
    fun `preset maior gera bitrate maior na mesma resolucao`() {
        val fhd30 = VideoOption(Quality.FHD, 30)
        val bitrates = VideoBitratePreset.entries.map { it.bitrateFor(fhd30) }
        assertEquals(
            bitrates.sorted(), bitrates,
            "LOW < MEDIUM < HIGH < VERY_HIGH precisa valer para a UI fazer sentido"
        )
    }

    @Test
    fun `dobrar o fps dobra o bitrate`() {
        val trinta = VideoBitratePreset.HIGH.bitrateFor(VideoOption(Quality.FHD, 30))
        val sessenta = VideoBitratePreset.HIGH.bitrateFor(VideoOption(Quality.FHD, 60))
        assertEquals(trinta * 2, sessenta)
    }

    @Test
    fun `4K 60 na qualidade maxima é limitado a 150 Mbps`() {
        // 3840 * 2160 * 60 * 0.40 = 199.065.600 — acima do que encoders H.264
        // de hardware costumam aceitar, por isso existe o teto.
        val semTeto = 3840L * 2160 * 60 * 0.40
        assertTrue(semTeto > 150_000_000, "o caso precisa de fato estourar o teto")
        assertEquals(
            150_000_000,
            VideoBitratePreset.VERY_HIGH.bitrateFor(VideoOption(Quality.UHD, 60))
        )
    }

    @Test
    fun `resolucao dentro do teto nao é limitada`() {
        val fhd60 = VideoOption(Quality.FHD, 60)
        assertEquals(
            (1920L * 1080 * 60 * 0.40).toInt(),
            VideoBitratePreset.VERY_HIGH.bitrateFor(fhd60)
        )
    }
}

/** Dimensões e rótulos alimentam tanto o cálculo de bitrate quanto a UI. */
class VideoOptionTest {

    @Test
    fun `cada qualidade mapeia para as dimensoes e rotulo esperados`() {
        val casos = listOf(
            Triple(Quality.UHD, 3840 to 2160, "4K"),
            Triple(Quality.FHD, 1920 to 1080, "FHD"),
            Triple(Quality.HD, 1280 to 720, "HD"),
            Triple(Quality.SD, 854 to 480, "SD"),
        )
        casos.forEach { (quality, dim, rotulo) ->
            val option = VideoOption(quality, 30)
            assertEquals(dim.first, option.widthPx, "largura de $quality")
            assertEquals(dim.second, option.heightPx, "altura de $quality")
            assertEquals(rotulo, option.qualityLabel)
        }
    }

    @Test
    fun `label combina qualidade e fps`() {
        assertEquals("4K 60", VideoOption(Quality.UHD, 60).label)
    }
}
