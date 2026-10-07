package com.spacecamera.camera

import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import com.spacecamera.camera.mode.SensorCharacteristics

/**
 * Requirements: FR-5, FR-13
 * Decisions: ADR-005, ADR-007
 *
 * O lado HAL da sondagem de controle manual: lê as quatro chaves e converte para
 * tipos Kotlin. Fica de propósito sem lógica nenhuma — a decisão mora em
 * [com.spacecamera.camera.mode.CapabilityProbe], que é testável na JVM; isto aqui
 * só se verifica em aparelho real, porque o emulador não reporta `MANUAL_SENSOR`.
 *
 * Arquivo próprio, e não dentro do `CameraManager`, pelo NFR-3: aquele arquivo não
 * pode ganhar linha líquida nesta spec.
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class]) // ADR-005
internal fun Camera2CameraInfo.sensorCharacteristics() = SensorCharacteristics(
    requestCapabilities = getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        ?.toList(),
    sensitivityRange = getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        ?.let { it.lower..it.upper },
    exposureTimeRangeNs = getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        ?.let { it.lower..it.upper },
    maxAnalogSensitivity = getCameraCharacteristic(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY)
)
