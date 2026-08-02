# Project Structure - Space Camera

Estrutura completa do projeto Space Camera após setup completo.

## 📁 Diretório Raiz

```
space-camera/
├── 📄 README.md                    # Documentação principal
├── 📄 QUICKSTART.md               # Guia rápido de início
├── 📄 BUILD.md                    # Instruções de build
├── 📄 ARCHITECTURE.md             # Arquitetura do projeto
├── 📄 ICON_GENERATION.md          # Guia de geração de ícones
├── 📄 CONTRIBUTING.md             # Guia para contribuidores
├── 📄 Makefile                    # Comandos make
├── 📄 package.json                # Scripts npm/yarn
├── 📄 .gitignore                  # Ignorar arquivos
├── 📄 gradle.properties           # Propriedades gradle
├── 📄 local.properties            # Arquivo local (não comitado)
├── 📄 local.properties.example    # Template local.properties
├── 📄 space-cam.png               # Imagem base para ícones
├── 📄 setup.sh                    # Script de setup
├── 📄 build.gradle.kts            # Gradle root
├── 📄 settings.gradle.kts         # Gradle settings
├── 
├── 📁 app/                        # Módulo Android
│   ├── build.gradle.kts           # Build do módulo
│   ├── proguard-rules.pro         # ProGuard config
│   ├── 
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── 
│       │   ├── java/com/spacecamera/
│       │   │   ├── MainActivity.kt              # Activity principal
│       │   │   │
│       │   │   ├── camera/
│       │   │   │   ├── CameraManager.kt        # Gerenciador de câmera
│       │   │   │   └── VideoProcessor.kt       # Processamento (futuro)
│       │   │   │
│       │   │   ├── presentation/
│       │   │   │   ├── screens/
│       │   │   │   │   └── CameraScreen.kt    # UI Compose
│       │   │   │   └── viewmodels/
│       │   │   │       └── CameraViewModel.kt # ViewModel
│       │   │   │
│       │   │   ├── domain/
│       │   │   │   ├── repository/
│       │   │   │   │   └── VideoRepository.kt # Interface
│       │   │   │   └── usecase/
│       │   │   │       ├── StartRecordingUseCase.kt
│       │   │   │       ├── StopRecordingUseCase.kt
│       │   │   │       └── PauseRecordingUseCase.kt
│       │   │   │
│       │   │   └── data/
│       │   │       ├── repository/
│       │   │       │   └── VideoRepositoryImpl.kt
│       │   │       └── storage/
│       │   │           └── VideoStorage.kt
│       │   │
│       │   └── res/
│       │       ├── .gitignore
│       │       ├── values/
│       │       │   ├── strings.xml
│       │       │   ├── colors.xml
│       │       │   └── themes.xml
│       │       ├── mipmap-ldpi/
│       │       │   └── ic_launcher.png        # 36x36
│       │       ├── mipmap-mdpi/
│       │       │   └── ic_launcher.png        # 48x48
│       │       ├── mipmap-hdpi/
│       │       │   └── ic_launcher.png        # 72x72
│       │       ├── mipmap-xhdpi/
│       │       │   └── ic_launcher.png        # 96x96
│       │       ├── mipmap-xxhdpi/
│       │       │   └── ic_launcher.png        # 144x144
│       │       ├── mipmap-xxxhdpi/
│       │       │   └── ic_launcher.png        # 192x192
│       │       ├── drawable-mdpi/
│       │       │   └── ic_notification.png
│       │       └── ... (mais densidades)
│       │
│       └── test/
│           └── java/com/spacecamera/
│               └── presentation/viewmodels/
│                   └── CameraViewModelTest.kt
│
├── 📁 buildSrc/                              # Build customizado
│   └── src/main/kotlin/
│       └── Config.kt                        # Configurações do build
│
├── 📁 scripts/                              # Scripts auxiliares
│   ├── README.md                            # Documentação dos scripts
│   ├── generateIcons.js                     # Gerar ícones (Node.js)
│   ├── generateIcons.sh                     # Gerar ícones (Bash)
│   ├── generateNotificationIcons.js         # Gerar notification icons
│   └── iconHelp.js                          # Help sobre ícones
│
└── 📁 .gradle/                              # Cache gradle (git ignored)
```

---

## 🔄 Fluxo de Dados na Aplicação

```
┌─ UI Layer (Jetpack Compose) ─────────────────────────┐
│  CameraScreen.kt                                      │
│  • Preview da câmera                                  │
│  • Botões de controle                                 │
│  • Indicadores de estado                              │
└──────────────┬──────────────────────────────────────┘
               │ Coleta estado
               ▼
┌─ ViewModel Layer ─────────────────────────────────────┐
│  CameraViewModel.kt                                   │
│  • Gerencia RecordingState (StateFlow)               │
│  • Coordena ações do usuário                         │
│  • Expõe FileSize e ResolutionState                  │
└──────────────┬──────────────────────────────────────┘
               │ Chama métodos
               ▼
┌─ Domain Layer ────────────────────────────────────────┐
│  VideoRepository (Interface)                          │
│  • startRecording()                                   │
│  • stopRecording()                                    │
│  • pauseRecording()                                   │
│  • setVideoResolution()                               │
└──────────────┬──────────────────────────────────────┘
               │ Implementação
               ▼
┌─ Data Layer ──────────────────────────────────────────┐
│  VideoRepositoryImpl.kt                               │
│  • Coordena CameraManager e VideoStorage             │
│  • Gerencia ciclo de vida de gravação                │
└──────────────┬───────┬─────────────────────────────┘
               │       │
       ┌───────┘       └──────────┐
       │                          │
       ▼                          ▼
┌─ Camera Layer ────┐  ┌─ Storage Layer ─────┐
│ CameraManager.kt  │  │ VideoStorage.kt     │
│ • Usa CameraX     │  │ • Cria arquivos     │
│ • Controla        │  │ • Salva em MediaStore
│   gravação com    │  │ • Formata tamanhos  │
│   MediaRecorder   │  │                     │
│ • Exposição       │  │                     │
└───────────────────┘  └─────────────────────┘
```

