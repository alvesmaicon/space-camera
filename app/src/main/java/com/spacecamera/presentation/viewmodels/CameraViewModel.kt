package com.spacecamera.presentation.viewmodels

import android.content.Context
import android.net.Uri
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.camera.video.Quality
import com.spacecamera.camera.CameraManager
import com.spacecamera.camera.CameraMode
import com.spacecamera.camera.PhotoQualityPreset
import com.spacecamera.camera.RecordingState
import com.spacecamera.camera.VideoBitratePreset
import com.spacecamera.camera.VideoOption
import com.spacecamera.data.repository.VideoRepositoryImpl
import com.spacecamera.data.storage.SettingsStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RecordingDelay(val seconds: Int) {
    OFF(0), THREE(3), FIVE(5), TEN(10);
    fun next() = entries[(ordinal + 1) % entries.size]
}

enum class PhotoFlashMode { OFF, AUTO, ON }

class CameraViewModel : ViewModel() {

    private var cameraManager: CameraManager? = null
    private var videoRepository: VideoRepositoryImpl? = null
    private var timerJob: Job? = null
    private var countdownJob: Job? = null
    private var settingsStorage: SettingsStorage? = null

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _recordingDelay = MutableStateFlow(RecordingDelay.OFF)
    val recordingDelay: StateFlow<RecordingDelay> = _recordingDelay.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(0)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private val _selectedVideoOption = MutableStateFlow(VideoOption(Quality.FHD, 30))
    val selectedVideoOption: StateFlow<VideoOption> = _selectedVideoOption.asStateFlow()

    private val _availableVideoOptions = MutableStateFlow<List<VideoOption>>(
        listOf(VideoOption(Quality.FHD, 30), VideoOption(Quality.HD, 30))
    )
    val availableVideoOptions: StateFlow<List<VideoOption>> = _availableVideoOptions.asStateFlow()

    private val _cameraInitialized = MutableStateFlow(false)
    val cameraInitialized: StateFlow<Boolean> = _cameraInitialized.asStateFlow()

    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    private val _photoFlashMode = MutableStateFlow(PhotoFlashMode.OFF)
    val photoFlashMode: StateFlow<PhotoFlashMode> = _photoFlashMode.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isGridEnabled = MutableStateFlow(false)
    val isGridEnabled: StateFlow<Boolean> = _isGridEnabled.asStateFlow()

    private val _isLevelEnabled = MutableStateFlow(false)
    val isLevelEnabled: StateFlow<Boolean> = _isLevelEnabled.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _selectedAspectRatio = MutableStateFlow("9:16")
    val selectedAspectRatio: StateFlow<String> = _selectedAspectRatio.asStateFlow()

    private val _availableAspectRatios = MutableStateFlow<List<String>>(listOf("9:16", "3:4"))
    val availableAspectRatios: StateFlow<List<String>> = _availableAspectRatios.asStateFlow()

    private val _lastVideoUri = MutableStateFlow<Uri?>(null)
    val lastVideoUri: StateFlow<Uri?> = _lastVideoUri.asStateFlow()

    private val _lastPhotoUri = MutableStateFlow<Uri?>(null)
    val lastPhotoUri: StateFlow<Uri?> = _lastPhotoUri.asStateFlow()

    private val _cameraMode = MutableStateFlow(CameraMode.VIDEO)
    val cameraMode: StateFlow<CameraMode> = _cameraMode.asStateFlow()

    private val _availableZoomLevels = MutableStateFlow<List<Float>>(listOf(1f))
    val availableZoomLevels: StateFlow<List<Float>> = _availableZoomLevels.asStateFlow()

    private val _selectedZoomLevel = MutableStateFlow(1f)
    val selectedZoomLevel: StateFlow<Float> = _selectedZoomLevel.asStateFlow()

    private val _minZoomRatio = MutableStateFlow(1f)
    val minZoomRatio: StateFlow<Float> = _minZoomRatio.asStateFlow()

    private val _maxZoomRatio = MutableStateFlow(1f)
    val maxZoomRatio: StateFlow<Float> = _maxZoomRatio.asStateFlow()

    private val _isStabilizationEnabled = MutableStateFlow(true)
    val isStabilizationEnabled: StateFlow<Boolean> = _isStabilizationEnabled.asStateFlow()

    private val _isEisSupported = MutableStateFlow(false)
    val isEisSupported: StateFlow<Boolean> = _isEisSupported.asStateFlow()

    private val _isNoiseReductionEnabled = MutableStateFlow(true)
    val isNoiseReductionEnabled: StateFlow<Boolean> = _isNoiseReductionEnabled.asStateFlow()

