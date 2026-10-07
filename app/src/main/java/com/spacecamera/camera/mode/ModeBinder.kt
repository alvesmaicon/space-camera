package com.spacecamera.camera.mode

/**
 * Requirements: FR-2, NFR-1, NFR-3
 * Decisions: ADR-002
 *
 * Traduz o conjunto de [AppUseCase] declarado pelo modo em use cases do
 * controller, e aplica a regra de falha do fluxo 4.2 do design.
 *
 * Genérico no tipo do use case e no da câmera **de propósito**: assim não conhece
 * CameraX e se testa na JVM pura, com texto no lugar dos objetos. Quem fornece os
 * use cases construídos e a chamada de `bindToLifecycle` é o `CameraManager`.
 *
 * Mora fora do `CameraManager` pelo NFR-3 — aquele arquivo não pode ganhar linha
 * líquida nesta spec, e a tradução é a maior adição dela. É também o lugar único
 * que a migração para o `SessionConfig` do CameraX 1.6 vai trocar.
 */
object ModeBinder {

    /**
     * Ordem em que os use cases vão para o `bindToLifecycle`: a de antes da
     * refatoração. Não é contrato do CameraX, mas iso-comportamento (NFR-1) não
     * aposta no que "não deveria" importar.
     */
    private val BIND_ORDER = listOf(AppUseCase.PREVIEW, AppUseCase.VIDEO_CAPTURE, AppUseCase.IMAGE_CAPTURE)

    /** [useCases] na ordem fixa de bind — também a ordem estável da telemetria. */
    fun ordered(useCases: Set<AppUseCase>): List<AppUseCase> = BIND_ORDER.filter { it in useCases }

    /**
     * Os use cases que o bind deve ligar: exatamente os declarados, e nenhum outro
     * (FR-2).
     *
     * @throws IllegalStateException se um use case declarado não tiver construtor em
     *   [available]. Acontece se o [AppUseCase] ganhar um valor e ninguém ensinar o
     *   controller a construí-lo — sem esta falha, o modo ligaria menos do que
     *   declarou, calado.
     */
    fun <T> resolve(declared: Set<AppUseCase>, available: Map<AppUseCase, T>): List<T> =
        ordered(declared).map { checkNotNull(available[it]) { "use case $it declarado sem construtor" } }

    /** Como terminou o bind de um modo. */
    sealed class Outcome<out C> {
        /** O modo pedido está ligado. */
        data class Bound<C>(val camera: C, val mode: CameraModeDefinition) : Outcome<C>()

        /** O aparelho recusou [rejected]; o conjunto de [mode] foi religado no lugar. */
        data class Restored<C>(
            val camera: C,
            val mode: CameraModeDefinition,
            val rejected: CameraModeDefinition,
            val cause: Throwable
        ) : Outcome<C>()

        /** Nada ficou ligado. [cause] é a primeira recusa; as seguintes vão em `suppressed`. */
        data class Failed(val cause: Throwable) : Outcome<Nothing>()
    }

    /**
     * Liga [requested]; se o aparelho recusar, religa [previous].
     *
     * Pré-visualização preta é o pior resultado possível — e esta base já viveu um
     * caminho em que `evt=bind` reportava sucesso em 7 ms com a tela preta. Por isso
     * a recusa não termina em sessão vazia enquanto houver um conjunto que sabidamente
     * funcionou.
     *
     * Não repete a tentativa quando não há modo anterior (primeiro bind) nem quando o
     * anterior é o próprio modo pedido (rebind por troca de proporção ou resolução):
     * seria a mesma chamada de novo, dobrando o tempo de tela preta.
     *
     * @param bindSet desfaz o bind atual e liga o conjunto do modo recebido.
     */
    fun <C> bind(
        requested: CameraModeDefinition,
        previous: CameraModeDefinition?,
        bindSet: (CameraModeDefinition) -> C
    ): Outcome<C> {
        val recusa = try {
            return Outcome.Bound(bindSet(requested), requested)
        } catch (e: Exception) {
            e
        }
        if (previous == null || previous === requested) return Outcome.Failed(recusa)
        return try {
            Outcome.Restored(bindSet(previous), previous, requested, recusa)
        } catch (e: Exception) {
            recusa.addSuppressed(e)
            Outcome.Failed(recusa)
        }
    }
}
