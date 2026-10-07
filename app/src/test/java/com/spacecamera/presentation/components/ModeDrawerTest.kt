package com.spacecamera.presentation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.camera.mode.VideoMode
import com.spacecamera.camera.mode.modoDeTeste
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Requirements: FR-6, FR-17, AC-6.1
 * Decisions: ADR-004, ADR-006
 *
 * O painel "Mais modos": os modos que não estão no plano e o atalho para a
 * personalização.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModeDrawerTest {

    @get:Rule
    val compose = createComposeRule()

    private val pro = modoDeTeste("pro", "Pro")
    private val noturno = modoDeTeste("noturno", "Noturno")

    private fun montar(
        modos: List<CameraModeDefinition> = listOf(pro, noturno),
        aoEscolher: (CameraModeDefinition) -> Unit = {},
        aoEditar: () -> Unit = {}
    ) = compose.setContent {
        ModeDrawer(
            modes = modos,
            active = VideoMode,
            onModeSelect = aoEscolher,
            onEdit = aoEditar
        )
    }

    @Test
    fun `mostra os modos da gaveta`() {
        montar()

        compose.onNodeWithText("Pro").assertIsDisplayed()
        compose.onNodeWithText("Noturno").assertIsDisplayed()
    }

    @Test
    fun `escolher um modo da gaveta avisa quem escolheu`() {
        var escolhido: CameraModeDefinition? = null
        montar(aoEscolher = { escolhido = it })

        compose.onNodeWithText("Noturno").performClick()

        assertSame(noturno, escolhido)
    }

    @Test
    fun `Editar leva à personalização`() {
        // FR-17: sem o atalho, ninguém descobriria a personalização em Configurações.
        var editou = 0
        montar(aoEditar = { editou++ })

        compose.onNodeWithText("Editar").performClick()

        assertEquals(1, editou)
    }

    @Test
    fun `gaveta vazia explica e mantém o Editar`() {
        montar(modos = emptyList())

        compose.onNodeWithText("Todos os modos já estão no seletor.").assertIsDisplayed()
        compose.onNodeWithText("Editar").assertIsDisplayed()
    }
}
