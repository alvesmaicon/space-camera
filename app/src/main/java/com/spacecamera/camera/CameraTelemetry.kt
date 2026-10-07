package com.spacecamera.camera

import com.spacecamera.camera.mode.DeviceCapabilities
import timber.log.Timber

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
        mode: CameraMode,
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
            mode.name, option.qualityLabel, option.fps, aspectRatio,
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
            manualSensorFields(device)
        )
    }

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
