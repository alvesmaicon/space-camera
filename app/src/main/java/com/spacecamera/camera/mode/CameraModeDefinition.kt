package com.spacecamera.camera.mode

/**
 * Requirements: FR-1, FR-2
 * Decisions: ADR-001 (forma híbrida aprovada em 2026-08-05)
 *
 * O que um modo de câmera declara sobre si.
 *
 * **Forma híbrida**, e a divisão não é estilística:
 *
 * - **dados** (`val` abstratos) para escolha de conjunto fechado — use cases,
 *   capacidade exigida, controles, overlay, se vem no plano;
 * - **método abstrato** para o que é comportamento e varia por modo — a ação do
 *   disparador e o comportamento do flash —, devolvendo **descrição**, nunca
 *   executando.
 *
 * O que decidiu pelo método abstrato em vez de mais um campo: dos cinco pontos de
 * ramificação por modo examinados, três são escolha fechada mas **dois são
 * comportamento** — o flash é tocha em Vídeo e ciclo de três estados em Foto, e o
 * disparador grava em Vídeo e captura em Foto. Com dados puros esse "como"
 * continuaria num `when` do ViewModel, e cada modo novo voltaria a editar arquivo
 * grande. Com membro abstrato, o compilador obriga cada modo a responder — e o
 * disparador do time-lapse, que não é nem gravar nem capturar, será obrigado a se
 * declarar em vez de cair num `else` que ninguém revisou.
 *
 * O segundo argumento é a migração: quando o `SessionConfig` do CameraX 1.6
 * chegar, cada modo precisará declarar seu grupo de features. Membro abstrato novo
 * **quebra o build em todos os modos** e transforma a migração em checklist; campo
 * novo com valor padrão compila calado e faz cada modo declarar "nada" — o Vídeo
 * deixaria de pedir estabilização e UHD sem ninguém notar.
 *
 * ## Proibido aqui dentro
 *
 * Cada um destes desfaz a razão do desenho, e há teste para os dois primeiros:
 *
 * - **estado mutável** — o modo viraria um segundo ViewModel, e o teste de unidade
 *   deixaria de ser determinístico.
 * - **chamar o `CameraController`** — inverteria a dependência e traria CameraX
 *   para dentro do registro.
 * - **`@Composable`** — poria UI no domínio e tiraria o registro do alcance da JVM
 *   pura (NFR-5). Foi exatamente a linha que reprovou a alternativa por herança na
 *   matriz do ADR-001.
 * - **executar a ação em vez de descrevê-la** — quem faz é o ViewModel.
 *
 * ## `abstract`, e não `sealed` — desvio do design §3
 *
 * O design especificou `sealed class`. Na implementação isso impediu o teste do
 * gate de capacidade (FR-5), porque Kotlin proíbe herdar de classe selada a partir
 * de outro módulo de compilação — e `src/test` é outro. Sem um modo de mentira que
 * exija capacidade, o caso negativo do gate só teria teste na Tarefa 11, quatro
 * ondas depois de a lógica existir.
 *
 * A troca não custa nada do que o ADR-001 defende. O que `sealed` daria é `when`
 * exaustivo sobre modos — e é exatamente esse `when` que a spec existe para
 * eliminar: os consumidores iteram o registro, não ramificam por modo. Como o
 * projeto é módulo único, `sealed` também não fecha a hierarquia contra ninguém.
 * Todos os argumentos do ADR-001 — membro abstrato obrigando cada modo a
 * responder, migração que quebra o build em vez de compilar calada — vêm de
 * `abstract`, não de `sealed`.
 *
 * Registrado como Q-05 em `decisions.md`.
 */
abstract class CameraModeDefinition {

    // ── dados: escolha de conjunto fechado ──────────────────────────────────

    /** Identificador estável. É o que a preferência do usuário grava (FR-8). */
    abstract val id: CameraModeId

    /** Rótulo de exibição no seletor. */
    abstract val label: String

    /** O conjunto que o bind deve ligar — exatamente este, e nenhum outro (FR-2). */
    abstract val useCases: Set<AppUseCase>

    /** Capacidade de hardware exigida, ou `null` se o modo roda em qualquer aparelho (FR-5). */
    abstract val requiredCapability: Capability?

    /** Controles da barra superior, na ordem em que aparecem. */
    abstract val controls: List<ControlId>

    /** Controles da linha expandida ("mais opções"), na ordem em que aparecem. */
    abstract val moreControls: List<ControlId>

    /** Como a proporção de captura é escolhida (FR-3, FR-4). */
    abstract val aspectRatio: AspectRatioRule

    /** Se o EIS vale neste modo (AC-4.1). */
    abstract val stabilization: StabilizationRule

    /** O que o modo produz: vídeo ou foto. */
    abstract val output: CaptureOutput

    /** Overlay próprio, ou `null` se o modo não desenha nada sobre a pré-visualização. */
    abstract val overlay: OverlayId?

    /** Se o modo vem no plano do seletor, antes de qualquer personalização. */
    abstract val pinnedByDefault: Boolean

    // ── comportamento: devolve descrição, nunca executa ─────────────────────

    /** O que o disparador faz neste modo, dado o estado atual da captura. */
    abstract fun shutterAction(state: CaptureState): ShutterAction

    /** Como o flash se comporta neste modo. */
    abstract fun flashBehavior(): FlashBehavior
}
