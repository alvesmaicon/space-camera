package com.spacecamera.presentation.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spacecamera.camera.CameraMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Requirements: NFR-5
 * Decisions: ADR-006
 *
 * Teste de fumaça da infraestrutura, não do seletor.
 *
 * O que ele prova é que **Compose roda na JVM sob Robolectric neste projeto** —
 * a técnica que a spec inteira usa como rede de segurança para recortar uma tela
 * de 2.220 linhas, e que esta base nunca tinha executado. Se esta classe passar, a
 * Tarefa 8 pode extrair o seletor com verificação automatizada em vez de olhômetro.
 *
 * Ainda usa `CameraMode.entries`, de propósito: é o seletor de **hoje**. A Tarefa 8
 * troca a fonte para o `ModeRegistry`, e é bom que este teste exista antes disso —
 * do contrário a troca não teria contra o que ser comparada.
 *
 * **Asserções só de árvore semântica** — nó existe, rótulo, clique. Nada de
 * `captureToImage`: com gráficos nativos ele estoura o tempo sob Robolectric
 * (robolectric#8071), e "tem imagem na tela" continua sendo luminância de
 * `screencap` em aparelho real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModeSelectorSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun montarSeletor(
        selecionado: CameraMode = CameraMode.VIDEO,
        vertical: Boolean = false,
        aoEscolher: (CameraMode) -> Unit = {}
    ) {
        composeTestRule.setContent {
            ModeSelector(
                modes = CameraMode.entries.toList(),
                selectedMode = selecionado,
                vertical = vertical,
                scrimColor = Color.Black.copy(alpha = 0.4f),
                onModeSelect = aoEscolher
            )
        }
    }

    @Test
    fun `os modos aparecem com seus rótulos`() {
        montarSeletor()

        composeTestRule.onNodeWithText("Vídeo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Foto").assertIsDisplayed()
    }

    @Test
    fun `tocar num modo avisa quem escolheu`() {
        var escolhido: CameraMode? = null
        montarSeletor(aoEscolher = { escolhido = it })

        composeTestRule.onNodeWithText("Foto").performClick()

        assertEquals(CameraMode.PHOTO, escolhido)
    }

    @Test
    fun `tocar no modo já ativo também avisa`() {
        // Documenta o comportamento de hoje, que custou uma rodada de medição no
        // baseline: o seletor **recentra o modo ativo**, então em Foto o rótulo
        // "Foto" está no meio e tocar nele não muda nada de visível. O callback
        // dispara mesmo assim — quem decide ignorar é o ViewModel.
        var escolhido: CameraMode? = null
        montarSeletor(selecionado = CameraMode.PHOTO, aoEscolher = { escolhido = it })

        composeTestRule.onNodeWithText("Foto").performClick()

        assertEquals(CameraMode.PHOTO, escolhido)
    }

    @Test
    fun `o eixo vertical monta sem quebrar`() {
        // O ramo de janela larga. Hoje ele assume dois modos — a premissa que a
        // Tarefa 9 remove. Aqui só se verifica que monta e responde.
        montarSeletor(vertical = true)

        composeTestRule.onNodeWithText("Vídeo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Foto").assertIsDisplayed()
    }
}
