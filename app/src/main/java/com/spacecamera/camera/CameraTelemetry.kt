package com.spacecamera.camera

import com.spacecamera.camera.mode.AppUseCase
import com.spacecamera.camera.mode.CameraModeId
import com.spacecamera.camera.mode.DeviceCapabilities
import com.spacecamera.camera.mode.ModeBinder
import timber.log.Timber
import java.util.Locale

/**
 * Eventos estruturados dos momentos decisivos da câmera.
 *
 * Motivação: quase todo problema deste app é específico de aparelho — "4K/60 não
 * aparece no meu celular", "a estabilização não liga", "a foto saiu escura". Sem
 * registro do que o HAL respondeu no bind, a única saída é pedir o aparelho
 * emprestado. Estes eventos deixam o diagnóstico a um `scripts/logcat.sh` de
 * distância.
 *
 * Formato: `evt=<nome> chave=valor chave=valor`, uma linha por evento. É legível
 * a olho nu e dá para fatiar com grep/awk sem parser:
 *
 * ```
 * adb logcat -s SpaceCam | grep 'evt=bind '
 * ```
 *
 * Não registre conteúdo do usuário aqui — só capacidades do device e parâmetros
 * escolhidos. URIs de mídia entram apenas como o último segmento (o id), nunca o
 * caminho completo.
 */
internal object CameraTelemetry {

    /** Bind do CameraX concluído: o que de fato foi aplicado na sessão. */
    fun bind(
        mode: CameraModeId,
        option: VideoOption,
        aspectRatio: String,
        bitrate: Int,
        eis: Boolean,
        noiseReduction: Boolean,
        hdr: Boolean,
        frontCamera: Boolean,
        elapsedMs: Long
    ) {
        Timber.i(
            "evt=bind mode=%s quality=%s fps=%d aspect=%s bitrate=%d eis=%b nr=%b hdr=%b front=%b elapsed_ms=%d",
            TelemetryFields.bindModeName(mode), option.qualityLabel, option.fps, aspectRatio,
            bitrate, eis, noiseReduction, hdr, frontCamera, elapsedMs
        )
    }

    fun bindFailed(error: Throwable) {
        Timber.e(error, "evt=bind_failed")
    }

    /**
     * Capacidades sondadas do hardware.
     *
     * É o evento que responde "por que a opção X não aparece": as listas aqui são
     * exatamente as que alimentam os seletores da UI.
     */
    fun capabilities(
        cameraId: String,
        eisSupported: Boolean,
        hdrSupported: Boolean,
        videoOptions: List<VideoOption>,
        zoomRange: ClosedFloatingPointRange<Float>,
        device: DeviceCapabilities
    ) {
        // Campos de controle manual no fim: são aditivos (FR-13), e quem já fatia a
        // linha até `options=[...]` continua lendo o mesmo de antes (NFR-1).
        Timber.i(
            "evt=caps camera=%s eis_supported=%b hdr_supported=%b zoom=%.1fx-%.1fx options=[%s] %s",
            cameraId, eisSupported, hdrSupported,
            zoomRange.start, zoomRange.endInclusive,
            videoOptions.joinToString(",") { it.label },
            TelemetryFields.manualSensorFields(device)
        )
    }

    fun recordingStarted(option: VideoOption, bitrate: Int, micEnabled: Boolean) {
        Timber.i(
            "evt=rec_start quality=%s fps=%d bitrate=%d mic=%b",
            option.qualityLabel, option.fps, bitrate, micEnabled
        )
    }

    fun recordingFinished(mediaId: String?, durationMs: Long, sizeBytes: Long) {
        Timber.i(
            "evt=rec_stop media=%s duration_ms=%d size_bytes=%d",
            mediaId ?: "-", durationMs, sizeBytes
        )
    }

    fun recordingFailed(reason: String) {
        Timber.e("evt=rec_failed reason=%s", reason)
    }

    fun photoCaptured(preset: PhotoQualityPreset, widthPx: Int, heightPx: Int, elapsedMs: Long) {
        Timber.i(
            "evt=photo preset=%s size=%dx%d elapsed_ms=%d",
            preset.name, widthPx, heightPx, elapsedMs
        )
    }

    fun photoFailed(reason: String, error: Throwable? = null) {
        if (error != null) Timber.e(error, "evt=photo_failed reason=%s", reason)
        else Timber.e("evt=photo_failed reason=%s", reason)
    }
}

