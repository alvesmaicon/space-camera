package com.spacecamera.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.camera.mode.CameraModeId
import com.spacecamera.camera.mode.DeviceCapabilities
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Superfície da câmera vista pelo [com.spacecamera.presentation.viewmodels.CameraViewModel].
 *
 * Existe para separar *o que* o ViewModel pede da câmera de *como* o CameraX faz.
 * Antes o ViewModel dava `CameraManager(...)` direto dentro de `initializeCamera`,
 * o que obrigava um device/emulador real para exercitar qualquer lógica dele —
 * countdown, ciclo de flash, EIS por modo, persistência. Com a interface, o teste
 * injeta um dublê e roda na JVM.
 *
 * A implementação de produção é [CameraManager]. Ao adicionar um método aqui,
 * lembre que ele passa a ser contrato: mantenha o mínimo que o ViewModel usa.
 */
interface CameraController {

    // ── Estado observável ───────────────────────────────────────────────────

    val recordingState: StateFlow<RecordingState>
    val isCameraReady: StateFlow<Boolean>

    /** Capacidades sondadas do hardware — só se sabe depois do bind. */
    val isEisSupported: StateFlow<Boolean>
    val isHdrSupported: StateFlow<Boolean>

    /** Capacidades que decidem quais modos existem (FR-5). [DeviceCapabilities.UNKNOWN] até o bind. */
    val deviceCapabilities: StateFlow<DeviceCapabilities>

    /** Modo pedido que o aparelho recusou, e o modo que ficou ligado no lugar dele. */
    val modeRejections: SharedFlow<ModeRejection>

    val availableVideoOptions: StateFlow<List<VideoOption>>
    val availableAspectRatios: StateFlow<List<String>>

    val availableZoomLevels: StateFlow<List<Float>>
    val selectedZoomLevel: StateFlow<Float>
    val minZoomRatio: StateFlow<Float>
    val maxZoomRatio: StateFlow<Float>

    val exposureIndex: StateFlow<Int>
    val exposureMin: StateFlow<Int>
    val exposureMax: StateFlow<Int>

    val lastVideoUri: StateFlow<Uri?>
    val lastPhotoUri: StateFlow<Uri?>

    // ── Ajustes aplicados no próximo bind ───────────────────────────────────
    // São `var` (e não setters) porque o ViewModel os semeia com o valor
    // persistido antes do primeiro bind, quando ainda não há câmera para
    // reconfigurar. Depois do bind, use os `set*` correspondentes.

    var isStabilizationEnabled: Boolean
    var isNoiseReductionEnabled: Boolean
    var isHdrEnabled: Boolean
    var bitratePreset: VideoBitratePreset
    var photoQualityPreset: PhotoQualityPreset
    var isFrontCameraMirrorEnabled: Boolean
    var isSaveLocationEnabled: Boolean
    var isImageEnhancementEnabled: Boolean

    /**
     * Requirements: FR-3
     *
     * Proporção que a **caixa de pré-visualização** está usando na tela, só para o
     * `evt=bind` registrar. Não reconfigura nada: em vídeo o CameraX é sempre
     * `RATIO_16_9` independente disso.
     *
     * Existe separado de [setAspectRatio] de propósito — aquele é a preferência de
     * foto escolhida pelo usuário e persistida, e não pode ser sobrescrita pela
     * orientação da janela.
     */
    var previewAspectLabel: String

    // ── Ciclo de vida ───────────────────────────────────────────────────────

    suspend fun initializeCamera(surfaceProvider: Preview.SurfaceProvider?)

    /** Após voltar de outra tela o PreviewView é recriado; religa a surface sem rebind completo. */
    fun updateSurfaceProvider(newSurfaceProvider: Preview.SurfaceProvider)

    fun rebindWithCurrentSettings()
    fun release()

    // ── Configuração ────────────────────────────────────────────────────────

    fun setVideoOption(option: VideoOption)
    fun setAspectRatio(ratio: String)
    /**
     * Requirements: FR-2 · Decisions: ADR-002
     *
     * Liga na sessão exatamente os use cases que a definição declara. Se o aparelho
     * recusar a combinação, o controller religa o conjunto do modo anterior e avisa
     * por [modeRejections] — a sessão nunca fica sem bind (fluxo 4.2 do design).
     */
    fun applyMode(definition: CameraModeDefinition)
    fun setStabilization(enabled: Boolean)
    fun setNoiseReduction(enabled: Boolean)
    fun setHdr(enabled: Boolean)
    fun updateBitratePreset(preset: VideoBitratePreset)
    fun applyPhotoQualityPreset(preset: PhotoQualityPreset)
    fun flipCamera()

    // ── Controles ópticos ───────────────────────────────────────────────────

    fun setZoomLevel(ratio: Float)
    fun setExposureCompensationIndex(index: Int)
    fun tapToFocus(action: FocusMeteringAction, onFocusAcquired: () -> Unit)
    fun cancelFocusLock()

    // ── Captura ─────────────────────────────────────────────────────────────

    fun startRecording(micEnabled: Boolean, targetRotation: Int)
    fun pauseRecording()
    fun resumeRecording()
    fun stopRecording()
    fun takePhoto(targetRotation: Int, onSaved: (Uri) -> Unit, onError: (String) -> Unit)

    /** Tocha contínua (modo vídeo). Para foto use [setPhotoFlashMode]. */
    fun toggleFlash(enable: Boolean)

    /** Recebe uma constante `ImageCapture.FLASH_MODE_*`. */
    fun setPhotoFlashMode(mode: Int)
}

/**
 * Como o ViewModel obtém um [CameraController].
 *
 * Em produção aponta para o construtor de [CameraManager]; no teste, para um dublê.
 * A criação é adiada porque depende do [LifecycleOwner] da tela, que só existe
 * quando a UI já está montada.
 */
/**
 * O aparelho recusou a combinação de use cases de [rejected]; [active] é o modo cujo
 * conjunto foi religado no lugar.
 */
data class ModeRejection(val rejected: CameraModeId, val active: CameraModeId)

fun interface CameraControllerFactory {
    fun create(context: Context, lifecycleOwner: LifecycleOwner): CameraController
}
