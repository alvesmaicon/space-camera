# Space Camera - Android Video Recording App

Um aplicativo Android de câmera de alta qualidade que grava vídeos em Full HD e 4K com suporte completo a áudio.

## Características

- 📹 **Gravação de Vídeo de Alta Qualidade**: Suporte para HD (720p), Full HD (1080p) e 4K (2160p)
- 🎵 **Áudio Sincronizado**: Gravação de áudio do telefone junto com o vídeo
- ⚡ **Balanceamento de Imagem em Tempo Real**: Processamento automático de exposição e white balance
- 💾 **Integração com Galeria**: Vídeos salvos automaticamente na galeria do Android
- 🔦 **Controle de Flash**: Ativar/desativar luz frontal durante gravação
- ⏸️ **Controles Básicos**: Iniciar, pausar, retomar e parar gravação

## Arquitetura

O projeto segue **Clean Architecture** com **MVVM**:

```
app/
├── presentation/          # UI com Jetpack Compose
│   ├── screens/          # Telas
│   └── viewmodels/       # ViewModels
├── domain/               # Business logic
│   ├── repository/       # Interfaces
│   └── usecase/          # Use cases
├── data/                 # Data layer
│   ├── repository/       # Implementações
│   └── storage/          # Acesso a armazenamento
└── camera/               # Lógica de câmera
    ├── CameraManager.kt  # Gerenciador de câmera
    └── VideoProcessor.kt # Processamento de vídeo
```

## Stack Tecnológico

- **Kotlin**: Linguagem principal
- **Jetpack Compose**: Interface de usuário moderna
- **CameraX**: API de câmera do Android
- **MediaRecorder**: Gravação de vídeo com áudio
- **Coroutines**: Programação assíncrona
- **Gradle**: Build system

## Requisitos

- Android 7.0+ (API 24)
- Android SDK 34
- Java 17
- Node.js 16+ (para scripts yarn)

## Instalação

1. **Clone ou navegue para o projeto**:
```bash
cd /Users/SEU_USUARIO/projetos/space-camera
```

2. **Configure o local.properties** (se necessário):
```bash
echo "sdk.dir=/Users/SEU_USUARIO/Library/Android/sdk" > local.properties
```

3. **Instale dependências** (se tiver Node.js):
```bash
npm install
# ou
yarn install
```

4. **Gere os ícones do app** (opcional, recomendado):
```bash
npm run generate:icons
```

## Geração de Ícones

O projeto inclui um script para gerar automaticamente os ícones do app em todos os tamanhos necessários para Android.

### Usar Ícone Customizado

Para usar seu próprio ícone:

1. **Coloque sua imagem na raiz do projeto** com o nome `space-cam.png`
2. **Execute o script de geração**:
```bash
# Node.js (recomendado - multiplataforma)
npm run generate:icons

# Ou bash com ImageMagick
chmod +x scripts/generateIcons.sh
./scripts/generateIcons.sh
```

3. **Compile e teste**:
```bash
npm run build:debug
```

### Formatos Suportados
- PNG (recomendado)
- JPG
- Qualquer formato suportado por Sharp/ImageMagick

**Resolução recomendada**: Mínimo 512x512px para melhor qualidade

Para mais detalhes, veja [scripts/README.md](scripts/README.md)

## Build e Execução

### Com Yarn/NPM

```bash
# Build Debug
yarn build:debug

# Instalar e Executar
yarn install:debug
yarn run:debug

# Build Release
yarn build:release

# Limpar build
yarn clean

# Executar testes
yarn test

# Lint
yarn lint
```

### Com Gradle Direto

```bash
# Build Debug APK
cd android && ./gradlew assembleDebug

# Build Release APK
cd android && ./gradlew assembleRelease

# Instalar em dispositivo conectado
cd android && ./gradlew installDebug

# Executar app
adb shell am start -n com.spacecamera/.MainActivity

# Limpar
cd android && ./gradlew clean
```

## Permissões Necessárias

O app solicita as seguintes permissões:
- `CAMERA`: Acesso à câmera
- `RECORD_AUDIO`: Gravação de áudio
- `WRITE_EXTERNAL_STORAGE`: Salvar vídeos no armazenamento
- `READ_EXTERNAL_STORAGE`: Ler da galeria

## Uso

1. **Iniciar App**: O app solicitará permissões na primeira execução
2. **Selecionar Resolução**: Use os botões no topo para escolher 720p, 1080p ou 4K
3. **Controlar Flash**: Toque o ícone de flash para ligar/desligar
4. **Iniciar Gravação**: Clique no botão vermelho grande
5. **Pausar/Retomar**: Use o botão laranja durante gravação
6. **Parar**: Clique no botão vermelho de parada

## Arquivos Gerados

Os vídeos são salvos em:
- **Android 10+**: `/storage/emulated/0/Android/data/com.spacecamera/files/Movies/`
- **Android 9-**: `/storage/emulated/0/Movies/`

Os vídeos ficarão acessíveis na galeria do Android após finalização.

## Estrutura de Diretórios do Projeto

```
space-camera/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/spacecamera/
│   │   │   │   ├── camera/           # Lógica de câmera
│   │   │   │   ├── presentation/     # UI com Compose
│   │   │   │   ├── domain/           # Interfaces
│   │   │   │   ├── data/             # Repositórios e storage
│   │   │   │   └── MainActivity.kt
│   │   │   ├── res/
│   │   │   │   └── values/           # Strings, cores, temas
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── package.json
├── .gitignore
└── README.md
```

## Próximas Melhorias

- [ ] Filtros e efeitos de câmera
- [ ] Modo de gravação lenta (slow-motion)
- [ ] Compressão automática de vídeo
- [ ] Histórico de gravações
- [ ] Compartilhamento direto de vídeos
- [ ] Múltiplas câmeras (frontal/traseira)

## Troubleshooting

### Erro: "sdk.dir not found"
Execute:
```bash
echo "sdk.dir=/Users/SEU_USUARIO/Library/Android/sdk" > local.properties
```

### App fecha ao iniciar gravação
- Verifique se tem espaço em disco
- Confirme permissões de escrita em armazenamento
- Verifique logs: `adb logcat | grep CameraManager`

### Vídeo não aparece na galeria
- Aguarde um momento (a indexação é assíncrona)
- Reinicie o app de galeria
- Use gerenciador de arquivos para verificar `/Movies/`

## Contribuindo

Para contribuir com melhorias:
1. Crie uma branch: `git checkout -b feature/nova-feature`
2. Commit: `git commit -am 'Add nova feature'`
3. Push: `git push origin feature/nova-feature`
4. Abra um Pull Request

## Licença

MIT License - veja LICENSE file para detalhes

## Contato

Para dúvidas ou sugestões, abra uma issue no repositório.
