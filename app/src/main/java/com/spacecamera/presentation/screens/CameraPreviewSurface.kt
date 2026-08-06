package com.spacecamera.presentation.screens

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Decisions: Q-05
 *
 * A superfície onde o CameraX desenha a pré-visualização.
 *
 * Extraída da `CameraScreen` quando o modo de implementação deixou de ser constante: o
 * arquivo bateu no teto de linhas do NFR-5, e esta é a parte que a mudança tocou.
 *
 * **A decisão que este composable carrega** é qual view o `PreviewView` usa por baixo,
 * e ela não é cosmética:
 *
 * | Situação | Modo | Por quê |
 * |---|---|---|
 * | sem espelho | `PERFORMANCE` (`SurfaceView`) | compõe em overlay, o app quase não desenha |
 * | com espelho | `COMPATIBLE` (`TextureView`) | `graphicsLayer` não transforma `SurfaceView` |
 *
 * Medido em Motorola edge 60 neo com A/B alternado: 60% de CPU e ~700 quadros/s em
 * `COMPATIBLE`, contra 36–48% e ~100 quadros/s em `PERFORMANCE`.
 *
 * Que `graphicsLayer` não espelha `SurfaceView` também foi medido, não suposto:
 * comparando a tela com espelho ligado contra o **reflexo horizontal** da tela com ele
 * desligado, o reflexo piorava a semelhança (29,4 contra 15,8, sobre piso de ruído de
 * 3,7) — ou seja, a transformação era ignorada.
 *
 * Como [mirrorFrontCamera] vem desligado por padrão, o caminho barato é o comum: o custo
 * antigo só é pago por quem liga o espelho **e** está na câmera frontal.
 *
 * Nada disto afeta a mídia gravada. Quem espelha o arquivo é o `setMirrorMode` do
 * CameraX, aplicado ao `VideoCapture` no `CameraManager`.
 *
 * @param fillCenter recorta para preencher em vez de caber inteiro. É o modo "Full" da
 *   foto; calculado por quem chama, para este composable não conhecer regra de negócio.
 * @param onPreviewView chamado com a view criada, e **de novo** a cada recriação por
 *   troca de modo. Quem recebe precisa religar a surface — no ViewModel esse caminho é o
 *   de "PreviewView novo, mesmo LifecycleOwner" (Q-02).
 */
@Composable
internal fun CameraPreviewSurface(
    isFrontCamera: Boolean,
    mirrorFrontCamera: Boolean,
    fillCenter: Boolean,
    onPreviewView: (PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    val mirrored = isFrontCamera && mirrorFrontCamera
    // `implementationMode` só é honrado antes de a superfície existir, então trocar de
    // modo exige um `PreviewView` novo — é o que a `key` força.
    key(mirrored) {
        AndroidView(
            modifier = modifier.then(
                if (mirrored) Modifier.graphicsLayer { scaleX = -1f } else Modifier
            ),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = if (mirrored) {
                        PreviewView.ImplementationMode.COMPATIBLE
                    } else {
                        PreviewView.ImplementationMode.PERFORMANCE
                    }
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
            },
            update = { view ->
                onPreviewView(view)
                view.scaleType = if (fillCenter) {
                    PreviewView.ScaleType.FILL_CENTER
                } else {
                    PreviewView.ScaleType.FIT_CENTER
                }
            }
        )
    }
}
