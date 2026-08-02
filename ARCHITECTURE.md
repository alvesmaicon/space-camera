# Arquitetura do Projeto Space Camera

## Visão Geral

O projeto segue a arquitetura **Clean Architecture** combinada com o padrão **MVVM** (Model-View-ViewModel). Esta abordagem garante:

- ✅ Separação de responsabilidades clara
- ✅ Testabilidade aprimorada
- ✅ Manutenibilidade a longo prazo
- ✅ Escalabilidade do projeto

## Camadas

### 1. Presentation Layer (Apresentação)

**Responsabilidade**: Gerenciar a UI e interação do usuário

**Componentes**:
- `screens/`: Telas Jetpack Compose
- `viewmodels/`: ViewModels que gerenciam estado

**Fluxo de Dados**:
```
User Interaction → Screen → ViewModel → Repository
        ↓                                    ↓
    Update UI ← ViewModel State ← Data Layer
```

**Exemplo**:
```kotlin
@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    val recordingState by viewModel.recordingState.collectAsState()
    // UI updates based on state
    Button(onClick = { viewModel.startRecording() })
}
```

### 2. Domain Layer (Domínio)

**Responsabilidade**: Definir regras de negócio e interfaces

**Componentes**:
- `repository/`: Interfaces de repositório (contracts)
- `usecase/`: Use cases para operações específicas

**Características**:
- Não depende de outras camadas
- Contém apenas lógica pura
- Independente de framework

**Exemplo**:
```kotlin
interface VideoRepository {
    suspend fun startRecording()
    suspend fun stopRecording()
    fun setVideoResolution(height: Int)
}
```

### 3. Data Layer (Dados)

**Responsabilidade**: Implementar acesso aos dados

**Componentes**:
- `repository/`: Implementações de repositório
- `storage/`: Gerenciamento de armazenamento local

**Funcionalidades**:
- Salvar vídeos em disco
- Gerenciar acesso ao armazenamento
- Integração com MediaStore

**Exemplo**:
```kotlin
class VideoRepositoryImpl(
    private val cameraManager: CameraManager,
    private val videoStorage: VideoStorage
) : VideoRepository {
    override suspend fun startRecording() {
        val videoFile = videoStorage.createVideoFile()
        cameraManager.startRecording(videoFile)
    }
}
```

### 4. Camera Layer (Câmera)

**Responsabilidade**: Gerenciar hardware de câmera

**Componentes**:
- `CameraManager.kt`: Interface com CameraX
- Configuração de resolução e qualidade
- Controle de recording

**Tecnologias**:
- CameraX (recomendado pelo Google)
- MediaRecorder para gravação
- Coroutines para async

## Fluxo de Dados Completo

```
┌─────────────────────────────────────────────────────────────┐
│                   PRESENTATION LAYER                        │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ CameraScreen (Jetpack Compose)                        │ │
│  │ - Layout e componentes UI                             │ │
│  │ - Gerencia permissões                                 │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ CameraViewModel                                       │ │
│  │ - Gerencia estado (RecordingState, fileSize, etc)    │ │
│  │ - Coordena ações do usuário                           │ │
│  │ - Expõe StateFlow para UI reagir                      │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
           ↓ Calls methods (startRecording, etc)
┌─────────────────────────────────────────────────────────────┐
│                    DOMAIN LAYER                             │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ VideoRepository (Interface)                           │ │
│  │ - Define contrato de operações                        │ │
│  │ - Independente de implementação                       │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
           ↓ Implements
┌─────────────────────────────────────────────────────────────┐
│                    DATA LAYER                               │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ VideoRepositoryImpl                                    │ │
│  │ - Implementa interface VideoRepository               │ │
│  │ - Orquestra CameraManager e VideoStorage             │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌──────────────────┐  ┌──────────────────────────────────┐ │
│  │ CameraManager    │  │ VideoStorage                     │ │
│  │ - Gerencia       │  │ - Cria arquivos                  │ │
│  │   CameraX        │  │ - Salva em MediaStore           │ │
│  │ - Gravação       │  │ - Formata tamanhos              │ │
│  └──────────────────┘  └──────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
           ↓ Uses
┌─────────────────────────────────────────────────────────────┐
│                   CAMERA LAYER                              │
│  - CameraX API                                              │
│  - MediaRecorder                                            │
│  - Hardware de câmera                                       │
└─────────────────────────────────────────────────────────────┘
           ↓ Interacts with
┌─────────────────────────────────────────────────────────────┐
│                 ANDROID FRAMEWORK                           │
│  - Camera HAL                                               │
│  - MediaStore                                               │
│  - Storage (Scoped Storage)                                 │
└─────────────────────────────────────────────────────────────┘
```

