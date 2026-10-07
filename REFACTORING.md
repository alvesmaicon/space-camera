# Backlog de refatoração

Levantamento feito na rodada de instrumentação. É mapa, não plano aprovado. A ordem
sugerida vai do que mais destrava para o que mais custa.

**O item 0 foi executado** (API 36, em 2026-08-04, mais o layout adaptativo que a
mudança de orientação exigiu). Os itens 1 a 5 seguem pendentes.

Por que só agora: refatorar sem git, sem teste e sem build reprodutível é
trabalhar no escuro. Essas três coisas estão no lugar; a partir daqui cada passo
abaixo pode ser feito com rede de segurança.

## 0. Subir para API 36 (Android 16) — CONCLUÍDO em 2026-08-04

Tinha data: a Play Store exige `targetSdk` a no máximo um ano da versão mais recente
do Android, e a partir de **31/10/2026** não aceita mais atualização fora dessa regra.

**Como ficou:** `compileSdk` e `targetSdk` em 36, AGP 8.13.2 e Gradle 8.14.3 (o 8.7.3
de fato não compilava contra o SDK 36), `minSdk 24` mantido. Tudo em
`gradle/libs.versions.toml`.

### O que precisa ser verificado no app

Passar de 34 direto para 36 acumula as mudanças de comportamento de duas versões.
As que têm chance real de afetar este app:

- **Edge-to-edge obrigatório (Android 15/16).** A partir do targetSdk 35 o
  sistema desenha de borda a borda e no 36 não há mais como optar por sair. O
  app já faz `WindowCompat.setDecorFitsSystemWindows(window, false)` e esconde a
  status bar, então está no caminho certo — mas todos os `padding` da
  `CameraScreen` e o `Scaffold` das telas de configuração precisam ser conferidos
  contra os insets, principalmente em aparelho com barra de gestos.
- **Alinhamento de 16 KB de página — verificado, já conforme.** O app tem
  biblioteca nativa (o CameraX empacota `libimage_processing_util_jni.so` nas
  quatro ABIs), mas medindo os cabeçalhos ELF os segmentos `PT_LOAD` já vêm com
  `p_align = 0x4000` (16 KB) no CameraX 1.4.0, e o `zipalign -c -P 16` passa nas
  quatro ABIs. **Não é preciso subir o CameraX por causa disso.**
- **Permissões de mídia.** `READ_EXTERNAL_STORAGE` já não vale desde a API 33.
  As miniaturas da última foto/vídeo hoje só funcionam para mídia do próprio app.
  Ao subir o target, decidir entre declarar `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO`
  ou usar o photo picker.
- **Predictive back.** Verificado em emulador API 36: o `BackHandler` das telas
  de configuração e Sobre continua funcionando (volta para a câmera sem fechar o
  app). Sem animação preditiva, que exigiria `PredictiveBackHandler` — cosmético.

- **Orientação em telas grandes — RESOLVIDO com layout adaptativo.** A partir do
  targetSdk 36 o Android ignora `screenOrientation="portrait"` em telas com largura
  mínima >= 600dp. Em tablet API 36 (2560x1600 @320dpi) a activity recebia os limites
  de paisagem cheios e o layout de retrato se espalhava.

  **Correção de registro:** este documento afirmava que "não existe escapatória". Isso
  estava errado, e o erro veio de um nome de propriedade inventado. A escapatória é
  `android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`, foi usada como ponte
  temporária e **já foi removida** — o framework a elimina no targetSdk 37 de qualquer
  forma.

  A solução definitiva está especificada e implementada em
  [specs/2026-08-03-adaptive-layout/](specs/2026-08-03-adaptive-layout/): em janela
  larga a barra superior vai para o bordo esquerdo, os controles para o direito e a
  pré-visualização usa 16:9. Não dependeu de quebrar a `CameraScreen` antes (o
  item 1 abaixo) — os três blocos já eram âncoras de um `Box`, então bastou inverter
  eixo e âncora.

  Duas ressalvas ficaram abertas, registradas em
  [tasks.md](specs/2026-08-03-adaptive-layout/tasks.md): o contraste do estado
  "desligado" dos ícones fica em 3,5:1 contra o limiar de 4,5:1 da spec, e o
  transbordo em janela larga e baixa não tem rolagem de fallback.

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
| `presentation/screens/CameraScreen.kt` | 1.830 | composable ainda grande: gestos, overlays de grade/nível/foco |
| `camera/CameraManager.kt` | 1.150 | gravação, processamento de bitmap e EXIF no mesmo arquivo |
| `presentation/viewmodels/CameraViewModel.kt` | ~790 | ~30 `StateFlow` soltos |

Medido em 2026-10-07, ao fim da spec de registro de modos
([specs/2026-08-05-mode-registry/](specs/2026-08-05-mode-registry/)), que tirou dos três
arquivos o que tocou. O que saiu está anotado em cada item abaixo.

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

**Já saiu** (spec de registro de modos): seletor de modos e gaveta
(`components/ModeSelector`, `ModeDrawer`), a barra superior inteira
(`components/TopBarControl`, `TopBarButtons`, montada da lista que o modo declara),
overlays por modo (`components/ModeOverlays`), escalas do Pro (`ProScaleSlider`) e a
proporção da pré-visualização (`layout/PreviewAspect`). Os roteiros de comparação de
árvore de UI em `specs/2026-08-05-mode-registry/verificacao/roteiros/` servem de rede
para os próximos recortes.

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

**Já saiu** (spec de registro de modos): a tradução de use cases e a recuperação de bind
recusado (`mode/ModeBinder`, `ModeSession`), a sondagem de `MANUAL_SENSOR`, EIS e HDR
(`SensorCharacteristicsReader`, `mode/CapabilityProbe`), a exposição manual
(`ManualExposureControls`) e a lista de tags EXIF (`ExifCameraTags`). Faltam
`VideoRecorder`, `PhotoCapturer`, `ImagePostProcessor` e `MediaSaver`; a sondagem de
resoluções e ultra-wide continua dentro do `CameraManager`.

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

**Já saiu:** o estado de modos (ativo, arranjo, preferência, exposição manual) está em
`viewmodels/CameraModes`, puro e testado em JVM. É o modelo a seguir para os outros grupos.

## 3a. Defeitos conhecidos, registrados e não corrigidos

Encontrados durante a spec de registro de modos; ficaram fora porque corrigi-los mudaria
comportamento numa spec cujo critério era não mudar. Cada um tem a análise completa em
`specs/2026-08-05-mode-registry/decisions.md`.

- **`evt=bind` registra a proporção anterior** (Q-06). O bind roda antes de o
  `LaunchedEffect` entregar o rótulo novo ao controller. A captura está certa — só a
  telemetria mente, sempre por um passo.
- **O modo não é reaplicado quando o controller é recriado** (Q-08). Em janela larga,
  girar o aparelho em Foto 3:4 recria a Activity, e o controller novo nasce em Vídeo. Não
  verificado em aparelho.
- **Gaveta aberta em janela larga desloca o seletor** (verificação da Tarefa 9): a coluna
  de controles alarga para os 280dp do painel. Cosmético.

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
- ~~**`UnsafeOptInUsageError`** (31x)~~ — **corrigido** na spec de registro de modos
  (Tarefa 3): o consentimento passou a ser `@androidx.annotation.OptIn`, e o baseline de
  lint encolheu de 119 para 88.
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
