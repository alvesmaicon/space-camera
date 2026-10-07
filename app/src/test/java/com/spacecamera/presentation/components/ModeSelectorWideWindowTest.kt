package com.spacecamera.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.spacecamera.camera.mode.ArrangedModes
import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.camera.mode.PhotoMode
import com.spacecamera.camera.mode.VideoMode
import com.spacecamera.camera.mode.modoDeTeste
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Requirements: FR-14, AC da Tarefa 9
 * Decisions: ADR-001, ADR-006
 *
 * O seletor no eixo vertical (janela larga), sem quantidade fixa de modos. Janela de
 * tablet deitado e baixa: o eixo não comporta muitos modos, e o ativo tem de seguir
 * visível.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w960dp-h480dp")
class ModeSelectorWideWindowTest {

    @get:Rule
    val compose = createComposeRule()

    private val extras = (1..4).map { modoDeTeste("extra$it", "Extra $it", pinned = true) }

    private fun montar(modos: ArrangedModes, ativo: CameraModeDefinition) = compose.setContent {
        ModeSelector(
            modes = modos,
            active = ativo,
            vertical = true,
            scrimColor = Color.Black.copy(alpha = 0.4f),
            onModeSelect = {},
            onOpenDrawer = {}
        )
    }

    @Test
    fun `com muitos modos o ativo no fim da coluna fica visível`() {
        // Seis modos no plano + "Mais": não cabem na altura máxima do eixo.
        val pinned = listOf(VideoMode, PhotoMode) + extras
        montar(ArrangedModes(pinned = pinned, drawer = emptyList()), ativo = extras.last())

        compose.onNodeWithText("Extra 4").assertIsDisplayed()
    }

    @Test
    fun `com muitos modos os demais continuam alcançáveis`() {
        val pinned = listOf(VideoMode, PhotoMode) + extras
        montar(ArrangedModes(pinned = pinned, drawer = emptyList()), ativo = extras.last())

        compose.onNodeWithText("Vídeo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mais").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `com dois modos e o Mais tudo aparece, sem rolar`() {
        // O caso de hoje: o resultado tem de ser o de antes da tarefa.
        montar(ArrangedModes(pinned = listOf(VideoMode, PhotoMode), drawer = emptyList()), ativo = VideoMode)

        listOf("Vídeo", "Foto", "Mais").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `a coluna não cresce sem limite`() {
        // Sem teto, seis modos empurrariam o disparador para fora numa janela baixa.
        val pinned = listOf(VideoMode, PhotoMode) + extras
        montar(ArrangedModes(pinned = pinned, drawer = emptyList()), ativo = VideoMode)

        val visiveis = listOf("Vídeo", "Foto", "Extra 1", "Extra 2", "Extra 3", "Extra 4", "Mais").count { texto ->
            runCatching { compose.onNodeWithText(texto).assertIsDisplayed() }.isSuccess
        }
        assertTrue(visiveis < 7, "todos os 7 itens visíveis: a coluna não tem limite de altura")
    }
}
