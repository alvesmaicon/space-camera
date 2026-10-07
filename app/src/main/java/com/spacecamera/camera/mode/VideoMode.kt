package com.spacecamera.camera.mode

/**
 * Requirements: FR-3
 * Decisions: ADR-001, ADR-002
 *
 * O modo Vídeo, declarado.
 *
 * Cada linha aqui reproduz, em declaração, algo que hoje é ramificação por
 * `CameraMode.VIDEO` espalhada por `CameraScreen`, `CameraViewModel` e
 * `CameraManager`. Nada de novo: a Tarefa 6 troca o `when` pela leitura destes
 * membros, e o critério de sucesso é o usuário não perceber diferença (FR-3).
 */
object VideoMode : CameraModeDefinition() {

    override val id = CameraModeId("video")

    override val label = "Vídeo"

    /**
     * Os três, como hoje. `bindCameraUseCases()` liga `Preview`, `VideoCapture` e
     * `ImageCapture` juntos nas três chamadas, sem olhar o modo — então declarar
     * os três **é** o iso-comportamento que o FR-3 exige (AC-2.2).
     */
    override val useCases = setOf(
        AppUseCase.PREVIEW,
        AppUseCase.VIDEO_CAPTURE,
        AppUseCase.IMAGE_CAPTURE
    )

    /** Roda em qualquer aparelho. */
    override val requiredCapability: Capability? = null

    /** Barra superior do Vídeo, na ordem em que aparece hoje. */
    override val controls = listOf(
        ControlId.RESOLUTION,
        ControlId.STABILIZATION,
        ControlId.FLASH,
        ControlId.TIMER,
        ControlId.MORE_OPTIONS
    )

    /** Linha expandida: ruído e microfone são só do vídeo. */
    override val moreControls = listOf(
        ControlId.NOISE_REDUCTION,
        ControlId.HDR,
        ControlId.MICROPHONE,
        ControlId.GRID,
        ControlId.SETTINGS
    )

    /** 16:9 sempre — a proporção escolhida no seletor é só da foto (AC-3.1). */
    override val aspectRatio = AspectRatioRule.FIXED_16_9

    /** O EIS segue a preferência salva; voltar da Foto restaura o que estava (AC-4.1). */
    override val stabilization = StabilizationRule.FOLLOWS_PREFERENCE

    override val output = CaptureOutput.VIDEO

    /** Sem camada própria sobre a pré-visualização. */
    override val overlay: OverlayId? = null

    /** Não sai do plano: é a função principal do app (premissa P2). */
    override val pinnedByDefault = true

    /**
     * A ordem das condições importa e é a de hoje: gravando, o disparador **para**
     * — mesmo com contagem regressiva em curso. Inverter faria o botão cancelar um
     * timer em vez de encerrar a gravação, que é perda de mídia do usuário.
     */
    override fun shutterAction(state: CaptureState): ShutterAction = when {
        state.recording -> ShutterAction.StopRecording
        state.countdownActive -> ShutterAction.CancelCountdown
        else -> ShutterAction.StartRecording
    }

    /** Lanterna contínua enquanto grava. */
    override fun flashBehavior(): FlashBehavior = FlashBehavior.Torch
}
