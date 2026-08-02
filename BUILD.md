# Instruções de Build

## Pré-requisitos

- Android SDK 34+
- Android NDK (opcional)
- Emulador ou dispositivo Android conectado
- Variável de ambiente ANDROID_HOME configurada

## Setup Inicial

### 1. Configure a variável ANDROID_HOME

**macOS/Linux:**
```bash
export ANDROID_HOME=/Users/$(whoami)/Library/Android/sdk
echo 'export ANDROID_HOME=/Users/$(whoami)/Library/Android/sdk' >> ~/.zshrc
```

**Windows:**
```cmd
setx ANDROID_HOME %USERPROFILE%\AppData\Local\Android\sdk
```

### 2. Execute o script de setup

```bash
chmod +x setup.sh
./setup.sh
```

### 3. Verifique a instalação

```bash
adb devices
```

## Build Debug

```bash
# Com Yarn
yarn build:debug

# Ou com Gradle direto
cd android && ./gradlew assembleDebug
```

O APK será salvo em: `app/build/outputs/apk/debug/app-debug.apk`

## Build Release

```bash
# Com Yarn
yarn build:release

# Ou com Gradle direto
cd android && ./gradlew assembleRelease
```

**Nota:** Para release, você precisa de uma chave de assinatura.

## Instalar em Dispositivo

### Automático com Yarn

```bash
yarn install:debug
```

### Manual

```bash
# Detectar dispositivo
adb devices

# Instalar APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Ou com Gradle
cd android && ./gradlew installDebug
```

## Executar App

```bash
# Automático
yarn run:debug

# Manual
adb shell am start -n com.spacecamera/.MainActivity
```

## Comandos Gradle Úteis

```bash
cd android

# Build sem assinatura
./gradlew assembleDebug

# Build e instalar
./gradlew installDebug

# Apenas build debug
./gradlew compileDebug

# Limpar tudo
./gradlew clean

# Ver dependências
./gradlew dependencies

# Rodar testes
./gradlew test

# Lint
./gradlew lint
```

## Troubleshooting

### Erro: JAVA_HOME não encontrado
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### Erro: "No Android SDK found"
```bash
cd android && ./gradlew --init --type basic
```

### Limpeza completa
```bash
./android/gradlew clean
rm -rf .gradle
rm -rf build
npm run build:debug
```

### Ver logs do app
```bash
adb logcat | grep -i spacecamera
```

### Desinstalar app do dispositivo
```bash
adb uninstall com.spacecamera
```

## CI/CD

Para automatizar builds em CI/CD, use:

```bash
./gradlew assembleRelease \
  -DKEYSTORE_PATH=$KEYSTORE_PATH \
  -DKEYSTORE_PASSWORD=$KEYSTORE_PASSWORD \
  -DKEY_ALIAS=$KEY_ALIAS \
  -DKEY_PASSWORD=$KEY_PASSWORD
```

## Performance

Para builds mais rápidos:

```gradle
# gradle.properties
org.gradle.jvmargs=-Xmx4096m
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.workers.max=4
```

## Documentação Oficial

- [Android Build System](https://developer.android.com/build)
- [Gradle Documentation](https://gradle.org/documentation/)
- [CameraX Documentation](https://developer.android.com/training/camerax)
