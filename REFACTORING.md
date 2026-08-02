# Backlog de refatoração

Levantamento feito na rodada de instrumentação. **Nada aqui foi executado** — é
mapa, não plano aprovado. A ordem sugerida vai do que mais destrava para o que
mais custa.

Por que só agora: refatorar sem git, sem teste e sem build reprodutível é
trabalhar no escuro. Essas três coisas estão no lugar; a partir daqui cada passo
abaixo pode ser feito com rede de segurança.

## 0. Subir para API 36 (Android 16) — tem prazo

**Prioridade acima de tudo o que vem abaixo**, porque tem data: a Play Store
exige `targetSdk` a no máximo um ano da versão mais recente do Android, e a
partir de **31/10/2026** não aceita mais atualização de app fora dessa regra. O
projeto está em `targetSdk = 34` (Android 14), duas versões atrás.

Isso não é refatoração — é pré-requisito para publicar. Faça antes dos itens 1 a 4.

### O que muda no build

`compileSdk` e `targetSdk` para 36 em `gradle/libs.versions.toml`. O AGP 8.7.3
atual **não compila contra o SDK 36**; provavelmente será preciso subir o AGP de
novo (e o Gradle junto). `minSdk = 24` pode ficar como está.

### O que precisa ser verificado no app

Passar de 34 direto para 36 acumula as mudanças de comportamento de duas versões.
As que têm chance real de afetar este app:

- **Edge-to-edge obrigatório (Android 15/16).** A partir do targetSdk 35 o
  sistema desenha de borda a borda e no 36 não há mais como optar por sair. O
  app já faz `WindowCompat.setDecorFitsSystemWindows(window, false)` e esconde a
  status bar, então está no caminho certo — mas todos os `padding` da
  `CameraScreen` e o `Scaffold` das telas de configuração precisam ser conferidos
  contra os insets, principalmente em aparelho com barra de gestos.
- **Alinhamento de 16 KB de página.** Confirmado que o app **tem** biblioteca
  nativa: o CameraX empacota `libimage_processing_util_jni.so` nas quatro ABIs.
  Então esse requisito se aplica de fato, e provavelmente vai exigir subir o
  CameraX (hoje 1.4.0) junto com o SDK.
- **Permissões de mídia.** `READ_EXTERNAL_STORAGE` já não vale desde a API 33.
  As miniaturas da última foto/vídeo hoje só funcionam para mídia do próprio app.
  Ao subir o target, decidir entre declarar `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO`
  ou usar o photo picker.
- **Predictive back.** Vale conferir o comportamento do `BackHandler` nas telas
  de configuração e Sobre.

### Além do target, para publicar

