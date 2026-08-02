# Space Camera

App Android de câmera com controle manual de qualidade. Grava vídeo e tira fotos,
expondo na própria interface os parâmetros que a câmera do sistema esconde:
resolução e taxa de quadros, bitrate, estabilização, HDR e compensação de exposição.

Kotlin + Jetpack Compose + CameraX. Módulo único, mínimo de Android 7.0 (API 24).

## Funcionalidades

**Vídeo**
- Resolução e fps conforme o que o aparelho realmente suporta (até 4K/60 quando existe)
- Quatro presets de bitrate, de Baixa a Máxima, com teto de 150 Mbps
- Estabilização EIS e redução de ruído, com detecção de suporte por aparelho
- HDR quando o HAL oferece scene mode, tonemap de alta qualidade e 10-bit
- Pausar e retomar durante a gravação
- Espelhamento opcional da câmera frontal

**Foto**
- Quatro presets de qualidade, de 2MP ao máximo do sensor
- Melhoria adaptativa de saturação e contraste após a captura (opcional)
- Flash em ciclo desligado → automático → ligado
- Proporções 9:16, 3:4 e Full (recorte para tela cheia)
- EXIF preservado, com GPS opcional

**Controles**
- Zoom por dial e por presets, incluindo a lente ultra-wide quando existe
- Toque para focar com ajuste de exposição
- Grade de composição e nível de horizonte
- Temporizador de 3, 5 ou 10 segundos
- Miniatura da última captura, com abertura na galeria

A tela de configurações segue o Material 3 com cor dinâmica do sistema,
acompanhando o tema claro/escuro do aparelho.

## Começando

Requisitos: **JDK 17+** (roda no 21), **Android SDK 34**, aparelho ou emulador
com API 24+. Node só é necessário para regerar ícones.

```bash
cp local.properties.example local.properties   # ajuste sdk.dir
./gradlew installDebug
```

## Comandos

```bash
./gradlew assembleDebug        # APK de debug
./gradlew installDebug         # instala no device conectado
./gradlew testDebugUnitTest    # testes unitários (JVM, sem device)
./gradlew lint                 # Android Lint
./gradlew detekt               # análise estática Kotlin

scripts/smoke.sh               # build + install + start, confere que a câmera ligou
scripts/logcat.sh              # logcat filtrado só no app
npm run generate:all-icons     # regera ícones a partir de space-cam.png
```

`make help` lista os atalhos equivalentes.

## Diagnóstico

O app emite eventos estruturados no logcat sob a tag única `SpaceCam`. Para
entender por que uma opção não aparece num aparelho específico:

```bash
scripts/logcat.sh 'evt=caps'   # o que o HAL respondeu: EIS, HDR, zoom, resoluções
scripts/logcat.sh 'evt=bind'   # o que foi aplicado na sessão, com a latência
```

A tela Sobre mostra o hash do commit que gerou o APK instalado, o que permite
casar um relato com o código exato.

## Onde a mídia é salva

`DCIM/SpaceCamera/` via MediaStore, aparecendo na galeria automaticamente.
Vídeos como `VID_<data>.mp4`, fotos como `IMG_<data>.jpg`.

## Estrutura

```
app/src/main/java/com/spacecamera/
├── SpaceCameraApp.kt      Application; configura o Timber
├── MainActivity.kt        host do Compose + navegação
├── camera/                CameraController (interface), CameraManager (CameraX), telemetria
├── presentation/          telas Compose, ViewModel e ícones
├── data/                  SharedPreferences e armazenamento
└── domain/                contratos de repositório
```

Detalhes em [ARCHITECTURE.md](ARCHITECTURE.md). Convenções e armadilhas do
projeto em [CLAUDE.md](CLAUDE.md). Dívida técnica mapeada em
[REFACTORING.md](REFACTORING.md).

## Permissões

| Permissão | Para quê |
|---|---|
| `CAMERA` | pré-visualização e captura |
| `RECORD_AUDIO` | áudio do vídeo (dispensável com o microfone mudo) |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | apenas se "Salvar localização" estiver ligado |
| `READ/WRITE_EXTERNAL_STORAGE` | compatibilidade com Android 9 e anteriores |

## Solução de problemas

**`sdk.dir not found`** — crie o `local.properties` a partir do
`local.properties.example`.

**Build falha em `JdkImageTransform`/jlink** — combinação de AGP antigo com JDK
21. O projeto está em AGP 8.7.3, que é compatível; não regrida essa versão.

**A câmera não inicia** — rode `scripts/logcat.sh 'evt='` e procure
`evt=bind_failed` ou `evt=camera_init_failed`, que trazem o stacktrace.

**4K, 60fps, EIS ou HDR não aparecem** — provavelmente o aparelho não suporta.
`evt=caps` mostra exatamente o que o HAL declarou. No emulador a câmera é
virtual: reporta só HD 30, sem EIS e sem HDR.

**Vídeo não aparece na galeria** — a indexação do MediaStore é assíncrona;
aguarde alguns segundos e reabra a galeria.

## Licença

MIT.
