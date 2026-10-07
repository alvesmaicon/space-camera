package com.spacecamera.presentation.viewmodels

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.video.Quality
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.spacecamera.camera.CameraControllerFactory
import com.spacecamera.camera.FakeCameraController
import com.spacecamera.camera.ModeRejection
import com.spacecamera.camera.PhotoQualityPreset
import com.spacecamera.camera.RecordingState
import com.spacecamera.camera.VideoBitratePreset
import com.spacecamera.camera.VideoOption
import com.spacecamera.camera.mode.CapabilityProbe
import com.spacecamera.camera.mode.Capability
import com.spacecamera.camera.mode.ModeArrangement
import com.spacecamera.camera.mode.ModeRegistry
import com.spacecamera.camera.mode.SensorCharacteristics
import com.spacecamera.camera.mode.modoDeTeste
import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.camera.mode.PhotoMode
import com.spacecamera.camera.mode.ProMode
import com.spacecamera.camera.mode.ShutterAction
import com.spacecamera.camera.mode.VideoMode
import com.spacecamera.data.storage.SettingsStorage
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import kotlin.test.assertNull
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
        assertEquals(VideoMode, viewModel.activeMode.value)
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
        viewModel.selectMode(PhotoMode)
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
        viewModel.selectMode(PhotoMode)

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

        viewModel.selectMode(PhotoMode)

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
        viewModel.selectMode(PhotoMode)
        assertFalse(viewModel.isStabilizationEnabled.value)

        viewModel.selectMode(VideoMode)
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

        viewModel.selectMode(PhotoMode)

        assertEquals(VideoMode, viewModel.activeMode.value)
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

    /**
     * Requirements: NFR-1
     * Decisions: Q-03
     *
     * Regressão medida em aparelho real (Motorola edge 60 neo, Android 16): com EIS
     * suportado pelo hardware, o app subia com EIS **desligado** e gravava isso no
     * storage. O `evt=caps` dizia `eis_supported=true` e o `evt=bind` seguinte,
     * `eis=false`.
     *
     * A causa é ordem de inicialização. `_isEisSupported` do `CameraManager` começa
     * `false`, e esse valor significa "ainda não sondado" — indistinguível de "não
     * suportado". A sondagem só acontece dentro de `initializeCamera`, que é o bind.
     * Se o coletor de `isEisSupported` sobe **antes** disso, ele recebe o `false`
     * inicial, conclui "sem suporte" e desliga o EIS de forma persistente.
     *
     * Nenhum emulador pega: lá o EIS realmente não é suportado, então
     * `eis_supported=false` nos dois casos. E nenhum teste pegava porque o
     * `FakeCameraController` começa com os `*Flow` em `true`, mais otimista que a
     * produção — daí o gancho [FakeCameraController.aoInicializar], que reproduz a
     * transição false → true no momento do bind.
     */
    @Test
    fun `EIS suportado permanece ligado quando a sondagem ocorre no bind`() = runTest {
        // `viewModelScope` usa `Dispatchers.Main.immediate`, que roda o `launch` de
        // forma **eager** até a primeira suspensão — é isso que faz o coletor receber o
        // valor inicial antes de o bind sondar. O `StandardTestDispatcher` do resto da
        // suíte enfileira o `launch` e esconde o defeito: a primeira versão deste teste
        // passou com o bug presente. `Unconfined` reproduz a execução eager.
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = CameraViewModel(
            controllerFactory = CameraControllerFactory { _, _ -> controller },
            settingsStorageFactory = { storage }
        )
        controller.isEisSupportedFlow.value = false          // como o CameraManager real
        controller.aoInicializar = { controller.isEisSupportedFlow.value = true }

        vm.initializeCamera(context, mockk<LifecycleOwner>(relaxed = true), null)
        advanceUntilIdle()

        assertTrue(
            vm.isEisSupported.value,
            "a sondagem no bind precisa chegar ao ViewModel"
        )
        assertTrue(
            vm.isStabilizationEnabled.value,
            "EIS suportado não pode ser desligado pelo valor inicial de 'ainda não sondado'"
        )
        assertTrue(
            storage.isStabilizationEnabled,
            "e o desligamento indevido não pode ser persistido"
        )
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

    // ── Despacho pela definição do modo (Tarefa 6) ──────────────────────────
    //
    // Requirements: FR-2, FR-3, FR-15 · Decisions: ADR-001, ADR-002
    //
    // O disparador e o flash deixam de ramificar por `CameraMode` e passam a
    // perguntar à definição do modo. O que se testa aqui é que a pergunta chega e
    // que a resposta é executada — o conteúdo das respostas tem teste JVM puro em
    // `CameraModeDefinitionTest`.

    @Test
    fun `disparador no video em repouso inicia a gravacao`() = teste {
        inicializar()

        val acao = viewModel.onShutter(targetRotation = 0)
        advanceTimeBy(50)

        assertEquals(ShutterAction.StartRecording, acao)
        assertEquals(1, controller.startRecordingCount)
    }

    @Test
    fun `disparador no video gravando para a gravacao`() = teste {
        inicializar()
        viewModel.startRecording(targetRotation = 0)
        advanceTimeBy(100)

        val acao = viewModel.onShutter(targetRotation = 0)
        advanceTimeBy(50)

        assertEquals(ShutterAction.StopRecording, acao)
        assertEquals(1, controller.stopRecordingCount)
    }

    @Test
    fun `disparador durante a contagem cancela em vez de disparar de novo`() = teste {
        inicializar()
        viewModel.cycleRecordingDelay() // 3s
        viewModel.onShutter(targetRotation = 0)
        advanceTimeBy(1_500)

        val acao = viewModel.onShutter(targetRotation = 0)
        advanceTimeBy(5_000)

        assertEquals(ShutterAction.CancelCountdown, acao)
        assertEquals(0, controller.startRecordingCount)
        assertEquals(0, viewModel.countdownSeconds.value)
    }

    @Test
    fun `disparador na foto captura`() = teste {
        inicializar()
        viewModel.selectMode(PhotoMode)

        val acao = viewModel.onShutter(targetRotation = 0)
        advanceTimeBy(50)

        assertEquals(ShutterAction.CapturePhoto, acao)
        assertEquals(1, controller.takePhotoCount)
        assertEquals(0, controller.startRecordingCount)
    }

    @Test
    fun `trocar de modo entrega a definicao ao controller`() = teste {
        inicializar()

        viewModel.selectMode(PhotoMode)
        assertEquals(PhotoMode, controller.lastAppliedMode)

        viewModel.selectMode(VideoMode)
        assertEquals(VideoMode, controller.lastAppliedMode)
    }

    @Test
    fun `modo recusado pelo aparelho volta o seletor e o EIS para o modo restaurado`() = teste {
        // Fluxo 4.2 do design: o controller já religou o conjunto anterior; o
        // ViewModel só precisa parar de mostrar o modo que não existe na sessão.
        storage.isStabilizationEnabled = true
        inicializar()
        viewModel.selectMode(PhotoMode)
        assertFalse(viewModel.isStabilizationEnabled.value)

        controller.modeRejectionsFlow.emit(ModeRejection(rejected = PhotoMode.id, active = VideoMode.id))
        advanceUntilIdle()

        assertEquals(VideoMode, viewModel.activeMode.value)
        assertTrue(viewModel.isStabilizationEnabled.value, "o EIS do vídeo precisa voltar junto")
    }

    // ── Modos vindos do registro (Tarefa 8) ─────────────────────────────────
    //
    // Requirements: FR-1, FR-6 · Decisions: ADR-001, ADR-004

    @Test
    fun `modo inicial e o primeiro do registro`() = teste {
        assertEquals(ModeRegistry.all.first(), viewModel.activeMode.value)
    }

    @Test
    fun `arranjo de modos comeca no padrao do registro`() = teste {
        // AC-8.2: sem preferência gravada (a persistência chega na Tarefa 10).
        inicializar()

        assertEquals(listOf(VideoMode, PhotoMode), viewModel.arrangedModes.value.pinned)
        assertTrue(viewModel.arrangedModes.value.drawer.isEmpty())
    }

    @Test
    fun `modo exigente entra na gaveta quando o aparelho reporta a capacidade`() = teste {
        // FR-5 na ponta do ViewModel: antes da sondagem nada exigente aparece; quando
        // o controller publica MANUAL_SENSOR, o arranjo se refaz. O registro de
        // produção ainda não tem modo exigente (o Pro é a Tarefa 11), então o teste
        // injeta um.
        val exigente = modoDeTeste("exigente", capability = Capability.MANUAL_SENSOR)
        val vm = CameraViewModel(
            controllerFactory = CameraControllerFactory { _, _ -> controller },
            settingsStorageFactory = { storage },
            modeRegistry = listOf(VideoMode, PhotoMode, exigente)
        )
        vm.initializeCamera(context, mockk<LifecycleOwner>(relaxed = true), null)
        advanceUntilIdle()
        assertTrue(exigente !in vm.arrangedModes.value.drawer, "sem sondagem, o modo não pode aparecer")

        controller.deviceCapabilitiesFlow.value = CapabilityProbe.decide(
            SensorCharacteristics(
                requestCapabilities = listOf(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR),
                sensitivityRange = 100..3200,
                exposureTimeRangeNs = 65_424L..30_071_705_440L,
                maxAnalogSensitivity = 3200
            )
        )
        advanceUntilIdle()

        assertEquals(listOf(exigente), vm.arrangedModes.value.drawer)
    }

    // ── Modo Pro: ISO manual (Tarefa 11) ────────────────────────────────────
    //
    // Requirements: FR-9, FR-12, AC-9.1 · Decisions: ADR-007, Q-11

    /** O que o Redmi Note 10 reportou na câmera traseira. */
    private fun publicarCapacidadeManual(iso: IntRange = 100..3200) {
        controller.deviceCapabilitiesFlow.value = CapabilityProbe.decide(
            SensorCharacteristics(
                requestCapabilities = listOf(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR),
                sensitivityRange = iso,
                exposureTimeRangeNs = 65_424L..30_071_705_440L,
                maxAnalogSensitivity = iso.last
            )
        )
    }

    @Test
    fun `ISO pedido fora da faixa e limitado antes de chegar ao controller`() = teste {
        // AC-9.1, na ponta do ViewModel.
        inicializar()
        publicarCapacidadeManual(iso = 50..3200)
        advanceUntilIdle()
        viewModel.selectMode(ProMode)

        viewModel.setIso(6400)

        assertEquals(3200, viewModel.manualExposure.value.iso)
        assertEquals(3200, controller.lastManualIso)
    }

    @Test
    fun `ISO automatico devolve o controle ao AE`() = teste {
        inicializar()
        publicarCapacidadeManual()
        advanceUntilIdle()
        viewModel.selectMode(ProMode)
        viewModel.setIso(800)

        viewModel.setIso(null)

        assertNull(viewModel.manualExposure.value.iso)
        assertEquals(1, controller.manualIsoCalls.count { it == null })
    }

    @Test
    fun `sair do Pro volta o ISO ao automatico`() = teste {
        // Nenhum outro modo tem escala de ISO: se o valor manual sobrevivesse à troca,
        // a Foto sairia com exposição travada e sem controle para destravar.
        inicializar()
        publicarCapacidadeManual()
        advanceUntilIdle()
        viewModel.selectMode(ProMode)
        viewModel.setIso(800)

        viewModel.selectMode(PhotoMode)

        assertNull(viewModel.manualExposure.value.iso)
        assertNull(controller.lastManualIso)
    }

    @Test
    fun `obturador pedido fora da faixa e limitado antes de chegar ao controller`() = teste {
        // AC-10.1, na ponta do ViewModel. O sensor aceita até ~30 s, mas a escala vai
        // até 1/4 s: acima disso a captura demora dezenas de segundos ou falha (Q-13).
        inicializar()
        publicarCapacidadeManual()
        advanceUntilIdle()
        viewModel.selectMode(ProMode)

        viewModel.setShutter(60_000_000_000L)

        assertEquals(250_000_000L, viewModel.manualExposure.value.exposureNs)
        assertEquals(250_000_000L, controller.lastManualExposureNs)
    }

    @Test
    fun `ISO e obturador em automatico devolvem o AE`() = teste {
        // AC-11.1: só com os dois em AUTO o pedido some e o AE volta.
        inicializar()
        publicarCapacidadeManual()
        advanceUntilIdle()
        viewModel.selectMode(ProMode)
        viewModel.setIso(800)
        viewModel.setShutter(10_000_000L)

        viewModel.setIso(null)
        assertEquals(null to 10_000_000L, controller.manualCalls.last(), "obturador ainda manual")

        viewModel.setShutter(null)
        assertEquals(null to null, controller.manualCalls.last())
    }

    @Test
    fun `sem capacidade manual o ISO pedido e ignorado`() = teste {
        inicializar()

        viewModel.setIso(800)

        assertNull(viewModel.manualExposure.value.iso)
        assertTrue(controller.manualIsoCalls.isEmpty())
    }

    @Test
    fun `faixas do aparelho chegam a tela`() = teste {
        // A escala mostra exatamente o que o HAL reportou (AC-5.2).
        inicializar()
        publicarCapacidadeManual(iso = 100..3200)
        advanceUntilIdle()

        assertEquals(100..3200, viewModel.manualSensorRanges.value?.iso)
    }

    @Test
    fun `modo recusado vira aviso para a tela`() = teste {
        // Fluxo 4.2 do design, o "aviso discreto" que a Q-07 deixou para a Tarefa 11.
        inicializar()
        val avisos = mutableListOf<CameraModeDefinition>()
        val coleta = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.modeRejected.collect { avisos += it }
        }
        viewModel.selectMode(PhotoMode)

        controller.modeRejectionsFlow.emit(ModeRejection(rejected = PhotoMode.id, active = VideoMode.id))
        advanceUntilIdle()

        assertEquals(listOf<CameraModeDefinition>(PhotoMode), avisos)
        coleta.cancel()
    }

    // ── Personalização persistida (Tarefa 10) ───────────────────────────────
    //
    // Requirements: FR-7, FR-8, FR-16, AC-7.2, AC-16.1 · Decisions: ADR-004

    @Test
    fun `personalizacao e gravada e volta ao reabrir o app`() = teste {
        // O roteiro manual da tarefa — reordenar, sair, reabrir — em JVM.
        inicializar()
        publicarCapacidadeManual()
        advanceUntilIdle()
        viewModel.setModePinned(ProMode, true)
        viewModel.moveMode(ProMode, by = -2)

        val reaberto = CameraViewModel(
            controllerFactory = CameraControllerFactory { _, _ -> controller },
            settingsStorageFactory = { SettingsStorage(context) }
        )
        reaberto.initializeCamera(context, mockk<LifecycleOwner>(relaxed = true), null)
        advanceUntilIdle()

        assertEquals(listOf(ProMode, VideoMode, PhotoMode), reaberto.arrangedModes.value.pinned)
    }

    @Test
    fun `restaurar padrao tambem e gravado`() = teste {
        // AC-16.1: "volta ao padrão e persiste assim".
        inicializar()
        publicarCapacidadeManual()
        advanceUntilIdle()
        viewModel.setModePinned(ProMode, true)

        viewModel.restoreDefaultModes()

        assertEquals(ModeArrangement.DEFAULT, storage.modeArrangement)
    }
}
