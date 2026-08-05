package com.spacecamera.presentation.viewmodels

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.video.Quality
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.spacecamera.camera.CameraControllerFactory
import com.spacecamera.camera.CameraMode
import com.spacecamera.camera.FakeCameraController
import com.spacecamera.camera.PhotoQualityPreset
import com.spacecamera.camera.RecordingState
import com.spacecamera.camera.VideoBitratePreset
import com.spacecamera.camera.VideoOption
import com.spacecamera.data.storage.SettingsStorage
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes do [CameraViewModel] sem device.
 *
 * Antes da extração de `CameraController`, o ViewModel instanciava o
 * `CameraManager` dentro de `initializeCamera`, o que exigia CameraX e um sensor
 * real — o arquivo de teste que existia aqui tinha seis métodos com só um `TODO`
 * dentro (e nem compilava, porque importava `kotlin.test` sem a dependência).
 *
 * Agora o controller é injetado por factory e a persistência usa
 * SharedPreferences de verdade sob Robolectric, então o que se testa é o
 * comportamento inteiro: contagem regressiva, ciclo de flash, EIS por modo e o
 * que efetivamente é gravado no storage.
 *
 * Cuidado ao editar: o cronômetro do ViewModel roda `while (true) { delay }`.
 * Use `advanceTimeBy` com valor explícito — `advanceUntilIdle` depois que a
 * gravação começou nunca retorna.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var controller: FakeCameraController
    private lateinit var storage: SettingsStorage
    private lateinit var context: Context
    private lateinit var viewModel: CameraViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        limparPreferencias()
        controller = FakeCameraController()
        storage = SettingsStorage(context)
        viewModel = CameraViewModel(
            controllerFactory = CameraControllerFactory { _, _ -> controller },
            settingsStorageFactory = { storage }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun limparPreferencias() {
        context.getSharedPreferences("space_camera_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    /**
     * Envolve o corpo do teste garantindo que a gravação termine.
     *
     * O `runTest` drena o scheduler depois que o corpo retorna. Como o
     * cronômetro do ViewModel é `while (true) { delay(1000) }`, qualquer teste
     * que deixasse a gravação ativa faria esse dreno rodar para sempre — o
     * primeiro rascunho desta suíte travou exatamente assim. Voltar o estado
     * para Idle faz o ViewModel cancelar o cronômetro.
     *
     * O `finally` não é detalhe: sem ele, um teste que **falha** com a gravação
     * ativa pula a limpeza e trava a suíte inteira, escondendo a falha real
     * atrás de um timeout.
     */
    private fun teste(corpo: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            corpo()
        } finally {
            controller.recordingStateFlow.value = RecordingState.Idle
            advanceTimeBy(1_100)
        }
    }

    /** Roda `initializeCamera`, que semeia o controller e liga os coletores. */
    private suspend fun TestScope.inicializar() {
        viewModel.initializeCamera(context, mockk<LifecycleOwner>(relaxed = true), null)
        advanceUntilIdle()
    }

    // ── Estado inicial ──────────────────────────────────────────────────────

    @Test
    fun `estado inicial antes de inicializar a camera`() = teste {
        assertEquals(RecordingState.Idle, viewModel.recordingState.value)
        assertEquals(CameraMode.VIDEO, viewModel.cameraMode.value)
        assertEquals(RecordingDelay.OFF, viewModel.recordingDelay.value)
        assertEquals(0, viewModel.recordingSeconds.value)
        assertFalse(viewModel.cameraInitialized.value)
    }

    /**
     * Requirements: FR-3
     *
     * A UI define a proporção efetiva durante a primeira composição, **antes** de
     * `initializeCamera` criar o controller. A primeira implementação só repassava
     * (`cameraManager?.previewAspectLabel = label`), então a atribuição se perdia em
     * silêncio e o `evt=bind` reportava sempre `9:16` — pego só na verificação em
     * tablet. Este teste tranca o caminho.
     */
    @Test
    fun `proporcao definida antes de inicializar chega ao controller`() = teste {
        viewModel.setPreviewAspectLabel("16:9")

        inicializar()

        assertEquals("16:9", controller.previewAspectLabel)
    }

    @Test
    fun `proporcao definida depois de inicializar chega ao controller`() = teste {
        inicializar()

        viewModel.setPreviewAspectLabel("4:3")

        assertEquals("4:3", controller.previewAspectLabel)
    }

    @Test
    fun `inicializar semeia o controller com o que estava persistido`() = teste {
        storage.isStabilizationEnabled = false
        storage.isNoiseReductionEnabled = false
        storage.bitratePreset = VideoBitratePreset.HIGH
        storage.photoQualityPreset = PhotoQualityPreset.BAIXA

        inicializar()

        assertTrue(viewModel.cameraInitialized.value)
        assertEquals(1, controller.initializeCount)
        assertFalse(controller.isStabilizationEnabled, "EIS persistido precisa chegar no controller")
        assertFalse(controller.isNoiseReductionEnabled)
        assertEquals(VideoBitratePreset.HIGH, controller.bitratePreset)
        assertEquals(PhotoQualityPreset.BAIXA, controller.photoQualityPreset)
    }

    // ── Contagem regressiva ─────────────────────────────────────────────────

    @Test
    fun `sem atraso a gravacao comeca na hora`() = teste {
        inicializar()

        viewModel.startRecordingWithDelay(targetRotation = 0)
        advanceTimeBy(50)

        assertEquals(1, controller.startRecordingCount)
    }

    @Test
    fun `com atraso a gravacao so comeca no fim da contagem`() = teste {
        inicializar()
        viewModel.cycleRecordingDelay() // OFF -> 3s

        viewModel.startRecordingWithDelay(targetRotation = 0)
        advanceTimeBy(1)
        assertEquals(3, viewModel.countdownSeconds.value)
        assertEquals(0, controller.startRecordingCount, "não pode gravar antes da contagem acabar")

        advanceTimeBy(2_000)
        assertEquals(1, viewModel.countdownSeconds.value)
        assertEquals(0, controller.startRecordingCount)

        advanceTimeBy(1_100)
        assertEquals(1, controller.startRecordingCount)
        assertEquals(0, viewModel.countdownSeconds.value)
    }

    @Test
    fun `cancelar a contagem impede a gravacao`() = teste {
        inicializar()
        viewModel.cycleRecordingDelay() // 3s
        viewModel.startRecordingWithDelay(targetRotation = 0)
        advanceTimeBy(1_500)

        viewModel.cancelCountdown()
        advanceTimeBy(5_000)

        assertEquals(0, controller.startRecordingCount)
        assertEquals(0, viewModel.countdownSeconds.value)
    }

    @Test
    fun `contagem regressiva vale tambem para foto`() = teste {
        inicializar()
        viewModel.setCameraMode(CameraMode.PHOTO)
        viewModel.cycleRecordingDelay() // 3s

        viewModel.takePhotoWithDelay()
        advanceTimeBy(2_000)
        assertEquals(0, controller.takePhotoCount)

        advanceTimeBy(1_100)
        assertEquals(1, controller.takePhotoCount)
    }

    // ── Cronômetro ──────────────────────────────────────────────────────────

    @Test
    fun `cronometro conta durante a gravacao e zera ao parar`() = teste {
        inicializar()

        viewModel.startRecording(targetRotation = 0)
        advanceTimeBy(3_500)
        assertEquals(3, viewModel.recordingSeconds.value)

        viewModel.stopRecording()
        advanceTimeBy(100)
        assertEquals(0, viewModel.recordingSeconds.value, "parar precisa zerar o cronômetro")
    }

    @Test
    fun `pausar congela o cronometro e retomar continua de onde parou`() = teste {
        inicializar()
        viewModel.startRecording(targetRotation = 0)
        advanceTimeBy(2_500)

        viewModel.pauseRecording()
        advanceTimeBy(5_000)
        assertEquals(2, viewModel.recordingSeconds.value, "pausado não pode continuar contando")

        viewModel.resumeRecording()
        advanceTimeBy(1_100)
        assertEquals(3, viewModel.recordingSeconds.value)
    }

    // ── Flash ───────────────────────────────────────────────────────────────

    @Test
    fun `no modo video o flash aciona a tocha continua`() = teste {
        inicializar()

        viewModel.toggleFlash()
        assertTrue(viewModel.isFlashOn.value)
        assertEquals(true, controller.lastTorchEnabled)

        viewModel.toggleFlash()
        assertFalse(viewModel.isFlashOn.value)
        assertEquals(false, controller.lastTorchEnabled)
    }

    @Test
    fun `no modo foto o flash cicla off auto on`() = teste {
        inicializar()
        viewModel.setCameraMode(CameraMode.PHOTO)

        viewModel.toggleFlash()
        assertEquals(PhotoFlashMode.AUTO, viewModel.photoFlashMode.value)
        assertEquals(ImageCapture.FLASH_MODE_AUTO, controller.lastPhotoFlashMode)

        viewModel.toggleFlash()
        assertEquals(PhotoFlashMode.ON, viewModel.photoFlashMode.value)
        assertEquals(ImageCapture.FLASH_MODE_ON, controller.lastPhotoFlashMode)

        viewModel.toggleFlash()
        assertEquals(PhotoFlashMode.OFF, viewModel.photoFlashMode.value)
        assertEquals(ImageCapture.FLASH_MODE_OFF, controller.lastPhotoFlashMode)
    }

    @Test
    fun `trocar de modo apaga a tocha`() = teste {
        inicializar()
        viewModel.toggleFlash()
        assertTrue(viewModel.isFlashOn.value)

        viewModel.setCameraMode(CameraMode.PHOTO)

        assertFalse(viewModel.isFlashOn.value, "tocha acesa não pode atravessar a troca de modo")
        assertEquals(false, controller.lastTorchEnabled)
    }

    // ── EIS por modo ────────────────────────────────────────────────────────

    @Test
    fun `modo foto desliga o EIS e voltar para video restaura o valor salvo`() = teste {
        storage.isStabilizationEnabled = true
        inicializar()
        assertTrue(viewModel.isStabilizationEnabled.value)

        // EIS não se aplica a captura de imagem.
        viewModel.setCameraMode(CameraMode.PHOTO)
        assertFalse(viewModel.isStabilizationEnabled.value)

        viewModel.setCameraMode(CameraMode.VIDEO)
        assertTrue(viewModel.isStabilizationEnabled.value, "deve voltar ao que estava persistido")

        // O desligamento em foto é temporário: não pode ter sido gravado.
        assertTrue(storage.isStabilizationEnabled)
    }

    @Test
    fun `aparelho sem suporte a EIS forca o desligamento`() = teste {
        storage.isStabilizationEnabled = true
        inicializar()
        assertTrue(viewModel.isStabilizationEnabled.value)

        controller.isEisSupportedFlow.value = false
        advanceUntilIdle()

        assertFalse(viewModel.isStabilizationEnabled.value)
        assertFalse(storage.isStabilizationEnabled, "precisa persistir, senão volta ligado no próximo boot")
    }

    @Test
    fun `HDR nao liga em aparelho sem suporte`() = teste {
        inicializar()
        controller.isHdrSupportedFlow.value = false
        advanceUntilIdle()

        viewModel.toggleHdr()

        assertFalse(viewModel.isHdrEnabled.value)
        assertFalse(storage.isHdrEnabled)
    }

    // ── Troca de modo bloqueada durante gravação ────────────────────────────

    @Test
    fun `nao troca de modo enquanto grava`() = teste {
        inicializar()
        viewModel.startRecording(targetRotation = 0)
        advanceTimeBy(100)
        assertEquals(RecordingState.Recording, viewModel.recordingState.value)

        viewModel.setCameraMode(CameraMode.PHOTO)

        assertEquals(CameraMode.VIDEO, viewModel.cameraMode.value)
    }

    // ── Persistência dos toggles ────────────────────────────────────────────

    @Test
    fun `toggles da tela de configuracoes sao persistidos`() = teste {
        inicializar()

        viewModel.toggleGrid()
        viewModel.toggleLevel()
        viewModel.toggleMic()
        viewModel.toggleTapToFocus()
        viewModel.toggleSaveLocation()
        viewModel.toggleImageEnhancement()
        viewModel.setBitratePreset(VideoBitratePreset.VERY_HIGH)
        viewModel.setPhotoQualityPreset(PhotoQualityPreset.MEDIA)

        assertTrue(storage.isGridEnabled)
        assertTrue(storage.isLevelEnabled)
        assertTrue(storage.isMicMuted)
        assertFalse(storage.isTapToFocusEnabled, "estava ligado por padrão, o toggle desliga")
        assertTrue(storage.isSaveLocationEnabled)
        assertTrue(storage.isImageEnhancementEnabled)
        assertEquals(VideoBitratePreset.VERY_HIGH, storage.bitratePreset)
        assertEquals(PhotoQualityPreset.MEDIA, storage.photoQualityPreset)

        // e chegaram ao controller
        assertEquals(VideoBitratePreset.VERY_HIGH, controller.bitratePreset)
        assertEquals(PhotoQualityPreset.MEDIA, controller.photoQualityPreset)
        assertTrue(controller.isSaveLocationEnabled)
        assertTrue(controller.isImageEnhancementEnabled)
    }

    @Test
    fun `com manter configuracoes desligado a inicializacao volta aos padroes`() = teste {
        // Usuário mexeu em tudo numa sessão anterior e desligou "manter configurações".
        storage.isGridEnabled = true
        storage.isMicMuted = true
        storage.isStabilizationEnabled = false
        storage.bitratePreset = VideoBitratePreset.LOW
        storage.isKeepSettingsEnabled = false

        inicializar()

        assertFalse(viewModel.isGridEnabled.value)
        assertFalse(viewModel.isMicMuted.value)
        assertTrue(viewModel.isStabilizationEnabled.value)
        assertEquals(VideoBitratePreset.MEDIUM, viewModel.bitratePreset.value)

        // E o reset foi gravado, não só aplicado em memória.
        assertFalse(storage.isGridEnabled)
        assertEquals(VideoBitratePreset.MEDIUM, storage.bitratePreset)
        assertFalse(storage.isKeepSettingsEnabled, "a própria opção não pode se auto-religar")
    }

    // ── Sincronização com o hardware ────────────────────────────────────────

    @Test
    fun `opcoes de video vindas do controller substituem a lista provisoria`() = teste {
        inicializar()
        val doAparelho = listOf(
            VideoOption(Quality.UHD, 30),
            VideoOption(Quality.FHD, 60),
        )

        controller.availableVideoOptionsFlow.value = doAparelho
        advanceUntilIdle()

        assertEquals(doAparelho, viewModel.availableVideoOptions.value)
        assertEquals(
            doAparelho.first(), viewModel.selectedVideoOption.value,
            "a seleção anterior (FHD 30) não existe nesta lista e deve cair na primeira"
        )
    }

    @Test
    fun `virar a camera desliga o flash`() = teste {
        inicializar()
        viewModel.toggleFlash()
        assertTrue(viewModel.isFlashOn.value)

        viewModel.flipCamera()

        assertTrue(viewModel.isFrontCamera.value)
        assertFalse(viewModel.isFlashOn.value, "não há flash na câmera frontal")
        assertEquals(1, controller.flipCount)
    }

    // ── Recriação da Activity (Q-02) ────────────────────────────────────────

    /**
     * ViewModel com factory que registra cada controller criado e o dono usado.
     * Diferente do `setUp`, que devolve sempre a mesma instância — aqui o que se
     * mede é justamente **quantos** controllers foram construídos.
     */
    private fun viewModelComFactoryContada(
        criados: MutableList<FakeCameraController>,
        donos: MutableList<LifecycleOwner>
    ) = CameraViewModel(
        controllerFactory = CameraControllerFactory { _, owner ->
            donos += owner
            FakeCameraController().also { criados += it }
        },
        settingsStorageFactory = { storage }
    )

    /**
     * Requirements: NFR-3
     * Decisions: Q-02
     *
     * Girar o tablet recria a Activity, mas o ViewModel sobrevive — e com ele o
     * controller, que guarda o `LifecycleOwner` no construtor. `bindToLifecycle`
     * num owner já DESTROYED registra o binding e **nunca o ativa**, sem lançar
     * exceção: medido em 2026-08-04, a câmera fecha no destroy e não reabre, e a
     * pré-visualização fica preta permanentemente (Q-02, Medição 3).
     *
     * O ViewModel precisa perceber que o dono mudou e reconstruir o controller.
     */
    @Test
    fun `recria o controller quando a Activity chega com outro LifecycleOwner`() =
        runTest(dispatcher) {
            val criados = mutableListOf<FakeCameraController>()
            val donos = mutableListOf<LifecycleOwner>()
            val vm = viewModelComFactoryContada(criados, donos)
            val donoAntigo = mockk<LifecycleOwner>(relaxed = true)
            val donoNovo = mockk<LifecycleOwner>(relaxed = true)

            vm.initializeCamera(context, donoAntigo, null)
            advanceUntilIdle()
            assertEquals(1, criados.size, "primeira ligação cria um controller")

            vm.initializeCamera(context, donoNovo, null)
            advanceUntilIdle()

            assertEquals(
                2, criados.size,
                "dono novo é Activity recriada: o controller preso ao dono morto não serve"
            )
            assertEquals(donoNovo, donos.last(), "o controller novo usa o dono novo")
            assertEquals(
                1, criados[0].releaseCount,
                "o controller antigo tem de ser liberado, senão a câmera fica presa a ele"
            )
            assertEquals(1, criados[1].initializeCount, "o controller novo é inicializado")
        }

    /**
     * Requirements: NFR-1
     * Decisions: Q-02
     *
     * O contraponto do teste acima, e a razão de a correção não poder ser "recriar
     * sempre": voltar de Configurações recria o `PreviewView` **dentro da mesma
     * Activity**, com o dono vivo. Aí reconstruir a câmera seria desperdício visível
     * (um ciclo de fechar/abrir a cada volta). O caminho certo é religar a surface.
     */
    @Test
    fun `reaproveita o controller quando o LifecycleOwner e o mesmo`() = runTest(dispatcher) {
        val criados = mutableListOf<FakeCameraController>()
        val donos = mutableListOf<LifecycleOwner>()
        val vm = viewModelComFactoryContada(criados, donos)
        val dono = mockk<LifecycleOwner>(relaxed = true)
        val surfaceNova = mockk<Preview.SurfaceProvider>(relaxed = true)

        vm.initializeCamera(context, dono, null)
        advanceUntilIdle()

        vm.initializeCamera(context, dono, surfaceNova)
        advanceUntilIdle()

        assertEquals(1, criados.size, "mesma Activity: nada a reconstruir")
        assertEquals(0, criados[0].releaseCount, "não pode liberar a câmera em uso")
        assertEquals(
            1, criados[0].surfaceProviderUpdates,
            "o PreviewView novo precisa receber a surface do Preview existente"
        )
        assertEquals(1, criados[0].rebindCount, "e os ajustes voltam a ser aplicados")
    }
}