    private val _isHdrEnabled = MutableStateFlow(false)
    val isHdrEnabled: StateFlow<Boolean> = _isHdrEnabled.asStateFlow()

    private val _isHdrSupported = MutableStateFlow(true)
    val isHdrSupported: StateFlow<Boolean> = _isHdrSupported.asStateFlow()

    private val _bitratePreset = MutableStateFlow(VideoBitratePreset.MEDIUM)
    val bitratePreset: StateFlow<VideoBitratePreset> = _bitratePreset.asStateFlow()

    private val _photoQualityPreset = MutableStateFlow(PhotoQualityPreset.MAXIMA)
    val photoQualityPreset: StateFlow<PhotoQualityPreset> = _photoQualityPreset.asStateFlow()

    private val _isTapToFocusEnabled = MutableStateFlow(true)
    val isTapToFocusEnabled: StateFlow<Boolean> = _isTapToFocusEnabled.asStateFlow()

    private val _isSaveLocationEnabled = MutableStateFlow(false)
    val isSaveLocationEnabled: StateFlow<Boolean> = _isSaveLocationEnabled.asStateFlow()

    private val _isImageEnhancementEnabled = MutableStateFlow(false)
    val isImageEnhancementEnabled: StateFlow<Boolean> = _isImageEnhancementEnabled.asStateFlow()

    private val _isFrontCameraMirrorEnabled = MutableStateFlow(false)
    val isFrontCameraMirrorEnabled: StateFlow<Boolean> = _isFrontCameraMirrorEnabled.asStateFlow()

    private val _isKeepSettingsEnabled = MutableStateFlow(true)
    val isKeepSettingsEnabled: StateFlow<Boolean> = _isKeepSettingsEnabled.asStateFlow()

    private val _exposureIndex = MutableStateFlow(0)
    val exposureIndex: StateFlow<Int> = _exposureIndex.asStateFlow()

    private val _exposureMin = MutableStateFlow(-8)
    val exposureMin: StateFlow<Int> = _exposureMin.asStateFlow()

    private val _exposureMax = MutableStateFlow(8)
    val exposureMax: StateFlow<Int> = _exposureMax.asStateFlow()

    private val _isCameraReady = MutableStateFlow(false)
    val isCameraReady: StateFlow<Boolean> = _isCameraReady.asStateFlow()

