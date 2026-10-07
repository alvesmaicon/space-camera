package com.spacecamera.camera

import com.spacecamera.camera.mode.AppUseCase
import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.camera.mode.CameraModeId
import com.spacecamera.camera.mode.ModeBinder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Requirements: FR-2, FR-13
 * Decisions: ADR-002
 *
 * O modo **pedido** e o modo **ligado** na sessão, que nem sempre coincidem: entre o
 * pedido e o bind há um `postDelayed`, e o aparelho pode recusar a combinação.
 *
 * Decide duas coisas que quebrariam caladas: quando sai `evt=mode` (só quando o
 * modo ligado muda — um rebind por proporção não é troca de modo) e o que fazer
 * quando o aparelho recusa um modo (fluxo 4.2 do design, via [ModeBinder]).
 *
 * Mora fora do `CameraManager` pelo NFR-3.
 */
internal class ModeSession(initial: CameraModeDefinition) {

    /** O modo que a sessão deve ter. Volta ao restaurado se o aparelho recusar. */
    var current: CameraModeDefinition = initial
        private set

    /** O que de fato está ligado — é o "anterior" que se religa numa recusa. */
    private var bound: CameraModeDefinition? = null

    /** O último modo anunciado em `evt=mode`. */
    private var reported: CameraModeDefinition? = null

    /**
     * Sem replay de propósito: um coletor que chega depois não pode receber uma
     * recusa velha e reverter um modo que já está certo.
     */
    private val _rejections = MutableSharedFlow<ModeRejection>(extraBufferCapacity = 1)
    val rejections: SharedFlow<ModeRejection> = _rejections

    /** Para onde vai o anúncio da troca. Trocável só para teste. */
    var onModeChanged: (CameraModeId?, CameraModeId, Set<AppUseCase>, Long) -> Unit = ModeTelemetry::modeChanged

    /** @return `false` se [mode] já é o pedido — nada a religar. */
    fun request(mode: CameraModeDefinition): Boolean {
        if (current === mode) return false
        current = mode
        return true
    }

    /**
     * Liga [current] por [bindSet]. Se o aparelho recusar, religa o modo ligado antes,
     * avisa em [rejections] e chama [onRestored] — os use cases foram montados para o
     * modo recusado (proporção inclusive) e precisam ser refeitos.
     *
     * @throws Throwable a recusa original, quando nada ficou ligado.
     */
    fun <C> bind(onRestored: () -> Unit, bindSet: (CameraModeDefinition) -> C): C =
        when (val r = ModeBinder.bind(current, bound, bindSet)) {
            is ModeBinder.Outcome.Bound -> r.camera.also { bound = r.mode }
            is ModeBinder.Outcome.Failed -> throw r.cause
            is ModeBinder.Outcome.Restored -> r.camera.also {
                ModeTelemetry.modeRejected(r.rejected.id, r.mode.id, r.cause)
                bound = r.mode
                current = r.mode
                _rejections.tryEmit(ModeRejection(r.rejected.id, r.mode.id))
                onRestored()
            }
        }

    /** Anuncia `evt=mode` se o modo ligado mudou desde o último anúncio. */
    fun reportIfChanged(elapsedMs: Long) {
        val ligado = bound ?: return
        if (ligado === reported) return
        onModeChanged(reported?.id, ligado.id, ligado.useCases, elapsedMs)
        reported = ligado
    }
}
