package com.spacecamera.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import com.spacecamera.camera.mode.SensorCharacteristics
import timber.log.Timber

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

/** EIS e HDR de vídeo como o HAL os reporta. */
internal data class VideoFeatureSupport(val eis: Boolean, val hdr: Boolean)

/**
 * A sondagem de EIS e HDR que morava no bind do `CameraManager`, trazida para cá
 * sem mudar a regra (NFR-3 da spec de registro de modos): conferida em aparelho pelo
 * `evt=caps` idêntico antes e depois.
 *
 * HDR exige os três: scene mode HDR, tonemap de alta qualidade e, a partir do
 * Android 13, captura em 10 bits.
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class]) // ADR-005
internal fun Camera2CameraInfo.videoFeatureSupport(requestCapabilities: List<Int>?): VideoFeatureSupport {
    val stabModes = getCameraCharacteristic(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
    val oisModes = getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
    Timber.d("HAL stabilization — video modes=${stabModes?.toList()}  OIS modes=${oisModes?.toList()}")
    val eis = stabModes?.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true

    val hasHdrSceneMode = getCameraCharacteristic(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)
        ?.contains(CaptureRequest.CONTROL_SCENE_MODE_HDR) == true
    val hasHighQualityToneMap = getCameraCharacteristic(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)
        ?.contains(CaptureRequest.TONEMAP_MODE_HIGH_QUALITY) == true
    val hasTenBitCapability = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        requestCapabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT) == true
    val hdr = hasHdrSceneMode && hasHighQualityToneMap && hasTenBitCapability
    Timber.d("HDR support: scene=$hasHdrSceneMode tonemapHQ=$hasHighQualityToneMap tenBit=$hasTenBitCapability => supported=$hdr")
    return VideoFeatureSupport(eis = eis, hdr = hdr)
}
