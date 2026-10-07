package com.spacecamera.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.spacecamera.camera.mode.ArrangedModes
import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.camera.mode.ModeRegistry
import com.spacecamera.camera.mode.PhotoMode
import com.spacecamera.camera.mode.VideoMode
import com.spacecamera.camera.mode.modoDeTeste
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Requirements: FR-6, FR-17, AC-1.1, AC-6.1
 * Decisions: ADR-004, ADR-006
 *
 * O seletor orientado ao registro, sob Robolectric + Compose. Sucede o
 * `ModeSelectorSmokeTest` da Tarefa 4, que provava a infraestrutura com o seletor
 * de antes (`CameraMode.entries`) e anunciava esta troca.
 *
 * Só árvore semântica: nó existe, rótulo, clique. Nada de `captureToImage`.
 */
@RunWith(RobolectricTestRunner::class)
// Largura de celular real (o padrão do Robolectric, 320dp, é mais estreito que
// qualquer aparelho-alvo). A janela estreita tem teste próprio abaixo.
@Config(sdk = [34], qualifiers = "w393dp-h851dp")
class ModeSelectorTest {

    @get:Rule
    val compose = createComposeRule()

    private val pro = modoDeTeste("pro", "Pro")
    private val tresModos = ArrangedModes(pinned = listOf(VideoMode, PhotoMode), drawer = listOf(pro))

    private fun montar(
        modos: ArrangedModes = tresModos,
        ativo: CameraModeDefinition = VideoMode,
        vertical: Boolean = false,
        aoEscolher: (CameraModeDefinition) -> Unit = {},
        aoAbrirGaveta: () -> Unit = {}
    ) = compose.setContent {
        ModeSelector(
            modes = modos,
            active = ativo,
            vertical = vertical,
            scrimColor = Color.Black.copy(alpha = 0.4f),
            onModeSelect = aoEscolher,
            onOpenDrawer = aoAbrirGaveta
        )
    }

    @Test
    fun `os modos vêm do registro`() {
        // AC-1.1: com o registro de produção, o seletor mostra o que ele declara.
        montar(modos = ModeRegistry.arranged(com.spacecamera.camera.mode.ModeArrangement.DEFAULT, emptySet()))

        compose.onNodeWithText("Vídeo").assertIsDisplayed()
        compose.onNodeWithText("Foto").assertIsDisplayed()
    }

    @Test
    fun `plano mostra os fixados e a gaveta fica atrás do Mais`() {
        // AC-6.1: três modos, dois no plano — o terceiro não aparece no carrossel.
        montar()

        compose.onNodeWithText("Vídeo").assertIsDisplayed()
        compose.onNodeWithText("Foto").assertIsDisplayed()
        compose.onNodeWithText("Mais").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTextCount("Pro") == 0, "Pro está na gaveta, não no plano")
    }

    @Test
    fun `tocar num modo avisa quem escolheu`() {
        var escolhido: CameraModeDefinition? = null
        montar(aoEscolher = { escolhido = it })

        compose.onNodeWithText("Foto").performClick()

        assertSame(PhotoMode, escolhido)
    }

    @Test
    fun `tocar em Mais abre a gaveta`() {
        var abriu = 0
        montar(aoAbrirGaveta = { abriu++ })

        compose.onNodeWithText("Mais").performClick()

        assertEquals(1, abriu)
    }

    @Test
    fun `modo ativo vindo da gaveta aparece no carrossel`() {
        montar(ativo = pro)

        compose.onNodeWithText("Pro").assertIsDisplayed()
    }

    @Test
    fun `Mais aparece mesmo com a gaveta vazia`() {
        // Decisão do usuário (2026-10-07): o acesso à personalização (FR-17) mora no
        // painel, então o item não some quando não há modo na gaveta.
        montar(modos = ArrangedModes(pinned = listOf(VideoMode, PhotoMode), drawer = emptyList()))

        compose.onNodeWithText("Mais").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `em tela estreita o Mais continua alcançável rolando o seletor`() {
        // Com o Vídeo no centro, o "Mais" fica 176dp à direita e sai da vista em
        // 320dp. O carrossel rola para que o atalho da personalização (FR-17) nunca
        // fique inalcançável.
        var abriu = 0
        montar(aoAbrirGaveta = { abriu++ })

        compose.onNodeWithText("Mais").performScrollTo().performClick()

        assertEquals(1, abriu)
    }

    @Test
    fun `o eixo vertical monta com o Mais`() {
        montar(vertical = true)

        compose.onNodeWithText("Vídeo").assertIsDisplayed()
        compose.onNodeWithText("Mais").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(texto: String) =
        onAllNodes(androidx.compose.ui.test.hasText(texto)).fetchSemanticsNodes().size
}
