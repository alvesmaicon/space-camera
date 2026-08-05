package com.spacecamera.camera

import android.net.Uri
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Dublê de [CameraController] para os testes de ViewModel na JVM.
 *
 * Grava as chamadas recebidas e deixa os `StateFlow` publicamente mutáveis, para
 * que o teste simule o que o hardware responderia — "este aparelho não suporta
 * EIS", "só existe HD 30" — sem precisar do aparelho.
 */
class FakeCameraController : CameraController {

    // ── Estado, mutável pelo teste ──────────────────────────────────────────

    val recordingStateFlow = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val recordingState: StateFlow<RecordingState> = recordingStateFlow

    val isCameraReadyFlow = MutableStateFlow(false)
    override val isCameraReady: StateFlow<Boolean> = isCameraReadyFlow

    /** Default `true` para não interferir em testes que não são sobre EIS. */
    val isEisSupportedFlow = MutableStateFlow(true)
    override val isEisSupported: StateFlow<Boolean> = isEisSupportedFlow

    val isHdrSupportedFlow = MutableStateFlow(true)
    override val isHdrSupported: StateFlow<Boolean> = isHdrSupportedFlow

    val availableVideoOptionsFlow = MutableStateFlow<List<VideoOption>>(emptyList())
    override val availableVideoOptions: StateFlow<List<VideoOption>> = availableVideoOptionsFlow

    val availableAspectRatiosFlow = MutableStateFlow(listOf("9:16", "3:4"))
    override val availableAspectRatios: StateFlow<List<String>> = availableAspectRatiosFlow

    val availableZoomLevelsFlow = MutableStateFlow(listOf(1f))
    override val availableZoomLevels: StateFlow<List<Float>> = availableZoomLevelsFlow

    val selectedZoomLevelFlow = MutableStateFlow(1f)
    override val selectedZoomLevel: StateFlow<Float> = selectedZoomLevelFlow

    val minZoomRatioFlow = MutableStateFlow(1f)
    override val minZoomRatio: StateFlow<Float> = minZoomRatioFlow

    val maxZoomRatioFlow = MutableStateFlow(1f)
    override val maxZoomRatio: StateFlow<Float> = maxZoomRatioFlow

    val exposureIndexFlow = MutableStateFlow(0)
    override val exposureIndex: StateFlow<Int> = exposureIndexFlow

    val exposureMinFlow = MutableStateFlow(-8)
    override val exposureMin: StateFlow<Int> = exposureMinFlow

    val exposureMaxFlow = MutableStateFlow(8)
    override val exposureMax: StateFlow<Int> = exposureMaxFlow

    val lastVideoUriFlow = MutableStateFlow<Uri?>(null)
    override val lastVideoUri: StateFlow<Uri?> = lastVideoUriFlow

    val lastPhotoUriFlow = MutableStateFlow<Uri?>(null)
    override val lastPhotoUri: StateFlow<Uri?> = lastPhotoUriFlow

    // ── Ajustes ─────────────────────────────────────────────────────────────

    override var isStabilizationEnabled: Boolean = true
    override var isNoiseReductionEnabled: Boolean = true
    override var isHdrEnabled: Boolean = false
    override var bitratePreset: VideoBitratePreset = VideoBitratePreset.MEDIUM
    override var photoQualityPreset: PhotoQualityPreset = PhotoQualityPreset.MAXIMA
    override var isFrontCameraMirrorEnabled: Boolean = false
    override var isSaveLocationEnabled: Boolean = false
    override var isImageEnhancementEnabled: Boolean = false
    override var previewAspectLabel: String = "9:16"

    // ── Chamadas registradas ────────────────────────────────────────────────

    var initializeCount = 0; private set
    var releaseCount = 0; private set
    var rebindCount = 0; private set
    var surfaceProviderUpdates = 0; private set
    var flipCount = 0; private set
    var startRecordingCount = 0; private set
    var pauseRecordingCount = 0; private set
    var resumeRecordingCount = 0; private set
    var stopRecordingCount = 0; private set
    var takePhotoCount = 0; private set

    var lastMicEnabled: Boolean? = null; private set
    var lastTargetRotation: Int? = null; private set
    var lastVideoOption: VideoOption? = null; private set
    var lastAspectRatio: String? = null; private set
    var lastCameraMode: CameraMode? = null; private set
    var lastZoomLevel: Float? = null; private set
    var lastExposureIndex: Int? = null; private set
    var lastTorchEnabled: Boolean? = null; private set
    var lastPhotoFlashMode: Int? = null; private set

    /** Histórico de `setStabilization`, para checar a ordem das transições. */
    val stabilizationCalls = mutableListOf<Boolean>()
    val hdrCalls = mutableListOf<Boolean>()
    val noiseReductionCalls = mutableListOf<Boolean>()

    // ── Implementação ───────────────────────────────────────────────────────

    override suspend fun initializeCamera(surfaceProvider: Preview.SurfaceProvider?) {
        initializeCount++
    }

    override fun updateSurfaceProvider(newSurfaceProvider: Preview.SurfaceProvider) {
        surfaceProviderUpdates++
    }
    override fun rebindWithCurrentSettings() { rebindCount++ }
    override fun release() { releaseCount++ }

    override fun setVideoOption(option: VideoOption) { lastVideoOption = option }
    override fun setAspectRatio(ratio: String) { lastAspectRatio = ratio }
    override fun setCameraMode(mode: CameraMode) { lastCameraMode = mode }

    override fun setStabilization(enabled: Boolean) {
        isStabilizationEnabled = enabled
        stabilizationCalls += enabled
    }

    override fun setNoiseReduction(enabled: Boolean) {
        isNoiseReductionEnabled = enabled
        noiseReductionCalls += enabled
    }

    override fun setHdr(enabled: Boolean) {
        isHdrEnabled = enabled
        hdrCalls += enabled
    }

    override fun updateBitratePreset(preset: VideoBitratePreset) { bitratePreset = preset }
    override fun applyPhotoQualityPreset(preset: PhotoQualityPreset) { photoQualityPreset = preset }
    override fun flipCamera() { flipCount++ }

    override fun setZoomLevel(ratio: Float) { lastZoomLevel = ratio }
    override fun setExposureCompensationIndex(index: Int) { lastExposureIndex = index }
    override fun tapToFocus(action: FocusMeteringAction, onFocusAcquired: () -> Unit) {
        onFocusAcquired()
    }
    override fun cancelFocusLock() = Unit

    override fun startRecording(micEnabled: Boolean, targetRotation: Int) {
        startRecordingCount++
        lastMicEnabled = micEnabled
        lastTargetRotation = targetRotation
        recordingStateFlow.value = RecordingState.Recording
    }

    override fun pauseRecording() {
        pauseRecordingCount++
        recordingStateFlow.value = RecordingState.Paused
    }

    override fun resumeRecording() {
        resumeRecordingCount++
        recordingStateFlow.value = RecordingState.Recording
    }

    override fun stopRecording() {
        stopRecordingCount++
        recordingStateFlow.value = RecordingState.Idle
    }

    override fun takePhoto(targetRotation: Int, onSaved: (Uri) -> Unit, onError: (String) -> Unit) {
        takePhotoCount++
        lastTargetRotation = targetRotation
    }

    override fun toggleFlash(enable: Boolean) { lastTorchEnabled = enable }
    override fun setPhotoFlashMode(mode: Int) { lastPhotoFlashMode = mode }
}
