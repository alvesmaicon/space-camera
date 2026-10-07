package com.spacecamera.presentation.components

import com.spacecamera.camera.mode.ControlId
import com.spacecamera.camera.mode.OverlayId

/**
 * Requirements: FR-1, NFR-5
 * Decisions: ADR-001
 *
 * A tabela que resolve identificador → superfície de UI.
 *
 * Existe porque a definição de modo não pode expor `@Composable`: isso poria UI no
 * domínio e tiraria o registro do alcance do teste JVM puro — foi exatamente a
 * linha que reprovou a alternativa por herança na matriz do ADR-001.
 *
 * **O preço dessa escolha** é que um identificador declarado sem correspondente
 * aqui deixa de ser erro de compilação e vira erro de execução, que numa câmera
 * significa tela quebrada na mão do usuário. O contrapeso obrigatório é o teste de
 * completude em `CameraModeDefinitionTest`, que varre o registro e falha se algo
 * não resolver. Se você acrescentar uma entrada aqui, ele também cobra que algum
 * modo a use — entrada órfã é código morto, do tipo que a Q-01 encontrou no
 * `cameraXAspectRatio()`.
 *
 * Os conjuntos abaixo são **dados puros**, sem tipo do Compose, para que o teste
 * possa lê-los sem carregar o Robolectric.
 */
object ModeSurfaces {

    /**
     * Overlays com composable registrado.
     *
     * Vazio de propósito: Vídeo e Foto não desenham camada própria. O primeiro
     * habitante é o `PRO_SCALES` da Tarefa 11 — e é a partir dali que o teste de
     * completude deixa de passar vazio e passa a valer de fato.
     */
    val overlays: Set<OverlayId> = emptySet()

    /** Controles da barra superior com composable registrado. */
    val controls: Set<ControlId> = setOf(
        ControlId.RESOLUTION,
        ControlId.STABILIZATION,
        ControlId.PHOTO_QUALITY,
        ControlId.ASPECT_RATIO,
        ControlId.FLASH,
        ControlId.TIMER,
        ControlId.MORE_OPTIONS,
        ControlId.NOISE_REDUCTION,
        ControlId.IMAGE_ENHANCEMENT,
        ControlId.HDR,
        ControlId.MICROPHONE,
        ControlId.GRID,
        ControlId.SETTINGS
    )
}