/**
 * Requirements: FR-13, NFR-1, NFR-7
 *
 * Os pedaços de linha de telemetria que têm regra própria, separados da emissão
 * para serem testados na JVM sem depender do Timber. Só nomes e números saem daqui.
 */
internal object TelemetryFields {

    /** Campos de `evt=manual`. `iso=auto` quando a exposição está com o AE. */
    fun manualFields(request: ManualRequest?): String =
        if (request == null) "iso=auto" else "iso=%d exposure_ns=%d".format(request.sensitivity, request.exposureTimeNs)

    /**
     * Requirements: NFR-1
     *
     * O `mode=` de `evt=bind`. Passou a vir do identificador estável do modo, mas é
     * campo preexistente comparado entre commits: `video` continua saindo `VIDEO`.
     */
    fun bindModeName(id: CameraModeId): String = id.value.uppercase(Locale.ROOT)

    /** Campos de `evt=mode`. Use cases na ordem do bind, para a linha ser estável no grep. */
    fun modeFields(from: CameraModeId?, to: CameraModeId, useCases: Set<AppUseCase>, elapsedMs: Long): String =
        "from=%s to=%s use_cases=%s elapsed_ms=%d".format(
            from?.value ?: "-", to.value,
            ModeBinder.ordered(useCases).joinToString(",") { it.name.lowercase(Locale.ROOT) },
            elapsedMs
        )

    /**
     * Requirements: FR-13, NFR-7
     *
     * Os campos de controle manual que fecham a linha `evt=caps`. Sem suporte, sai
     * só `manual_sensor=false` — sem faixa nenhuma, para não sugerir que existe.
     * Com suporte, os quatro campos sempre presentes (limite analógico ausente vira
     * `-`), para quem fatia a linha por posição não quebrar.
     *
     * A exposição sai em nanossegundos, como o HAL reporta: a conversão para
     * fração de segundo é coisa de UI, e na telemetria só atrapalharia o grep.
     */
    fun manualSensorFields(caps: DeviceCapabilities): String {
        val faixas = caps.manualSensor ?: return "manual_sensor=false"
        return "manual_sensor=true iso=%d-%d iso_analog_max=%s exposure_ns=%d-%d".format(
            faixas.iso.first, faixas.iso.last,
            faixas.maxAnalogIso?.toString() ?: "-",
            faixas.exposureTimeNs.first, faixas.exposureTimeNs.last
        )
    }
}

/**
 * Requirements: FR-9, FR-13, NFR-7
 *
 * Os eventos do registro de modos: troca efetiva de modo, modo recusado pelo aparelho
 * e exposição manual do Pro. Separados de [CameraTelemetry] por assunto, no mesmo
 * arquivo e no mesmo formato `evt=`.
 */
internal object ModeTelemetry {

    /**
     * Requirements: FR-13
     *
     * Troca de modo efetivada na sessão. Só sai quando o modo **ligado** muda — um
     * rebind por proporção ou resolução não é troca de modo.
     */
    fun modeChanged(from: CameraModeId?, to: CameraModeId, useCases: Set<AppUseCase>, elapsedMs: Long) {
        Timber.i("evt=mode %s", TelemetryFields.modeFields(from, to, useCases, elapsedMs))
    }

    /**
     * O que o AE usava no instante em que o ISO virou manual. Responde "por que o Pro
     * sai mais escuro que o automático": o AE soma ganho digital pós-RAW (`ae_boost`,
     * 100 = nenhum) que o modo manual não tem.
     */
    fun autoExposureFrozen(exposureNs: Long?, iso: Int?, boost: Int?) {
        Timber.i("evt=ae_frozen exposure_ns=%s ae_iso=%s ae_boost=%s", exposureNs ?: "-", iso ?: "-", boost ?: "-")
    }

    /** Exposição manual aplicada (FR-9): o que de fato foi pedido ao sensor. */
    fun manualExposure(request: ManualRequest?) {
        Timber.i("evt=manual %s", TelemetryFields.manualFields(request))
    }

    /** O aparelho recusou o conjunto de [rejected]; o de [restored] foi religado no lugar. */
    fun modeRejected(rejected: CameraModeId, restored: CameraModeId, error: Throwable) {
        Timber.w(error, "evt=mode_failed to=%s restored=%s", rejected.value, restored.value)
    }
}