Os pendentes de release já estão listados em [BUILD.md](BUILD.md#release-o-que-falta):
sem R8, sem regras de ProGuard e sem assinatura configurada. Some-se a isso o
que a loja pede por fora do código — política de privacidade (o app acessa
câmera, microfone e localização), formulário de segurança de dados e material
gráfico.

### Como validar

Com a instrumentação no lugar, a checagem é direta: subir o target, rodar
`scripts/smoke.sh` e comparar o `evt=caps` e o `evt=bind` antes e depois. Se o
aparelho passar a reportar capacidades diferentes, aparece ali.

## Tamanho atual

| Arquivo | Linhas | Problema |
|---|---:|---|
| `presentation/screens/CameraScreen.kt` | ~1.950 | um composable de ~1.100 linhas |
| `camera/CameraManager.kt` | ~1.130 | cinco responsabilidades no mesmo arquivo |
| `presentation/viewmodels/CameraViewModel.kt` | ~615 | ~30 `StateFlow` soltos |

## 1. Quebrar `CameraScreen.kt`

O composable `CameraScreen` (linhas ~113–1210) concentra permissões, sensores,
gestos, animações e todos os overlays. Os próprios comentários de seção já
marcam onde cortar:

- `CameraTopBar` — linha principal + linha expansível
- `CameraPreview` — `AndroidView`/`PreviewView` + gestos de toque e arraste
- Overlays, um por arquivo: grade, nível de horizonte, anel de foco, slider de
  exposição, cronômetro, contagem regressiva, transição de modo, revisão da foto
- `CameraBottomControls` — obturador, miniatura, seletor de modo

O que já é privado e coeso (`LensZoomDial`, `ZoomPresetBar`, `ModeSelector`,
`LastVideoThumbnail`, `LastPhotoThumbnail`, `ExposureSlider`,
`HorizontalPickerBar`, os botões da barra) pode sair primeiro para
`presentation/components/` — é recorte mecânico, sem mudar comportamento.

O estado local (`showZoomDial`, `focusPoint`, `exposureAnchor`,
`isTopBarExpanded`, os `Animatable`) precisa ir junto para um
`rememberCameraScreenState()`, senão os pedaços continuam amarrados.

**Sugestão:** um overlay por PR, verificando na tela a cada passo. Não vale a
pena tentar tudo de uma vez.

## 2. Separar `CameraManager.kt`

Hoje o arquivo faz cinco coisas distintas:

| Responsabilidade | Onde está hoje |
|---|---|
| Bind dos use cases do CameraX | `bindCameraUseCases`, `scheduleBind` |
| Sondagem de capacidades (Camera2) | `detectUltraWideCamera`, `refreshAvailableResolutions`, checagens de EIS/HDR |
| Gravação de vídeo | `startRecording`, `pause/resume/stop` |
| Captura e processamento de imagem | `takePhoto`, `enhanceBitmap`, `cropBitmapToScreenRatio`, `rotateBitmap` |
| Persistência de mídia e EXIF | `saveBitmapWithExif`, `decimalToDmsExif` |

Os dois últimos blocos são os mais fáceis de tirar e os mais valiosos: são
função pura sobre `Bitmap` e escrita no MediaStore, ambos testáveis isoladamente
(`enhanceBitmap` e `cropBitmapToScreenRatio` não dependem de câmera nenhuma).

Alvo: `CameraBinder`, `CameraCapabilityProbe`, `VideoRecorder`, `PhotoCapturer`,
`ImagePostProcessor`, `MediaSaver` — com `CameraManager` virando o orquestrador
que já é a implementação de `CameraController`.

`decimalToDmsExif` (~linha 1035) tem um `val d = ...` colado na mesma linha da
assinatura; provavelmente um merge malfeito. Vale olhar ao mexer.

## 3. Agrupar o estado do ViewModel

~30 `StateFlow` privados espelhados em ~30 públicos, mais 15 blocos
`viewModelScope.launch { controller.X.collect { ... } }` que fazem só repasse.

Duas frentes:

- Agrupar em `data class` por assunto: `CaptureUiState` (modo, gravação,
  cronômetro, contagem), `CameraSettingsUiState` (EIS, NR, HDR, bitrate,
  qualidade), `HardwareState` (suportes, zoom, exposição, resoluções).
- Substituir os `collect` de repasse por `stateIn(viewModelScope, ...)` sobre o
  flow do controller — menos código e sem risco de coletor esquecido.

Faça **depois** dos itens 1 e 2: mudar a forma do estado obriga a tocar a UI, e é
melhor que a UI já esteja quebrada em pedaços.

## 4. `VideoRepository` não paga o próprio custo

`VideoRepositoryImpl` só repassa quatro chamadas para o controller, e dois de
seus métodos são stub (`getVideoFileSize` retorna `0L`,
`getFormattedFileSize` retorna `""`). Com `CameraController` já existindo como
abstração, a camada virou intermediária sem função.

Opções: implementar de verdade os dois métodos (tamanho do arquivo é informação
útil na UI) ou remover a camada e deixar o ViewModel falar com o controller.

`VideoStorage` também está órfã — nenhuma classe a referencia. Ou some, ou passa
a ser usada pelo `MediaSaver` do item 2.

## 5. Pendências menores

O baseline de lint congelou 158 achados (`app/lint-baseline.xml`). Os que valem
atenção, já que afetam distribuição ou correção:

- **`UnnecessaryRequiredFeature`** — o manifesto exige
  `android.hardware.camera.autofocus` sem `android:required="false"`, o que
  **exclui aparelhos da Play Store** desnecessariamente. Correção de uma linha e
  vale fazer antes de publicar.
- **`ScopedStorage`** (2x) — `READ_EXTERNAL_STORAGE` está depreciado a partir da
  API 33 e não é concedido. Ver o item 0; é o que limita as miniaturas à mídia
  do próprio app.
- **`MissingPermission`** — chamada de localização sem checagem explícita de
  permissão. Hoje protegida por convenção (`isSaveLocationEnabled`), não por
  verificação.
- **`UnsafeOptInUsageError`** (31x) — o `@OptIn(ExperimentalCamera2Interop)` no
  topo do `CameraManager` não está surtindo efeito. O compilador Kotlin também
  avisa: *"Annotation ... is not an opt-in requirement marker"*. Na prática o uso
  de Camera2 interop está sem opt-in válido.
- **`GradleDependency`** (84x) — dependências desatualizadas. Uma passada de
  atualização faz sentido junto do item 0, já que subir o SDK provavelmente vai
  exigir CameraX e Compose mais novos de qualquer forma.
- **`AutoboxingStateCreation`** (7x) — `mutableStateOf<Int/Float>` onde caberia
  `mutableIntStateOf`/`mutableFloatStateOf`, na `CameraScreen`. Alocação por
  recomposição num caminho quente.
- **`UnusedResources`** (14x) — os strings mortos citados abaixo e ícones.
- **`LockedOrientationActivity`** — `screenOrientation="portrait"` fixo. Consciente,
  mas o lint reclama por causa de dobráveis e tablets.

- **`allowBackup="true"`** com backup automático das preferências — decidir se é
  intencional.
- **Release sem minify:** `isMinifyEnabled = false`, e `proguard-rules.pro` está
  praticamente vazio. Sem assinatura configurada também.
- **Avisos do compilador:** APIs depreciadas (`setTargetAspectRatio`,
  `ThumbnailUtils.createVideoThumbnail`, `CamcorderProfile.get`), variáveis não
  usadas (`isCameraReady` em `CameraScreen`, `dragStartPos`) e sombreamento de
  `density`.
- **`android.enableJetifier=true`** em `gradle.properties` sem nenhuma
  dependência de support library — só custa tempo de build.
- **`themes.xml`** herda de `android:Theme.Material.Light.NoActionBar` enquanto o
  app inteiro é Compose Material 3 com cor dinâmica; o tema XML só vale para a
  splash.
- **`strings.xml`** tem entradas que ninguém usa (`start_recording`,
  `resolution_4k`, ...) enquanto a UI hardcoda texto em português direto no
  Compose. Decidir por um dos dois caminhos.
