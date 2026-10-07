package com.spacecamera.camera.mode

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Requirements: FR-1, FR-5, NFR-5
 * Decisions: ADR-001, ADR-002, ADR-006
 *
 * O registro é a razão de existir da spec, e o teste dele é **JVM pura** de
 * propósito: sem Robolectric, sem Compose, sem CameraX. Se algum dia precisar de
 * um desses, o registro deixou de ser dados e o desenho do ADR-001 se desfez.
 */
class ModeRegistryTest {

    /** Aparelho que não oferece nada além do básico. */
    private val semNada = emptySet<Capability>()

    /** Aparelho com controle manual, como o edge 60 neo do baseline. */
    private val comManual = setOf(Capability.MANUAL_SENSOR)

    // ── FR-1: o registro é a fonte única ────────────────────────────────────

    @Test
    fun `identificadores dos modos são únicos`() {
        val ids = ModeRegistry.all.map { it.id.value }
        assertEquals(ids.size, ids.toSet().size, "ids repetidos em $ids")
    }

    @Test
    fun `identificador é texto estável, nunca posição ordinal`() {
        // FR-8 / premissa P3: a preferência grava isto. Se virar ordinal, inserir
        // um modo no meio embaralha a configuração de todo mundo.
        assertEquals("video", VideoMode.id.value)
        assertEquals("photo", PhotoMode.id.value)
    }

    @Test
    fun `vídeo e foto vêm no plano por padrão`() {
        // P2: são a função principal do app; o usuário não pode se trancar fora.
        assertTrue(VideoMode.pinnedByDefault)
        assertTrue(PhotoMode.pinnedByDefault)
    }

    // ── FR-5: gate de capacidade ────────────────────────────────────────────

    @Test
    fun `modo sem capacidade exigida aparece em qualquer aparelho`() {
        val disponiveis = ModeRegistry.available(semNada)
        assertTrue(VideoMode in disponiveis)
        assertTrue(PhotoMode in disponiveis)
    }

    @Test
    fun `modo que exige capacidade ausente não aparece`() {
        // Sem depender do Pro, que só chega na Tarefa 11: o gate se testa com um
        // modo de mentira, e assim este teste continua válido quando o Pro entrar.
        val exigente = object : CameraModeDefinition() {
            override val id = CameraModeId("exigente")
            override val label = "Exigente"
            override val useCases = setOf(AppUseCase.PREVIEW)
            override val requiredCapability = Capability.MANUAL_SENSOR
            override val controls = emptyList<ControlId>()
            override val moreControls = emptyList<ControlId>()
            override val aspectRatio = AspectRatioRule.USER_SELECTED
            override val stabilization = StabilizationRule.OFF
            override val output = CaptureOutput.PHOTO
            override val overlay: OverlayId? = null
            override val pinnedByDefault = false
            override fun shutterAction(state: CaptureState) = ShutterAction.CapturePhoto
            override fun flashBehavior() = FlashBehavior.Unavailable
        }
        val registro = listOf(VideoMode, PhotoMode, exigente)

        assertFalse(exigente in ModeRegistry.available(semNada, registro))
        assertTrue(exigente in ModeRegistry.available(comManual, registro))
    }

    // ── FR-6 / FR-8: ordem e plano vindos da preferência ────────────────────

    @Test
    fun `sem preferência gravada usa a ordem e o plano do registro`() {
        // AC-8.2
        val arranjo = ModeRegistry.arranged(ModeArrangement.DEFAULT, semNada)
        assertEquals(listOf("video", "photo"), arranjo.pinned.map { it.id.value })
        assertTrue(arranjo.drawer.isEmpty())
    }

    @Test
    fun `preferência reordena o plano`() {
        // AC-7.2
        val pref = ModeArrangement(order = listOf("photo", "video"), pinned = setOf("photo", "video"))
        val arranjo = ModeRegistry.arranged(pref, semNada)
        assertEquals(listOf("photo", "video"), arranjo.pinned.map { it.id.value })
    }

    @Test
    fun `identificador desconhecido é descartado sem exceção`() {
        // AC-8.1 — o modo que não existe mais some, e os válidos mantêm a ordem gravada.
        val pref = ModeArrangement(
            order = listOf("photo", "sepia_antigo", "video"),
            pinned = setOf("photo", "video", "sepia_antigo")
        )
        val arranjo = ModeRegistry.arranged(pref, semNada)
        assertEquals(listOf("photo", "video"), arranjo.pinned.map { it.id.value })
    }