    suspend fun initializeCamera(context: Context, lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider?) {
        try {
            if (cameraManager == null) {
                val ctx = context.applicationContext
                // Carrega configurações persistidas antes de criar o CameraManager
                if (settingsStorage == null) settingsStorage = SettingsStorage(ctx)
                val storage = settingsStorage!!
                // isKeepSettings é sempre carregado
                _isKeepSettingsEnabled.value = storage.isKeepSettingsEnabled
                if (storage.isKeepSettingsEnabled) {
                    _isStabilizationEnabled.value = storage.isStabilizationEnabled
                    _isNoiseReductionEnabled.value = storage.isNoiseReductionEnabled
                    _isGridEnabled.value = storage.isGridEnabled
                    _isLevelEnabled.value = storage.isLevelEnabled
                    _isMicMuted.value = storage.isMicMuted
                    _bitratePreset.value = storage.bitratePreset
                    _photoQualityPreset.value = storage.photoQualityPreset
                    _isHdrEnabled.value = storage.isHdrEnabled
                    _isTapToFocusEnabled.value = storage.isTapToFocusEnabled
                    _isSaveLocationEnabled.value = storage.isSaveLocationEnabled
                    _isFrontCameraMirrorEnabled.value = storage.isFrontCameraMirrorEnabled
                    _isImageEnhancementEnabled.value = storage.isImageEnhancementEnabled
                } else {
                    // Manter configurações desativado: resetar para padrões e salvar
                    _isStabilizationEnabled.value = true
                    _isNoiseReductionEnabled.value = true
                    _isGridEnabled.value = false
                    _isLevelEnabled.value = false
                    _isMicMuted.value = false
                    _bitratePreset.value = VideoBitratePreset.MEDIUM
                    _photoQualityPreset.value = PhotoQualityPreset.MAXIMA
                    _isHdrEnabled.value = false
                    _isTapToFocusEnabled.value = true
                    _isSaveLocationEnabled.value = false
                    _isFrontCameraMirrorEnabled.value = false
                    storage.isStabilizationEnabled = true
                    storage.isNoiseReductionEnabled = true
                    storage.isGridEnabled = false
                    storage.isLevelEnabled = false
                    storage.isMicMuted = false
                    storage.bitratePreset = VideoBitratePreset.MEDIUM
                    storage.photoQualityPreset = PhotoQualityPreset.MAXIMA
                    storage.isHdrEnabled = false
                    storage.isTapToFocusEnabled = true
                    storage.isSaveLocationEnabled = false
                    storage.isFrontCameraMirrorEnabled = false
                    _isImageEnhancementEnabled.value = false
                    storage.isImageEnhancementEnabled = false
                }
                cameraManager = CameraManager(ctx, lifecycleOwner).also { mgr ->
                    mgr.isStabilizationEnabled = _isStabilizationEnabled.value
                    mgr.isNoiseReductionEnabled = _isNoiseReductionEnabled.value
                    mgr.bitratePreset = _bitratePreset.value
                    mgr.photoQualityPreset = _photoQualityPreset.value
                    mgr.isHdrEnabled = _isHdrEnabled.value
                    mgr.isFrontCameraMirrorEnabled = _isFrontCameraMirrorEnabled.value
                    mgr.isSaveLocationEnabled = _isSaveLocationEnabled.value
                    mgr.isImageEnhancementEnabled = _isImageEnhancementEnabled.value
                }
                videoRepository = VideoRepositoryImpl(cameraManager!!)
                viewModelScope.launch {
                    cameraManager!!.recordingState.collect { state ->
                        _recordingState.value = state
                        when (state) {
                            is RecordingState.Recording -> startTimer()
                            is RecordingState.Paused -> pauseTimer()
                            is RecordingState.Idle -> stopTimer()
                        }
                    }
                }
            }
            cameraManager!!.initializeCamera(surfaceProvider)
            _cameraInitialized.value = true
            // Sync available video options from CameraManager
            viewModelScope.launch {
                cameraManager!!.availableVideoOptions.collect { list ->
                    if (list.isNotEmpty()) {
                        _availableVideoOptions.value = list
                        if (_selectedVideoOption.value !in list) {
                            _selectedVideoOption.value = list.first()
                        }
                    }
                }
            }
            // Sync available aspect ratios from CameraManager
            viewModelScope.launch {
                cameraManager!!.availableAspectRatios.collect { list ->
                    if (list.isNotEmpty()) {
                        _availableAspectRatios.value = list
                        // "Full" é especial (só foto) e não está na lista dinâmica — não resetar
                        if (_selectedAspectRatio.value !in list && _selectedAspectRatio.value != "Full") {
                            _selectedAspectRatio.value = list.first()
                        }
                    }
                }
            }
            // Sync last video URI
            viewModelScope.launch {
                cameraManager!!.lastVideoUri.collect { uri ->
                    _lastVideoUri.value = uri
                }
            }
            // Sync last photo URI
            viewModelScope.launch {
                cameraManager!!.lastPhotoUri.collect { uri ->
                    _lastPhotoUri.value = uri
                }
            }
            // Sync zoom levels
            viewModelScope.launch {
                cameraManager!!.availableZoomLevels.collect { levels ->
                    _availableZoomLevels.value = levels
                }
            }
            viewModelScope.launch {
                cameraManager!!.selectedZoomLevel.collect { ratio ->
                    _selectedZoomLevel.value = ratio
                }
            }
            viewModelScope.launch {
                cameraManager!!.minZoomRatio.collect { ratio ->
                    _minZoomRatio.value = ratio
                }
            }
            viewModelScope.launch {
                cameraManager!!.maxZoomRatio.collect { ratio ->
                    _maxZoomRatio.value = ratio
                }
            }
            viewModelScope.launch {
                cameraManager!!.isEisSupported.collect { supported ->
                    _isEisSupported.value = supported
                    // Se não suportado, garante que EIS fique desligado
                    if (!supported && _isStabilizationEnabled.value) {
                        _isStabilizationEnabled.value = false
                        settingsStorage?.isStabilizationEnabled = false
                        cameraManager!!.setStabilization(false)
                    }
                }
            }
            viewModelScope.launch {
                cameraManager!!.isHdrSupported.collect { supported ->
                    _isHdrSupported.value = supported
                    if (!supported && _isHdrEnabled.value) {
                        _isHdrEnabled.value = false
                        settingsStorage?.isHdrEnabled = false
                        cameraManager!!.setHdr(false)
                    }
                }
            }
            viewModelScope.launch {
                cameraManager!!.exposureIndex.collect { _exposureIndex.value = it }
            }
            viewModelScope.launch {
                cameraManager!!.exposureMin.collect { _exposureMin.value = it }
            }
            viewModelScope.launch {
                cameraManager!!.exposureMax.collect { _exposureMax.value = it }
            }
            viewModelScope.launch {
                cameraManager!!.isCameraReady.collect { _isCameraReady.value = it }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _cameraInitialized.value = false
        }
    }

    fun updateSurfaceProvider(surfaceProvider: Preview.SurfaceProvider) {
        cameraManager?.updateSurfaceProvider(surfaceProvider)
    }

    fun rebindCamera() {
        cameraManager?.rebindWithCurrentSettings()
    }

    fun startRecording(targetRotation: Int = 0) {
        viewModelScope.launch {
            videoRepository?.startRecording(micEnabled = !_isMicMuted.value, targetRotation = targetRotation)
        }
    }

    fun startRecordingWithDelay(targetRotation: Int = 0) {
        if (_recordingDelay.value == RecordingDelay.OFF) {
            startRecording(targetRotation)
            return
        }
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = _recordingDelay.value.seconds
            _countdownSeconds.value = remaining
            while (remaining > 0) {
                delay(1000)
                remaining--
                _countdownSeconds.value = remaining
            }
            countdownJob = null
            startRecording(targetRotation)
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _countdownSeconds.value = 0
    }

    fun cycleRecordingDelay() {
        _recordingDelay.value = _recordingDelay.value.next()
    }

    fun pauseRecording() {
        viewModelScope.launch { videoRepository?.pauseRecording() }
    }

    fun resumeRecording() {
        viewModelScope.launch { videoRepository?.resumeRecording() }
    }

    fun stopRecording() {
        viewModelScope.launch { videoRepository?.stopRecording() }
    }

    fun setVideoOption(option: VideoOption) {
        _selectedVideoOption.value = option
        cameraManager?.setVideoOption(option)
    }

    fun toggleFlash() {
        if (_cameraMode.value == CameraMode.VIDEO) {
            _isFlashOn.value = !_isFlashOn.value
            cameraManager?.toggleFlash(_isFlashOn.value)
        } else {
            // Foto: cicla OFF → AUTO → ON → OFF sem tocha contínua
            val next = when (_photoFlashMode.value) {
                PhotoFlashMode.OFF -> PhotoFlashMode.AUTO
                PhotoFlashMode.AUTO -> PhotoFlashMode.ON
                PhotoFlashMode.ON -> PhotoFlashMode.OFF
            }
            _photoFlashMode.value = next
            val flashMode = when (next) {
                PhotoFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                PhotoFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                PhotoFlashMode.ON -> ImageCapture.FLASH_MODE_ON
            }
            cameraManager?.setPhotoFlashMode(flashMode)
        }
    }

    fun resetFlash() {
        if (_isFlashOn.value) {
            _isFlashOn.value = false
            cameraManager?.toggleFlash(false)
        }
    }

    fun setCameraMode(mode: CameraMode) {
        if (_recordingState.value != RecordingState.Idle) return
        _cameraMode.value = mode
        resetFlash()
        // Desativa EIS no modo Foto (não se aplica a captura de imagem)
        // Ao voltar para Vídeo, restaura o valor persistido no storage
        if (mode == CameraMode.PHOTO) {
            if (_isStabilizationEnabled.value) {
                _isStabilizationEnabled.value = false
                cameraManager?.setStabilization(false)
            }
        } else if (mode == CameraMode.VIDEO) {
            val stored = settingsStorage?.isStabilizationEnabled ?: false
            if (stored != _isStabilizationEnabled.value) {
                _isStabilizationEnabled.value = stored
                cameraManager?.setStabilization(stored)
            }
        }
        // Rebind com aspect ratio correto para o modo (vídeo sempre 9:16)
        cameraManager?.setCameraMode(mode)
    }

    fun takePhoto(targetRotation: Int = android.view.Surface.ROTATION_0) {
        cameraManager?.takePhoto(
            targetRotation = targetRotation,
            onSaved = { /* _lastPhotoUri já sincronizado via flow */ },
            onError = { /* silently log; UI não precisa de feedback de erro por ora */ }
        )
    }

    fun takePhotoWithDelay(targetRotation: Int = android.view.Surface.ROTATION_0) {
        if (_recordingDelay.value == RecordingDelay.OFF) {
            takePhoto(targetRotation)
            return
        }
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = _recordingDelay.value.seconds
            _countdownSeconds.value = remaining
            while (remaining > 0) {
                delay(1000)
                remaining--
                _countdownSeconds.value = remaining
            }
            countdownJob = null
            takePhoto(targetRotation)
        }
    }

