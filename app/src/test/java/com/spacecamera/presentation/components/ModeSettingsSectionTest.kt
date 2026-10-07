package com.spacecamera.presentation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.camera.mode.PhotoMode
import com.spacecamera.camera.mode.VideoMode
import com.spacecamera.camera.mode.modoDeTeste
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Requirements: FR-7, FR-16, AC-7.1, AC-16.1
 * Decisions: ADR-004, ADR-006, Q-12
 *
 * A seção "Modos da câmera" de Configurações, sob Robolectric + Compose — só árvore
 * semântica.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModeSettingsSectionTest {

    @get:Rule
    val compose = createComposeRule()

    private val pro = modoDeTeste("pro", "Pro")

    private val movimentos = mutableListOf<Pair<String, Int>>()
    private val fixacoes = mutableListOf<Pair<String, Boolean>>()
    private var restaurou = 0

    private fun montar(fixados: Set<CameraModeDefinition> = setOf(VideoMode, PhotoMode)) = compose.setContent {
        ModeSettingsSection(
            modes = listOf(VideoMode, PhotoMode, pro),
            pinned = fixados,
            isRemovable = { it === pro },
            onMove = { modo, passo -> movimentos += modo.id.value to passo },
            onPinnedChange = { modo, fixo -> fixacoes += modo.id.value to fixo },
            onRestoreDefault = { restaurou++ }
        )
    }

    @Test
    fun `lista os modos na ordem recebida`() {
        montar()
        compose.onNodeWithText("Vídeo").assertIsDisplayed()
        compose.onNodeWithText("Foto").assertIsDisplayed()
        compose.onNodeWithText("Pro").assertIsDisplayed()
    }

    @Test
    fun `Vídeo e Foto não oferecem sair do seletor`() {
        // AC-7.1: a ação não é oferecida — o interruptor nem existe para eles.
        montar()
        assertEquals(0, compose.onAllNodesComDescricao("Vídeo no seletor"))
        assertEquals(0, compose.onAllNodesComDescricao("Foto no seletor"))
        compose.onNodeWithContentDescription("Pro no seletor").assertIsDisplayed()
    }

    @Test
    fun `interruptor leva o modo ao plano`() {
        montar()
        compose.onNodeWithContentDescription("Pro no seletor").performClick()
        assertEquals(listOf("pro" to true), fixacoes)
    }

    @Test
    fun `setas reordenam`() {
        montar()
        compose.onNodeWithContentDescription("Subir Pro").performClick()
        compose.onNodeWithContentDescription("Descer Vídeo").performClick()
        assertEquals(listOf("pro" to -1, "video" to +1), movimentos)
    }

    @Test
    fun `restaurar padrão avisa quem persiste`() {
        // AC-16.1.
        montar()
        compose.onNodeWithText("Restaurar padrão").performClick()
        assertTrue(restaurou == 1)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesComDescricao(d: String) =
        onAllNodes(androidx.compose.ui.test.hasContentDescription(d)).fetchSemanticsNodes().size
}