    @Test
    fun `modo disponível ausente da preferência entra no fim`() {
        // Caso 3 do fluxo 4.5 do design: preferência gravada por uma versão que não
        // conhecia este modo. Ele não pode sumir só por não estar na lista.
        val pref = ModeArrangement(order = listOf("photo"), pinned = setOf("photo"))
        val arranjo = ModeRegistry.arranged(pref, semNada)
        val todos = arranjo.pinned + arranjo.drawer
        assertEquals(setOf("photo", "video"), todos.map { it.id.value }.toSet())
        assertEquals("photo", todos.first().id.value)
    }

    @Test
    fun `modo fora do plano vai para a gaveta`() {
        // AC-6.1. O exemplo era a Foto, escrito antes de a regra de essenciais existir
        // (Tarefa 10); Foto não sai do plano (P2, AC-7.1), então o exemplo é um modo
        // removível — e o teste ao lado cobre o essencial.
        val noturno = modoDeTeste("noturno")
        val pref = ModeArrangement(order = listOf("video", "photo", "noturno"), pinned = setOf("video", "photo"))
        val arranjo = ModeRegistry.arranged(pref, semNada, listOf(VideoMode, PhotoMode, noturno))
        assertEquals(listOf("video", "photo"), arranjo.pinned.map { it.id.value })
        assertEquals(listOf("noturno"), arranjo.drawer.map { it.id.value })
    }

    @Test
    fun `Vídeo e Foto ficam no plano mesmo se a preferência os deixar de fora`() {
        // P2 contra preferência corrompida ou de outra versão.
        val pref = ModeArrangement(order = listOf("video", "photo"), pinned = setOf("video"))
        val arranjo = ModeRegistry.arranged(pref, semNada)
        assertEquals(listOf("video", "photo"), arranjo.pinned.map { it.id.value })
    }

    @Test
    fun `modo conhecido mas indisponível não aparece e não apaga a preferência`() {
        // AC-5.3 — o caso que o consenso do Gate 1 pegou. A preferência é do
        // usuário, não do hardware: some da tela, permanece gravada.
        val pref = ModeArrangement(
            order = listOf("exigente", "video", "photo"),
            pinned = setOf("exigente", "video", "photo")
        )
        val exigente = object : CameraModeDefinition() {
            override val id = CameraModeId("exigente")
            override val label = "Exigente"
            override val useCases = setOf(AppUseCase.PREVIEW)
            override val requiredCapability = Capability.MANUAL_SENSOR
            override val controls = emptyList<ControlId>()
            override val moreControls = emptyList<ControlId>()
            override val aspectRatio = AspectRatioRule.USER_SELECTED
            override val stabilization = StabilizationRule.OFF
            override val output = CaptureOutput.PHOTO
            override val overlay: OverlayId? = null
            override val pinnedByDefault = false
            override fun shutterAction(state: CaptureState) = ShutterAction.CapturePhoto
            override fun flashBehavior() = FlashBehavior.Unavailable
        }
        val registro = listOf(VideoMode, PhotoMode, exigente)

        val arranjo = ModeRegistry.arranged(pref, semNada, registro)
        assertFalse("exigente" in (arranjo.pinned + arranjo.drawer).map { it.id.value })

        // O arranjo não devolve preferência nenhuma para gravar — quem grava é a UI,
        // e ela grava o que o usuário mexeu. Este teste falha se `arranged` passar a
        // devolver uma preferência "limpa" que o chamador possa persistir por engano.
        assertEquals(
            listOf("exigente", "video", "photo"), pref.order,
            "arranged() não pode alterar a preferência recebida"
        )
    }

    @Test
    fun `preferência corrompida degrada para o padrão`() {
        // NFR-8, quarto caso: nada aproveitável na preferência.
        val arranjo = ModeRegistry.arranged(
            ModeArrangement(order = listOf("nada", "disso", "existe"), pinned = setOf("nada")),
            semNada
        )
        val todos = (arranjo.pinned + arranjo.drawer).map { it.id.value }
        assertEquals(setOf("video", "photo"), todos.toSet())
        assertTrue(arranjo.pinned.isNotEmpty(), "não pode sobrar um seletor vazio")
    }
}
