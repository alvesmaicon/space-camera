# Space Camera

App Android de câmera (vídeo + foto) em Kotlin/Jetpack Compose, com controle
manual de qualidade: resolução e fps, bitrate, estabilização EIS, HDR, redução de
ruído, zoom variável e toque para focar.

Módulo único (`:app`), sem injeção de dependência por framework — as dependências
são passadas por construtor e factories.

## Comandos

```bash
./gradlew assembleDebug        # build (make build / npm run build:debug)
./gradlew installDebug         # instala no device conectado
./gradlew testDebugUnitTest    # testes unitários JVM
./gradlew lint                 # Android Lint
./gradlew detekt               # análise estática Kotlin
./gradlew detektBaseline       # recongela a dívida atual (ver "Detekt" abaixo)

scripts/smoke.sh               # build + install + start e confere que a câmera ligou
scripts/logcat.sh              # logcat filtrado só no app
scripts/logcat.sh evt=         # só os eventos de telemetria
npm run generate:all-icons     # regera os ícones a partir de space-cam.png
```

`make help` lista os atalhos. Os scripts npm são wrappers finos do Gradle.

## Pré-requisitos do ambiente

- **JDK 17+.** O projeto roda no JDK 21. Não volte o AGP para < 8.2.1: aquelas
  versões falham no JDK 21 em `JdkImageTransform`/jlink.
- **Android SDK** com plataforma 36, apontado por `local.properties`
  (`sdk.dir=...`), que é local e não versionado — copie de
  `local.properties.example`.
- **Node** só é necessário para regerar ícones. Os ícones gerados são versionados
  de propósito, então um clone limpo compila sem Node.

## Estrutura

```
app/src/main/java/com/spacecamera/
├── SpaceCameraApp.kt              Application; configura o Timber
├── MainActivity.kt                host do Compose + NavHost (camera/settings/about)
├── camera/
│   ├── CameraController.kt        interface que o ViewModel enxerga + factory
│   ├── CameraManager.kt           implementação CameraX/Camera2  ⚠ 1.100+ linhas
│   ├── CameraTelemetry.kt         eventos estruturados evt=...
│   └── CameraMode.kt              VIDEO | PHOTO
├── presentation/
│   ├── screens/
│   │   ├── CameraScreen.kt        UI da câmera  ⚠ 2.100+ linhas
│   │   ├── SettingsScreen.kt      configurações (Material 3 dinâmico)
│   │   └── AboutScreen.kt
│   ├── layout/
│   │   ├── WindowAxis.kt          janela larga, AxisContainer, largura de leitura
│   │   └── DeviceRotation.kt      rotação de captura vs compensação da UI
│   ├── viewmodels/CameraViewModel.kt   ⚠ ~30 StateFlows
│   └── icons/TimerIcons.kt
├── data/
│   ├── storage/SettingsStorage.kt      SharedPreferences
│   └── storage/VideoStorage.kt
└── domain/repository/VideoRepository.kt
```

Fluxo: `CameraScreen` → `CameraViewModel` → `CameraController` → CameraX.
O ViewModel espelha em `StateFlow` tudo o que o controller publica.

## Convenções

- **Idioma:** comentários, mensagens de commit e rótulos de UI em **português**;
  identificadores em inglês. Nomes de teste em português entre crases.
- **Versões:** só em `gradle/libs.versions.toml`. Nunca escreva versão literal
  num `build.gradle.kts`. `versionCode`/`versionName` também vêm de lá e chegam à
  tela Sobre via `BuildConfig`.
- **Logging:** `Timber`, nunca `android.util.Log` nem `printStackTrace()`. Para
  momentos decisivos da câmera use `CameraTelemetry` em vez de log solto.
- **Privacidade no log:** nada de conteúdo do usuário. De mídia só sai o id do
  MediaStore, nunca o caminho. Em release o Timber já descarta DEBUG/VERBOSE.

## Como diagnosticar problema de aparelho

