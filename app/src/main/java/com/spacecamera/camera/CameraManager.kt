package com.spacecamera.camera

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.location.Location
import android.location.LocationManager
import android.media.CamcorderProfile
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Range as AndroidRange
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MirrorMode
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import androidx.camera.core.FocusMeteringAction
import timber.log.Timber
import com.spacecamera.camera.mode.CapabilityProbe
import com.spacecamera.camera.mode.DeviceCapabilities
import com.spacecamera.camera.mode.AppUseCase.IMAGE_CAPTURE
import com.spacecamera.camera.mode.AppUseCase.PREVIEW
import com.spacecamera.camera.mode.AppUseCase.VIDEO_CAPTURE
import com.spacecamera.camera.mode.AspectRatioRule
import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.camera.mode.ModeBinder
import com.spacecamera.camera.mode.VideoMode
import kotlinx.coroutines.flow.SharedFlow

sealed class RecordingState {
    object Idle : RecordingState()
    object Recording : RecordingState()
    object Paused : RecordingState()
}

data class VideoOption(val quality: Quality, val fps: Int) {
    val heightPx: Int
        get() = when (quality) {
            Quality.UHD -> 2160; Quality.FHD -> 1080; Quality.HD -> 720; else -> 480
        }
    val widthPx: Int
        get() = when (quality) {
            Quality.UHD -> 3840; Quality.FHD -> 1920; Quality.HD -> 1280; else -> 854
        }
    val qualityLabel: String
        get() = when (quality) {
            Quality.UHD -> "4K"; Quality.FHD -> "FHD"; Quality.HD -> "HD"; else -> "SD"
        }
    val label: String
        get() = "$qualityLabel $fps"
}

enum class PhotoQualityPreset(val label: String, val displayLabel: String, val targetSize: android.util.Size?) {
    MAXIMA("Máxima", "MAX", null),
    ALTA("Alta", "12MP", android.util.Size(4032, 3024)),
    MEDIA("Média", "5MP", android.util.Size(2560, 1920)),
    BAIXA("Baixa", "2MP", android.util.Size(1600, 1200));

    fun next() = entries[(ordinal + 1) % entries.size]
}

enum class VideoBitratePreset(val bpp: Float, val label: String) {
    LOW(0.07f, "Baixa"),
    MEDIUM(0.12f, "Média"),
    HIGH(0.20f, "Alta"),
    VERY_HIGH(0.40f, "Máxima");

    fun bitrateFor(option: VideoOption): Int {
        val bitsPerSecond = option.widthPx.toLong() * option.heightPx * option.fps * bpp
        // Cap at 150 Mbps — most hardware H.264 encoders reject higher values
        return bitsPerSecond.toLong().coerceAtMost(150_000_000L).toInt()
    }
}

