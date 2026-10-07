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

scripts/backup-apk.sh --instalar   # arquiva APK nomeado por versão+commit e instala
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
│   ├── CameraManager.kt           implementação CameraX/Camera2
│   ├── CameraTelemetry.kt         eventos evt=... (CameraTelemetry, ModeTelemetry, TelemetryFields)
│   ├── ModeSession.kt             modo pedido × ligado; quando sai evt=mode
│   ├── ManualExposure*.kt         ISO e obturador do Pro (regra pura + escrita no Camera2)
│   ├── SensorCharacteristicsReader.kt  leitura do HAL: MANUAL_SENSOR, EIS, HDR
│   └── mode/                      o registro de modos — ver "Como adicionar um modo"
│       ├── ModeRegistry.kt        a lista de modos (uma linha por modo)
│       ├── VideoMode, PhotoMode, ProMode
│       ├── CameraModeDefinition.kt, ModeContract.kt   o que um modo declara
│       ├── ModeBinder.kt          AppUseCase → use case; falha religa o modo anterior
│       └── CapabilityProbe.kt     decide as capacidades a partir do HAL
├── presentation/
│   ├── screens/
│   │   ├── CameraScreen.kt        UI da câmera
│   │   ├── SettingsScreen.kt      configurações (Material 3 dinâmico)
│   │   └── AboutScreen.kt
│   ├── components/                seletor, gaveta, overlays e controles por identificador
│   ├── layout/
│   │   ├── WindowAxis.kt          janela larga, AxisContainer, largura de leitura
│   │   ├── PreviewAspect.kt       proporção da caixa de pré-visualização
│   │   └── DeviceRotation.kt      rotação de captura vs compensação da UI
│   ├── viewmodels/
│   │   ├── CameraViewModel.kt     ~30 StateFlows
│   │   └── CameraModes.kt         estado de modos agrupado (puro)
│   └── icons/TimerIcons.kt
├── data/
│   ├── storage/SettingsStorage.kt      SharedPreferences
│   └── storage/VideoStorage.kt
└── domain/repository/VideoRepository.kt
```

Fluxo: `CameraScreen` → `CameraViewModel` → `CameraController` → CameraX.
O ViewModel espelha em `StateFlow` tudo o que o controller publica.

## Como adicionar um modo

O que um modo faz mora na própria definição (`CameraModeDefinition`): use cases a ligar,
capacidade exigida, controles da barra, proporção, EIS, o que o disparador faz, o
comportamento do flash. Ninguém ramifica por modo — tela, ViewModel e controller leem a
definição.

**Custo medido** (recibo do NFR-2 da spec de registro de modos): **um arquivo novo** com o
`object` do modo e **uma linha** em `ModeRegistry.all`. Nenhuma edição em `CameraScreen`,
`CameraViewModel` ou `CameraManager`, e os testes passam sem mudança. Só sai disso quando
o modo precisa de algo que ainda não existe:

- **controle novo na barra:** valor em `ControlId` + o desenho em `components/TopBarControl.kt`
  (o `when` é exaustivo — esquecer não compila);
- **overlay próprio:** `OverlayId` em `ModeSurfaces` + o ramo em `components/ModeOverlays.kt`;
- **capacidade nova:** valor em `Capability` + a decisão em `CapabilityProbe`.

Um membro abstrato novo em `CameraModeDefinition` quebra o build em todos os modos, de
propósito: cada um precisa responder. Detalhes e decisões em
[specs/2026-08-05-mode-registry/](specs/2026-08-05-mode-registry/).

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

`scripts/backup-apk.sh` já faz essa conferência, comparando o `GIT_SHA` do commit com o
que está dentro do APK antes de arquivar. Use `grep -c` e não `grep -q` num pipe: com
`set -o pipefail`, o `-q` sai no primeiro acerto, o `cat` toma SIGPIPE e o pipeline
retorna erro **quando a busca dá certo**.

### Outras armadilhas de verificação manual

Todas produziram conclusão errada nesta base, e nenhuma é óbvia:

1. **`am start` depois de `am force-stop` retoma a task preservada em recentes**, não
   abre na tela inicial. Uma rodada de captura pegou a tela Sobre acreditando ser a
   câmera. Use `am start -S -n com.spacecamera/.MainActivity --activity-clear-task` e
   **confira a tela** (`content-desc` no dump do `uiautomator`) antes de medir. A
   contagem de nós é um bom canário: número inesperado de caixas = outra tela.
2. **Emulador em boot frio abre "System UI isn't responding"** e o diálogo engole os
   toques em silêncio. Dispense antes de qualquer roteiro de UI.
3. **`evt=bind` não prova imagem na tela.** Ele mede configurar os use cases; no
   caminho em que a câmera não reabria, reportava sucesso em 7ms com a
   pré-visualização preta. Para "tem imagem" o instrumento é luminância média de
   `adb exec-out screencap -p`.
4. **O emulador reportar EIS/HDR como não suportados faz o comportamento defeituoso
   coincidir com o correto.** Um bug que desligava o EIS em aparelho capaz passou por
   toda uma spec porque `eis_supported=false` no emulador nos dois casos. Capacidade de
   hardware só se verifica em aparelho real. Mas **depende da AVD**: o `Tablet_API36`
   reporta `eis_supported=true` e `manual_sensor=true` — confira `evt=caps` antes de
   supor.
5. **O anel `main` do logcat tem 256 KiB e o HAL o esvazia em segundos** com a câmera
   aberta: `logcat -d` depois do fato contou 3 binds onde houve 10. Antes de medir,
   `adb logcat -G 16M` e capture ao vivo (`adb logcat -v time -s SpaceCam:V > arquivo &`).
6. **Os controles mudam de posição com a proporção da foto** (o disparador vai de y=1921
   a y=2455). Toque em coordenada gravada cai no vazio, sem log nem erro. Localize por
   rótulo a cada passo — e **não** faça `uiautomator dump` dentro de um laço de toques,
   que atrapalha a sessão. Os roteiros prontos estão em
   [specs/2026-08-05-mode-registry/verificacao/roteiros/](specs/2026-08-05-mode-registry/verificacao/roteiros/).
7. **Latência só se compara na mesma sessão.** Contra um número de outro dia, o
   "depois" saiu 28% mais rápido sem causa no código; com o aparelho a 37 °C, as mesmas
   20 trocas foram de 43 ms para ~100 ms. Meça antes e depois em sequência, de
   preferência A/B/A.

Para comparar antes/depois entre dois commits, use `git worktree` e **nunca** `git
stash` — o stash é justamente o que confundiu o cálculo de atualidade do Gradle acima.

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

O emulador usa câmera virtual, e o que ela reporta depende da AVD (ver armadilha 4).
Validação de qualidade precisa de aparelho real.

Para testar o registro de modos sem depender dos modos de produção, use `modoDeTeste(...)`
(em `src/test`) e passe o registro explicitamente — `ModeRegistry.arranged(..., registry)`,
`CameraViewModel(modeRegistry = ...)`, `CameraModes(registry)`. Teste que afirma o conteúdo
exato do registro de produção quebra a cada modo novo.

## Detekt

`detekt-baseline.xml` congela a dívida existente; a análise só falha em problema
**novo**. Ao corrigir itens antigos, rode `./gradlew detektBaseline` para
reencolher o baseline — não o regenere para silenciar um achado novo.

## Dívida conhecida

O prazo da Play Store (`targetSdk` 36 até **31/10/2026**) foi cumprido: o item 0 do
[REFACTORING.md](REFACTORING.md) está concluído, e com ele o layout adaptativo que a
mudança de orientação do Android 16 exigiu — ver
[specs/2026-08-03-adaptive-layout/](specs/2026-08-03-adaptive-layout/).

O resto está mapeado em [REFACTORING.md](REFACTORING.md): `CameraScreen.kt` com gestos e
os overlays de grade, nível e foco; `CameraManager.kt` com gravação, processamento de
bitmap e EXIF; e o ViewModel com ~30 `StateFlow` soltos. A separação em módulos e
arquivos menores é uma refatoração planejada à parte — **não** use contagem de linhas como
meta nem como critério de aceite ao mexer nesses arquivos.

`ARCHITECTURE.md`, `PROJECT_STRUCTURE.md` e `README.md` descreviam `usecase/`,
`VideoProcessor.kt`, Hilt e Room, que nunca existiram. Foram corrigidos — se
reencontrar essas referências, é doc velha.
