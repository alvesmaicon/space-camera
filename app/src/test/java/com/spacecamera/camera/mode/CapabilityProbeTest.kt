package com.spacecamera.camera.mode

import android.hardware.camera2.CameraCharacteristics
import com.spacecamera.camera.TelemetryFields
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Requirements: FR-5, FR-13, NFR-7
 * Decisions: ADR-007
 *
 * A decisão "este aparelho oferece controle manual?" testada sem aparelho. A
 * leitura do HAL fica do outro lado de [SensorCharacteristics]; aqui entram só
 * números, que é o que permite exercitar o caso positivo na JVM — no emulador ele
 * nunca acontece, e o comportamento certo e o defeituoso coincidiriam.
 *
 * Os números do caso positivo são os do edge 60 neo no baseline da Tarefa 1.
 */
class CapabilityProbeTest {

    private val manualSensor = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
    private val backwardCompatible = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE

    /** O que o edge 60 neo reportou: ISO 100–19200, analógico até 4480, 1/10000 s a 1/2,5 s. */
    private val edge60Neo = SensorCharacteristics(
        requestCapabilities = listOf(backwardCompatible, manualSensor),
        sensitivityRange = 100..19200,
        exposureTimeRangeNs = 100_000L..400_000_000L,
        maxAnalogSensitivity = 4480
    )

    /** Aparelho básico, como a câmera virtual do emulador. */
    private val semManual = SensorCharacteristics(
        requestCapabilities = listOf(backwardCompatible),
        sensitivityRange = null,
        exposureTimeRangeNs = null,
        maxAnalogSensitivity = null
    )

    // ── Decisão ─────────────────────────────────────────────────────────────

    @Test
    fun `aparelho que reporta MANUAL_SENSOR oferece a capacidade com as duas faixas`() {
        val caps = CapabilityProbe.decide(edge60Neo)

        assertTrue(Capability.MANUAL_SENSOR in caps.supported)
        val faixas = caps.manualSensor!!
        assertEquals(100..19200, faixas.iso)
        assertEquals(100_000L..400_000_000L, faixas.exposureTimeNs)
        assertEquals(4480, faixas.maxAnalogIso)
    }

    @Test
    fun `aparelho sem MANUAL_SENSOR não oferece a capacidade nem faixa nenhuma`() {
        val caps = CapabilityProbe.decide(semManual)

        assertFalse(Capability.MANUAL_SENSOR in caps.supported)
        assertNull(caps.manualSensor)
    }

    @Test
    fun `faixas reportadas sem declarar MANUAL_SENSOR não oferecem a capacidade`() {
        // Aparelho LIMITED costuma reportar a faixa de ISO sem aceitar controle
        // manual. Quem decide é a capacidade declarada, não a presença das faixas —
        // sem este caso, tirar a checagem da capacidade passava em todos os testes.
        val limited = edge60Neo.copy(requestCapabilities = listOf(backwardCompatible))

        assertFalse(Capability.MANUAL_SENSOR in CapabilityProbe.decide(limited).supported)
        assertNull(CapabilityProbe.decide(limited).manualSensor)
    }

    @Test
    fun `HAL que não responde a lista de capacidades conta como sem suporte`() {
        val caps = CapabilityProbe.decide(semManual.copy(requestCapabilities = null))

        assertTrue(caps.supported.isEmpty())
        assertNull(caps.manualSensor)
    }

    @Test
    fun `MANUAL_SENSOR sem faixa de ISO ou de exposição não é oferecido`() {
        // O Camera2 garante as faixas junto com a capacidade, mas um HAL que mente
        // deixaria o Pro com escala vazia. Melhor o modo não existir do que existir
        // quebrado — mesma regra da premissa P1.
        val semIso = edge60Neo.copy(sensitivityRange = null)
        val semExposicao = edge60Neo.copy(exposureTimeRangeNs = null)

        assertFalse(Capability.MANUAL_SENSOR in CapabilityProbe.decide(semIso).supported)
        assertFalse(Capability.MANUAL_SENSOR in CapabilityProbe.decide(semExposicao).supported)
        assertNull(CapabilityProbe.decide(semIso).manualSensor)
    }

    @Test
    fun `limite analógico ausente não derruba a capacidade`() {
        // SENSOR_MAX_ANALOG_SENSITIVITY é opcional no Camera2: sem ele a escala só
        // não marca onde o ganho vira digital.
        val caps = CapabilityProbe.decide(edge60Neo.copy(maxAnalogSensitivity = null))

        assertTrue(Capability.MANUAL_SENSOR in caps.supported)
        assertNull(caps.manualSensor!!.maxAnalogIso)
    }

    @Test
    fun `o resultado alimenta o gate do registro`() {
        // Passo 2 da tarefa: o formato publicado é o que available() consome.
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

        val comPro = ModeRegistry.available(CapabilityProbe.decide(edge60Neo).supported, registro)
        val semPro = ModeRegistry.available(CapabilityProbe.decide(semManual).supported, registro)

        assertTrue(exigente in comPro)
        assertFalse(exigente in semPro)
    }

    @Test
    fun `antes da sondagem nada é oferecido`() {
        // O controller publica isto até o primeiro bind. Oferecer o Pro "por
        // otimismo" faria o modo piscar no seletor e sumir.
        assertTrue(DeviceCapabilities.UNKNOWN.supported.isEmpty())
        assertNull(DeviceCapabilities.UNKNOWN.manualSensor)
    }

    // ── Telemetria (FR-13, NFR-7) ───────────────────────────────────────────

    @Test
    fun `evt=caps traz manual_sensor=true com as duas faixas`() {
        val campos = TelemetryFields.manualSensorFields(CapabilityProbe.decide(edge60Neo))

        assertEquals(
            "manual_sensor=true iso=100-19200 iso_analog_max=4480 exposure_ns=100000-400000000",
            campos
        )
    }

    @Test
    fun `evt=caps traz manual_sensor=false e nenhuma faixa`() {
        val campos = TelemetryFields.manualSensorFields(CapabilityProbe.decide(semManual))

        assertEquals("manual_sensor=false", campos)
    }

    @Test
    fun `limite analógico ausente sai como traço, sem sumir o campo`() {
        // Campo fixo deixa o grep/awk previsível: quem fatia por posição não quebra.
        val campos = TelemetryFields.manualSensorFields(
            CapabilityProbe.decide(edge60Neo.copy(maxAnalogSensitivity = null))
        )

        assertTrue("iso_analog_max=- " in campos, campos)
    }

    @Test
    fun `campos novos de evt=caps só têm nomes e números`() {
        // NFR-7: nada de caminho, nome de arquivo ou dado do usuário.
        val linhas = listOf(edge60Neo, semManual).map {
            TelemetryFields.manualSensorFields(CapabilityProbe.decide(it))
        }
        linhas.forEach { assertTrue(Regex("^[a-z_]+=[a-z0-9-]+( [a-z_]+=[a-z0-9-]+)*$").matches(it), it) }
    }
}
