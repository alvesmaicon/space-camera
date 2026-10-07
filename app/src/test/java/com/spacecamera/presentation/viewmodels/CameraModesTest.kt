package com.spacecamera.presentation.viewmodels

import com.spacecamera.camera.ModeRejection
import com.spacecamera.camera.mode.Capability
import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.camera.mode.DeviceCapabilities
import com.spacecamera.camera.mode.ManualSensorRanges
import com.spacecamera.camera.mode.ModeArrangement
import com.spacecamera.camera.mode.PhotoMode
import com.spacecamera.camera.mode.VideoMode
import com.spacecamera.camera.mode.modoDeTeste
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Requirements: FR-5, FR-6, FR-7, FR-8, FR-16, AC-5.3, AC-7.1, AC-7.2, AC-16.1
 * Decisions: ADR-004, Q-10, Q-12
 *
 * O estado de modos, agrupado fora do ViewModel (Q-10) — e por isso testável em JVM
 * pura: guarda estado e decide, sem controller nem persistência.
 */
class CameraModesTest {

    private val pro = modoDeTeste("pro", "Pro", capability = Capability.MANUAL_SENSOR)
    private val noturno = modoDeTeste("noturno", "Noturno")
    private val registro = listOf(VideoMode, PhotoMode, pro, noturno)
    private val comManual = DeviceCapabilities(
        supported = setOf(Capability.MANUAL_SENSOR),
        manualSensor = ManualSensorRanges(iso = 100..3200, exposureTimeNs = 1_000L..1_000_000_000L, maxAnalogIso = 3200)
    )

    private fun modos(caps: DeviceCapabilities = comManual) = CameraModes(registro).apply { onCapabilities(caps) }

    private fun ids(lista: List<CameraModeDefinition>) = lista.map { it.id.value }

    // ── Arranjo e personalização ────────────────────────────────────────────

    @Test
    fun `sem preferência o arranjo é o padrão do registro`() {
        val m = modos()
        assertEquals(listOf("video", "photo"), ids(m.arranged.value.pinned))
        assertEquals(listOf("pro", "noturno"), ids(m.arranged.value.drawer))
    }

    @Test
    fun `Pro no plano e na primeira posição aparece primeiro no seletor`() {
        // AC-7.2.
        val m = modos()
        m.setPinned(pro, true)
        m.move(pro, by = -1)
        m.move(pro, by = -1)

        assertEquals(listOf("pro", "video", "photo"), ids(m.arranged.value.pinned))
    }

    @Test
    fun `Vídeo e Foto não saem do plano`() {
        // AC-7.1: a ação não é oferecida — e se alguém pedir, nada muda.
        val m = modos()
        assertFalse(m.isRemovable(VideoMode))
        assertFalse(m.isRemovable(PhotoMode))
        assertTrue(m.isRemovable(pro))

        m.setPinned(pro, true)  // personaliza: a preferência passa a citar o plano inteiro
        m.setPinned(VideoMode, false)

        assertTrue(VideoMode in m.arranged.value.pinned)
        assertTrue("video" in m.preference.value.pinned, "nem a preferência gravada pode perder o Vídeo")
    }

    @Test
    fun `reordenar num aparelho sem o Pro não tira o Pro da preferência`() {
        // AC-5.3: a preferência é do usuário, não do hardware. Reordenar aqui não pode
        // apagar nem deslocar o modo que este aparelho não oferece.
        val m = CameraModes(registro)
        m.loadPreference(ModeArrangement(order = listOf("video", "pro", "photo", "noturno"), pinned = setOf("video", "photo", "pro")))
        m.onCapabilities(DeviceCapabilities.UNKNOWN)
        assertFalse(pro in m.arranged.value.pinned + m.arranged.value.drawer)

        m.move(PhotoMode, by = -1)

        assertEquals(listOf("photo", "pro", "video", "noturno"), m.preference.value.order)
        assertTrue("pro" in m.preference.value.pinned)
        assertEquals(listOf("photo", "video"), ids(m.arranged.value.pinned))
    }

    @Test
    fun `identificador desconhecido fica na preferência e não aparece`() {
        // AC-8.1 do lado do estado: some da tela, sem exceção.
        val m = CameraModes(registro)
        m.loadPreference(ModeArrangement(order = listOf("sepia_antigo", "photo", "video"), pinned = setOf("photo", "video")))
        m.onCapabilities(comManual)

        assertEquals(listOf("photo", "video"), ids(m.arranged.value.pinned))
    }

    @Test
    fun `mover o primeiro para cima não faz nada`() {
        val m = modos()
        m.move(VideoMode, by = -1)
        assertEquals(listOf("video", "photo"), ids(m.arranged.value.pinned))
    }

    @Test
    fun `restaurar padrão volta ao registro`() {
        // AC-16.1 (a persistência é do ViewModel).
        val m = modos()
        m.setPinned(pro, true)
        m.move(pro, by = -2)

        val padrao = m.restoreDefault()

        assertEquals(ModeArrangement.DEFAULT, padrao)
        assertEquals(listOf("video", "photo"), ids(m.arranged.value.pinned))
    }

    @Test
    fun `lista da personalização segue a ordem do usuário, com os da gaveta intercalados`() {
        val m = modos()
        m.move(noturno, by = -2)  // video, noturno, photo, pro

        assertEquals(listOf("video", "noturno", "photo", "pro"), ids(m.arranged.value.ordered))
    }

    // ── Modo ativo, recusa e exposição manual ───────────────────────────────

    @Test
    fun `recusa volta ao modo restaurado e avisa o recusado`() {
        val m = modos()
        m.select(pro)

        val restaurado = m.onRejection(ModeRejection(rejected = pro.id, active = VideoMode.id))

        assertEquals(VideoMode, restaurado)
        assertEquals(VideoMode, m.active.value)
        assertEquals(pro, m.lastRejected)
    }

    @Test
    fun `ISO é limitado à faixa e ignorado sem capacidade`() {
        assertEquals(3200, modos().requestIso(6400)?.iso)
        assertNull(CameraModes(registro).apply { onCapabilities(DeviceCapabilities.UNKNOWN) }.requestIso(800))
    }

    @Test
    fun `obturador é limitado à faixa e convive com o ISO`() {
        val m = modos()
        m.requestIso(800)

        val estado = m.requestShutter(5_000_000_000L)!!

        assertEquals(800, estado.iso)
        assertEquals(250_000_000L, estado.exposureNs, "teto prático de 1/4 s (Q-13)")
    }

    @Test
    fun `trocar de modo zera o obturador manual também`() {
        val m = modos()
        m.requestShutter(10_000_000L)

        assertTrue(m.select(PhotoMode))
        assertEquals(ManualExposureState(), m.manualExposure.value)
    }

    @Test
    fun `trocar de modo zera o ISO manual e diz se havia algo a zerar`() {
        val m = modos()
        m.requestIso(800)

        assertTrue(m.select(PhotoMode))
        assertNull(m.manualExposure.value.iso)
        assertFalse(m.select(VideoMode), "sem ISO manual não há o que devolver ao AE")
    }
}