---

## 📦 Dependências Principais

```
androidxgradleVersion = "8.1.4"
kotlinVersion = "1.9.10"
cameraXVersion = "1.3.0"
composeVersion = "1.5.4"
lifecycleVersion = "2.6.2"
coroutinesVersion = "1.7.3"
hiltVersion = "2.48"
roomVersion = "2.5.2"
```

---

## 🎯 Camadas e Responsabilidades

### Presentation Layer (UI)
- **Arquivo**: `presentation/screens/CameraScreen.kt`
- **Responsabilidade**: Renderizar UI e capturar eventos do usuário
- **Tecnologia**: Jetpack Compose

### ViewModel Layer
- **Arquivo**: `presentation/viewmodels/CameraViewModel.kt`
- **Responsabilidade**: Gerenciar estado e coordenar domain layer
- **Tecnologia**: ViewModel + StateFlow

### Domain Layer (Negócio)
- **Arquivo**: `domain/repository/VideoRepository.kt`
- **Responsabilidade**: Definir contrato de operações
- **Tecnologia**: Interfaces Kotlin

### Data Layer (Dados)
- **Arquivo**: `data/repository/VideoRepositoryImpl.kt`
- **Responsabilidade**: Implementar acesso aos dados
- **Arquivo**: `data/storage/VideoStorage.kt`
- **Responsabilidade**: Gerenciar armazenamento

### Camera Layer (Hardware)
- **Arquivo**: `camera/CameraManager.kt`
- **Responsabilidade**: Interface com CameraX e MediaRecorder
- **Tecnologia**: CameraX API

---

## 🚀 Arquivos de Build

### Gradle Files
```
build.gradle.kts              # Root build
settings.gradle.kts           # Configuração de submódulos
app/build.gradle.kts          # App module build
buildSrc/src/main/kotlin/Config.kt  # Configurações centralizadas
```

### Node.js
```
package.json                  # Scripts npm/yarn
scripts/generateIcons.js      # Geração de ícones
```

### Shell
```
setup.sh                      # Setup inicial
scripts/generateIcons.sh      # Geração de ícones (alternativa)
```

### Make
```
Makefile                      # Comandos Make
```

---

## 📊 Tamanhos de Ícones Gerados

| Densidade | Launcher | Notification | Pasta                |
|-----------|----------|--------------|---------------------|
| ldpi      | 36x36    | -            | mipmap-ldpi          |
| mdpi      | 48x48    | 24x24        | mipmap-mdpi          |
| hdpi      | 72x72    | 36x36        | mipmap-hdpi          |
| xhdpi     | 96x96    | 48x48        | mipmap-xhdpi         |
| xxhdpi    | 144x144  | 72x72        | mipmap-xxhdpi        |
| xxxhdpi   | 192x192  | 96x96        | mipmap-xxxhdpi       |

---

## 🔐 Permissões Necessárias

Definidas em `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

---

## 📚 Arquivos de Documentação

```
README.md              # Documentação principal
QUICKSTART.md          # Guia rápido
BUILD.md               # Instruções de build
ICON_GENERATION.md     # Geração de ícones
ARCHITECTURE.md        # Arquitetura detalhada
CONTRIBUTING.md        # Guia para contribuidores
PROJECT_STRUCTURE.md   # Este arquivo
```

---

## 🔄 Scripts npm/yarn

```bash
npm run generate:icons              # Gerar launcher icons
npm run generate:notification-icons # Gerar notification icons
npm run generate:all-icons          # Gerar todos os ícones
npm run build:debug                 # Build debug
npm run build:release               # Build release
npm run install:debug               # Instalar em dispositivo
npm run run:debug                   # Build + Install + Run
npm run clean                       # Limpar builds
npm run test                        # Rodar testes
npm run lint                        # Lint
```

---

## 🎯 Próximas Etapas de Desenvolvimento

1. **Testes**:
   - [ ] Implementar unit tests
   - [ ] Adicionar integration tests
   - [ ] UI tests com Compose

2. **Features**:
   - [ ] Filtros de câmera
   - [ ] Slow-motion
   - [ ] Múltiplas câmeras

3. **Performance**:
   - [ ] Otimizar gravação
   - [ ] Compressão de vídeo
   - [ ] Memory profiling

4. **UX**:
   - [ ] Adaptive icons
   - [ ] Temas claros/escuros
   - [ ] Animações

---

**Última atualização**: 27 de Abril de 2026
