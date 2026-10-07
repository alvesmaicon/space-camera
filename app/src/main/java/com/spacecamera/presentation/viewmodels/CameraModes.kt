package com.spacecamera.presentation.viewmodels

import com.spacecamera.camera.ManualExposure
import com.spacecamera.camera.ModeRejection
import com.spacecamera.camera.mode.ArrangedModes
import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.camera.mode.Capability
import com.spacecamera.camera.mode.DeviceCapabilities
import com.spacecamera.camera.mode.ManualSensorRanges
import com.spacecamera.camera.mode.ModeArrangement
import com.spacecamera.camera.mode.ModeRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sign

/**
 * Requirements: FR-5, FR-6, FR-7, FR-8, FR-9, FR-16, NFR-8
 * Decisions: ADR-004, Q-10, Q-12
 *
 * O estado de modos, agrupado fora do `CameraViewModel` (Q-10): modo ativo, arranjo
 * plano/gaveta, preferência do usuário e exposição manual do Pro.
 *
 * **Só guarda estado e decide.** Não chama o controller nem grava a preferência —
 * isso fica com o ViewModel, que recebe de volta o que precisa aplicar ou persistir.
 * É o que deixa as regras de personalização testáveis em JVM pura.
 */
class CameraModes(private val registry: List<CameraModeDefinition>) {

    private var supported: Set<Capability> = emptySet()

    private val _preference = MutableStateFlow(ModeArrangement.DEFAULT)
    /** A preferência como gravada — inclusive modos que este aparelho não oferece (AC-5.3). */
    val preference: StateFlow<ModeArrangement> = _preference.asStateFlow()

    private val _arranged = MutableStateFlow(arrange())
    /** Plano e gaveta do seletor, já filtrados pelo aparelho (FR-5, FR-6). */
    val arranged: StateFlow<ArrangedModes> = _arranged.asStateFlow()

    private val _active = MutableStateFlow(registry.first())
    val active: StateFlow<CameraModeDefinition> = _active.asStateFlow()

    private val _manualExposure = MutableStateFlow(ManualExposureState())
    /** Exposição manual do Pro; `iso == null` é automático (FR-9). */
    val manualExposure: StateFlow<ManualExposureState> = _manualExposure.asStateFlow()

    private val _manualRanges = MutableStateFlow<ManualSensorRanges?>(null)
    /** Faixas que a escala do Pro oferece — exatamente as do HAL (AC-5.2). */
    val manualRanges: StateFlow<ManualSensorRanges?> = _manualRanges.asStateFlow()

    /** O último modo que o aparelho recusou, para o aviso da tela. */
    var lastRejected: CameraModeDefinition? = null
        private set

    private fun arrange() = ModeRegistry.arranged(_preference.value, supported, registry)
    private fun rearrange() { _arranged.value = arrange() }

    // ── Aparelho e preferência ──────────────────────────────────────────────

    fun loadPreference(preference: ModeArrangement) {
        _preference.value = preference
        rearrange()
    }

    fun onCapabilities(caps: DeviceCapabilities) {
        supported = caps.supported
        _manualRanges.value = caps.manualSensor
        rearrange()
    }

    // ── Modo ativo ──────────────────────────────────────────────────────────

    /**
     * Ativa [mode]. Nenhum outro modo tem escala de ISO, então o ISO manual volta ao
     * automático.
     *
     * @return `true` se havia ISO manual a desfazer — o ViewModel avisa o controller.
     */
    fun select(mode: CameraModeDefinition): Boolean {
        _active.value = mode
        if (_manualExposure.value.iso == null) return false
        _manualExposure.value = ManualExposureState()
        return true
    }

    /** O aparelho recusou um modo; o ativo volta ao que ficou na sessão (fluxo 4.2). */
    fun onRejection(rejection: ModeRejection): CameraModeDefinition {
        lastRejected = registry.firstOrNull { it.id == rejection.rejected }
        return registry.first { it.id == rejection.active }.also { _active.value = it }
    }

    /**
     * ISO pedido, limitado à faixa do aparelho (AC-9.1); `null` volta ao automático.
     *
     * @return o ISO em vigor depois do pedido — `null` também quando não há capacidade
     *   manual e o pedido é ignorado.
     */
    fun requestIso(iso: Int?): Int? {
        val faixa = _manualRanges.value?.iso ?: return null
        val aplicado = iso?.let { ManualExposure.clampIso(it, faixa) }
        _manualExposure.value = ManualExposureState(iso = aplicado)
        return aplicado
    }

    // ── Personalização (FR-7, FR-16) ────────────────────────────────────────

    /** Vídeo e Foto não saem do plano (AC-7.1). */
    fun isRemovable(mode: CameraModeDefinition) = mode.id !in ModeRegistry.essential

    /**
     * Move [mode] [by] posições entre os modos **disponíveis**. Os que este aparelho
     * não oferece, ou que esta versão não conhece, ficam exatamente onde estavam na
     * preferência — trocar de aparelho devolve o Pro à posição configurada (AC-5.3).
     *
     * @return a preferência nova, para persistir.
     */
    fun move(mode: CameraModeDefinition, by: Int): ModeArrangement {
        val atual = effective()
        val ordem = atual.order.toMutableList()
        val disponiveis = _arranged.value.ordered.map { it.id.value }.toSet()
        var i = ordem.indexOf(mode.id.value)
        repeat(abs(by)) {
            if (i < 0) return@repeat
            var j = i + by.sign
            while (j in ordem.indices && ordem[j] !in disponiveis) j += by.sign
            if (j !in ordem.indices) return@repeat
            ordem[i] = ordem[j].also { ordem[j] = ordem[i] }
            i = j
        }
        return update(atual.copy(order = ordem))
    }

    /** Leva [mode] ao plano ou à gaveta. Pedido de tirar um essencial é ignorado. */
    fun setPinned(mode: CameraModeDefinition, pinned: Boolean): ModeArrangement {
        val atual = effective()
        if (!pinned && !isRemovable(mode)) return atual
        val fixados = if (pinned) atual.pinned + mode.id.value else atual.pinned - mode.id.value
        return update(atual.copy(pinned = fixados))
    }

    /** Volta à ordem e ao plano do registro (AC-16.1). */
    fun restoreDefault(): ModeArrangement = update(ModeArrangement.DEFAULT)

    private fun update(nova: ModeArrangement): ModeArrangement {
        _preference.value = nova
        rearrange()
        return nova
    }

    /**
     * A preferência completa, para editar: a gravada, mais os disponíveis que ela não
     * cita (no fim), e com o plano do registro se ainda não houve personalização.
     */
    private fun effective(): ModeArrangement {
        val gravada = _preference.value
        val exibido = _arranged.value
        val ordem = gravada.order + exibido.ordered.map { it.id.value }.filter { it !in gravada.order }
        val fixados = gravada.pinned.ifEmpty { exibido.pinned.map { it.id.value }.toSet() }
        return ModeArrangement(order = ordem, pinned = fixados)
    }
}
