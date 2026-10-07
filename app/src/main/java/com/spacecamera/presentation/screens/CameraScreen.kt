package com.spacecamera.presentation.screens

import com.spacecamera.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.TimerOff
import com.spacecamera.presentation.icons.TimerIcon3
import com.spacecamera.presentation.icons.TimerIcon5
import com.spacecamera.presentation.icons.TimerIcon10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import android.view.Surface as AndroidSurface
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.camera.mode.CaptureOutput
import com.spacecamera.camera.mode.ControlId
import com.spacecamera.camera.mode.FlashBehavior
import com.spacecamera.camera.mode.ShutterAction
import com.spacecamera.presentation.components.ModeDrawer
import com.spacecamera.presentation.components.TopBarContext
import com.spacecamera.presentation.components.TopBarControl
import com.spacecamera.presentation.components.ModeOverlay
import com.spacecamera.presentation.components.ModeRejectedNotice
import com.spacecamera.presentation.components.ModeSelector
import com.spacecamera.presentation.layout.previewAspect
import com.spacecamera.camera.RecordingState
import com.spacecamera.camera.VideoOption
import com.spacecamera.presentation.layout.AxisContainer
import com.spacecamera.presentation.layout.AxisScope
import com.spacecamera.presentation.layout.LEVEL_MIN_DELTA
import com.spacecamera.presentation.layout.LEVEL_SMOOTHING
import com.spacecamera.presentation.layout.LEVEL_TOLERANCE
import com.spacecamera.presentation.layout.captureRotation
import com.spacecamera.presentation.layout.rememberIsWideWindow
import com.spacecamera.presentation.layout.rollFromGravity
import com.spacecamera.presentation.layout.shouldPublishRoll
import com.spacecamera.presentation.layout.smoothGravity
import com.spacecamera.presentation.layout.uiRotation
import com.spacecamera.presentation.layout.windowRelativeRoll
import com.spacecamera.presentation.viewmodels.CameraViewModel
import com.spacecamera.presentation.viewmodels.PhotoFlashMode
import com.spacecamera.presentation.viewmodels.RecordingDelay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.Paint
import android.graphics.Typeface
import androidx.camera.core.FocusMeteringAction
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/**
 * Requirements: NFR-2
 *
 * Opacidade do branco num controle **desligado** (ou não selecionado).
 *
 * Era 0,38 — o valor que o app usava desde antes do layout adaptativo — e não
 * alcançava o limiar de 4,5:1 do NFR-2 em nenhum dos dois fundos. Medido e
 * recalculado em 2026-08-04, pela fórmula de luminância relativa da WCAG 2.1:
 *
 * | Alfa | Sobre o scrim (`#232325`) | Sobre a faixa preta |
 * |---|---|---|
 * | 0,38 | 3,51:1 | 3,39:1 |
 * | 0,48 | **4,73:1** | **4,89:1** |
 *
 * O `#232325` é o pior caso do fundo composto em janela larga: scrim `0xFF1C1C1E`
 * com alfa 0,97 sobre pré-visualização toda branca.
 *
 * Não confundir com o alfa 0,2 dos controles **não suportados** pelo aparelho, que
 * fica como está: ali o cinza fraco é a própria informação ("indisponível"), a WCAG
 * isenta componentes inativos do contraste mínimo, e igualar os dois faria
 * "não suportado" parecer apenas "desligado".
 */
internal const val OFF_CONTROL_ALPHA = 0.48f

