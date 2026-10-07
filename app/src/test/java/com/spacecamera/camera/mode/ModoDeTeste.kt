package com.spacecamera.camera.mode

/**
 * Um modo de mentira para teste, com tudo no padrão de "modo de foto que vai para a
 * gaveta". Existe porque `CameraModeDefinition` é `abstract` e cada teste que
 * precisava de um terceiro modo declarava um objeto anônimo inteiro (Q-05).
 */
fun modoDeTeste(
    id: String,
    label: String = id,
    pinned: Boolean = false,
    capability: Capability? = null
): CameraModeDefinition = object : CameraModeDefinition() {
    override val id = CameraModeId(id)
    override val label = label
    override val useCases = setOf(AppUseCase.PREVIEW, AppUseCase.IMAGE_CAPTURE)
    override val requiredCapability = capability
    override val controls = emptyList<ControlId>()
    override val moreControls = emptyList<ControlId>()
    override val aspectRatio = AspectRatioRule.USER_SELECTED
    override val stabilization = StabilizationRule.OFF
    override val output = CaptureOutput.PHOTO
    override val overlay: OverlayId? = null
    override val pinnedByDefault = pinned
    override fun shutterAction(state: CaptureState) = ShutterAction.CapturePhoto
    override fun flashBehavior() = FlashBehavior.PhotoCycle
    override fun toString() = "modoDeTeste($id)"
}
