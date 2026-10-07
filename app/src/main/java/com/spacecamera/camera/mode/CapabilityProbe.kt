package com.spacecamera.camera.mode

import android.hardware.camera2.CameraCharacteristics

/**
 * Requirements: FR-5, FR-13
 * Decisions: ADR-007
 *
 * O que o HAL respondeu sobre controle manual, já fora dos tipos `android.util`.
 *
 * É a fronteira dublável da sondagem: quem lê o HAL preenche isto (ver
 * `Camera2CameraInfo.sensorCharacteristics()`), e daqui para dentro só há
 * números. Os tipos Kotlin não são capricho — `android.util.Range` do android.jar
 * de stub devolve `null` nos limites fora do Robolectric, e o teste passaria sem
 * testar nada (a mesma armadilha do `Size` registrada no CLAUDE.md).
 */
data class SensorCharacteristics(
    /** `REQUEST_AVAILABLE_CAPABILITIES`, ou `null` se o HAL não respondeu. */
    val requestCapabilities: List<Int>?,
    /** `SENSOR_INFO_SENSITIVITY_RANGE` — ISO. */
    val sensitivityRange: IntRange?,
    /** `SENSOR_INFO_EXPOSURE_TIME_RANGE`, em nanossegundos. */
    val exposureTimeRangeNs: LongRange?,
    /** `SENSOR_MAX_ANALOG_SENSITIVITY` — acima dele o ganho é digital. Opcional no Camera2. */
    val maxAnalogSensitivity: Int?
)

/** Faixas que a UI do Pro oferece — exatamente as reportadas (AC-5.2). */
data class ManualSensorRanges(
    val iso: IntRange,
    val exposureTimeNs: LongRange,
    val maxAnalogIso: Int?
)

/**
 * O que este aparelho oferece, no formato que [ModeRegistry.available] consome.
 *
 * @param manualSensor presente se, e somente se, [Capability.MANUAL_SENSOR] estiver
 *   em [supported] — o Pro nunca recebe a capacidade sem as faixas.
 */
data class DeviceCapabilities(
    val supported: Set<Capability>,
    val manualSensor: ManualSensorRanges?
) {
    companion object {
        /**
         * Antes do primeiro bind. Não oferece nada: oferecer por otimismo faria um
         * modo exigente aparecer no seletor e sumir assim que a sondagem chegasse.
         */
        val UNKNOWN = DeviceCapabilities(supported = emptySet(), manualSensor = null)
    }
}

/**
 * Decide, a partir do que o HAL respondeu, quais [Capability] este aparelho
 * oferece. Função pura — a leitura do HAL fica fora, para que o caso positivo seja
 * testável na JVM; no emulador ele nunca acontece.
 */
object CapabilityProbe {

    fun decide(raw: SensorCharacteristics): DeviceCapabilities {
        val declarado = raw.requestCapabilities.orEmpty()
            .contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
        val iso = raw.sensitivityRange
        val exposicao = raw.exposureTimeRangeNs

        // O Camera2 garante as duas faixas junto com MANUAL_SENSOR. Se um HAL
        // declarar a capacidade sem elas, o Pro abriria com escala vazia — melhor o
        // modo não existir do que existir quebrado (premissa P1).
        if (!declarado || iso == null || exposicao == null) return DeviceCapabilities.UNKNOWN

        return DeviceCapabilities(
            supported = setOf(Capability.MANUAL_SENSOR),
            manualSensor = ManualSensorRanges(iso, exposicao, raw.maxAnalogSensitivity)
        )
    }
}
