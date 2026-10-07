package com.spacecamera.camera.mode

import com.spacecamera.presentation.components.ModeSurfaces

/**
 * Requirements: FR-2, FR-5, FR-9, FR-12
 * Decisions: ADR-001, ADR-007, Q-11
 *
 * O modo Pro: foto com ISO manual (e obturador, na Tarefa 12).
 *
 * É a prova da costura — o primeiro modo que entra **só** por ela: este arquivo e uma
 * linha em [ModeRegistry.all]. Nada em `CameraScreen`, `CameraViewModel` ou
 * `CameraManager` sabe que ele existe; tudo o que o distingue está declarado aqui.
 */
object ProMode : CameraModeDefinition() {

    override val id = CameraModeId("pro")

    override val label = "Pro"

    /** Sem `VideoCapture`: o Pro é modo de foto (ADR-007, AC-2.1). */
    override val useCases = setOf(AppUseCase.PREVIEW, AppUseCase.IMAGE_CAPTURE)

    /** Só existe onde o HAL aceita controle manual (FR-5, premissa P1). */
    override val requiredCapability = Capability.MANUAL_SENSOR

    /**
     * Os controles da Foto, menos o flash: com o AE desligado, o flash automático do
     * CameraX não dispara de forma confiável, e um botão que falha é pior que nenhum.
     */
    override val controls = listOf(
        ControlId.PHOTO_QUALITY,
        ControlId.ASPECT_RATIO,
        ControlId.TIMER,
        ControlId.MORE_OPTIONS
    )

    /** Sem HDR: o scene mode HDR assume a exposição e anularia o ISO manual. */
    override val moreControls = listOf(
        ControlId.IMAGE_ENHANCEMENT,
        ControlId.GRID,
        ControlId.SETTINGS
    )

    override val aspectRatio = AspectRatioRule.USER_SELECTED

    override val stabilization = StabilizationRule.OFF

    override val output = CaptureOutput.PHOTO

    /** O slider de ISO na lateral da pré-visualização. */
    override val overlay = ModeSurfaces.PRO_SCALES

    /** Vem na gaveta; o usuário traz para o plano se quiser (decisão de 2026-10-07). */
    override val pinnedByDefault = false

    /** Como na Foto: só a contagem regressiva desvia do caminho de capturar. */
    override fun shutterAction(state: CaptureState): ShutterAction =
        if (state.countdownActive) ShutterAction.CancelCountdown else ShutterAction.CapturePhoto

    override fun flashBehavior(): FlashBehavior = FlashBehavior.Unavailable
}
