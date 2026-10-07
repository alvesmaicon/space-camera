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
import com.spacecamera.camera.CameraController
import com.spacecamera.camera.CameraControllerFactory
import com.spacecamera.camera.CameraManager
import com.spacecamera.camera.PhotoQualityPreset
import com.spacecamera.camera.RecordingState
import com.spacecamera.camera.VideoBitratePreset
import com.spacecamera.camera.VideoOption
import com.spacecamera.camera.mode.ArrangedModes
import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.camera.mode.CaptureState
import com.spacecamera.camera.mode.ModeArrangement
import com.spacecamera.camera.mode.ModeRegistry
import com.spacecamera.camera.mode.FlashBehavior
import com.spacecamera.camera.mode.ShutterAction
import com.spacecamera.camera.mode.StabilizationRule
import com.spacecamera.data.repository.VideoRepositoryImpl
import com.spacecamera.data.storage.SettingsStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.ref.WeakReference

enum class RecordingDelay(val seconds: Int) {
    OFF(0), THREE(3), FIVE(5), TEN(10);
    fun next() = entries[(ordinal + 1) % entries.size]
}

/** Ciclo do flash em foto, na ordem em que o botão percorre. */
enum class PhotoFlashMode(val imageCaptureMode: Int) {
    OFF(ImageCapture.FLASH_MODE_OFF), AUTO(ImageCapture.FLASH_MODE_AUTO), ON(ImageCapture.FLASH_MODE_ON);

    fun next(): PhotoFlashMode = entries[(ordinal + 1) % entries.size]
}

/**
 * @param controllerFactory como obter o [CameraController]. Em produção cria um
 *   [CameraManager]; no teste, um dublê — é o que permite exercitar countdown,
 *   ciclo de flash e EIS por modo na JVM, sem device.
 * @param settingsStorageFactory idem para a persistência.
 * @param modeRegistry os modos que o app conhece. Em produção, [ModeRegistry.all];
 *   no teste, um registro com modo exigente para exercitar o gate de capacidade.
 *
 * Todos os parâmetros têm default, então o Kotlin gera o construtor sem
 * argumentos que `viewModel()` precisa para instanciar por reflexão.
 */
