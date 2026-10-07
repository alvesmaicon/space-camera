package com.spacecamera.camera.mode

/**
 * Requirements: FR-1, FR-5, FR-6, FR-8, NFR-2, NFR-8
 * Decisions: ADR-001, ADR-004
 *
 * A lista única de modos, e as duas perguntas que a UI e o controller fazem a ela:
 * *quais existem neste aparelho* e *em que ordem mostrar*.
 *
 * **Acrescentar um modo é acrescentar uma linha em [all]** — é a medida do NFR-2,
 * e a razão de existir da spec.
 */
object ModeRegistry {

    /**
     * Todos os modos que o app conhece, na ordem padrão.
     *
     * Conhecer ≠ oferecer: quem filtra por hardware é [available].
     */
    val all: List<CameraModeDefinition> = listOf(
        VideoMode,
        PhotoMode,
        ProMode,  // vírgula final: o próximo modo é uma linha só no diff
    )

    /**
     * Os modos que nunca saem do plano: a função principal do app (premissa P2,
     * AC-7.1). Valem mesmo contra uma preferência corrompida que os deixasse de fora.
     */
    val essential: Set<CameraModeId> = setOf(VideoMode.id, PhotoMode.id)

    /**
     * Os modos que **este** aparelho oferece (FR-5).
     *
     * Modo cuja capacidade exigida não veio do HAL não aparece em superfície
     * nenhuma — nem no plano, nem na gaveta, nem na personalização. Segue a
     * convenção do CLAUDE.md: se não veio em `evt=caps`, não aparece na tela.
     * Exibir desabilitado com aviso foi rejeitado na premissa P1, porque cria
     * estado morto e contradiz o resto do app.
     *
     * @param registry existe para teste; em produção é sempre [all].
     */
    fun available(
        capabilities: Set<Capability>,
        registry: List<CameraModeDefinition> = all
    ): List<CameraModeDefinition> = registry.filter { modo ->
        modo.requiredCapability == null || modo.requiredCapability in capabilities
    }

    /**
     * Aplica a preferência do usuário aos modos disponíveis, devolvendo o que vai
     * no plano e o que vai na gaveta (FR-6, FR-8).
     *
     * Trata os quatro casos de compatibilidade do fluxo 4.5 do design, que é onde
     * o NFR-8 mora:
     *
     * 1. **Identificador desconhecido** (modo que não existe mais): descartado.
     * 2. **Modo conhecido mas indisponível neste aparelho**: não aparece — e a
     *    preferência recebida **não é alterada**. Esta função é pura de propósito:
     *    apagar o modo da preferência faria o usuário perder a configuração ao
     *    trocar de aparelho. A preferência é dele, não do hardware (AC-5.3).
     * 3. **Modo disponível ausente da preferência** (gravada por versão anterior):
     *    entra no fim, com o padrão do registro.
     * 4. **Preferência vazia ou sem nada aproveitável**: cai no padrão (AC-8.2).
     *
     * @param registry existe para teste; em produção é sempre [all].
     */
    fun arranged(
        preference: ModeArrangement,
        capabilities: Set<Capability>,
        registry: List<CameraModeDefinition> = all
    ): ArrangedModes {
        val disponiveis = available(capabilities, registry)
        val porId = disponiveis.associateBy { it.id.value }

        // Casos 1 e 2 caem juntos aqui: o que não está em `porId` some da exibição,
        // seja porque não existe mais ou porque este aparelho não o oferece.
        val naOrdemGravada = preference.order.mapNotNull { porId[it] }

        // Caso 3: disponível que a preferência não menciona entra no fim, na ordem
        // do registro. Sem isto, atualizar o app esconderia o modo novo.
        val restantes = disponiveis - naOrdemGravada.toSet()
        val ordenados = naOrdemGravada + restantes

        // Caso 4: sem nada aproveitável, `pinned` fica vazio e o seletor sumiria.
        // O padrão do registro é a rede — o app nunca fica sem modo à mão.
        val semPreferenciaUtil = naOrdemGravada.isEmpty() && preference.pinned.none { it in porId }

        val (plano, gaveta) = ordenados.partition { modo ->
            modo.id in essential ||
                if (semPreferenciaUtil) modo.pinnedByDefault else modo.id.value in preference.pinned
        }

        return ArrangedModes(pinned = plano, drawer = gaveta, ordered = ordenados)
    }
}
