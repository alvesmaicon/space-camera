package com.spacecamera.camera.mode

import androidx.annotation.StringRes

/**
 * Requirements: FR-1, FR-2, FR-5, FR-8
 * Decisions: ADR-001, ADR-002
 *
 * Os tipos que um modo de câmera usa para se declarar. Nenhum deles conhece
 * CameraX, Compose ou o controller — é o que mantém o registro testável na JVM
 * pura (NFR-5) e o que permitirá trocar o interior do bind pelo `SessionConfig`
 * do CameraX 1.6 sem reescrever as declarações.
 */

/**
 * Identificador estável de um modo, como texto.
 *
 * **Nunca `ordinal`.** É isto que a preferência do usuário grava (FR-8, premissa
 * P3): a lista de modos é aberta e reordenável, e um modo inserido no meio
 * embaralharia toda a configuração se a chave fosse posicional.
 */
@JvmInline
value class CameraModeId(val value: String)

/**
 * O nome do modo na tela. Os modos do app apontam para um recurso de texto (traduzido);
 * os modos de teste usam um literal — é o que mantém o registro testável na JVM pura,
 * sem `Context` para resolver o texto.
 */
sealed interface ModeLabel {
    data class Resource(@StringRes val id: Int) : ModeLabel
    data class Literal(val text: String) : ModeLabel
}

/**
 * Use case da sessão, no vocabulário do app — **não** o tipo do CameraX.
 *
 * A tradução para `Preview`/`VideoCapture`/`ImageCapture` mora no `ModeBinder`
 * (Tarefa 6). Três motivos, do ADR-002: o registro fica testável sem instanciar
 * nada do CameraX; a migração para `SessionConfig` fica isolada num lugar só; e
 * configuração de use case (qualidade, bitrate, proporção) não vaza para a
 * declaração do modo, que diz *o quê*, não *como*.
 */
enum class AppUseCase { PREVIEW, VIDEO_CAPTURE, IMAGE_CAPTURE }

/**
 * Algo que o aparelho pode ou não oferecer, sondado no HAL.
 *
 * Hoje só há uma, a que o modo Pro exige. As capacidades que já existem no app
 * (EIS, HDR, ultra-wide) continuam onde estão: cada uma tem seu par de
 * `StateFlow` no `CameraManager`, e trazê-las para cá seria outra spec.
 */
enum class Capability { MANUAL_SENSOR }

/**
 * Um controle da barra superior. A definição do modo diz **quais** e em que
 * ordem; quem desenha é a UI.
 */
enum class ControlId {
    RESOLUTION,
    STABILIZATION,
    PHOTO_QUALITY,
    ASPECT_RATIO,
    FLASH,
    TIMER,
    MORE_OPTIONS,

    // Linha expandida ("mais opções")
    NOISE_REDUCTION,
    IMAGE_ENHANCEMENT,
    HDR,
    MICROPHONE,
    GRID,
    SETTINGS
}

/** Requirements: FR-3, FR-4 · Como a proporção de captura é escolhida. */
enum class AspectRatioRule {
    /** Sempre 16:9, qualquer que seja a proporção escolhida para foto (AC-3.1). */
    FIXED_16_9,

    /** A proporção que o usuário escolheu no seletor de foto (9:16, 3:4, Full). */
    USER_SELECTED
}

/** Requirements: FR-3, FR-4 · Se a estabilização eletrônica vale neste modo. */
enum class StabilizationRule {
    /** Liga ou desliga conforme a preferência salva do usuário. */
    FOLLOWS_PREFERENCE,

    /** Desligada enquanto o modo estiver ativo, sem mexer na preferência (AC-4.1). */
    OFF
}

/**
 * O que o modo produz. Decide a miniatura do canto (último vídeo com pausa, ou
 * última foto) e o desenho do disparador — não o que ele faz, que é [ShutterAction].
 */
enum class CaptureOutput { VIDEO, PHOTO }