class CameraViewModel(
    private val controllerFactory: CameraControllerFactory =
        CameraControllerFactory { context, lifecycleOwner -> CameraManager(context, lifecycleOwner) },
    private val settingsStorageFactory: (Context) -> SettingsStorage = { SettingsStorage(it) },
    private val modeRegistry: List<CameraModeDefinition> = ModeRegistry.all
) : ViewModel() {

    private var cameraManager: CameraController? = null
    private var videoRepository: VideoRepositoryImpl? = null
    private var timerJob: Job? = null
    private var countdownJob: Job? = null
    private var settingsStorage: SettingsStorage? = null

    /**
     * Decisions: Q-02
     *
     * Qual `LifecycleOwner` o [cameraManager] atual está usando. Este ViewModel
     * sobrevive à recriação da Activity, e o controller guarda o owner que recebeu no
     * construtor — então sem esta comparação o rebind depois de girar acerta um
     * ciclo de vida já DESTROYED, que o CameraX registra e nunca ativa.
     *
     * `WeakReference` porque a alternativa é o ViewModel segurar a Activity morta:
     * era o que acontecia por acidente através do controller retido, e é vazamento.
     */
    private var boundLifecycleOwner: WeakReference<LifecycleOwner>? = null

    /**
     * Os coletores dos fluxos do controller, como um job pai só.
     *
     * Existem juntos para poderem ser cancelados juntos: ao trocar de controller, os
     * coletores do antigo continuariam vivos — o `collect` num `StateFlow` nunca
     * termina — mantendo a instância velha viva e escrevendo nos mesmos `_flows` que
     * o controller novo escreve.
     */
    private var controllerCollectors: Job? = null

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

    /** O modo ativo, como definição do registro (FR-1). Começa no primeiro dele. */
    private val _activeMode = MutableStateFlow(modeRegistry.first())
    val activeMode: StateFlow<CameraModeDefinition> = _activeMode.asStateFlow()

    /**
     * Plano e gaveta do seletor (FR-6), filtrados pelo que o aparelho oferece (FR-5).
     * Começa sem capacidade nenhuma — um modo exigente não aparece antes da sondagem.
     * A preferência do usuário chega na Tarefa 10; até lá, o padrão do registro.
     */
    private val _arrangedModes = MutableStateFlow(ModeRegistry.arranged(ModeArrangement.DEFAULT, emptySet(), modeRegistry))
    val arrangedModes: StateFlow<ArrangedModes> = _arrangedModes.asStateFlow()

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

    /**
     * Requirements: NFR-3
     * Decisions: Q-02
     *
     * Liga a câmera ao [lifecycleOwner] da tela. É chamada de novo a cada
     * `PreviewView` novo, e o que ela decide é **reaproveitar ou reconstruir** o
     * controller:
     *
     * - mesmo owner (voltou de Configurações — navegação do Compose dentro da mesma
     *   Activity): religa a surface, sem fechar a câmera
     * - owner diferente (Activity recriada por rotação): reconstrói, porque o
     *   controller retido guarda o owner antigo e rebindar nele não reabre a câmera
     */
    suspend fun initializeCamera(context: Context, lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider?) {
        try {
            if (cameraManager != null && boundLifecycleOwner?.get() === lifecycleOwner) {
                surfaceProvider?.let { cameraManager!!.updateSurfaceProvider(it) }
                cameraManager!!.rebindWithCurrentSettings()
                return
            }
            val ctx = context.applicationContext
            if (cameraManager != null) {
                // Activity recriada: derruba o controller preso ao owner morto. Sem
                // isso a câmera fecha no destroy e nunca reabre (Q-02, Medição 3).
                controllerCollectors?.cancel()
                cameraManager?.release()
                cameraManager = null
            }
            // Só na primeira criação: numa recriação por rotação o estado em memória já
            // é o corrente, e recarregar aqui faria o ramo de "manter configurações
            // desligado" resetar para os padrões a cada virada de tela.
            if (boundLifecycleOwner == null) {
                // Carrega configurações persistidas antes de criar o CameraManager
                if (settingsStorage == null) settingsStorage = settingsStorageFactory(ctx)
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
            }
            cameraManager = controllerFactory.create(ctx, lifecycleOwner).also { mgr ->
                mgr.isStabilizationEnabled = _isStabilizationEnabled.value
                mgr.isNoiseReductionEnabled = _isNoiseReductionEnabled.value
                mgr.bitratePreset = _bitratePreset.value
                mgr.photoQualityPreset = _photoQualityPreset.value
                mgr.isHdrEnabled = _isHdrEnabled.value
                mgr.isFrontCameraMirrorEnabled = _isFrontCameraMirrorEnabled.value
                mgr.isSaveLocationEnabled = _isSaveLocationEnabled.value
                mgr.isImageEnhancementEnabled = _isImageEnhancementEnabled.value
                mgr.previewAspectLabel = pendingPreviewAspectLabel
            }
            boundLifecycleOwner = WeakReference(lifecycleOwner)
            videoRepository = VideoRepositoryImpl(cameraManager!!)
            cameraManager!!.initializeCamera(surfaceProvider)
            _cameraInitialized.value = true
            // O bind vem **antes** dos coletores, e a ordem é o defeito medido em
            // aparelho real (Q-03): as capacidades só são sondadas dentro de
            // `initializeCamera`, e os fluxos do controller começam em `false` — valor
            // que significa "ainda não sondado" e é indistinguível de "não suportado".
            // Com os coletores antes, o de `isEisSupported` recebia esse `false`,
            // concluía "sem suporte" e desligava o EIS de forma persistente num
            // aparelho que o suporta. `viewModelScope` usa `Dispatchers.Main.immediate`,
            // que roda o `launch` de forma eager até a primeira suspensão, então não há
            // janela de sorte aqui.
            controllerCollectors = viewModelScope.launch {
                launch {
                    cameraManager!!.recordingState.collect { state ->
                        _recordingState.value = state
                        when (state) {
                            is RecordingState.Recording -> startTimer()
                            is RecordingState.Paused -> pauseTimer()
                            is RecordingState.Idle -> stopTimer()
                        }
                    }
                }
                // Sync available video options from CameraManager
                launch {
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
                launch {
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
                launch {
                    cameraManager!!.lastVideoUri.collect { uri ->
                        _lastVideoUri.value = uri
                    }
                }
                // Sync last photo URI
                launch {
                    cameraManager!!.lastPhotoUri.collect { uri ->
                        _lastPhotoUri.value = uri
                    }
                }
                // Sync zoom levels
                launch {
                    cameraManager!!.availableZoomLevels.collect { levels ->
                        _availableZoomLevels.value = levels
                    }
                }
                launch {
                    cameraManager!!.selectedZoomLevel.collect { ratio ->
                        _selectedZoomLevel.value = ratio
                    }
                }
                launch {
                    cameraManager!!.minZoomRatio.collect { ratio ->
                        _minZoomRatio.value = ratio
                    }
                }
                launch {
                    cameraManager!!.maxZoomRatio.collect { ratio ->
                        _maxZoomRatio.value = ratio
                    }
                }
                launch {
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
                launch {
                    cameraManager!!.isHdrSupported.collect { supported ->
                        _isHdrSupported.value = supported
                        if (!supported && _isHdrEnabled.value) {
                            _isHdrEnabled.value = false
                            settingsStorage?.isHdrEnabled = false
                            cameraManager!!.setHdr(false)
                        }
                    }
                }
                launch {
                    cameraManager!!.exposureIndex.collect { _exposureIndex.value = it }
                }
                launch {
                    cameraManager!!.exposureMin.collect { _exposureMin.value = it }
                }
                launch {
                    cameraManager!!.exposureMax.collect { _exposureMax.value = it }
                }
                launch {
                    cameraManager!!.isCameraReady.collect { _isCameraReady.value = it }
                }
                // O aparelho recusou o modo e o controller religou o anterior (fluxo
                // 4.2 do design): o seletor e o EIS voltam para o que está na sessão.
                launch {
                    cameraManager!!.deviceCapabilities.collect { caps ->
                        _arrangedModes.value = ModeRegistry.arranged(ModeArrangement.DEFAULT, caps.supported, modeRegistry)
                    }
                }
                launch {
                    cameraManager!!.modeRejections.collect { recusa ->
                        val restaurado = modeRegistry.first { it.id == recusa.active }
                        _activeMode.value = restaurado
                        syncStabilizationWithMode(restaurado)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "evt=camera_init_failed")
            _cameraInitialized.value = false
        }
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
        when (_activeMode.value.flashBehavior()) {
            FlashBehavior.Torch -> {
                _isFlashOn.value = !_isFlashOn.value
                cameraManager?.toggleFlash(_isFlashOn.value)
            }
            FlashBehavior.PhotoCycle -> cyclePhotoFlash()
            FlashBehavior.Unavailable -> Unit
        }
    }

    /** Cicla OFF → AUTO → ON → OFF, sem tocha contínua. */
    private fun cyclePhotoFlash() {
        _photoFlashMode.value = _photoFlashMode.value.next()
        cameraManager?.setPhotoFlashMode(_photoFlashMode.value.imageCaptureMode)
    }

    fun resetFlash() {
        if (_isFlashOn.value) {
            _isFlashOn.value = false
            cameraManager?.toggleFlash(false)
        }
    }

    fun selectMode(mode: CameraModeDefinition) {
        if (_recordingState.value != RecordingState.Idle) return
        _activeMode.value = mode
        resetFlash()
        syncStabilizationWithMode(mode)
        cameraManager?.applyMode(mode)
    }

    /**
     * O modo diz se o EIS vale (AC-4.1). Desligar não grava: a preferência é do
     * usuário, e voltar a um modo que a segue restaura o valor salvo.
     */
    private fun syncStabilizationWithMode(mode: CameraModeDefinition) {
        val desejado = when (mode.stabilization) {
            StabilizationRule.OFF -> false
            StabilizationRule.FOLLOWS_PREFERENCE -> settingsStorage?.isStabilizationEnabled ?: false
        }
        if (desejado != _isStabilizationEnabled.value) {
            _isStabilizationEnabled.value = desejado
            cameraManager?.setStabilization(desejado)
        }
    }

    /**
     * Requirements: FR-2, FR-3 · Decisions: ADR-001
     *
     * O disparador pergunta à definição do modo **o que** fazer e faz. É o que deixa
     * um modo novo decidir o próprio disparador sem `when` por modo aqui ou na tela.
     *
     * @return a ação executada — a tela guarda a rotação da revisão quando é foto.
     */
    fun onShutter(targetRotation: Int): ShutterAction {
        val estado = CaptureState(
            recording = _recordingState.value != RecordingState.Idle,
            countdownActive = _countdownSeconds.value > 0
        )
        val acao = _activeMode.value.shutterAction(estado)
        when (acao) {
            ShutterAction.StartRecording -> startRecordingWithDelay(targetRotation)
            ShutterAction.StopRecording -> stopRecording()
            ShutterAction.CapturePhoto -> takePhotoWithDelay(targetRotation)
            ShutterAction.CancelCountdown -> cancelCountdown()
        }
        return acao
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

    private var pendingPreviewAspectLabel: String = "9:16"

    /**
     * Requirements: FR-3
     *
     * Informa ao controller a proporção que a pré-visualização está usando de fato,
     * para o `evt=bind` registrá-la (AC-3.2). Não persiste e não reconfigura a
     * câmera — é só rótulo de diagnóstico.
     *
     * Guarda o valor em campo além de repassar: a UI chama isto durante a primeira
     * composição, quando o `cameraManager` ainda não existe. Sem o campo, a
     * atribuição se perdia em silêncio e o `evt=bind` reportava sempre `9:16` — foi
     * o que a verificação em tablet pegou.
     */
    fun setPreviewAspectLabel(label: String) {
        pendingPreviewAspectLabel = label
        cameraManager?.previewAspectLabel = label
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
        controllerCollectors?.cancel()
        cameraManager?.release()
        boundLifecycleOwner = null
    }
}
