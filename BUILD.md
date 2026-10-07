# Build

Para o caminho rápido, veja [QUICKSTART.md](QUICKSTART.md). Este documento cobre
a mecânica do build: versões, configuração de release e integração contínua.

## Matriz de versões

Tudo vem de `gradle/libs.versions.toml`. **Nunca** escreva versão literal num
`build.gradle.kts` — o projeto já teve duas fontes de verdade divergindo entre si.

| Componente | Versão | Observação |
|---|---|---|
| AGP | 8.13.2 | não regrida abaixo de 8.2.1 — versões anteriores falham no JDK 21 |
| Gradle | 8.14.3 | |
| Kotlin | 1.9.25 | última 1.9.x |
| Compose Compiler | 1.5.15 | amarrado ao Kotlin; trocar um obriga a trocar o outro |
| compileSdk / targetSdk | 36 | |
| CameraX | 1.4.0 | 1.5+ exige Kotlin 2.x — ver ADR-003 da spec de registro de modos |
| minSdk | 24 | Android 7.0 |
| JVM target | 17 | do bytecode, independente do JDK que roda o Gradle |

O JDK que executa o Gradle precisa ser 17 ou superior. Foi testado no 21.

### A armadilha do JdkImageTransform

O projeto ficou muito tempo em AGP 8.1.4, que quebra no JDK 21 em
`JdkImageTransform`/jlink. A falha não aparecia porque não há nenhum arquivo
`.java` no projeto: a tarefa `compileDebugJavaWithJavac` era `NO-SOURCE` e o
transform nunca rodava. Ligar `buildConfig = true` passou a gerar
`BuildConfig.java` e o problema veio à tona na hora.

Se você vir esse erro, a causa é AGP antigo com JDK novo. Suba o AGP; não baixe
o JDK.

## Versionamento do app

`versionCode` e `versionName` vivem no version catalog e chegam ao código por
`BuildConfig`. A tela Sobre lê de lá, junto com `BuildConfig.GIT_SHA`, que é o
hash curto do commit embutido no momento do build.

Isso resolve um problema real: por muito tempo todos os APKs saíram como
`versionCode=1` / `versionName=1.0.0`, e a versão "de verdade" existia só no nome
do arquivo em `backups/`. Não havia como saber qual build estava num aparelho.

Para lançar, altere apenas:

```toml
# gradle/libs.versions.toml
appVersionName = "0.5.0"
appVersionCode = "50"     # derivado do semver, para sempre crescer
```

Builds de debug ganham o sufixo `-debug` no `versionName`. O `applicationId` é o
mesmo nos dois tipos, de propósito: os scripts iniciam a activity por
`com.spacecamera/.MainActivity`.

## Artefatos

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # app/build/outputs/apk/release/
./gradlew bundleRelease      # .aab para a Play Store
```

APKs não são versionados (`.gitignore`). Para distribuir uma build específica,
use o `GIT_SHA` da tela Sobre para identificá-la.

## Release: o que falta

O build de release **ainda não está pronto para publicação**:

- `isMinifyEnabled = false` — sem R8, o APK é maior e o código vai legível
- `proguard-rules.pro` está praticamente vazio
- não há `signingConfig` — o `assembleRelease` sai sem assinar

Ao configurar a assinatura, coloque as credenciais num `keystore.properties`
fora do controle de versão (já está no `.gitignore`, junto de `*.jks` e
`*.keystore`) e leia dali no `build.gradle.kts`. Nunca no version catalog nem em
`gradle.properties`, que são versionados.

## Verificação

```bash
./gradlew testDebugUnitTest    # testes JVM, sem device
./gradlew lint                 # Android Lint (baseline em app/lint-baseline.xml)
./gradlew detekt               # análise estática Kotlin
scripts/smoke.sh               # instala e confere que a câmera inicializa
```

Os dois baselines — `app/lint-baseline.xml` e `config/detekt/detekt-baseline.xml`
— congelam a dívida existente para que a análise só falhe em problema novo. Ao
corrigir itens antigos, reencolha o baseline correspondente
(`./gradlew updateLintBaseline`, `./gradlew detektBaseline`) em vez de deixá-lo
crescer.

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) roda a cada push na `main` e em todo
pull request:

```bash
./gradlew assembleDebug testDebugUnitTest lint detekt
```

Usa JDK 21 e o Android SDK do runner, com a plataforma 36 instalada pelo próprio workflow.
`scripts/smoke.sh` exige emulador e fica de fora; verificação em aparelho continua manual
(ver as armadilhas no [CLAUDE.md](CLAUDE.md)).

## Cache e limpeza

```bash
./gradlew clean          # saída do build
make clean-all           # clean + remove .gradle/ local
```

`gradle.properties` já liga daemon e execução paralela com 4 GB de heap. Está
com `android.enableJetifier=true` sem nenhuma dependência de support library
sobrando — remover economiza tempo de build
(ver [REFACTORING.md](REFACTORING.md#5-pendências-menores)).

A primeira execução dos testes baixa ~145 MB de artefatos `android-all` do
Robolectric para `~/.m2`. Depois disso fica em cache.
