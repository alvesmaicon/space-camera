package com.spacecamera.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.spacecamera.camera.PhotoQualityPreset
import com.spacecamera.camera.VideoBitratePreset
import com.spacecamera.camera.mode.ModeArrangement
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

    // ── Preferência de modos (Tarefa 10) ────────────────────────────────────
    //
    // Requirements: FR-8, NFR-8, AC-8.1, AC-8.2 · Decisions: ADR-004
    //
    // Os quatro casos de compatibilidade do fluxo 4.5 do design, com SharedPreferences
    // de verdade: o que importa é o que sobrevive a outra versão do app.

    @Test
    fun `modos sem preferencia gravada caem no padrao`() {
        // AC-8.2.
        assertEquals(ModeArrangement.DEFAULT, storage.modeArrangement)
    }

    @Test
    fun `preferencia de modos sobrevive por identificador e na ordem`() {
        // FR-8: texto, nunca ordinal.
        val gravada = ModeArrangement(order = listOf("pro", "video", "photo"), pinned = setOf("pro", "video", "photo"))
        storage.modeArrangement = gravada

        assertEquals(gravada, SettingsStorage(ApplicationProvider.getApplicationContext()).modeArrangement)
    }

    @Test
    fun `identificador desconhecido volta intacto da leitura`() {
        // AC-8.1: quem descarta é o registro, na hora de mostrar. A preferência guarda
        // o que o usuário gravou — inclusive o que esta versão não conhece.
        storage.modeArrangement = ModeArrangement(order = listOf("sepia_antigo", "video"), pinned = setOf("video"))

        assertEquals(listOf("sepia_antigo", "video"), storage.modeArrangement.order)
    }

    @Test
    fun `preferencia de modos corrompida degrada para o padrao sem excecao`() {
        // NFR-8: outra versão gravou outro tipo na mesma chave.
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("space_camera_settings", Context.MODE_PRIVATE)
            .edit().putInt("mode_order", 42).putInt("mode_pinned", 7).commit()

        assertEquals(ModeArrangement.DEFAULT, storage.modeArrangement)
    }
}
