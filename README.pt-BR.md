# Space Camera

[English](README.md) · **Português**

Uma câmera como ela deve ser: simples e efetiva.

O projeto nasceu de uma necessidade concreta — gravar vídeo no Motorola edge 60 neo sem o
zoom automático obrigatório do app de câmera nativo. Cresceu para um app de vídeo e foto com
controle manual de qualidade, que expõe na própria interface o que a câmera do sistema
esconde: resolução e taxa de quadros, bitrate, estabilização, HDR, redução de ruído e, no
modo **Pro**, ISO e obturador.

Kotlin + Jetpack Compose + CameraX. Módulo único, Android 7.0 (API 24) em diante, com alvo
na API 36. A interface está em português e em inglês.

## Funcionalidades

**Vídeo**
- Resolução e fps conforme o que o aparelho realmente suporta (até 4K/60 quando existe)
- Quatro presets de bitrate, com teto de 150 Mbps
- Estabilização EIS e redução de ruído, com detecção de suporte por aparelho
- HDR quando o HAL oferece scene mode HDR, tonemap de alta qualidade e 10 bits
- Pausar e retomar durante a gravação
- Espelhamento opcional da câmera frontal

**Foto**
- Quatro presets de qualidade, de 2 MP ao máximo do sensor
- Melhoria adaptativa de saturação e contraste após a captura (opcional)
- Flash em ciclo desligado → automático → ligado
- Proporções 9:16, 3:4 e Full (recorte para tela cheia)
- EXIF preservado, com GPS opcional

**Modo Pro**
- ISO e obturador manuais em escalas verticais, cada uma com seu AUTO
- As faixas vêm do aparelho; a escala de ISO marca onde termina o ganho analógico
- Só aparece em aparelho que reporta controle manual do sensor

**Em todo o app**
- Seletor de modos com a gaveta "Mais"; em Configurações → Modos da câmera você escolhe quais
  modos ficam no seletor e em que ordem
- Zoom por dial e por presets de lente (incluindo a ultra-wide quando existe), toque para
  focar com ajuste de exposição, grade, nível de horizonte, timer de 3/5/10 s
- Layout adaptativo para tablet e janela larga
- Configurações em Material 3 com a cor dinâmica do sistema

## Começando

Requisitos: **JDK 17+** (testado no 21), **Android SDK com a plataforma 36**, aparelho ou
emulador com API 24+. Node só é necessário para regerar ícones.

```bash
cp local.properties.example local.properties   # aponte sdk.dir para o seu Android SDK
./gradlew installDebug
```

## Comandos

```bash
./gradlew assembleDebug        # APK de debug
./gradlew installDebug         # instala no aparelho conectado
./gradlew testDebugUnitTest    # testes unitários (JVM, sem aparelho)
./gradlew lint                 # Android Lint
./gradlew detekt               # análise estática Kotlin

scripts/smoke.sh               # build + install + start, confere que a câmera ligou
scripts/logcat.sh              # logcat filtrado só no app
npm run generate:all-icons     # regera os ícones a partir de space-cam.png
```

`make help` lista os atalhos equivalentes.

## Diagnóstico de problema de aparelho

Quase todo defeito de câmera é específico de hardware ("4K/60 não aparece", "a foto sai
escura"). O app registra eventos estruturados sob a tag única `SpaceCam`:

```bash
scripts/logcat.sh 'evt=caps'   # o que o HAL respondeu: EIS, HDR, zoom, resoluções, controle manual
scripts/logcat.sh 'evt=bind'   # o que foi aplicado na sessão, com a latência
scripts/logcat.sh 'evt=mode'   # trocas de modo e os use cases que cada modo ligou
```

Se uma opção não aparece na tela, é porque não veio em `evt=caps`. A tela Sobre mostra o
commit que gerou o APK instalado, o que permite casar um relato com o código exato.

## Arquitetura em um parágrafo

`CameraScreen` → `CameraViewModel` → `CameraController` → CameraX. Um **modo** de câmera
(Vídeo, Foto, Pro) é uma declaração num registro: quais use cases do CameraX ligar, que
capacidade de hardware exige, que controles mostra, que regras de proporção e
estabilização segue, o que o disparador faz. Ninguém pergunta "que modo é este?" — a tela,
o ViewModel e o controller leem a declaração. Acrescentar um modo custa um arquivo novo e
uma linha no `ModeRegistry`; isso foi medido, não estimado.

A mídia vai para `DCIM/SpaceCamera/` via MediaStore e aparece na galeria.

## Documentação do projeto

| Documento | O que tem |
|---|---|
| [CLAUDE.md](CLAUDE.md) | convenções, como adicionar um modo, armadilhas de teste e de verificação em aparelho |
| [ARCHITECTURE.md](ARCHITECTURE.md) | camadas e fluxo de dados |
| [BUILD.md](BUILD.md) | versões da toolchain, release, CI |
| [ROADMAP.md](ROADMAP.md) | funcionalidades que o app ainda não tem |
| [REFACTORING.md](REFACTORING.md) | dívida técnica e defeitos conhecidos |
| [specs/](specs/) | especificações com requisitos, decisões e registros de verificação em aparelho |

## Permissões

| Permissão | Para quê |
|---|---|
| `CAMERA` | pré-visualização e captura |
| `RECORD_AUDIO` | áudio do vídeo (dispensável com o microfone mudo) |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | apenas se "Salvar localização" estiver ligado |
| `READ/WRITE_EXTERNAL_STORAGE` | compatibilidade com Android 9 e anteriores |

## Contribuindo

Issues e pull requests são bem-vindos, em português ou inglês — veja
[CONTRIBUTING.md](CONTRIBUTING.md). Para defeito de aparelho, o modelo de issue pede a linha
`evt=caps`, que costuma dizer mais que a descrição.

## Licença

[MIT](LICENSE) © 2026 Maicon Alves
