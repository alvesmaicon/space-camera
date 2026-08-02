# Quick Start

Do clone ao app rodando.

## Pré-requisitos

- **JDK 17 ou superior** (o projeto roda no 21)
- **Android SDK** com a plataforma 34
- Aparelho com API 24+ ou emulador rodando

Node **não** é necessário para compilar — os ícones já vêm versionados. Só
instale se for regerá-los a partir de `space-cam.png`.

## Três passos

```bash
# 1. Aponte o SDK
cp local.properties.example local.properties
#    edite sdk.dir se o seu SDK não estiver no caminho padrão

# 2. Confirme que há um device
adb devices

# 3. Compile, instale e abra
./gradlew installDebug && adb shell am start -n com.spacecamera/.MainActivity
```

Ou, em um comando só, já verificando que a câmera realmente inicializou:

```bash
scripts/smoke.sh
```

A primeira execução baixa o Gradle 8.9 e as dependências — conte alguns minutos.

## Confirmando que funcionou

```bash
scripts/logcat.sh 'evt='
```

Deve aparecer um `evt=bind` logo após o app abrir, seguido de `evt=caps` com as
capacidades do aparelho. Se aparecer `evt=bind_failed`, o stacktrace vem junto.

## Comandos do dia a dia

```bash
./gradlew installDebug         # compila e instala
./gradlew testDebugUnitTest    # testes (JVM, não precisa de device)
./gradlew lint                 # Android Lint
./gradlew detekt               # análise estática Kotlin
scripts/logcat.sh              # logs só do app
```

`make help` lista os atalhos equivalentes.

## Se algo der errado

**`sdk.dir not found`** — o passo 1 não foi feito ou o caminho está errado.

**Falha em `JdkImageTransform` ou `jlink`** — JDK incompatível com a versão do
AGP. O projeto está em AGP 8.7.3, que funciona no JDK 21; se você baixou a versão
do AGP, volte atrás.

**`sharp not found`** — só afeta a geração de ícones. `npm install`.

**O app abre mas a tela fica preta** — permissão de câmera negada. Reinstale ou
conceda: `adb shell pm grant com.spacecamera android.permission.CAMERA`.

## Próxima leitura

- [README.md](README.md) — o que o app faz e como diagnosticar
- [CLAUDE.md](CLAUDE.md) — convenções e armadilhas do código
- [ARCHITECTURE.md](ARCHITECTURE.md) — como as peças se encaixam
- [BUILD.md](BUILD.md) — matriz de versões, release e assinatura