@Composable
fun CameraScreen(viewModel: CameraViewModel = viewModel(), onOpenSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val recordingState by viewModel.recordingState.collectAsState()
    val selectedVideoOption by viewModel.selectedVideoOption.collectAsState()
    val cameraInitialized by viewModel.cameraInitialized.collectAsState()
    val isFlashOn by viewModel.isFlashOn.collectAsState()
    val isMicMuted by viewModel.isMicMuted.collectAsState()
    val isGridEnabled by viewModel.isGridEnabled.collectAsState()
    val isLevelEnabled by viewModel.isLevelEnabled.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val selectedAspectRatio by viewModel.selectedAspectRatio.collectAsState()
    val recordingSeconds by viewModel.recordingSeconds.collectAsState()
    val availableVideoOptions by viewModel.availableVideoOptions.collectAsState()
    val isStabilizationEnabled by viewModel.isStabilizationEnabled.collectAsState()
    val isEisSupported by viewModel.isEisSupported.collectAsState()
    val isNoiseReductionEnabled by viewModel.isNoiseReductionEnabled.collectAsState()
    val isHdrEnabled by viewModel.isHdrEnabled.collectAsState()
    val isHdrSupported by viewModel.isHdrSupported.collectAsState()
    val lastVideoUri by viewModel.lastVideoUri.collectAsState()
    val selectedZoomLevel by viewModel.selectedZoomLevel.collectAsState()
    val minZoomRatio by viewModel.minZoomRatio.collectAsState()
    val maxZoomRatio by viewModel.maxZoomRatio.collectAsState()
    val exposureIndex by viewModel.exposureIndex.collectAsState()
    val exposureMin by viewModel.exposureMin.collectAsState()
    val exposureMax by viewModel.exposureMax.collectAsState()
    val isTapToFocusEnabled by viewModel.isTapToFocusEnabled.collectAsState()
    val isFrontCameraMirrorEnabled by viewModel.isFrontCameraMirrorEnabled.collectAsState()
    val recordingDelay by viewModel.recordingDelay.collectAsState()
    val countdownSeconds by viewModel.countdownSeconds.collectAsState()
    val isSaveLocationEnabled by viewModel.isSaveLocationEnabled.collectAsState()
    val activeMode by viewModel.activeMode.collectAsState()
    val arrangedModes by viewModel.arrangedModes.collectAsState()
    var showModeDrawer by remember { mutableStateOf(false) }
    val lastPhotoUri by viewModel.lastPhotoUri.collectAsState()
    val photoFlashMode by viewModel.photoFlashMode.collectAsState()
    val photoQualityPreset by viewModel.photoQualityPreset.collectAsState()
    val isImageEnhancementEnabled by viewModel.isImageEnhancementEnabled.collectAsState()
    val isCameraReady by viewModel.isCameraReady.collectAsState()

    var transitionBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val transitionAlpha = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    var permissionGranted by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Armazena a rotação do ícone no momento em que a foto é disparada,
    // para que o preview de revisão exiba na orientação correta.
    var reviewIconRotation by remember { mutableStateOf(0f) }

    val switchMode: (CameraModeDefinition) -> Unit = { mode ->
        val snapshot = previewView?.bitmap
        if (snapshot != null) {
            transitionBitmap = snapshot
            coroutineScope.launch {
                transitionAlpha.snapTo(1f)
                viewModel.selectMode(mode)
                // Aguarda isCameraReady=false (bind iniciou) depois isCameraReady=true
                // (SurfaceProvider chamado + 350ms = câmera ativa com frames chegando)
                viewModel.isCameraReady.filter { !it }.first()
                viewModel.isCameraReady.filter { it }.first()
                delay(100)
                transitionAlpha.animateTo(0f, animationSpec = tween(400))
                transitionBitmap = null
            }
        } else {
            viewModel.selectMode(mode)
        }
    }


    val density = LocalDensity.current
    val reviewScreenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val reviewScreenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    var reviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val reviewScale = remember { Animatable(1f) }
    val reviewAlpha = remember { Animatable(0f) }
    val reviewTransX = remember { Animatable(0f) }
    val reviewTransY = remember { Animatable(0f) }
    LaunchedEffect(lastPhotoUri) {
        val uri = lastPhotoUri ?: return@LaunchedEffect
        reviewScale.snapTo(1f)
        reviewAlpha.snapTo(0f)
        reviewTransX.snapTo(0f)
        reviewTransY.snapTo(0f)
        val bmp = withContext(Dispatchers.IO) {
            try {
                // Primeira passagem: mede dimensões sem decodificar pixels
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                // Calcula inSampleSize para alvo ~1080px de largura
                var sampleSize = 1
                var w = opts.outWidth
                while (w > 1080 * 2) { sampleSize *= 2; w /= 2 }
                // Segunda passagem: decodifica com sample size
                val decoded = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null,
                        BitmapFactory.Options().apply { inSampleSize = sampleSize })
                } ?: return@withContext null
                // Terceira passagem: lê EXIF para corrigir orientação
                val orientation = context.contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
                val degrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (degrees != 0f) {
                    val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
                    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                    decoded.recycle()
                    rotated
                } else {
                    decoded
                }
            } catch (e: Exception) { null }
        } ?: return@LaunchedEffect
        // Rotaciona o bitmap para corresponder à orientação física do telefone.
        // Para fotos landscape, isso transforma o bitmap em retrato para preencher
        // a tela portrait-locked na mesma orientação que o usuário está segurando.
        val displayBmp = if (reviewIconRotation != 0f) {
            val mat = android.graphics.Matrix().apply { postRotate(reviewIconRotation) }
            val rot = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mat, true)
            bmp.recycle()
            rot
        } else bmp
        reviewBitmap = displayBmp
        reviewAlpha.animateTo(1f, tween(120))
        delay(800)
        // Anima em direção ao botão de thumbnail (canto inferior direito)
        val targetX = reviewScreenWidthPx / 2f - with(density) { 36.dp.toPx() }
        val targetY = reviewScreenHeightPx / 2f - with(density) { 80.dp.toPx() }
        launch { reviewScale.animateTo(0f, tween(380, easing = FastOutSlowInEasing)) }
        launch { reviewAlpha.animateTo(0f, tween(280)) }
        launch { reviewTransX.animateTo(targetX, tween(380, easing = FastOutSlowInEasing)) }
        launch { reviewTransY.animateTo(targetY, tween(380, easing = FastOutSlowInEasing)) }
        delay(400)
        reviewBitmap = null
    }

    var showResolutionMenu by remember { mutableStateOf(false) }
    var isTopBarExpanded by remember { mutableStateOf(false) }
    var showZoomDial by remember { mutableStateOf(false) }
    var zoomDialInteractionTick by remember { mutableStateOf(0) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusDimmed by remember { mutableStateOf(false) }
    var showExposureSlider by remember { mutableStateOf(false) }
    var exposureAnchor by remember { mutableStateOf<Offset?>(null) }
    var exposureInteractionTick by remember { mutableStateOf(0) }
    var rollDegrees by remember { mutableStateOf(0f) }

    // Inclinação para **desenhar** a linha do nível: mesma grandeza física de
    // `rollDegrees`, com filtro muito mais forte. Exigências opostas — aquela decide a
    // orientação do arquivo gravado e precisa ser rápida; esta só precisa ser estável.
    var levelRoll by remember { mutableFloatStateOf(0f) }

    // Rotação da janela: quanto o compositor já girou o conteúdo. Fica em ROTATION_0
    // enquanto a janela está travada em retrato (telefone) e acompanha o aparelho
    // quando o sistema é livre para girar (tablet a partir do targetSdk 36).
    //
    // Precisa ser estado, não leitura direta: `view.display` é nulo até a View ser
    // anexada à janela, e uma leitura simples nunca seria reavaliada — o valor
    // ficaria congelado em ROTATION_0. Quem atualiza é o listener do acelerômetro
    // abaixo, que é o mesmo lugar onde `rollDegrees` muda; as duas grandezas
    // precisam andar juntas, e uma rotação de 180° não muda a Configuration.
    val view = LocalView.current
    var displayRotation by remember { mutableIntStateOf(AndroidSurface.ROTATION_0) }

    // Travadas em quadrante, então mudam raramente — mas `rollDegrees` muda a cada
    // amostra. Lidas direto no corpo deste composable, é a leitura (não o resultado) que
    // invalida suas ~1.100 linhas; `derivedStateOf` limita isso à troca de quadrante.
    // Higiene de escopo, não otimização medida — ver Q-04.

    // Ícones: giram só o que a janela ainda não girou. Com a janela travada, o
    // resultado é idêntico ao de antes; com a janela livre é zero, senão o ícone
    // leva rotação dobrada e aparece deitado. Ver Q-01 na spec.
    val snappedIconRotation by remember {
        derivedStateOf { uiRotation(rollDegrees, displayRotation) }
    }

    // Mídia gravada: depende só do aparelho. A rotação da janela mede a mesma
    // grandeza física, então compor as duas torceria o arquivo.
    val snappedSurfaceRotation by remember { derivedStateOf { captureRotation(rollDegrees) } }
    val iconRotation by animateFloatAsState(
        targetValue = snappedIconRotation,
        animationSpec = tween(durationMillis = 300),
        label = "iconRotation"
    )

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionGranted =
            permissions[Manifest.permission.CAMERA] == true &&
            permissions[Manifest.permission.RECORD_AUDIO] == true
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* resultado ignorado — usuário pode ter negado, o toggle continua ligado */ }

    // Solicita permissão de localização quando o toggle for ativado
    LaunchedEffect(isSaveLocationEnabled) {
        if (!isSaveLocationEnabled) return@LaunchedEffect
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            permissionGranted = true
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    // Decisions: Q-02
    //
    // `lifecycleOwner` é chave do efeito porque girar recria a Activity, e o
    // ViewModel — que sobrevive — precisa saber disso: o controller retido guarda o
    // owner antigo, e rebindar nele não reabre a câmera, deixando a
    // pré-visualização preta para sempre.
    //
    // Quem decide entre reaproveitar e reconstruir é `initializeCamera`, não esta
    // tela: é decisão testável na JVM, e aqui não há como exercitá-la.
    LaunchedEffect(permissionGranted, previewView, lifecycleOwner) {
        if (!permissionGranted || previewView == null) return@LaunchedEffect
        viewModel.initializeCamera(context, lifecycleOwner, previewView!!.surfaceProvider)
    }

    val isRecording = recordingState != RecordingState.Idle

    // Reseta o flash ao retornar para o app (ON_RESUME)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.resetFlash()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Fecha menus abertos ao iniciar gravação (não fecha o dial de zoom)
    LaunchedEffect(isRecording) {
        if (isRecording) {
            showResolutionMenu = false
        }
    }

    // Fecha menus ao trocar de modo
    LaunchedEffect(activeMode) {
        showResolutionMenu = false
    }
    // A gaveta some junto com o seletor quando a gravação ou a contagem começa.
    LaunchedEffect(isRecording, countdownSeconds) {
        if (isRecording || countdownSeconds > 0) showModeDrawer = false
    }
    BackHandler(enabled = showModeDrawer) { showModeDrawer = false }

    LaunchedEffect(showZoomDial, zoomDialInteractionTick) {
        if (!showZoomDial) return@LaunchedEffect
        delay(2000)
        if (showZoomDial) showZoomDial = false
    }

    // Slider visibility is tied to focusPoint — no independent auto-hide

    // After 3s without interaction, dim the focus ring (it stays visible but semi-transparent)
    LaunchedEffect(focusPoint, exposureInteractionTick) {
        focusDimmed = false
        if (focusPoint != null) {
            delay(3000)
            focusDimmed = true
        }
    }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var prevX = 0f; var prevY = 0f; var prevZ = 0f; var firstReading = true
        // Vetor de gravidade suavizado. Fica aqui, e não em `mutableStateOf`, porque é
        // trabalho interno do filtro — o que interessa publicar é o resultado.
        var gravityX = 0f; var gravityY = 0f
        var levelGravityX = 0f; var levelGravityY = 0f
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    if (firstReading) {
                        // Semear com a primeira leitura, senão o filtro sobe de zero e o
                        // nível chega inclinado por ~1s ao abrir.
                        gravityX = x; gravityY = y
                        levelGravityX = x; levelGravityY = y
                    } else {
                        gravityX = smoothGravity(gravityX, x)
                        gravityY = smoothGravity(gravityY, y)
                        levelGravityX = smoothGravity(levelGravityX, x, LEVEL_SMOOTHING)
                        levelGravityY = smoothGravity(levelGravityY, y, LEVEL_SMOOTHING)
                    }
                    // Suaviza o **vetor** e só depois tira o ângulo: média de ângulo
                    // atravessa a descontinuidade de ±180° pelo lado errado.
                    val newRoll = rollFromGravity(gravityX, gravityY)
                    // Zona morta: ruído abaixo do limiar não vira escrita de estado.
                    if (firstReading || shouldPublishRoll(rollDegrees, newRoll)) {
                        rollDegrees = newRoll
                    }
                    // Caminho de exibição: filtro forte, que aqui não custa nada. O de
                    // cima não pode ser, sob pena de atrasar a captura (FR-6).
                    val newLevelRoll = rollFromGravity(levelGravityX, levelGravityY)
                    if (firstReading ||
                        shouldPublishRoll(levelRoll, newLevelRoll, LEVEL_MIN_DELTA)
                    ) {
                        levelRoll = newLevelRoll
                    }
                    // Leitura local e em cache no DisplayManagerGlobal — barata o
                    // bastante para acompanhar o sensor, e garante convergência em
                    // um quadro após qualquer virada da janela.
                    displayRotation = view.display?.rotation ?: AndroidSurface.ROTATION_0
                    if (!firstReading) {
                        val dx = x - prevX; val dy = y - prevY; val dz = z - prevZ
                        val shake = dx * dx + dy * dy + dz * dz
                        // ~1.7 m/s² threshold — requires intentional camera movement, not casual handling
                        if (shake > 3.0f && focusPoint != null) {
                            viewModel.cancelFocusLock()
                            focusPoint = null
                            focusDimmed = false
                            showExposureSlider = false
                            exposureAnchor = null
                        }
                    }
                    prevX = x; prevY = y; prevZ = z; firstReading = false
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager?.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager?.unregisterListener(sensorListener)
        }
    }

    // Preview: 9:16 no modo vídeo; Full ocupa a tela toda; 3:4 usa sensor nativo
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    // Fonte única de verdade do layout adaptativo (FR-1). Retrato é o caminho
    // default: qualquer falha na detecção cai no layout de hoje (NFR-1).
    val isWide = rememberIsWideWindow()

    // Em janela larga a proporção retrato vira paisagem (FR-3). O 3:4 do modo foto
    // acompanha pelo mesmo motivo: uma caixa alta numa janela larga desperdiça a tela.
    val previewAspect = previewAspect(activeMode.aspectRatio, selectedAspectRatio, isWide)
    val previewAspectLabel = previewAspect.label
    val previewSizeModifier: Modifier = when {
        previewAspect.fillsScreen -> Modifier.fillMaxSize()
        else -> {
            val (rW, rH) = when (previewAspectLabel) {
                "16:9" -> 16f to 9f
                "9:16" -> 9f to 16f
                "4:3" -> 4f to 3f
                else -> 3f to 4f
            }
            val desiredH = screenWidthDp * (rH / rW)
            if (desiredH <= screenHeightDp)
                Modifier.width(screenWidthDp).height(desiredH)
            else Modifier.width(screenHeightDp * (rW / rH)).height(screenHeightDp)
        }
    }

    // AC-3.2: o `evt=bind` seguinte precisa trazer a proporção efetiva. Uma virada
    // recria a activity (o manifesto não declara configChanges), então há bind novo.
    LaunchedEffect(previewAspectLabel) { viewModel.setPreviewAspectLabel(previewAspectLabel) }

    // FR-5: em retrato as barras ficam sobre a faixa preta fora do preview; em
    // paisagem o preview ocupa a largura toda e elas passam a ficar sobre a imagem.
    // Mesma cor e alfa já usados na linha expansível, para não inventar um segundo
    // tom de fundo. Fica condicionado à janela larga porque em retrato aplicar o
    // fundo seria mudança visível, e o NFR-1 proíbe.
    val controlScrimColor =
        if (isWide) Color(0xFF1C1C1E).copy(alpha = 0.97f) else Color.Transparent

    val dynamicColorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        dynamicDarkColorScheme(context)
    else
        darkColorScheme()

    MaterialTheme(colorScheme = dynamicColorScheme) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                enabled = showResolutionMenu || showZoomDial
            ) {
                showResolutionMenu = false
                showZoomDial = false
            }
    ) {
        // ── Barra superior (fora do preview) ─────────────────────────────
        // Em janela larga vai para o bordo esquerdo e empilha na vertical (FR-2):
        // o eixo do container externo inverte, as linhas internas viram colunas.
        AxisContainer(
            vertical = !isWide,
            modifier = Modifier
                // Em paisagem o grupo envolve o próprio conteúdo e o `CenterStart`
                // o centraliza na vertical. Com `fillMaxHeight` os cinco ícones se
                // espalhavam pelos 1600px e virava um paredão vazio.
                .align(if (isWide) Alignment.CenterStart else Alignment.TopCenter)
                .then(if (isWide) Modifier else Modifier.fillMaxWidth())
                .zIndex(1f)
                // safeDrawing em vez de statusBarsPadding: a status bar é
                // escondida em onWindowFocusChanged, então o inset dela vira 0 e
                // a barra subiria para debaixo do recorte da câmera (o manifesto
                // usa windowLayoutInDisplayCutoutMode="shortEdges"). safeDrawing
                // considera também o displayCutout e resolve os dois casos —
                // inclusive recorte na lateral, quando o eixo é o horizontal.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        if (isWide) WindowInsetsSides.Start else WindowInsetsSides.Top
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // ── Linha principal (sempre visível) ─────────────────────
            // Só um modo que grava pode estar gravando, e o modo não muda durante a
            // gravação (FR-15) — então `isRecording` já implica o modo certo.
            val barDisabled = isRecording || countdownSeconds > 0
            // Um controle da barra, pelo identificador que o modo declara — o desenho de cada
            // um mora em components/TopBarControls.kt (ADR-001, Q-09).
            val controleDaBarra: @Composable (ControlId, CameraModeDefinition) -> Unit = { id, modo ->
                TopBarControl(
                    id, modo,
                    TopBarContext(
                        viewModel = viewModel,
                        barDisabled = barDisabled,
                        rotationDeg = iconRotation,
                        resolutionMenuOpen = showResolutionMenu,
                        onToggleResolutionMenu = { showResolutionMenu = !showResolutionMenu },
                        expanded = isTopBarExpanded,
                        onToggleExpanded = { isTopBarExpanded = !isTopBarExpanded },
                        onOpenSettings = onOpenSettings
                    )
                )
            }
            AnimatedContent(
                targetState = activeMode,
                transitionSpec = {
                    val ordem = arrangedModes.pinned + arrangedModes.drawer
                    val toRight = ordem.indexOf(targetState) > ordem.indexOf(initialState)
                    if (toRight) {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 4 }) togetherWith
                        (fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 4 })
                    } else {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 4 }) togetherWith
                        (fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 4 })
                    }
                },
                label = "topBarMainRow"
            ) { mode ->
                AxisContainer(
                    vertical = isWide,
                    arrangement = if (isWide) Arrangement.spacedBy(2.dp) else null,
                    // AC-4.3: em janela larga e baixa a coluna rola em vez de
                    // transbordar. Em retrato é `Row` e o parâmetro não age.
                    scrollable = isWide,
                    modifier = Modifier
                        .then(if (isWide) Modifier else Modifier.fillMaxWidth())
                        .background(controlScrimColor, RoundedCornerShape(28.dp))
                        .padding(if (isWide) PaddingValues(vertical = 6.dp) else PaddingValues())
                ) {
                    mode.controls.forEach { id -> TopBarSlot { controleDaBarra(id, mode) } }
                }
            }

            // ── Linha expandível ────────────────────────────────────────
            AnimatedVisibility(
                visible = isTopBarExpanded,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                AxisContainer(
                    vertical = isWide,
                    arrangement = Arrangement.SpaceAround,
                    scrollable = isWide,
                    modifier = Modifier
                        .then(if (isWide) Modifier else Modifier.fillMaxWidth())
                        // Em paisagem a linha abre ao lado da barra, não abaixo dela.
                        .padding(if (isWide) PaddingValues(start = 6.dp) else PaddingValues(top = 6.dp))
                        .background(Color(0xFF1C1C1E).copy(alpha = 0.97f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    activeMode.moreControls.forEach { controleDaBarra(it, activeMode) }
                }
            }

            // Barra horizontal: Resolução
            AnimatedVisibility(
                visible = showResolutionMenu && ControlId.RESOLUTION in activeMode.controls,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                PickerBar(
                    modifier = Modifier.padding(
                        if (isWide) PaddingValues(start = 6.dp) else PaddingValues(top = 6.dp)
                    ),
                    vertical = isWide,
                    items = availableVideoOptions.map { opt -> opt to opt.label },
                    selectedKey = selectedVideoOption,
                    onSelect = {
                        viewModel.setVideoOption(it)
                        showResolutionMenu = false
                    }
                )
            }
        }

        // ── Preview + Grid + Controles (dentro do preview) ─────────────────
        Box(modifier = previewSizeModifier
            .align(Alignment.Center)
            .pointerInput(showZoomDial, isTapToFocusEnabled) {
                if (showZoomDial) return@pointerInput
                if (!isTapToFocusEnabled) return@pointerInput
                detectTapGestures { offset ->
                    // Restringe área de foco: exclui topo e base onde ficam os controles
                    if (offset.y < 90.dp.toPx()) return@detectTapGestures
                    if (offset.y > size.height - 200.dp.toPx()) return@detectTapGestures
                    val pv = previewView ?: return@detectTapGestures
                    val point = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                    val action = FocusMeteringAction.Builder(
                        point,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                    )
                        .build() // No auto-cancel: focus stays locked at tap point until next tap
                    // Reset slider immediately so it disappears before the new focus ring appears
                    showExposureSlider = false
                    exposureAnchor = null
                    focusDimmed = false
                    viewModel.cancelFocusLock() // release previous AF lock before starting new metering
                    viewModel.tapToFocus(action, onFocusAcquired = {
                        // Reset compensation to 0 so the slider starts from the auto-exposed baseline
                        viewModel.setExposureCompensationIndex(0)
                        showExposureSlider = true
                        exposureAnchor = offset
                        exposureInteractionTick++
                    })
                    focusPoint = offset
                }
            }
            .pointerInput("exposureDrag", showZoomDial, isTapToFocusEnabled, activeMode, arrangedModes, isRecording, countdownSeconds) {
                if (showZoomDial) return@pointerInput
                var dragStartPos = Offset.Zero
                var isVertical = false
                var directionDecided = false
                var cumulativeX = 0f
                var cumulativeY = 0f
                var ignoreThisDrag = false
                var swipeModeSwitched = false
                detectDragGestures(
                    onDragStart = { pos ->
                        dragStartPos = pos
                        isVertical = false
                        directionDecided = false
                        cumulativeX = 0f
                        cumulativeY = 0f
                        swipeModeSwitched = false
                        ignoreThisDrag = pos.y < 90.dp.toPx() || pos.y > size.height - 200.dp.toPx()
                    },
                    onDrag = { _, dragAmount ->
                        if (ignoreThisDrag) return@detectDragGestures
                        cumulativeX += dragAmount.x
                        cumulativeY += dragAmount.y
                        if (!directionDecided && (abs(cumulativeX) + abs(cumulativeY) > 15f)) {
                            isVertical = abs(cumulativeY) > abs(cumulativeX) * 1.2f
                            directionDecided = true
                        }
                        if (isVertical && isTapToFocusEnabled) {
                            // Ajuste de exposição (somente com slider visível)
                            if (!showExposureSlider) return@detectDragGestures
                            val range = (exposureMax - exposureMin).toFloat()
                            if (range > 0f) {
                                val deltaProgress = -dragAmount.y / (size.height * 0.5f)
                                val deltaIndex = (deltaProgress * range).roundToInt()
                                if (deltaIndex != 0) {
                                    viewModel.setExposureCompensationIndex(exposureIndex + deltaIndex)
                                    focusDimmed = false
                                    exposureInteractionTick++
                                }
                            }
                        } else if (!isVertical && !swipeModeSwitched && !isRecording && countdownSeconds == 0) {
                            // Swipe horizontal no preview → troca de modo
                            if (abs(cumulativeX) > 80.dp.toPx()) {
                                swipeModeSwitched = true
                                focusPoint = null
                                showExposureSlider = false
                                exposureAnchor = null
                                switchMode(arrangedModes.step(activeMode, by = if (cumulativeX < 0) +1 else -1))
                            }
                        }
                    }
                )
            }
        ) {
        if (permissionGranted) {
            // A escolha entre SurfaceView e TextureView, e o porquê de ela existir, ficam
            // em CameraPreviewSurface — extraído daqui quando o arquivo bateu no teto do
            // NFR-5 (Q-05).
            CameraPreviewSurface(
                isFrontCamera = isFrontCamera,
                mirrorFrontCamera = isFrontCameraMirrorEnabled,
                fillCenter = previewAspect.fillsScreen,
                onPreviewView = { if (previewView != it) previewView = it },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Overlay de transição de modo (blur) ──────────────────────
        transitionBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(4f)
                    .blur(28.dp)
                    .graphicsLayer { alpha = transitionAlpha.value }
            )
        }

        // ── Overlay de revisão de foto ────────────────────────────────
        reviewBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f)
                    .graphicsLayer {
                        scaleX = reviewScale.value
                        scaleY = reviewScale.value
                        alpha = reviewAlpha.value
                        translationX = reviewTransX.value
                        translationY = reviewTransY.value
                    }
            )
        }

        // ── Grid overlay ──────────────────────────────────────────────
        if (isGridEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val stroke = 1.dp.toPx()
                val color = Color.White.copy(alpha = 0.35f)
                drawLine(color, Offset(w / 3f, 0f), Offset(w / 3f, h), stroke)
                drawLine(color, Offset(2 * w / 3f, 0f), Offset(2 * w / 3f, h), stroke)
                drawLine(color, Offset(0f, h / 3f), Offset(w, h / 3f), stroke)
                drawLine(color, Offset(0f, 2 * h / 3f), Offset(w, 2 * h / 3f), stroke)
            }
        }
        // ── Nível de horizonte ─────────────────────────────────────────────
        if (isLevelEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val lineLen = size.width * 0.22f
                val gap = 20f
                // "Nivelado" é propriedade física do enquadramento, medida contra a
                // gravidade — independe da janela. Já o ângulo desenhado é dentro da
                // janela: com ela girada, rollDegrees cru deixaria a linha 90° fora.
                // `levelRoll` para linha e verde, senão discordariam em movimento.
                // Continua sendo inclinação física, como o AC-9.3 exige — só menos ruidosa.
                val isLevel = abs(levelRoll) < LEVEL_TOLERANCE ||
                    abs(abs(levelRoll) - 90f) < LEVEL_TOLERANCE
                val levelColor = if (isLevel) Color(0xFF30D158) else Color.White.copy(alpha = 0.55f)
                val strokeW = 1.dp.toPx()
                val angleRad =
                    (windowRelativeRoll(levelRoll, displayRotation) * PI / 180.0).toFloat()
                val cosA = cos(angleRad)
                val sinA = sin(angleRad)
                drawLine(levelColor,
                    Offset(cx - lineLen * cosA, cy - lineLen * sinA),
                    Offset(cx - gap * cosA, cy - gap * sinA),
                    strokeWidth = strokeW)
                drawLine(levelColor,
                    Offset(cx + gap * cosA, cy + gap * sinA),
                    Offset(cx + lineLen * cosA, cy + lineLen * sinA),
                    strokeWidth = strokeW)
                drawCircle(levelColor, radius = 2.dp.toPx(), center = Offset(cx, cy))
            }
        }

        // ── Focus ring (tap-to-focus) ──────────────────────────────────
        focusPoint?.let { fp ->
            val ringAlpha by animateFloatAsState(
                targetValue = if (focusDimmed) 0.25f else 0.85f,
                animationSpec = tween(durationMillis = 800),
                label = "focusRingAlpha"
            )
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (fp.x.toInt() - 32.dp.roundToPx()),
                            (fp.y.toInt() - 32.dp.roundToPx())
                        )
                    }
                    .size(64.dp)
                    .border(0.7.dp, Color.White.copy(alpha = ringAlpha), RoundedCornerShape(4.dp))
            )
        }
        // ── Exposure slider ───────────────────────────────────────
        if (showExposureSlider) {
            exposureAnchor?.let { anchor ->
                val sliderAlpha by animateFloatAsState(
                    targetValue = if (focusDimmed) 0.3f else 1f,
                    animationSpec = tween(durationMillis = 800),
                    label = "sliderAlpha"
                )
                val sliderHeightDp = 160.dp
                val sliderWidthDp = 28.dp
                val density = androidx.compose.ui.platform.LocalDensity.current
                val anchorXDp = with(density) { anchor.x.toDp() }
                val halfScreen = screenWidthDp / 2
                val sliderX = if (anchorXDp < halfScreen)
                    anchorXDp + 44.dp
                else
                    anchorXDp - 44.dp - sliderWidthDp
                val sliderY = with(density) { anchor.y.toDp() } - sliderHeightDp / 2
                ExposureSlider(
                    modifier = Modifier
                        .offset(x = sliderX, y = sliderY)
                        .width(sliderWidthDp)
                        .height(sliderHeightDp),
                    index = exposureIndex,
                    min = exposureMin,
                    max = exposureMax,
                    alpha = sliderAlpha
                )
            }
        }
        // ── Timer de gravação (centro-topo) ──────────────────────────
        AnimatedVisibility(
            visible = isRecording,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
                    .rotate(iconRotation),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (recordingState == RecordingState.Recording) Color.Red
                            else Color(0xFFFFB700),
                            CircleShape
                        )
                )
                Text(
                    text = formatSeconds(recordingSeconds),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Overlay próprio do modo (ADR-001) — resolvido fora da tela, em ModeOverlays.
        activeMode.overlay?.let { overlay ->
            ModeOverlay(overlay, viewModel, isWide, enabled = countdownSeconds == 0, rotationDeg = iconRotation)
        }

        ModeRejectedNotice(viewModel)

        // Véu da gaveta: um toque fora dela fecha, sem focar nem trocar de modo.
        if (showModeDrawer) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        showModeDrawer = false
                    }
            )
        }

        // ── Controles inferiores ─────────────────────────────────────
        // Em janela larga vão para o bordo direito, empilhados (FR-2). O eixo do
        // grupo não inverte: continua empilhando zoom, modo e botões na ordem de
        // sempre — o que muda é a âncora e de que lado vem o respiro do sistema.
        Column(
            modifier = Modifier
                .align(if (isWide) Alignment.CenterEnd else Alignment.BottomCenter)
                .then(if (isWide) Modifier else Modifier.fillMaxWidth())
                // Era `padding(bottom = 36.dp)` fixo, provavelmente calibrado a
                // olho num aparelho com navegação por gestos (~24dp de inset).
                // Em navegação de 3 botões (~48dp) o obturador ficava por baixo
                // da barra. O inset do sistema mais um respiro fixo se adapta.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        if (isWide) WindowInsetsSides.End else WindowInsetsSides.Bottom
                    )
                )
                .padding(if (isWide) PaddingValues(end = 12.dp) else PaddingValues(bottom = 12.dp))
                // AC-4.3: este é o grupo mais alto — zoom, seletor de modo, obturador
                // e miniatura, com 16dp entre eles. Numa janela larga e baixa é o
                // primeiro a não caber, e sem rolagem os controles se sobrepõem.
                // Só em janela larga: em retrato a altura é sobrando, e mexer no
                // caminho de retrato é o que o NFR-1 proíbe.
                .then(
                    if (isWide) Modifier.verticalScroll(rememberScrollState()) else Modifier
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedVisibility(
                visible = maxZoomRatio > minZoomRatio && !showZoomDial,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ZoomPresetBar(
                    selectedZoom = selectedZoomLevel,
                    minZoom = minZoomRatio,
                    maxZoom = maxZoomRatio,
                    enabled = true,
                    vertical = isWide,
                    rotationDeg = iconRotation,
                    onSelect = { zoom ->
                        val alreadySelected = abs(selectedZoomLevel - zoom) < 0.08f
                        if (alreadySelected) {
                            showZoomDial = true
                            zoomDialInteractionTick++
                        } else {
                            viewModel.setZoomLevel(zoom)
                            showZoomDial = false
                        }
                    },
                    onOpenDial = {
                        showZoomDial = true
                        zoomDialInteractionTick++
                    }
                )
            }

            // Gaveta "mais modos" (FR-6, FR-17): abre acima do seletor, na mesma coluna,
            // para acompanhar retrato e janela larga sem deslocamento fixo.
            AnimatedVisibility(visible = showModeDrawer, enter = fadeIn(), exit = fadeOut()) {
                ModeDrawer(
                    modes = arrangedModes.drawer,
                    active = activeMode,
                    onModeSelect = { showModeDrawer = false; switchMode(it) },
                    onEdit = { showModeDrawer = false; onOpenSettings() },
                    modifier = if (isWide) Modifier.width(280.dp) else Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            // Seletor de modo — os modos vêm do registro (AC-1.1)
            AnimatedVisibility(
                visible = !isRecording && countdownSeconds == 0,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ModeSelector(
                    modes = arrangedModes,
                    active = activeMode,
                    vertical = isWide,
                    scrimColor = controlScrimColor,
                    onModeSelect = { switchMode(it) },
                    onOpenDrawer = { showModeDrawer = true }
                )
            }

            // Linha de botões: Flip | Gravar | Pausar/Thumb.
            //
            // Em retrato é um `Box` com âncoras, e não um `Row`: é o que mantém o
            // obturador exatamente no centro da tela mesmo quando a miniatura não
            // existe. Em paisagem isso não serve — âncoras num `Box` empilhado
            // sobrepõem os três — então ali é uma coluna compacta de verdade, e os
            // três controles são declarados uma única vez como lambdas.
            val controleFlip: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .clickable(enabled = !isRecording) { viewModel.flipCamera() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FlipCameraAndroid,
                    contentDescription = stringResource(R.string.camera_switch),
                    tint = if (isRecording) Color.White.copy(alpha = 0.28f) else Color.White,
                    modifier = Modifier.size(26.dp).rotate(iconRotation)
                )
            }
            }

            // Gravar / Parar / Foto (centro)
            val controleObturador: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    .clickable {
                        // O modo decide o que o disparador faz (FR-2, ADR-001).
                        if (viewModel.onShutter(snappedSurfaceRotation) == ShutterAction.CapturePhoto) {
                            reviewIconRotation = snappedIconRotation
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    activeMode.output == CaptureOutput.PHOTO -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .border(3.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                                .padding(5.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                    isRecording -> {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.White, RoundedCornerShape(5.dp))
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFFE53935), CircleShape)
                        )
                    }
                }
            }
            }

            // Pausa/thumb (vídeo) ou thumb da foto
            val controleFinal: @Composable () -> Unit = {
            Box {
                if (activeMode.output == CaptureOutput.VIDEO) {
                    PauseOrThumbnailControl(
                        isRecording = isRecording,
                        isPaused = recordingState == RecordingState.Paused,
                        lastVideoUri = lastVideoUri,
                        rotationDeg = iconRotation,
                        onPause = { viewModel.pauseRecording() },
                        onResume = { viewModel.resumeRecording() },
                        onThumbnailClick = {
                            if (lastVideoUri != null) {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(lastVideoUri, "video/mp4")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                } else {
                    if (lastPhotoUri != null) {
                        LastPhotoThumbnail(uri = lastPhotoUri!!) {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(lastPhotoUri, "image/jpeg")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                    }
                }
            }
            }

            if (isWide) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    controleFlip()
                    controleObturador()
                    controleFinal()
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                ) {
                    Box(modifier = Modifier.align(Alignment.CenterStart)) { controleFlip() }
                    Box(modifier = Modifier.align(Alignment.Center)) { controleObturador() }
                    Box(modifier = Modifier.align(Alignment.CenterEnd)) { controleFinal() }
                }
            }
        } // end Column controles

        // ── Overlay de contagem regressiva ───────────────────────────────
        AnimatedVisibility(
            visible = countdownSeconds > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = countdownSeconds,
                    transitionSpec = {
                        (fadeIn(tween(200)) togetherWith fadeOut(tween(150)))
                    },
                    label = "countdown"
                ) { seconds ->
                    Text(
                        text = "$seconds",
                        color = Color.White,
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = showZoomDial && maxZoomRatio > minZoomRatio,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(if (isWide) Alignment.CenterEnd else Alignment.BottomCenter)
                .zIndex(2f)
        ) {
            LensZoomDial(
                minZoom = minZoomRatio,
                maxZoom = maxZoomRatio,
                currentZoom = selectedZoomLevel,
                screenHeightDp = screenHeightDp,
                railLengthDp = if (isWide) screenHeightDp else screenWidthDp,
                vertical = isWide,
                enabled = true,
                rotationDeg = iconRotation,
                onZoomChange = {
                    viewModel.setZoomLevel(it)
                    zoomDialInteractionTick++
                },
                onInteraction = { zoomDialInteractionTick++ }
            )
        }
        } // end preview Box
    }
    } // end MaterialTheme
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun formatSeconds(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

@Composable
private fun AxisScope.TopBarSlot(
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        // Peso só no horizontal: numa coluna, distribuir a altura da tela entre
        // cinco slots deixa 300px de vazio entre ícones. No vertical o slot tem o
        // tamanho do conteúdo e o grupo fica compacto.
        modifier = if (vertical) Modifier else Modifier.axisWeight(),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun PauseOrThumbnailControl(
    isRecording: Boolean,
    isPaused: Boolean,
    lastVideoUri: Uri?,
    rotationDeg: Float = 0f,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onThumbnailClick: () -> Unit
) {
    AnimatedVisibility(
        visible = isRecording,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                .clickable { if (isPaused) onResume() else onPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = stringResource(R.string.camera_pause_resume),
                tint = Color.White,
                modifier = Modifier.size(26.dp).rotate(rotationDeg)
            )
        }
    }
    AnimatedVisibility(
        visible = !isRecording && lastVideoUri != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (lastVideoUri != null) {
            LastVideoThumbnail(uri = lastVideoUri, onClick = onThumbnailClick)
        }
    }
}

/**
 * Disco de zoom decimal.
 *
 * @param railLengthDp comprimento do eixo em que o disco corre: a largura da tela em
 *   retrato, a altura em paisagem. Documentado aqui e não na lista de parâmetros
 *   porque a assinatura entra na chave do baseline do Detekt — comentário inline ali
 *   faz a entrada deixar de casar.
 */
@Composable
private fun LensZoomDial(
    minZoom: Float,
    maxZoom: Float,
    currentZoom: Float,
    screenHeightDp: Dp,
    railLengthDp: Dp,
    vertical: Boolean,
    enabled: Boolean,
    rotationDeg: Float = 0f,
    onZoomChange: (Float) -> Unit,
    onInteraction: () -> Unit
) {
    val startAngle = 198f
    val sweepAngle = 144f
    val indicatorAngle = 270f
    val trackColor = Color.White.copy(alpha = if (enabled) 0.36f else 0.16f)
    val activeColor = if (enabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f)
    var dialZoom by remember { mutableStateOf(currentZoom) }
    var isDragging by remember { mutableStateOf(false) }
    var dragBaseZoom by remember { mutableStateOf(currentZoom) }

    LaunchedEffect(currentZoom) {
        if (!isDragging) dialZoom = currentZoom
    }

    fun toProgress(value: Float): Float {
        if (maxZoom <= minZoom) return 0f
        return ((value - minZoom) / (maxZoom - minZoom)).coerceIn(0f, 1f)
    }

    fun zoomFromDragDx(baseZoom: Float, dx: Float): Float {
        if (maxZoom <= minZoom) return minZoom
        val deltaProgress = (-dx / 800f) // right-to-left increases zoom; 800px = full range
        val zoomDelta = (maxZoom - minZoom) * deltaProgress
        return (baseZoom + zoomDelta).coerceIn(minZoom, maxZoom)
    }

    fun isWithinArcWindow(angle: Float): Boolean {
        var normalized = angle
        while (normalized < startAngle) normalized += 360f
        while (normalized > startAngle + 360f) normalized -= 360f
        return normalized in startAngle..(startAngle + sweepAngle)
    }

    // O arco é sempre desenhado "deitado", com o centro do círculo abaixo da caixa.
    // Em paisagem a caixa externa reserva o espaço na lateral e o conteúdo é girado
    // -90°, então o arco passa a abrir para dentro da tela e a arrastar na vertical.
    //
    // `requiredSize` é necessário: o conteúdo continua medindo o comprimento do
    // trilho no eixo maior, o que a caixa externa girada não permitiria.
    // `Modifier.rotate` também transforma as coordenadas de toque, então o arraste
    // segue lido em `dragAmount.x` — no eixo do próprio disco, não da tela.
    Box(
        modifier = if (vertical)
            Modifier.width(DIAL_DEPTH).height(railLengthDp)
        else
            Modifier.fillMaxWidth().height(DIAL_DEPTH),
        contentAlignment = Alignment.Center
    ) {
    Box(
        modifier = Modifier
            .requiredSize(width = railLengthDp, height = DIAL_DEPTH)
            .then(if (vertical) Modifier.rotate(-90f) else Modifier)
            .pointerInput(minZoom, maxZoom, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        onInteraction()
                        isDragging = true
                        dragBaseZoom = dialZoom
                    },
                    onDrag = { _, dragAmount ->
                        onInteraction()
                        val nextZoom = zoomFromDragDx(dragBaseZoom, dragAmount.x)
                        if (abs(nextZoom - dialZoom) > 0.0005f) {
                            dialZoom = nextZoom
                            onZoomChange(nextZoom)
                        }
                        dragBaseZoom = dialZoom
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val textSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 11.sp.toPx() }
        val labelPaint = remember(activeColor, trackColor, textSizePx) {
            Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = textSizePx
                color = trackColor.toArgb()
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = screenHeightDp.toPx()
            val radius = diameter / 2f
            val cx = size.width / 2f
            val cy = size.height + radius * 0.42f
            val topLeft = Offset(cx - radius, cy - radius)
            val arcSize = ComposeSize(diameter, diameter)

            drawArc(
                color = Color.Black.copy(alpha = 0.28f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = topLeft,
                size = arcSize
            )

            val progress = toProgress(dialZoom)
            // Place current zoom exactly at the indicator (indicatorAngle - startAngle = 72°)
            val rotation = (indicatorAngle - startAngle) - progress * sweepAngle

            val tickStep = (maxZoom - minZoom) / 60f
            for (i in 0..60) {
                val t = i / 60f
                val angle = startAngle + (t * sweepAngle) + rotation
                if (!isWithinArcWindow(angle)) continue
                val rad = Math.toRadians(angle.toDouble())
                val tickZoom = minZoom + t * (maxZoom - minZoom)
                val major = abs(tickZoom - tickZoom.roundToInt()) < tickStep * 0.5f
                val inner = radius - if (major) 20.dp.toPx() else 12.dp.toPx()
                val outer = radius + if (major) 5.dp.toPx() else 2.dp.toPx()
                val x1 = cx + inner * cos(rad).toFloat()
                val y1 = cy + inner * sin(rad).toFloat()
                val x2 = cx + outer * cos(rad).toFloat()
                val y2 = cy + outer * sin(rad).toFloat()
                drawLine(
                    color = if (major) Color.White.copy(alpha = 0.55f) else trackColor,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = if (major) 2.2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            val rulerLabels = listOf(minZoom, 1f, 2f, 5f, 10f, 20f, maxZoom)
                .filter { it in minZoom..maxZoom }
                .distinctBy { (it * 10f).roundToInt() }
                .sorted()

            drawIntoCanvas { canvas ->
                rulerLabels.forEach { value ->
                    val p = toProgress(value)
                    val angle = startAngle + (p * sweepAngle) + rotation
                    if (!isWithinArcWindow(angle)) return@forEach
                    val rad = Math.toRadians(angle.toDouble())
                    val tx = cx + (radius - 34.dp.toPx()) * cos(rad).toFloat()
                    val ty = cy + (radius - 34.dp.toPx()) * sin(rad).toFloat()

                    labelPaint.color = if (abs(value - dialZoom) < 0.15f) activeColor.toArgb() else trackColor.toArgb()
                    val text = if (abs(value - value.roundToInt().toFloat()) < 0.05f) {
                        value.roundToInt().toString()
                    } else {
                        ((value * 10f).roundToInt() / 10f).toString()
                    }
                    canvas.nativeCanvas.save()
                    // +90 quando vertical para contrarrotacionar o giro do disco:
                    // sem isso os números das marcas saem tombados.
                    canvas.nativeCanvas.rotate(
                        rotationDeg + if (vertical) 90f else 0f, tx, ty
                    )
                    canvas.nativeCanvas.drawText(text, tx, ty, labelPaint)
                    canvas.nativeCanvas.restore()
                }
            }

            val pointerRad = Math.toRadians(indicatorAngle.toDouble())
            val p1 = Offset(
                cx + (radius - 16.dp.toPx()) * cos(pointerRad).toFloat(),
                cy + (radius - 16.dp.toPx()) * sin(pointerRad).toFloat()
            )
            val p2 = Offset(
                cx + (radius + 6.dp.toPx()) * cos(pointerRad).toFloat(),
                cy + (radius + 6.dp.toPx()) * sin(pointerRad).toFloat()
            )
            drawLine(
                color = activeColor,
                start = p1,
                end = p2,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        Text(
            text = "${(dialZoom * 10f).roundToInt() / 10f}×",
            color = if (enabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.32f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            // O rótulo contrarrotaciona os -90° do disco para continuar legível.
            modifier = Modifier
                .offset(y = (-122).dp)
                .rotate(rotationDeg + if (vertical) 90f else 0f)
        )
    }
    }
}

/** Profundidade do disco de zoom: o quanto ele avança para dentro da tela. */
private val DIAL_DEPTH = 360.dp

/**
 * Presets de zoom visíveis: 1×, 2× e 5×, com o slot da faixa atual substituído pelo
 * zoom exato em uso, filtrados pelo que o hardware suporta.
 *
 * Extraída de [ZoomPresetBar] em 2026-08-04. O motivo imediato foi a complexidade
 * ciclomática, que estava em 16 e congelada no baseline do Detekt — adicionar o
 * parâmetro de eixo mudou a assinatura e desfez o casamento da entrada. Em vez de
 * recongelar, a dívida foi paga.
 */
private const val ZOOM_SLOT_WIDE = 1f
private const val ZOOM_SLOT_MID = 2f
private const val ZOOM_SLOT_TELE = 5f

/** Tolerância para casar um preset com o slot que ele substitui. */
private const val ZOOM_SLOT_EPSILON = 0.001f

/** Uma casa decimal: o rótulo mostra `1.5×`, não `1.4999×`. */
private const val ZOOM_DECIMAL_SCALE = 10f

/** Abaixo disso o zoom é tratado como inteiro no rótulo. */
private const val ZOOM_INTEGER_EPSILON = 0.05f

private fun zoomPresets(selectedZoom: Float, minZoom: Float, maxZoom: Float): List<Float> {
    val replacementSlot = when {
        selectedZoom < ZOOM_SLOT_MID -> ZOOM_SLOT_WIDE
        selectedZoom <= ZOOM_SLOT_TELE -> ZOOM_SLOT_MID
        else -> ZOOM_SLOT_TELE
    }
    return listOf(ZOOM_SLOT_WIDE, ZOOM_SLOT_MID, ZOOM_SLOT_TELE)
        .map { slot -> if (abs(slot - replacementSlot) < ZOOM_SLOT_EPSILON) selectedZoom else slot }
        .filter { it in minZoom..maxZoom }
        .distinctBy { (it * ZOOM_DECIMAL_SCALE).roundToInt() }
}

/** `2×` para valores inteiros, `1.5×` para o resto. */
private fun zoomLabel(zoom: Float): String =
    if (abs(zoom - zoom.roundToInt().toFloat()) < ZOOM_INTEGER_EPSILON) {
        "${zoom.roundToInt()}×"
    } else {
        "${(zoom * ZOOM_DECIMAL_SCALE).roundToInt() / ZOOM_DECIMAL_SCALE}×"
    }

@Composable
private fun ZoomPresetBar(
    selectedZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    enabled: Boolean,
    vertical: Boolean,
    rotationDeg: Float = 0f,
    onSelect: (Float) -> Unit,
    onOpenDial: () -> Unit
) {
    val presets = zoomPresets(selectedZoom, minZoom, maxZoom)
    if (presets.isEmpty()) return

    AxisContainer(
        vertical = vertical,
        arrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.52f), RoundedCornerShape(24.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        presets.forEach { zoom ->
            val isSelected = abs(selectedZoom - zoom) < 0.08f
            Box(
                modifier = Modifier
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent,
                        CircleShape
                    )
                    .pointerInput(enabled, isSelected) {
                        detectTapGestures(
                            onLongPress = { if (enabled) onOpenDial() },
                            onTap = { if (enabled) onSelect(zoom) }
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = zoomLabel(zoom),
                    color = when {
                        !enabled -> Color.White.copy(alpha = 0.25f)
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> Color.White.copy(alpha = 0.55f)
                    },
                    fontSize = if (isSelected) 14.sp else 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.rotate(rotationDeg)
                )
            }
        }
    }
}

@Composable
private fun LastVideoThumbnail(uri: Uri, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(128, 128), null)
                } else {
                    @Suppress("DEPRECATION")
                    val path = uri.path ?: return@withContext null
                    ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = stringResource(R.string.camera_last_video),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Ícone de play sobreposto
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(24.dp)
            )
        } else {
            Icon(
                Icons.Default.VideoFile,
                contentDescription = stringResource(R.string.camera_last_video),
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}


// Resolution two-line button
@Composable
private fun LastPhotoThumbnail(uri: Uri, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(128, 128), null)
                } else {
                    null
                }
            } catch (e: Exception) { null }
        }
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = stringResource(R.string.camera_last_photo),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Default.PhotoCamera,
                contentDescription = stringResource(R.string.camera_last_photo),
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

// Barra horizontal genérica de seleção
@Composable
private fun <T> PickerBar(
    modifier: Modifier = Modifier,
    vertical: Boolean,
    items: List<Pair<T, String>>,
    selectedKey: T,
    onSelect: (T) -> Unit
) {
    AxisContainer(
        vertical = vertical,
        arrangement = Arrangement.SpaceAround,
        modifier = modifier
            .then(if (vertical) Modifier else Modifier.fillMaxWidth())
            .background(Color(0xFF1C1C1E).copy(alpha = 0.97f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        items.forEach { (key, label) ->
            val isSelected = key == selectedKey
            Box(
                modifier = Modifier
                    .then(if (vertical) Modifier else Modifier.axisWeight())
                    .padding(horizontal = 4.dp)
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onSelect(key) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = OFF_CONTROL_ALPHA),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ExposureSlider(
    modifier: Modifier = Modifier,
    index: Int,
    min: Int,
    max: Int,
    alpha: Float = 1f
) {
    if (max <= min) return
    val range = (max - min).toFloat()
    val progress = ((index - min) / range).coerceIn(0f, 1f)
    val trackFraction = 0.75f
    var boxHeightPx by remember { mutableStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(
        modifier = modifier.onGloballyPositioned { boxHeightPx = it.size.height.toFloat() },
        contentAlignment = Alignment.Center
    ) {
        // Track line
        Box(
            modifier = Modifier
                .width(1.5.dp)
                .fillMaxHeight(trackFraction)
                .background(Color.White.copy(alpha = 0.4f * alpha), RoundedCornerShape(1.dp))
        )
        // Sun icon tracks exposure position
        // offset: progress=1 (max/bright) → top of track; progress=0 (min/dark) → bottom
        val iconOffsetDp = with(density) {
            (boxHeightPx * (1f - 2f * progress) * trackFraction / 2f).toDp()
        }
        Icon(
            imageVector = Icons.Default.WbSunny,
            contentDescription = stringResource(R.string.camera_exposure),
            tint = if (index == 0) Color.White.copy(alpha = 0.75f * alpha) else MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            modifier = Modifier
                .size(18.dp)
                .offset(y = iconOffsetDp)
        )
    }
}