/**
 * Overlay próprio de um modo — a camada que só aquele modo desenha sobre a
 * pré-visualização, como as escalas de ISO e obturador do Pro.
 *
 * Texto e não enum porque a lista é aberta: cada modo novo traz o seu. A UI
 * resolve identificador → composable em [com.spacecamera.presentation.components.ModeSurfaces],
 * e o preço dessa indireção é um erro que sairia do compilador virar erro de
 * execução. O contrapeso obrigatório é o teste de completude que varre o
 * registro (ADR-001, §8.3 do design).
 */
@JvmInline
value class OverlayId(val value: String)

/**
 * O que o app está fazendo no momento em que o disparador é tocado.
 *
 * Existe para que [CameraModeDefinition.shutterAction] seja função pura do
 * estado, testável sem ViewModel.
 */
data class CaptureState(
    val recording: Boolean,
    val countdownActive: Boolean
)

/**
 * O que o disparador **faz** neste modo e neste estado.
 *
 * A definição do modo *descreve* a ação; quem executa é o ViewModel. Executar
 * aqui inverteria a dependência e traria o controller para dentro do registro
 * (guarda-corpo do ADR-001).
 */
sealed class ShutterAction {
    object StartRecording : ShutterAction()
    object StopRecording : ShutterAction()
    object CapturePhoto : ShutterAction()
    object CancelCountdown : ShutterAction()
}

/**
 * Como o flash se comporta neste modo.
 *
 * É um dos dois casos que decidiram o ADR-001 pela forma híbrida: tocha em Vídeo
 * e ciclo OFF→AUTO→ON em Foto não é escolha de conjunto fechado, é comportamento.
 * Com dados puros isso continuaria num `when` do ViewModel, e o modo novo voltaria
 * a editar arquivo grande — matando o NFR-2 na prática.
 */
sealed class FlashBehavior {
    /** Liga e desliga a lanterna continuamente. */
    object Torch : FlashBehavior()

    /** Cicla OFF → AUTO → ON, sem tocha. */
    object PhotoCycle : FlashBehavior()

    /** O modo não oferece flash. */
    object Unavailable : FlashBehavior()
}

/**
 * A preferência do usuário: em que ordem os modos aparecem e quais ficam no
 * plano (contra a gaveta).
 *
 * Guarda **identificadores em texto**, não definições, porque é o que se
 * persiste — e porque precisa sobreviver a identificadores que este aparelho ou
 * esta versão não conhece. Ver o fluxo 4.5 do design e o NFR-8.
 *
 * @param order ordem gravada. Modos ausentes entram depois, na ordem do registro.
 * @param pinned quais ficam no plano. Os demais vão para a gaveta.
 */
data class ModeArrangement(
    val order: List<String>,
    val pinned: Set<String>
) {
    companion object {
        /** Nenhuma preferência gravada: o registro decide (AC-8.2). */
        val DEFAULT = ModeArrangement(order = emptyList(), pinned = emptySet())
    }
}

/**
 * Resultado de aplicar uma preferência ao registro: o que vai no plano e o que
 * vai na gaveta, já filtrado por capacidade e já na ordem certa.
 */
data class ArrangedModes(
    val pinned: List<CameraModeDefinition>,
    val drawer: List<CameraModeDefinition>,
    /** Todos os disponíveis na ordem do usuário, plano e gaveta intercalados — a lista da personalização. */
    val ordered: List<CameraModeDefinition> = pinned + drawer
) {
    /**
     * O que o carrossel do seletor mostra: o plano — mais o modo ativo, se ele veio
     * da gaveta. O modo ativo nunca some da tela (FR-14).
     */
    fun carousel(active: CameraModeDefinition): List<CameraModeDefinition> =
        if (active in pinned) pinned else pinned + active

    /**
     * O modo vizinho no gesto de deslizar: anda pelo plano e dá a volta, como o
     * `CameraMode.next()` fazia. Não entra na gaveta; a partir de um modo dela, volta
     * ao começo (ou ao fim) do plano.
     */
    fun step(active: CameraModeDefinition, by: Int): CameraModeDefinition {
        if (pinned.isEmpty()) return active
        val i = pinned.indexOf(active)
        if (i < 0) return if (by > 0) pinned.first() else pinned.last()
        return pinned[((i + by) % pinned.size + pinned.size) % pinned.size]
    }
}
