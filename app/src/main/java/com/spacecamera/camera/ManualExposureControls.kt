package com.spacecamera.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Preview

/**
 * Requirements: FR-9, FR-11, NFR-3
 * Decisions: ADR-005, ADR-007, Q-11
 *
 * O lado CameraX da exposição manual. A regra mora em [ManualExposure] (puro,
 * testado na JVM); aqui só se lê o AE e se escrevem as chaves do `CaptureRequest`.
 *
 * **Não aplica nada sozinho.** O `CameraManager` monta todas as opções do
 * `Camera2CameraControl` num lugar só (`applyEisNrImmediate`) e as **substitui** a
 * cada bind; um segundo escritor apagaria o primeiro meio segundo depois. Por isso
 * este objeto só *contribui* para aquele construtor — e por último, para que o
 * modo de controle automático vença o scene mode do HDR, que anularia o ISO.
 *
 * Arquivo próprio pelo NFR-3: o `CameraManager` não pode ganhar linha líquida.
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class]) // ADR-005
internal class ManualExposureControls {

    /** Último tempo de exposição escolhido pelo AE — é o que se congela. */
    @Volatile
    private var lastAutoExposureNs: Long? = null

    /**
     * ISO e boost pós-RAW que o AE usava junto. Não entram no pedido — com o AE
     * desligado o boost volta a 100 —, mas explicam por que a imagem manual pode sair
     * mais escura que a automática no mesmo ISO: vão para a telemetria.
     */
    @Volatile
    private var lastAutoIso: Int? = null

    @Volatile
    private var lastAutoBoost: Int? = null

    /** O pedido em vigor; `null` é exposição automática. */
    private var request: ManualRequest? = null

    /**
     * Acompanha o tempo de exposição do AE a cada quadro. É só um ouvinte de
     * resultado — não fixa nenhuma chave, então não tem o problema de prioridade do
     * `Extender` com `setCaptureRequestOption` que congelou EIS e NR nesta base.
     */
    fun observe(previewBuilder: Preview.Builder) {
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    captureRequest: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    if (request != null) return
                    lastAutoExposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                    lastAutoIso = result.get(CaptureResult.SENSOR_SENSITIVITY)
                    lastAutoBoost = result.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST)
                }
            }
        )
    }

    /**
     * Troca o ISO. Ao sair do automático congela o último tempo do AE; trocando de um
     * ISO manual para outro, mantém o tempo já congelado.
     *
     * @return o pedido em vigor, para a telemetria.
     */
    fun setIso(iso: Int?, exposureRange: LongRange?): ManualRequest? {
        if (request == null && iso != null) {
            ModeTelemetry.autoExposureFrozen(lastAutoExposureNs, lastAutoIso, lastAutoBoost)
        }
        val congelado = request?.exposureTimeNs ?: lastAutoExposureNs
        request = if (exposureRange == null) null else ManualExposure.request(iso, congelado, exposureRange)
        return request
    }

    /** Acrescenta o pedido manual às opções. Chamar **por último**. */
    fun contribute(builder: CaptureRequestOptions.Builder) {
        val pedido = request ?: return
        builder
            .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            .setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, pedido.sensitivity)
            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, pedido.exposureTimeNs)
    }
}