## Dependências Entre Camadas

```
Presentation → Domain → Data → Camera
     ↓            ↑      ↓         ↓
 (Compose)    (Interfaces) (Impl) (CameraX)
```

**Regras**:
- ✅ Presentation depende de Domain
- ✅ Data depende de Domain
- ✅ Nenhuma camada depende de Presentation
- ✅ Domain não depende de ninguém

## Padrões Utilizados

### 1. Repository Pattern
```kotlin
// Interface em Domain
interface VideoRepository {
    suspend fun startRecording()
}

// Implementação em Data
class VideoRepositoryImpl : VideoRepository {
    override suspend fun startRecording() {
        // Implementation
    }
}
```

### 2. ViewModel Pattern
```kotlin
class CameraViewModel : ViewModel() {
    private val _recordingState = MutableStateFlow<RecordingState>(Idle)
    val recordingState = _recordingState.asStateFlow()
    
    fun startRecording() {
        viewModelScope.launch {
            repository.startRecording()
        }
    }
}
```

### 3. StateFlow para Reatividade
```kotlin
// ViewModel expõe state
val recordingState: StateFlow<RecordingState>

// UI coleta state
val recordingState by viewModel.recordingState.collectAsState()
```

### 4. Coroutines para Async
```kotlin
viewModelScope.launch {
    videoRepository.startRecording() // Suspending function
}
```

## Estrutura de Pastas

```
app/src/main/java/com/spacecamera/
├── camera/
│   ├── CameraManager.kt          # Gerenciador de câmera
│   └── VideoProcessor.kt         # Processamento (futuro)
├── data/
│   ├── repository/
│   │   └── VideoRepositoryImpl.kt # Implementação
│   └── storage/
│       └── VideoStorage.kt       # Acesso a storage
├── domain/
│   ├── repository/
│   │   └── VideoRepository.kt    # Interface
│   └── usecase/
│       ├── StartRecordingUseCase.kt
│       ├── StopRecordingUseCase.kt
│       └── PauseRecordingUseCase.kt
└── presentation/
    ├── screens/
    │   └── CameraScreen.kt       # UI Compose
    └── viewmodels/
        └── CameraViewModel.kt    # ViewModel
```

## Testabilidade

### Unit Tests
```kotlin
@Test
fun testStartRecording() {
    // Mock repository
    val mockRepository = mockk<VideoRepository>()
    val viewModel = CameraViewModel(mockRepository)
    
    viewModel.startRecording()
    
    coVerify { mockRepository.startRecording() }
}
```

### Integration Tests
```kotlin
@RunWith(AndroidTestRunner::class)
class CameraScreenTest {
    @Test
    fun testCameraScreenRenders() {
        composeTestRule.setContent {
            CameraScreen()
        }
        
        composeTestRule.onNodeWithTag("recordButton").assertExists()
    }
}
```

## Melhorias Futuras

### 1. Implementar Use Cases
```kotlin
class StartRecordingUseCase(
    private val videoRepository: VideoRepository
) {
    suspend operator fun invoke() = videoRepository.startRecording()
}
```

### 2. Adicionar Dependency Injection (Hilt)
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    fun provideVideoRepository(
        cameraManager: CameraManager,
        videoStorage: VideoStorage
    ): VideoRepository = VideoRepositoryImpl(cameraManager, videoStorage)
}
```

### 3. Implementar Database (Room)
Para manter histórico de gravações

### 4. Adicionar Analytics
Para rastrear uso do app

## Referências

- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [MVVM Pattern](https://developer.android.com/jetpack/guide)
- [CameraX Documentation](https://developer.android.com/training/camerax)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
