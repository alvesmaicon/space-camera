package com.spacecamera.camera.mode

/**
 * Requirements: FR-4
 * Decisions: ADR-001, ADR-002
 *
 * O modo Foto, declarado.
 *
 * Mesmo princípio do [VideoMode]: isto reproduz o que já existe, não propõe nada.
 * A Tarefa 7 é que troca as ramificações por leitura destes membros.
 */
object PhotoMode : CameraModeDefinition() {

    override val id = CameraModeId("photo")

    override val label = "Foto"

    /**
     * Os três, e isto merece explicação porque parece errado.
     *
     * A Foto não precisaria de `VideoCapture` — mas hoje ela liga, porque o bind é
     * cego ao modo. Declarar só `Preview` + `ImageCapture` aqui seria uma melhoria
     * **e** uma mudança de comportamento observável, justo na tarefa cujo requisito
     * é não mudar nada (FR-4, NFR-1).
     *
     * O conjunto reduzido tem dono: é o modo Pro (Tarefa 11), que existe exatamente
     * para exercitar o bind por modo — e que, por ser modo novo, não tem
     * iso-comportamento a preservar. Enxugar a Foto fica para depois de o Pro provar
     * que o caminho de foto se comporta igual sem o `VideoCapture` na sessão, que é
     * um risco registrado da spec.
     */
    override val useCases = setOf(
        AppUseCase.PREVIEW,
        AppUseCase.VIDEO_CAPTURE,
        AppUseCase.IMAGE_CAPTURE
    )

    /** Roda em qualquer aparelho. */
    override val requiredCapability: Capability? = null

    /** Barra superior da Foto, na ordem em que aparece hoje. */
    override val controls = listOf(
        ControlId.PHOTO_QUALITY,
        ControlId.ASPECT_RATIO,
        ControlId.FLASH,
        ControlId.TIMER,
        ControlId.MORE_OPTIONS
    )

    /** Sem camada própria sobre a pré-visualização. */
    override val overlay: OverlayId? = null

    /** Não sai do plano (premissa P2). */
    override val pinnedByDefault = true

    /**
     * Nunca grava. Só a contagem regressiva desvia do caminho de capturar.
     *
     * `state.recording` não é consultado de propósito: a Foto não pode estar
     * gravando, e tratar esse caso aqui sugeriria que pode.
     */
    override fun shutterAction(state: CaptureState): ShutterAction =
        if (state.countdownActive) ShutterAction.CancelCountdown else ShutterAction.CapturePhoto

    /** Ciclo OFF → AUTO → ON, sem tocha contínua. */
    override fun flashBehavior(): FlashBehavior = FlashBehavior.PhotoCycle
}
