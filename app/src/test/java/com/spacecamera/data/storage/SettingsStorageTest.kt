package com.spacecamera.data.storage

import androidx.test.core.app.ApplicationProvider
import com.spacecamera.camera.PhotoQualityPreset
import com.spacecamera.camera.VideoBitratePreset
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SettingsStorage] é a memória do app entre sessões. Os defaults importam:
 * são eles que valem na primeira execução e sempre que "Manter configurações"
 * está desligado.
 *
 * Roda com SharedPreferences de verdade (Robolectric) em vez de mock, porque o
 * que interessa testar é justamente a serialização — em especial os enums, que
 * são gravados por `ordinal` e por isso quebram silenciosamente se alguém
 * reordenar as constantes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsStorageTest {

    private lateinit var storage: SettingsStorage

    @Before
    fun setUp() {
        storage = SettingsStorage(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `defaults de primeira execucao`() {
        // Ligados por padrão: melhoram o resultado sem o usuário configurar nada.
        assertTrue(storage.isStabilizationEnabled, "EIS")
        assertTrue(storage.isNoiseReductionEnabled, "redução de ruído")
        assertTrue(storage.isTapToFocusEnabled, "toque para focar")
        assertTrue(storage.isKeepSettingsEnabled, "manter configurações")

        // Desligados por padrão: mudam o enquadramento, custam bateria ou
        // envolvem privacidade — precisam de escolha explícita.
        assertFalse(storage.isGridEnabled, "grade")
        assertFalse(storage.isLevelEnabled, "nível")
        assertFalse(storage.isMicMuted, "microfone mudo")
        assertFalse(storage.isHdrEnabled, "HDR")
        assertFalse(storage.isSaveLocationEnabled, "salvar localização")
        assertFalse(storage.isFrontCameraMirrorEnabled, "espelhar frontal")
        assertFalse(storage.isImageEnhancementEnabled, "melhoria de imagem")

        assertEquals(VideoBitratePreset.MEDIUM, storage.bitratePreset)
        assertEquals(PhotoQualityPreset.MAXIMA, storage.photoQualityPreset)
    }

    @Test
    fun `valores booleanos sobrevivem a uma nova instancia`() {
        storage.isGridEnabled = true
        storage.isStabilizationEnabled = false
        storage.isSaveLocationEnabled = true

        val recarregado = SettingsStorage(ApplicationProvider.getApplicationContext())
        assertTrue(recarregado.isGridEnabled)
        assertFalse(recarregado.isStabilizationEnabled)
        assertTrue(recarregado.isSaveLocationEnabled)
    }

    @Test
    fun `todos os presets de bitrate fazem ida e volta`() {
        VideoBitratePreset.entries.forEach { preset ->
            storage.bitratePreset = preset
            assertEquals(
                preset,
                SettingsStorage(ApplicationProvider.getApplicationContext()).bitratePreset
            )
        }
    }

    @Test
    fun `todos os presets de qualidade de foto fazem ida e volta`() {
        PhotoQualityPreset.entries.forEach { preset ->
            storage.photoQualityPreset = preset
            assertEquals(
                preset,
                SettingsStorage(ApplicationProvider.getApplicationContext()).photoQualityPreset
            )
        }
    }

    @Test
    fun `ordinal invalido cai no default em vez de estourar`() {
        // Cenário real: uma versão futura remove uma constante do enum e o
        // aparelho ainda tem o ordinal antigo gravado. Precisa degradar, não crashar.
        val prefs = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("space_camera_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("video_bitrate_preset", 99).commit()
        prefs.edit().putInt("photo_quality_preset", -1).commit()

        val recarregado = SettingsStorage(ApplicationProvider.getApplicationContext())
        assertEquals(VideoBitratePreset.MEDIUM, recarregado.bitratePreset)
        assertEquals(PhotoQualityPreset.MAXIMA, recarregado.photoQualityPreset)
    }
}