A maioria dos relatos é específica de hardware — "4K/60 não aparece", "a
estabilização não liga", "a foto sai escura". O caminho é a telemetria:

```bash
scripts/logcat.sh 'evt=caps'   # o que o HAL respondeu: EIS, HDR, zoom, resoluções
scripts/logcat.sh 'evt=bind'   # o que foi de fato aplicado na sessão + latência
```

`evt=caps` lista exatamente as opções que alimentam os seletores da UI: se algo
não aparece na tela, é porque não veio nessa linha. `evt=bind` traz também a
proporção efetiva da pré-visualização (`aspect`), que depende da largura da janela.
A tela Sobre mostra o `GIT_SHA` do APK instalado, então dá para casar o relato com o
commit exato.

**Confira o APK antes de acreditar no que viu.** Duas vezes nesta base o
`assembleDebug` reportou `UP-TO-DATE` mantendo um APK antigo no disco (depois de
`git stash`, que confunde o cálculo de atualidade), e o `adb install` seguinte
instalou o velho sem reclamar — duas rodadas de verificação em aparelho foram feitas
contra código que não estava lá. Também: `./gradlew assembleDebug detekt` aborta
antes de empacotar se o detekt falhar. Antes de concluir qualquer coisa a partir de
um teste manual:

```bash
unzip -qo app/build/outputs/apk/debug/app-debug.apk 'classes*.dex' -d /tmp/dexchk
cat /tmp/dexchk/*.dex | grep -ac ALGUM_SIMBOLO_NOVO   # 0 = APK velho
```

## Testes

`./gradlew testDebugUnitTest` — tudo roda na JVM, sem device.

- Lógica pura (`VideoBitratePresetTest`, `PresetCyclingTest`): rápido.
- Robolectric (`SettingsStorageTest`, `CameraViewModelTest`): SharedPreferences
  de verdade, para testar serialização e persistência em vez de mocks.
- `FakeCameraController` (em `src/test`) é o dublê que permite testar o
  ViewModel sem CameraX. Simule hardware mexendo nos `*Flow` públicos dele —
  ex.: `controller.isEisSupportedFlow.value = false`.

Duas armadilhas:

1. O cronômetro do ViewModel roda `while (true) { delay(1000) }`. **Nunca** chame
   `advanceUntilIdle()` depois de iniciar a gravação — não retorna. Use
   `advanceTimeBy(ms)`.
2. `PhotoQualityPreset` guarda um `android.util.Size`. Fora do Robolectric o
   android.jar stub devolve 0 em `width`/`height` e a asserção passa sem testar
   nada.

O emulador usa câmera virtual: reporta EIS/HDR não suportados e só HD 30.
Validação de qualidade precisa de aparelho real.

## Detekt

`detekt-baseline.xml` congela a dívida existente; a análise só falha em problema
**novo**. Ao corrigir itens antigos, rode `./gradlew detektBaseline` para
reencolher o baseline — não o regenere para silenciar um achado novo.

## Dívida conhecida

O prazo da Play Store (`targetSdk` 36 até **31/10/2026**) foi cumprido: o item 0 do
[REFACTORING.md](REFACTORING.md) está concluído, e com ele o layout adaptativo que a
mudança de orientação do Android 16 exigiu — ver
[specs/2026-08-03-adaptive-layout/](specs/2026-08-03-adaptive-layout/).

O resto está mapeado em [REFACTORING.md](REFACTORING.md). Em resumo: `CameraScreen.kt` tem um
composable de ~1.100 linhas, `CameraManager.kt` acumula bind, sondagem de
capacidades, gravação, processamento de bitmap e EXIF, e o ViewModel expõe ~30
`StateFlow` soltos. Ao mexer nesses arquivos, prefira extrair a parte que você
tocou a aumentá-los.

`ARCHITECTURE.md`, `PROJECT_STRUCTURE.md` e `README.md` descreviam `usecase/`,
`VideoProcessor.kt`, Hilt e Room, que nunca existiram. Foram corrigidos — se
reencontrar essas referências, é doc velha.