@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class]) // ADR-005
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : CameraController {
    private val manual = ManualExposureControls()

    // Handler para debounce do rebind (evita race condition em toggles rápidos)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val rebindRunnable = Runnable { bindCameraUseCases() }

    private fun scheduleBind(delayMs: Long = 0) {
        Timber.d("scheduleBind(delay=${delayMs}ms) EIS=$isStabilizationEnabled NR=$isNoiseReductionEnabled HDR=$isHdrEnabled")
        mainHandler.removeCallbacks(rebindRunnable)
        mainHandler.postDelayed(rebindRunnable, delayMs)
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageCapture: ImageCapture? = null
    private var currentRecording: Recording? = null

    // SurfaceProvider is stored so we can rebuild Preview on every bindCameraUseCases()
    private var surfaceProvider: Preview.SurfaceProvider? = null
    private var currentPreview: Preview? = null

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    override val recordingState: StateFlow<RecordingState> = _recordingState

    private val _isCameraReady = MutableStateFlow(false)
    override val isCameraReady: StateFlow<Boolean> = _isCameraReady

    private val _deviceCapabilities = MutableStateFlow(DeviceCapabilities.UNKNOWN)
    override val deviceCapabilities: StateFlow<DeviceCapabilities> = _deviceCapabilities
    private val modes = ModeSession(VideoMode)
    override val modeRejections: SharedFlow<ModeRejection> = modes.rejections

    private val _isEisSupported = MutableStateFlow(false)
    override val isEisSupported: StateFlow<Boolean> = _isEisSupported

    private val _availableVideoOptions = MutableStateFlow<List<VideoOption>>(emptyList())
    override val availableVideoOptions: StateFlow<List<VideoOption>> = _availableVideoOptions

    private val _lastVideoUri = MutableStateFlow<Uri?>(null)
    override val lastVideoUri: StateFlow<Uri?> = _lastVideoUri

    private val _lastPhotoUri = MutableStateFlow<Uri?>(null)
    override val lastPhotoUri: StateFlow<Uri?> = _lastPhotoUri

    /** Aspect ratio strings actually supported by the active camera (e.g. ["9:16", "3:4"]). */
    private val _availableAspectRatios = MutableStateFlow<List<String>>(listOf("9:16", "3:4"))
    override val availableAspectRatios: StateFlow<List<String>> = _availableAspectRatios

    private val _availableZoomLevels = MutableStateFlow<List<Float>>(listOf(1f))
    override val availableZoomLevels: StateFlow<List<Float>> = _availableZoomLevels

    private val _selectedZoomLevel = MutableStateFlow(1f)
    override val selectedZoomLevel: StateFlow<Float> = _selectedZoomLevel

    private val _minZoomRatio = MutableStateFlow(1f)
    override val minZoomRatio: StateFlow<Float> = _minZoomRatio

    private val _maxZoomRatio = MutableStateFlow(1f)
    override val maxZoomRatio: StateFlow<Float> = _maxZoomRatio

    private val _exposureIndex = MutableStateFlow(0)
    override val exposureIndex: StateFlow<Int> = _exposureIndex

    private val _exposureMin = MutableStateFlow(-8)
    override val exposureMin: StateFlow<Int> = _exposureMin

    private val _exposureMax = MutableStateFlow(8)
    override val exposureMax: StateFlow<Int> = _exposureMax

    override var isStabilizationEnabled: Boolean = true
    override var isNoiseReductionEnabled: Boolean = true
    override var isHdrEnabled: Boolean = false
    override var bitratePreset: VideoBitratePreset = VideoBitratePreset.MEDIUM
    override var isFrontCameraMirrorEnabled: Boolean = false
    override var isSaveLocationEnabled: Boolean = false
    override var isImageEnhancementEnabled: Boolean = false

    private val _isHdrSupported = MutableStateFlow(true)
    override val isHdrSupported: StateFlow<Boolean> = _isHdrSupported

    private var selectedVideoOption: VideoOption = VideoOption(Quality.FHD, 30)
    private var selectedAspectRatio: String = "9:16"   // aspect ratio do modo FOTO
    // Só telemetria — quem define é a UI, conforme a janela. Ver CameraController.
    override var previewAspectLabel: String = "9:16"
    private var currentPhotoFlashMode: Int = ImageCapture.FLASH_MODE_OFF
    override var photoQualityPreset: PhotoQualityPreset = PhotoQualityPreset.MAXIMA
    var isFrontCamera = false
        private set
    private var useUltraWide = false
    private var ultraWideCameraSelector: CameraSelector? = null

    private val cameraExecutor: Executor by lazy {
        ContextCompat.getMainExecutor(context)
    }

    private val effectiveCameraSelector: CameraSelector
        get() = when {
            isFrontCamera -> CameraSelector.DEFAULT_FRONT_CAMERA
            useUltraWide && ultraWideCameraSelector != null -> ultraWideCameraSelector!!
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }

    // Alias usado em refreshAvailableResolutions (sempre camâra lógica principal)
    private val logicalCameraSelector: CameraSelector
        get() = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    /** Converts our aspect ratio string to a CameraX AspectRatio constant. */
    private fun cameraXAspectRatio(): Int {
        // O modo diz se segue a proporção escolhida para foto (AC-3.1).
        if (modes.current.aspectRatio == AspectRatioRule.FIXED_16_9) return AspectRatio.RATIO_16_9
        return when (selectedAspectRatio) {
            "3:4", "1:1" -> AspectRatio.RATIO_4_3
            // "Full" usa sensor 16:9 + FILL_CENTER no PreviewView = crop para tela cheia
            else -> AspectRatio.RATIO_16_9  // "9:16", "Full"
        }
    }

    override suspend fun initializeCamera(surfaceProvider: Preview.SurfaceProvider?) {
        try {
            this.surfaceProvider = surfaceProvider
            val future = ProcessCameraProvider.getInstance(context)
            cameraProvider = future.get()
            detectUltraWideCamera()
            refreshAvailableResolutions()
            bindCameraUseCases()
        } catch (e: Exception) {
            Timber.e(e, "Error initializing camera")
        }
    }

    /**
     * Called when the PreviewView is recreated after navigation (e.g. back from Settings).
     * Updates the surface provider on the existing Preview use case without a full rebind.
     */
    override fun updateSurfaceProvider(newSurfaceProvider: Preview.SurfaceProvider) {
        surfaceProvider = newSurfaceProvider
        mainHandler.post {
            currentPreview?.setSurfaceProvider(newSurfaceProvider)
        }
    }

    /** Identifica a câmera ultra-grande-angular enumerando TODOS os IDs via Camera2 diretamente.
     *  CameraX às vezes só expõe 1 câmera lógica, mas getCameraIdList() retorna as câmeras
     *  físicas/auxiliares que os fabricantes registram separadamente. */
    private fun detectUltraWideCamera() {
        val cam2Manager = context.getSystemService(android.hardware.camera2.CameraManager::class.java)
            ?: return
        val allIds = try { cam2Manager.cameraIdList } catch (e: Exception) { return }

        // Coleta todas as câmeras traseiras com suas focais mínimas
        data class BackCam(val id: String, val minFocal: Float)
        val backCameras = allIds.mapNotNull { id ->
            try {
                val chars = cam2Manager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing != CameraCharacteristics.LENS_FACING_BACK) return@mapNotNull null
                val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.minOrNull() ?: return@mapNotNull null
                BackCam(id, focal)
            } catch (e: Exception) { null }
        }

        Timber.d("All back cameras (Camera2): ${backCameras.map { "${it.id}@${it.minFocal}mm" }}")

        if (backCameras.size < 2) {
            ultraWideCameraSelector = null
            return
        }

        val sorted = backCameras.sortedBy { it.minFocal }
        val ultraWide = sorted.first()
        val main = sorted.last()

        Timber.d("Ultra-wide candidate: id=${ultraWide.id} focal=${ultraWide.minFocal}mm  main: id=${main.id} focal=${main.minFocal}mm")

        if (ultraWide.minFocal >= main.minFocal) {
            ultraWideCameraSelector = null
            return
        }

        val uwId = ultraWide.id
        ultraWideCameraSelector = CameraSelector.Builder()
            .addCameraFilter { infos ->
                infos.filter { Camera2CameraInfo.from(it).cameraId == uwId }
            }
            .build()
        Timber.d("Ultra-wide selector created for camera $uwId")
    }

    private fun refreshAvailableResolutions() {
        val provider = cameraProvider ?: return
        val matchingInfo = provider.availableCameraInfos.firstOrNull { info ->
            try { logicalCameraSelector.filter(listOf(info)).isNotEmpty() } catch (e: Exception) { false }
        }
        if (matchingInfo == null) {
            _availableVideoOptions.value = listOf(VideoOption(Quality.FHD, 30), VideoOption(Quality.HD, 30))
            _availableAspectRatios.value = listOf("9:16")
            return
        }

        val cameraId = Camera2CameraInfo.from(matchingInfo).cameraId
        val cameraIdInt = cameraId.toIntOrNull() ?: 0
        val supportedQualities = QualitySelector.getSupportedQualities(matchingInfo)

        val camera2Manager = context.getSystemService(android.hardware.camera2.CameraManager::class.java)
        val characteristics = try { camera2Manager?.getCameraCharacteristics(cameraId) } catch (e: Exception) { null }
        val aeRanges = characteristics?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        val camera2MaxFps = aeRanges?.maxOfOrNull { it.upper } ?: 0
        Timber.d("cameraId=$cameraId camera2MaxFps=$camera2MaxFps aeRanges=${aeRanges?.toList()}")

        val streamMap = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val mediaRecorderSizes = streamMap?.getOutputSizes(android.media.MediaRecorder::class.java)
        Timber.d("MediaRecorder output sizes: ${mediaRecorderSizes?.toList()}")

        val qualityToCamcorderProfile = mapOf(
            Quality.UHD to CamcorderProfile.QUALITY_2160P,
            Quality.FHD to CamcorderProfile.QUALITY_1080P,
            Quality.HD  to CamcorderProfile.QUALITY_720P,
        )

        val qualityToSize = mapOf(
            Quality.UHD to android.util.Size(3840, 2160),
            Quality.FHD to android.util.Size(1920, 1080),
            Quality.HD  to android.util.Size(1280, 720),
        )

        val options = mutableListOf<VideoOption>()
        for (quality in supportedQualities) {
            val heightPx = when (quality) {
                Quality.UHD -> 2160; Quality.FHD -> 1080; Quality.HD -> 720; else -> -1
            }
            if (heightPx < 0) continue
            val camProfile = qualityToCamcorderProfile[quality] ?: continue

            // 1) Coleta FPS dos CamcorderProfiles registrados pelo OEM
            val fpsList: MutableList<Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                CamcorderProfile.getAll(cameraId, camProfile)
                    ?.videoProfiles?.map { it.frameRate }?.filter { it >= 24 }?.distinct()?.toMutableList()
                    ?: mutableListOf(30)
            } else {
                if (CamcorderProfile.hasProfile(cameraIdInt, camProfile)) {
                    mutableListOf(CamcorderProfile.get(cameraIdInt, camProfile)?.videoFrameRate ?: 30)
                } else {
                    mutableListOf(30)
                }
            }
            Timber.d("quality=$quality camProfile=$camProfile fpsList(OEM)=$fpsList")

            // 2) Verifica se o sensor suporta 60fps para esta resolução via StreamConfigurationMap
            if (!fpsList.contains(60)) {
                val targetSize = qualityToSize[quality]
                val supportsVia60Ranges = camera2MaxFps >= 60
                val supportsViaStreamMap = if (targetSize != null && streamMap != null) {
                    val sizes = streamMap.getOutputSizes(android.media.MediaRecorder::class.java) ?: emptyArray()
                    val matchedSize = sizes.firstOrNull { it.width == targetSize.width && it.height == targetSize.height }
                    val minDuration = if (matchedSize != null)
                        streamMap.getOutputMinFrameDuration(android.media.MediaRecorder::class.java, matchedSize)
                    else -1L
                    Timber.d("quality=$quality targetSize=$targetSize matchedSize=$matchedSize minDuration=$minDuration")
                    matchedSize != null && minDuration > 0 && minDuration <= 16_666_667L
                } else false

                Timber.d("quality=$quality supportsVia60Ranges=$supportsVia60Ranges supportsViaStreamMap=$supportsViaStreamMap")
                if (supportsVia60Ranges && supportsViaStreamMap) {
                    fpsList.add(60)
                    Timber.d("60fps adicionado para $quality via Camera2")
                }
            }

            for (fps in fpsList.sorted()) {
                options.add(VideoOption(quality, fps))
            }
        }

        if (options.isEmpty()) {
            options += listOf(VideoOption(Quality.FHD, 30), VideoOption(Quality.HD, 30))
        }

        // Ordena por qualidade decrescente, depois por fps crescente
        _availableVideoOptions.value = options.sortedWith(
            compareByDescending<VideoOption> { it.heightPx }.thenBy { it.fps }
        )

        if (selectedVideoOption !in _availableVideoOptions.value) {
            selectedVideoOption = _availableVideoOptions.value.first()
        }

        _availableAspectRatios.value = listOf("9:16", "3:4")
        // "Full" é uma opção especial (apenas foto) que não aparece na lista dinâmica,
        // mas é sempre válida — não resetar se já selecionada.
        if (selectedAspectRatio !in _availableAspectRatios.value && selectedAspectRatio != "Full") {
            selectedAspectRatio = _availableAspectRatios.value.first()
        }
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        val bindStartedAt = SystemClock.elapsedRealtime()
        Timber.d("bindCameraUseCases START — EIS=$isStabilizationEnabled NR=$isNoiseReductionEnabled HDR=$isHdrEnabled")

        val aspectRatio = cameraXAspectRatio()

        val targetFps = selectedVideoOption.fps
        val previewBuilder = Preview.Builder().setTargetAspectRatio(aspectRatio)
        manual.observe(previewBuilder)
        // Camera2Interop no Preview — apenas AE_TARGET_FPS_RANGE, pois precisa estar fixo
        // desde a criação da sessão.
        // IMPORTANTE: EIS e NR NÃO devem ser setados aqui. Camera2Interop tem prioridade
        // maior que Camera2CameraControl no merge do CaptureRequest — se setarmos EIS aqui,
        // o valor fica gravado na sessão e o Camera2CameraControl nunca consegue sobrescrevê-lo.
        // EIS e NR são aplicados exclusivamente via applyEisNrImmediate() (Camera2CameraControl).
        Camera2Interop.Extender(previewBuilder)
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                AndroidRange(targetFps, targetFps)
            )
        // Envolve o SurfaceProvider para sinalizar isCameraReady quando o CameraX
        // solicitar a superfície — mais confiável do que um timer fixo pós-bind.
        val wrappedProvider = Preview.SurfaceProvider { surfaceRequest ->
            surfaceProvider?.onSurfaceRequested(surfaceRequest)
            mainHandler.postDelayed({ _isCameraReady.value = true }, 350)
        }
        val preview = previewBuilder.build().also { it.setSurfaceProvider(wrappedProvider) }
        currentPreview = preview

        val qualitySelector = QualitySelector.from(
            selectedVideoOption.quality,
            FallbackStrategy.lowerQualityThan(selectedVideoOption.quality)
        )

        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .setAspectRatio(aspectRatio)
            .setExecutor(cameraExecutor)
            .setTargetVideoEncodingBitRate(bitratePreset.bitrateFor(selectedVideoOption))
            .build()

        // VideoCapture: use MirrorMode to flip front camera recording when enabled
        videoCapture = VideoCapture.Builder(recorder)
            .setMirrorMode(
                if (isFrontCameraMirrorEnabled && isFrontCamera) MirrorMode.MIRROR_MODE_ON
                else MirrorMode.MIRROR_MODE_OFF
            )
            .build()

        val photoAspectRatioStrategy = if (aspectRatio == AspectRatio.RATIO_16_9)
            AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        else
            AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        val photoResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(photoAspectRatioStrategy)
            .apply {
                photoQualityPreset.targetSize?.let { size ->
                    setResolutionStrategy(
                        ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                    )
                }
            }
            .build()
        imageCapture = ImageCapture.Builder()
            .setResolutionSelector(photoResolutionSelector)
            .setCaptureMode(
                if (photoQualityPreset == PhotoQualityPreset.MAXIMA || photoQualityPreset == PhotoQualityPreset.ALTA)
                    ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                else
                    ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            )
            .setFlashMode(currentPhotoFlashMode)
            .build()

        Timber.d("bindCameraUseCases: EIS=${isStabilizationEnabled} NR=${isNoiseReductionEnabled} HDR=${isHdrEnabled}")
        _isCameraReady.value = false

        // Liga só o que o modo declara (FR-2); se o aparelho recusar, religa o modo
        // anterior em vez de deixar a sessão vazia (fluxo 4.2 do design, ADR-002).
        val construidos = mapOf(PREVIEW to preview, VIDEO_CAPTURE to videoCapture!!, IMAGE_CAPTURE to imageCapture!!)
        try {
            camera = modes.bind(onRestored = { scheduleBind() }) { modo ->
                cameraProvider.unbindAll()
                @Suppress("SpreadOperator") // a API só tem vararg; até 3 itens, uma vez por bind
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, effectiveCameraSelector, *ModeBinder.resolve(modo.useCases, construidos).toTypedArray()
                )
            }
            if (VIDEO_CAPTURE !in modes.current.useCases) videoCapture = null  // nada grava fora da sessão
            camera?.cameraControl?.setExposureCompensationIndex(0)
            // Populate exposure range from camera info
            val expState = camera?.cameraInfo?.exposureState
            if (expState != null) {
                _exposureMin.value = expState.exposureCompensationRange.lower
                _exposureMax.value = expState.exposureCompensationRange.upper
                _exposureIndex.value = expState.exposureCompensationIndex
            }

            // Capacidades do HAL — leitura e regras em SensorCharacteristicsReader.kt
            val cam2Info = Camera2CameraInfo.from(camera!!.cameraInfo)
            val sensor = cam2Info.sensorCharacteristics()
            _deviceCapabilities.value = CapabilityProbe.decide(sensor)
            val video = cam2Info.videoFeatureSupport(sensor.requestCapabilities)
            _isEisSupported.value = video.eis
            _isHdrSupported.value = video.hdr

            if (!_isHdrSupported.value && isHdrEnabled) {
                Timber.w("HDR solicitado, mas não suportado por scene mode neste dispositivo. Desativando HDR.")
                isHdrEnabled = false
            }

            // Re-aplica EIS/NR após a sessão estar totalmente aberta.
            // Camera2Interop nem sempre garante a aplicação antes do VideoCapture
            // configurar seus defaults internos; os 500ms garantem sessão pronta.
            mainHandler.postDelayed({ applyEisNrImmediate() }, 500)
            // isCameraReady=true é sinalizado pelo wrappedProvider acima, não aqui.

            updateAvailableZoomLevels()

            val decorrido = SystemClock.elapsedRealtime() - bindStartedAt
            CameraTelemetry.bind(
                mode = modes.current.id,
                option = selectedVideoOption,
                // A proporção efetiva da caixa de pré-visualização, que depende da
                // janela — não a preferência de foto persistida (FR-3, AC-3.2).
                aspectRatio = previewAspectLabel,
                bitrate = bitratePreset.bitrateFor(selectedVideoOption),
                eis = isStabilizationEnabled,
                noiseReduction = isNoiseReductionEnabled,
                hdr = isHdrEnabled,
                frontCamera = isFrontCamera,
                elapsedMs = decorrido
            )
            modes.reportIfChanged(decorrido)
            CameraTelemetry.capabilities(
                cameraId = cam2Info.cameraId,
                eisSupported = _isEisSupported.value,
                hdrSupported = _isHdrSupported.value,
                videoOptions = _availableVideoOptions.value,
                zoomRange = _minZoomRatio.value.._maxZoomRatio.value,
                device = _deviceCapabilities.value
            )
        } catch (e: Exception) {
            CameraTelemetry.bindFailed(e)
        }
    }

    override fun setVideoOption(option: VideoOption) {
        selectedVideoOption = option
        scheduleBind()
    }

    override fun setStabilization(enabled: Boolean) {
        Timber.d("setStabilization($enabled) — anterior=$isStabilizationEnabled")
        isStabilizationEnabled = enabled
        applyEisNrImmediate()
    }

    override fun setNoiseReduction(enabled: Boolean) {
        isNoiseReductionEnabled = enabled
        applyEisNrImmediate()
    }

    override fun setHdr(enabled: Boolean) {
        isHdrEnabled = if (_isHdrSupported.value) enabled else false
        applyEisNrImmediate()
    }

    private fun applyEisNrImmediate() {
        val cam = camera ?: run {
            Timber.w("applyEisNrImmediate: camera null, agendando rebind")
            scheduleBind(150)
            return
        }
        Timber.d("applyEisNrImmediate: EIS=$isStabilizationEnabled NR=$isNoiseReductionEnabled HDR=$isHdrEnabled")
        // NOTE: Do NOT set CONTROL_AF_MODE here. Camera2CameraControl options merge into every
        // CaptureRequest but do NOT carry over CONTROL_AF_REGIONS set by startFocusAndMetering.
        // Setting AF_MODE without regions would cause the HAL to re-run AF on the whole frame,
        // overriding the metering point. AF state is managed exclusively by CameraX.
        val builder = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                if (isStabilizationEnabled) CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                else CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
            )
            .setCaptureRequestOption(
                CaptureRequest.NOISE_REDUCTION_MODE,
                if (isNoiseReductionEnabled) CaptureRequest.NOISE_REDUCTION_MODE_FAST
                else CaptureRequest.NOISE_REDUCTION_MODE_OFF
            )
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_MODE,
                if (isHdrEnabled && _isHdrSupported.value) CaptureRequest.CONTROL_MODE_USE_SCENE_MODE
                else CaptureRequest.CONTROL_MODE_AUTO
            )
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_SCENE_MODE,
                if (isHdrEnabled && _isHdrSupported.value) CaptureRequest.CONTROL_SCENE_MODE_HDR
                else CaptureRequest.CONTROL_SCENE_MODE_DISABLED
            )
            .setCaptureRequestOption(
                CaptureRequest.TONEMAP_MODE,
                if (isHdrEnabled) CaptureRequest.TONEMAP_MODE_HIGH_QUALITY
                else CaptureRequest.TONEMAP_MODE_FAST
            )
        manual.contribute(builder)  // por último: vence o HDR (Q-11)
        Camera2CameraControl.from(cam.cameraControl)
            .setCaptureRequestOptions(builder.build())
            .addListener(
                { Timber.d("applyEisNrImmediate aplicado: EIS=$isStabilizationEnabled NR=$isNoiseReductionEnabled HDR=$isHdrEnabled supported=${_isHdrSupported.value}") },
                ContextCompat.getMainExecutor(context)
            )
    }

    override fun setZoomLevel(ratio: Float) {
        val minAllowed = _minZoomRatio.value
        val maxAllowed = _maxZoomRatio.value
        val safeRatio = ratio.coerceIn(minAllowed, maxAllowed)
        val wantsUltraWide = ratio < 1f && !isFrontCamera
        if (wantsUltraWide && ultraWideCameraSelector != null && !supportsLogicalZoomOut) {
            // Câmeras físicas separadas detectadas: troca o selector
            useUltraWide = true
            _selectedZoomLevel.value = safeRatio
            bindCameraUseCases()
        } else if (!wantsUltraWide && useUltraWide) {
            // Voltando da câmera física ultra-wide para a principal
            useUltraWide = false
            _selectedZoomLevel.value = safeRatio
            bindCameraUseCases()
        } else {
            // Zoom via câmera lógica (inclui zoom-out via CONTROL_ZOOM_RATIO_RANGE)
            _selectedZoomLevel.value = safeRatio
            camera?.cameraControl?.setZoomRatio(safeRatio)
        }
    }

    // true se o 0.5x pode ser feito via setZoomRatio na câmera lógica (sem trocar selector)
    private var supportsLogicalZoomOut = false

    private fun updateAvailableZoomLevels() {
        val cam = camera ?: return
        val cam2Info = Camera2CameraInfo.from(cam.cameraInfo)

        var minZoom = 1f
        var maxZoom = 1f

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val range = cam2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            minZoom = range?.lower ?: 1f
            maxZoom = range?.upper
                ?: cam2Info.getCameraCharacteristic(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                ?: 1f
        } else {
            maxZoom = cam2Info.getCameraCharacteristic(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
        }

        Timber.d("CONTROL_ZOOM_RATIO_RANGE: min=$minZoom max=$maxZoom  physicalUltraWide=${ultraWideCameraSelector != null}")

        // Suporta zoom-out via câmera lógica (Android 11+, logical multi-camera)
        supportsLogicalZoomOut = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && minZoom < 0.9f

        val hasUltraWide = (ultraWideCameraSelector != null && !isFrontCamera) || supportsLogicalZoomOut
        val minForUi = if (supportsLogicalZoomOut && !isFrontCamera) minZoom.coerceAtMost(1f) else 1f
        val maxForUi = maxZoom.coerceAtLeast(1f).coerceAtMost(20f)
        _minZoomRatio.value = minForUi
        _maxZoomRatio.value = maxForUi

        val levels = mutableListOf<Float>()
        if (hasUltraWide && minForUi < 1f) levels.add(minForUi)
        levels.add(1f)
        if (maxForUi >= 1.8f) levels.add(2f)
        if (maxForUi >= 4.5f) levels.add(5f)
        if (maxForUi >= 9.5f) levels.add(10f)
        if (maxForUi >= 19f) levels.add(20f)
        _availableZoomLevels.value = levels
        _selectedZoomLevel.value = (if (useUltraWide && minForUi < 1f) minForUi else 1f).coerceIn(minForUi, maxForUi)
    }

    override fun setAspectRatio(ratio: String) {
        selectedAspectRatio = ratio
        bindCameraUseCases()
    }

    override fun updateBitratePreset(preset: VideoBitratePreset) {
        bitratePreset = preset
        scheduleBind()
    }

    override fun rebindWithCurrentSettings() {
        scheduleBind()
    }

    override fun applyManualIso(iso: Int?) {
        ModeTelemetry.manualExposure(manual.setIso(iso, _deviceCapabilities.value.manualSensor?.exposureTimeNs))
        applyEisNrImmediate()
    }

    override fun applyMode(definition: CameraModeDefinition) {
        if (!modes.request(definition)) return
        _isCameraReady.value = false  // sinaliza "não pronto" imediatamente antes do rebind
        scheduleBind()
    }

    override fun tapToFocus(action: FocusMeteringAction, onFocusAcquired: () -> Unit) {
        val cam = camera ?: return
        // Log max AF regions supported — 0 means this device ignores CONTROL_AF_REGIONS
        val maxAfRegions = Camera2CameraInfo.from(cam.cameraInfo)
            .getCameraCharacteristic(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)
        Timber.d("tapToFocus: maxAfRegions=$maxAfRegions")
        val future = cam.cameraControl.startFocusAndMetering(action)
        future.addListener({
            try {
                if (future.get().isFocusSuccessful) {
                    Timber.d("tapToFocus: focus acquired successfully")
                    onFocusAcquired()
                } else {
                    Timber.w("tapToFocus: isFocusSuccessful=false (HAL may have rejected the metering region)")
                }
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(context))
    }

    override fun cancelFocusLock() {
        camera?.cameraControl?.cancelFocusAndMetering()
    }

    override fun setExposureCompensationIndex(index: Int) {
        val clamped = index.coerceIn(_exposureMin.value, _exposureMax.value)
        _exposureIndex.value = clamped
        camera?.cameraControl?.setExposureCompensationIndex(clamped)
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val hasFine = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null
        val provider = when {
            hasFine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            hasCoarse && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }
        return lm.getLastKnownLocation(provider)
    }

    override fun startRecording(micEnabled: Boolean, targetRotation: Int) {
        val videoCapture = videoCapture ?: return
        if (currentRecording != null) {
            Timber.w("Recording already in progress")
            return
        }
        // Lock video orientation to the physical phone orientation at recording start
        videoCapture.targetRotation = targetRotation

        // Captura localização agora (início da gravação) para aplicar ao finalizar
        val recordingLocation: Location? = if (isSaveLocationEnabled) getLastKnownLocation() else null
        Timber.d("startRecording: location=$recordingLocation saveEnabled=$isSaveLocationEnabled")

        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "VID_$dateStamp.mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/SpaceCamera")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues)
            .apply { if (recordingLocation != null) setLocation(recordingLocation) }
            .build()

        currentRecording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .apply { if (micEnabled) withAudioEnabled() }
            .start(cameraExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _recordingState.value = RecordingState.Recording
                        CameraTelemetry.recordingStarted(
                            option = selectedVideoOption,
                            bitrate = bitratePreset.bitrateFor(selectedVideoOption),
                            micEnabled = micEnabled
                        )
                    }
                    is VideoRecordEvent.Pause -> _recordingState.value = RecordingState.Paused
                    is VideoRecordEvent.Resume -> _recordingState.value = RecordingState.Recording
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
                            val uri = event.outputResults.outputUri
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val cv = ContentValues().apply {
                                    put(MediaStore.Video.Media.IS_PENDING, 0)
                                }
                                context.contentResolver.update(uri, cv, null, null)
                            }
                            _lastVideoUri.value = uri
                            CameraTelemetry.recordingFinished(
                                // Só o id do MediaStore: o caminho completo expõe
                                // nome de arquivo e pasta do usuário no logcat.
                                mediaId = uri.lastPathSegment,
                                durationMs = event.recordingStats.recordedDurationNanos / 1_000_000,
                                sizeBytes = event.recordingStats.numBytesRecorded
                            )
                        } else {
                            CameraTelemetry.recordingFailed("code=${event.error}")
                        }
                        currentRecording = null
                        _recordingState.value = RecordingState.Idle
                    }
                    else -> Unit
                }
            }
    }

    override fun pauseRecording() { currentRecording?.pause() }
    override fun resumeRecording() { currentRecording?.resume() }
    override fun stopRecording() { currentRecording?.stop(); currentRecording = null }

    override fun takePhoto(targetRotation: Int, onSaved: (Uri) -> Unit, onError: (String) -> Unit) {
        val capture = imageCapture ?: run {
            CameraTelemetry.photoFailed("image_capture_nao_inicializado")
            onError("ImageCapture não inicializado")
            return
        }
        val captureStartedAt = SystemClock.elapsedRealtime()
        capture.targetRotation = targetRotation
        val photoLocation: Location? = if (isSaveLocationEnabled) getLastKnownLocation() else null
        Timber.d("takePhoto: location=$photoLocation saveEnabled=$isSaveLocationEnabled enhancement=$isImageEnhancementEnabled aspectRatio=$selectedAspectRatio targetRotation=$targetRotation")
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "IMG_$dateStamp.jpg"

        if (isImageEnhancementEnabled || selectedAspectRatio == "Full") {
            // Caminho bitmap: necessário para enhancement e/ou crop Full
            capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val jpegBytes = extractJpegBytes(image)
                        val rotationDegrees = image.imageInfo.rotationDegrees
                        val raw = image.toBitmap()
                        image.close()
                        val processed = processCapturedBitmap(raw, rotationDegrees, targetRotation)
                        val uri = saveBitmapWithExif(processed, displayName, photoLocation, jpegBytes)
                        CameraTelemetry.photoCaptured(
                            preset = photoQualityPreset,
                            widthPx = processed.width,
                            heightPx = processed.height,
                            elapsedMs = SystemClock.elapsedRealtime() - captureStartedAt
                        )
                        processed.recycle()
                        _lastPhotoUri.value = uri
                        ContextCompat.getMainExecutor(context).execute { onSaved(uri) }
                    } catch (e: Exception) {
                        CameraTelemetry.photoFailed("processamento", e)
                        ContextCompat.getMainExecutor(context).execute {
                            onError(e.message ?: "Erro ao processar foto")
                        }
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    CameraTelemetry.photoFailed("captura code=${exception.imageCaptureError}", exception)
                    ContextCompat.getMainExecutor(context).execute {
                        onError(exception.message ?: "Erro ao capturar foto")
                    }
                }
            })
        } else {
            // Caminho direto: CameraX salva via OutputFileOptions (sem processamento)
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/SpaceCamera")
                }
            }
            val metadata = ImageCapture.Metadata().apply {
                if (photoLocation != null) location = photoLocation
            }
            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).setMetadata(metadata).build()
            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val uri = outputFileResults.savedUri ?: return
                        CameraTelemetry.photoCaptured(
                            preset = photoQualityPreset,
                            // Caminho direto não decodifica o bitmap, então o tamanho
                            // real não é conhecido aqui: reporta o alvo do preset.
                            widthPx = photoQualityPreset.targetSize?.width ?: 0,
                            heightPx = photoQualityPreset.targetSize?.height ?: 0,
                            elapsedMs = SystemClock.elapsedRealtime() - captureStartedAt
                        )
                        _lastPhotoUri.value = uri
                        onSaved(uri)
                    }
                    override fun onError(exception: ImageCaptureException) {
                        CameraTelemetry.photoFailed("captura code=${exception.imageCaptureError}", exception)
                        onError(exception.message ?: "Erro ao capturar foto")
                    }
                }
            )
        }
    }

    /** Extrai os bytes JPEG brutos do ImageProxy para preservar EXIF do sensor. */
    private fun extractJpegBytes(image: ImageProxy): ByteArray? {
        return try {
            if (image.format == android.graphics.ImageFormat.JPEG) {
                val buf = image.planes[0].buffer.duplicate()
                ByteArray(buf.remaining()).also { buf.get(it) }
            } else null
        } catch (e: Exception) { null }
    }

    /**
     * Pipeline de processamento do bitmap capturado:
     * 1. Rotaciona conforme os graus informados pelo sensor
     * 2. Corta para proporção da tela em modo Full (respeitando a orientação)
     * 3. Aplica enhancement se ativado
     * Recicla bitmaps intermediários automaticamente.
     */
    private fun processCapturedBitmap(raw: Bitmap, rotationDegrees: Int, targetRotation: Int): Bitmap {
        var bmp = rotateBitmap(raw, rotationDegrees)
        if (raw !== bmp) raw.recycle()
        if (selectedAspectRatio == "Full") {
            bmp = cropBitmapToScreenRatio(bmp, targetRotation)
        }
        if (isImageEnhancementEnabled) {
            val enhanced = enhanceBitmap(bmp)
            bmp.recycle()
            bmp = enhanced
        }
        return bmp
    }

    /**
     * Aplica boost adaptativo de saturação e contraste com base na análise da imagem.
     * Cenas com baixa saturação recebem boost maior; cenas escuras recebem menos contraste.
     */
    private fun enhanceBitmap(original: Bitmap): Bitmap {
        // Amostra reduzida para análise rápida
        val sampleW = minOf(original.width, 200)
        val sampleH = minOf(original.height, 200)
        val sample = Bitmap.createScaledBitmap(original, sampleW, sampleH, false)
        val pixels = IntArray(sampleW * sampleH)
        sample.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        sample.recycle()

        val hsv = FloatArray(3)
        var totalSat = 0f
        var totalVal = 0f
        var skinCount = 0
        for (pixel in pixels) {
            Color.colorToHSV(pixel, hsv)
            val h = hsv[0]; val s = hsv[1]; val v = hsv[2]
            totalSat += s
            totalVal += v
            // Detecção de pele: hue entre 5°–40°, saturação e brilho moderados
            if (h in 5f..40f && s in 0.15f..0.80f && v in 0.25f..0.97f) skinCount++
        }
        val avgSat = totalSat / pixels.size  // 0..1
        val avgVal = totalVal / pixels.size  // 0..1
        // skinRatio: 0 = sem pele (paisagem), 1 = foto de rosto / selfie
        val skinRatio = skinCount.toFloat() / pixels.size

        // Saturação base: cenas opacas recebem mais boost — teto reduzido (1.35) para naturalidade
        val t = ((avgSat - 0.15f) / 0.45f).coerceIn(0f, 1f)
        var satBoost = 1.35f - t * 0.25f  // lerp 1.35 → 1.10
        // Presença de pele reduz boost adicional (evita pele alaranjada/artificial)
        val skinFactor = (skinRatio / 0.20f).coerceIn(0f, 1f)
        satBoost -= skinFactor * 0.15f

        // Contraste contínuo suave; cenas com pele recebem menos contraste
        val baseContrast = 1.03f + (avgVal.coerceIn(0.2f, 0.8f) - 0.2f) / 0.6f * 0.07f  // 1.03..1.10
        val contrastBoost = baseContrast - skinFactor * 0.04f

        Timber.d("enhanceBitmap: avgSat=${"%.2f".format(avgSat)} avgVal=${"%.2f".format(avgVal)} skinRatio=${"%.2f".format(skinRatio)} satBoost=${"%.2f".format(satBoost)} contrast=${"%.2f".format(contrastBoost)}")

        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(satBoost)
        val contrastOffset = 128f * (1f - contrastBoost)
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrastBoost, 0f, 0f, 0f, contrastOffset,
            0f, contrastBoost, 0f, 0f, contrastOffset,
            0f, 0f, contrastBoost, 0f, contrastOffset,
            0f, 0f, 0f, 1f, 0f
        ))
        satMatrix.postConcat(contrastMatrix)

        val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(original, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(satMatrix) })
        return result
    }

    /**
     * Salva um Bitmap como JPEG no MediaStore e, se houver localização, escreve EXIF GPS.
     */
    private fun saveBitmapWithExif(bitmap: Bitmap, displayName: String, location: Location?, originalJpegBytes: ByteArray? = null): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/SpaceCamera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ) ?: throw Exception("Não foi possível criar entrada no MediaStore")

        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        } ?: throw Exception("Não foi possível abrir OutputStream para salvar imagem")

        context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            val exif = ExifInterface(pfd.fileDescriptor)
            // Copia tags do sensor (abertura, ISO, velocidade, focal length, etc.)
            if (originalJpegBytes != null) {
                try {
                    val src = ExifInterface(originalJpegBytes.inputStream())
                    for (tag in EXIF_CAMERA_TAGS) {
                        src.getAttribute(tag)?.let { exif.setAttribute(tag, it) }
                    }
                } catch (e: Exception) {
                    Timber.w("EXIF copy failed: ${e.message}")
                }
            }
            // GPS
            if (location != null) {
                val lat = Math.abs(location.latitude)
                val lon = Math.abs(location.longitude)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, decimalToDmsExif(lat))
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (location.latitude >= 0) "N" else "S")
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, decimalToDmsExif(lon))
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (location.longitude >= 0) "E" else "W")
                if (location.hasAltitude()) {
                    val alt = Math.abs(location.altitude)
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "${(alt * 100).toLong()}/100")
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (location.altitude >= 0) "0" else "1")
                }
            }
            exif.saveAttributes()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            context.contentResolver.update(uri, cv, null, null)
        }
        return uri
    }

    /** Converte grau decimal para o formato DMS em racional EXIF (ex: "37/1,26/1,123456/10000"). */
    private fun decimalToDmsExif(decimal: Double): String {        val d = decimal.toInt()
        val mAll = (decimal - d) * 60
        val m = mAll.toInt()
        val s = ((mAll - m) * 60 * 10000).toLong()
        return "$d/1,$m/1,$s/10000"
    }

    /** Rotaciona o bitmap pelo número de graus informado. Retorna o original se graus == 0. */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Corta o bitmap para corresponder à proporção da tela (modo Full).
     * Quando targetRotation indica paisagem (90/270), as dimensões são trocadas
     * para corresponder ao enquadramento real — mesmo em apps com activity portrait-locked.
     * Recicla o bitmap original se um novo for criado.
     */
    private fun cropBitmapToScreenRatio(bitmap: Bitmap, targetRotation: Int = android.view.Surface.ROTATION_0): Bitmap {
        val dm = context.resources.displayMetrics
        val (screenW, screenH) = when (targetRotation) {
            android.view.Surface.ROTATION_90, android.view.Surface.ROTATION_270 ->
                Pair(dm.heightPixels, dm.widthPixels)  // landscape: troca dimensões
            else -> Pair(dm.widthPixels, dm.heightPixels)
        }
        val screenAspect = screenW.toFloat() / screenH
        val imgAspect = bitmap.width.toFloat() / bitmap.height
        return if (imgAspect > screenAspect) {
            // imagem mais larga → corta laterais
            val cropW = (bitmap.height * screenAspect).toInt()
            val cropX = (bitmap.width - cropW) / 2
            val cropped = Bitmap.createBitmap(bitmap, cropX, 0, cropW, bitmap.height)
            bitmap.recycle()
            cropped
        } else if (imgAspect < screenAspect) {
            // imagem mais alta → corta topo/base
            val cropH = (bitmap.width / screenAspect).toInt()
            val cropY = (bitmap.height - cropH) / 2
            val cropped = Bitmap.createBitmap(bitmap, 0, cropY, bitmap.width, cropH)
            bitmap.recycle()
            cropped
        } else bitmap
    }

    override fun toggleFlash(enable: Boolean) { camera?.cameraControl?.enableTorch(enable) }

    override fun setPhotoFlashMode(mode: Int) {
        currentPhotoFlashMode = mode
        imageCapture?.flashMode = mode
    }

    override fun applyPhotoQualityPreset(preset: PhotoQualityPreset) {
        photoQualityPreset = preset
        scheduleBind()
    }

    override fun flipCamera() {
        isFrontCamera = !isFrontCamera
        useUltraWide = false
        detectUltraWideCamera()
        refreshAvailableResolutions()
        bindCameraUseCases()
    }

    override fun release() {
        currentRecording?.stop()
        cameraProvider?.unbindAll()
    }

}