    fun toggleMic() {
        _isMicMuted.value = !_isMicMuted.value
        settingsStorage?.isMicMuted = _isMicMuted.value
    }

    fun toggleGrid() {
        _isGridEnabled.value = !_isGridEnabled.value
        settingsStorage?.isGridEnabled = _isGridEnabled.value
    }

    fun toggleLevel() {
        _isLevelEnabled.value = !_isLevelEnabled.value
        settingsStorage?.isLevelEnabled = _isLevelEnabled.value
    }

    fun flipCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
        // Flash não funciona na câmera frontal
        if (_isFrontCamera.value && _isFlashOn.value) {
            _isFlashOn.value = false
        }
        cameraManager?.flipCamera()
    }

    fun setZoomLevel(ratio: Float) {
        _selectedZoomLevel.value = ratio
        cameraManager?.setZoomLevel(ratio)
    }

    fun setAspectRatio(ratio: String) {
        _selectedAspectRatio.value = ratio
        cameraManager?.setAspectRatio(ratio)
    }

    fun toggleStabilization() {
        _isStabilizationEnabled.value = !_isStabilizationEnabled.value
        cameraManager?.setStabilization(_isStabilizationEnabled.value)
        settingsStorage?.isStabilizationEnabled = _isStabilizationEnabled.value
    }

    fun toggleNoiseReduction() {
        _isNoiseReductionEnabled.value = !_isNoiseReductionEnabled.value
        cameraManager?.setNoiseReduction(_isNoiseReductionEnabled.value)
        settingsStorage?.isNoiseReductionEnabled = _isNoiseReductionEnabled.value
    }

    fun toggleHdr() {
        if (!_isHdrSupported.value) {
            _isHdrEnabled.value = false
            settingsStorage?.isHdrEnabled = false
            cameraManager?.setHdr(false)
            return
        }
        _isHdrEnabled.value = !_isHdrEnabled.value
        cameraManager?.setHdr(_isHdrEnabled.value)
        settingsStorage?.isHdrEnabled = _isHdrEnabled.value
    }

    fun setBitratePreset(preset: VideoBitratePreset) {
        _bitratePreset.value = preset
        settingsStorage?.bitratePreset = preset
        cameraManager?.updateBitratePreset(preset)
    }

    fun setPhotoQualityPreset(preset: PhotoQualityPreset) {
        _photoQualityPreset.value = preset
        settingsStorage?.photoQualityPreset = preset
        cameraManager?.applyPhotoQualityPreset(preset)
    }

    fun toggleTapToFocus() {
        _isTapToFocusEnabled.value = !_isTapToFocusEnabled.value
        settingsStorage?.isTapToFocusEnabled = _isTapToFocusEnabled.value
    }

    fun toggleSaveLocation() {
        _isSaveLocationEnabled.value = !_isSaveLocationEnabled.value
        settingsStorage?.isSaveLocationEnabled = _isSaveLocationEnabled.value
        cameraManager?.isSaveLocationEnabled = _isSaveLocationEnabled.value
    }

    fun toggleImageEnhancement() {
        _isImageEnhancementEnabled.value = !_isImageEnhancementEnabled.value
        settingsStorage?.isImageEnhancementEnabled = _isImageEnhancementEnabled.value
        cameraManager?.isImageEnhancementEnabled = _isImageEnhancementEnabled.value
    }

    fun toggleFrontCameraMirror() {
        _isFrontCameraMirrorEnabled.value = !_isFrontCameraMirrorEnabled.value
        settingsStorage?.isFrontCameraMirrorEnabled = _isFrontCameraMirrorEnabled.value
        cameraManager?.isFrontCameraMirrorEnabled = _isFrontCameraMirrorEnabled.value
        // Rebind para aplicar MirrorMode no VideoCapture
        cameraManager?.rebindWithCurrentSettings()
    }

    fun toggleKeepSettings() {
        _isKeepSettingsEnabled.value = !_isKeepSettingsEnabled.value
        settingsStorage?.isKeepSettingsEnabled = _isKeepSettingsEnabled.value
    }

    fun tapToFocus(action: FocusMeteringAction, onFocusAcquired: () -> Unit = {}) {
        cameraManager?.tapToFocus(action, onFocusAcquired)
    }

    fun cancelFocusLock() {
        cameraManager?.cancelFocusLock()
    }

    fun setExposureCompensationIndex(index: Int) {
        cameraManager?.setExposureCompensationIndex(index)
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _recordingSeconds.value++
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _recordingSeconds.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager?.release()
    }
}
