# Decisões — Registro de Modos Extensível

## Progresso dos Gates

| Gate | Status | Confiança | Data |
|------|--------|-----------|------|
| 0 — Pesquisa | Concluído | 76% | 2026-08-05 |
| 1 — Requisitos | Concluído | 82% | 2026-08-05 |
| 2 — Contexto | Concluído | 84% | 2026-08-05 |
| 3 — Arquitetura | Concluído | 87% | 2026-08-05 |
| 4 — Tarefas | Concluído | 91% | 2026-08-05 |
| 5 — Go/No-Go | **GO** | **92%** | 2026-08-05 |

---

## Gate 0 — Pesquisa

### Contexto do repositório (§5.1)

Lidos: `CLAUDE.md`, `REFACTORING.md`, `ROADMAP.md`, `BUILD.md`, `gradle/libs.versions.toml`,
`app/build.gradle.kts`, `AndroidManifest.xml`, `CameraMode.kt`, `CameraController.kt`,
`CameraManager.kt`, `CameraViewModel.kt`, `CameraScreen.kt`, `SettingsStorage.kt`, e a spec
anterior `specs/2026-08-03-adaptive-layout/`.

**Pilha e restrições identificadas** (confirmadas com o usuário na elicitação):

| Item | Valor | Consequência para esta spec |
|---|---|---|
| Módulo | único (`:app`), sem DI por framework | dependências por construtor/factory; nada de Hilt |
| Kotlin | 1.9.25 | **trava o CameraX em 1.4.x** — ver Tópico 1 |
| Compose | 1.5.4, compiler 1.5.15 | amarrado ao Kotlin 1.9.25 |
| AGP / Gradle | 8.13.2 / 8.14.3 | última 8.x; 9.x exige Kotlin 2.x |
| compileSdk / targetSdk / minSdk | 36 / 36 / 24 | atende requisitos do CameraX 1.5/1.6 |
| CameraX | 1.4.0 | mantido nesta spec (ADR-003) |
| Persistência | `SharedPreferences` via `SettingsStorage` (13 chaves) | onde a personalização de modos vai morar |
| Testes | JVM puro + Robolectric + `FakeCameraController` | base para a rede de segurança (ADR-006) |
| Telemetria | `CameraTelemetry` com `evt=caps` e `evt=bind` | instrumento de verificação antes/depois |

**Achados que definem o problema:**

1. **`CameraMode` é um enum de dois valores** ([CameraMode.kt](../../app/src/main/java/com/spacecamera/camera/CameraMode.kt)) com
   `label` e `next()`/`previous()`. Consultado por `when`/`==` em **21 pontos** espalhados por
   `CameraScreen.kt` (14), `CameraViewModel.kt` (4) e `CameraManager.kt` (2), mais
   `CameraTelemetry`. Cada modo novo multiplica esses pontos, e um esquecido é defeito
   silencioso — não há nada que force a exaustividade.

2. **`bindCameraUseCases()` é cego ao modo.** [CameraManager.kt:406-490](../../app/src/main/java/com/spacecamera/camera/CameraManager.kt#L406)
   sempre liga `Preview` + `VideoCapture` + `ImageCapture` juntos, nas três chamadas.
   O modo hoje só influencia a **proporção** (`cameraXAspectRatio()`, linha 218). Não existe
   nenhum mecanismo para um modo declarar um conjunto diferente de use cases — que é
   exatamente o que Pro (Preview+ImageCapture), câmera lenta e time-lapse precisam.

3. **O `ModeSelector` já é orientado a dados, mas o layout vertical não.**
   [CameraScreen.kt:1838](../../app/src/main/java/com/spacecamera/presentation/screens/CameraScreen.kt#L1838)
   recebe `modes: List<CameraMode>` e é chamado com `CameraMode.entries.toList()` (linha 1204).
   O ramo `vertical = true` (janela larga) tem comentário explícito assumindo dois modos:
   *"com dois modos, os dois cabem empilhados e não há o que revelar"*. Um terceiro modo
   quebra essa premissa — e a spec do layout adaptativo já registrou o transbordo em janela
   larga e baixa como ressalva aberta.

4. **A sondagem de capacidade é copiada à mão por capacidade.** EIS, HDR, ultra-wide e
   resoluções cada um com seu par de `StateFlow` e sua checagem própria dentro do
   `CameraManager`. Não há um lugar onde "capacidade" seja um conceito — então cada feature
   nova do ROADMAP.md repete o padrão.

5. **O opt-in do Camera2Interop está inerte** — ver Tópico 3. Bloqueia o modo Pro, que é
   inteiramente Camera2Interop.

### Enquadramento de escopo (§5.2)

**Q1 — O que esta spec entrega: só a costura, ou a costura provada por um modo novo?**
**A1:** Costura + Pro mínimo real (ISO e obturador), com Vídeo/Foto remigrados sem mudança
de comportamento. Fora: WB, foco manual, RAW, câmera lenta, time-lapse.

**Q2 — Como o Pro aparece no seletor?**
**A2:** Gaveta "mais modos" já nesta spec, **e** com personalização: o usuário escolhe quais
modos ficam no plano e em que ordem, como fazem os apps de câmera conhecidos. Isso ampliou o
escopo em relação à pergunta original — virou FR-7/FR-8.

**Q3 — Subir o CameraX?**
**A3:** Usuário perguntou de volta se era possível ir para 1.6.1. Pesquisado (Tópico 1) e
respondido: possível, mas atrás de migração de toolchain. Decisão: **manter 1.4.0** nesta
spec (ADR-003).

**Escopo da pesquisa:** (a) viabilidade real de CameraX 1.5/1.6 nesta toolchain e o que elas
oferecem para modos; (b) API correta de controle manual de ISO/obturador com CameraX 1.4;
(c) causa do opt-in inerte; (d) viabilidade de teste de Compose na JVM.

### Pesquisa externa (§5.3)

> **Desvio de ferramenta registrado:** `perplexity_research` não está disponível nesta
> sessão. A pesquisa foi feita com `WebSearch` + `WebFetch` seguindo o mesmo pipeline
> DETECT → FETCH → IMPLEMENT → CITE do §5.3.1, e duas afirmações foram verificadas em
> **fonte primária local** (`javap` sobre o `android.jar` da plataforma 36 e sobre o
> `camera-camera2-1.4.0.aar`), que é qualidade superior a documentação de terceiros.

#### Tópico 1: CameraX 1.5/1.6 nesta toolchain

**Consulta:** versão estável atual do CameraX; requisitos de minSdk/compileSdk/Kotlin/AGP das
linhas 1.5 e 1.6; o que elas adicionam para câmera lenta, RAW e declaração de sessão.

**Achados:**

- **1.6.1 é a estável atual (06/05/2026).** Linha 1.6: 1.6.0 em 25/03/2026. Linha 1.5:
  1.5.0 em 10/09/2025, 1.5.3 em 28/01/2026.
- **1.5.0+ exige KGP 2.0.0 ou mais novo.** Nota do 1.5.0-beta01: *"Projects released with
  Kotlin 2.0 require KGP 2.0.0 or newer to be consumed"*. O projeto está em Kotlin 1.9.25
  com `composeCompiler = 1.5.15` amarrado — logo, **CameraX 1.5+ é inconsumível sem migrar
  para Kotlin 2.x**, o que arrasta o plugin do Compose Compiler.
- **compileSdk:** o 1.5.0-alpha05 subiu a exigência para 35 (*"Apps using CameraX libraries
  will also need to upgrade their compileSdk config setting"*); o 1.6.0 **removeu** a
  exigência de 37 (*"Remove requirement for compileSdk 37"*). O compileSdk 36 do projeto,
  portanto, serve para as duas linhas.
- **minSdk:** padrão subiu de 21 para 23 no 1.5.0-rc01. O minSdk 24 do projeto serve.
- **1.6.0 troca a stack inteira para CameraPipe** (*"CameraX now uses CameraPipe — the same
  modern, high-performance stack powering the Pixel camera"*) e passa a usar o **muxer do
  Media3 por padrão no `VideoCapture`**. São mudanças por baixo do `bind`, na parte que esta
  base já viu regredir.
- **O que a 1.5+ ofereceria para modos:** `SessionConfig` (estável no 1.6.0) declara
  use cases + grupo de features por sessão, com `CameraInfo.isFeatureGroupSupported` /
  `isSessionConfigSupported` para consultar suporte antes de ligar. Câmera lenta tem
  `Recorder.getHighSpeedVideoCapabilities(cameraInfo)` + `HighSpeedVideoSessionConfig`; RAW
  tem `ImageCapture.setOutputFormat(OUTPUT_FORMAT_RAW / OUTPUT_FORMAT_RAW_JPEG)` com
  consulta por `getImageCaptureCapabilities`.
  *Provisório:* as duas leituras da mesma documentação divergiram na forma exata da API de
  alta velocidade (construtor vs. `Builder`). A decisão desta spec não depende disso —
  confirmar na implementação da spec futura.

**Impacto no desenho:** confirma manter 1.4.0 (ADR-003) e, mais importante, **molda o
contrato do registro de modos**: `ModeDefinition` deve declarar *conjunto de use cases +
capacidade exigida*, que é a mesma forma que o `SessionConfig` do CameraX 1.6 assume. Assim
a migração futura troca o interior do bind sem mexer nas declarações dos modos.

**Fontes:**
- [CameraX — notas de release (androidx.camera)](https://developer.android.com/jetpack/androidx/releases/camera) — 1.6.1 em 06/05/2026; 1.5.0-beta01: *"Projects released with Kotlin 2.0 require KGP 2.0.0 or newer to be consumed"*; 1.5.0-alpha05: *"Upgraded compileSdk as 35 … Apps using CameraX libraries will also need to upgrade their compileSdk config setting"*; 1.6.0: *"Remove requirement for compileSdk 37"*; 1.5.0-rc01: *"Moving the default minSdk from API 21 to API 23"*.
- [Introducing CameraX 1.5: Powerful Video Recording and Pro-level Image Capture](https://developer.android.com/blog/posts/introducing-camera-x-powerful-video-recording-and-pro-level-image-capture) — `Recorder.getHighSpeedVideoCapabilities`, `HighSpeedVideoSessionConfig`, `OUTPUT_FORMAT_RAW_JPEG`, `SessionConfig` + `isFeatureGroupSupported`.

#### Tópico 2: controle manual de ISO e obturador com CameraX 1.4

**Consulta:** nomes exatos das chaves e constantes do Camera2 para sondar suporte a controle
manual e para aplicar ISO e tempo de exposição.

**Achados** (verificados por `javap` no `android.jar` da plataforma 36 — fonte primária):

| Símbolo | Assinatura verificada | Uso nesta spec |
|---|---|---|
| `CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR` | `int` | gate de capacidade do modo Pro |
| `CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES` | `Key<int[]>` | onde procurar a capacidade acima |
| `CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE` | `Key<Range<Integer>>` | faixa de ISO ofertada na UI |
| `CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE` | `Key<Range<Long>>` | faixa de obturador (nanossegundos) |
| `CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY` | `Key<Integer>` | limite acima do qual o ganho é digital (marcar na UI) |
| `CaptureRequest.SENSOR_SENSITIVITY` | `Key<Integer>` | aplicar ISO |
| `CaptureRequest.SENSOR_EXPOSURE_TIME` | `Key<Long>` | aplicar obturador |
| `CaptureRequest.CONTROL_AE_MODE` + `CameraMetadata.CONTROL_AE_MODE_OFF` | `Key<Integer>`, `int` | sem desligar o AE, os dois acima são ignorados |

Aplicação no CameraX 1.4 se dá por `Camera2CameraControl.from(cameraControl)
.setCaptureRequestOptions(CaptureRequestOptions.Builder()…)` — as classes já estão
importadas no `CameraManager` e já são usadas para EIS/NR em `applyEisNrImmediate()`
(linha 591), o que dá um precedente testado no próprio código.

**Impacto no desenho:** o modo Pro **não depende de nenhuma API nova do CameraX** — cabe
inteiro na 1.4.0. Isso é o que permite separar esta spec da migração de toolchain. O
`applyEisNrImmediate` também estabelece o padrão a seguir: aplicar por
`Camera2CameraControl` (e **não** por `Camera2Interop.Extender` no builder), porque o
comentário em [CameraManager.kt:416-421](../../app/src/main/java/com/spacecamera/camera/CameraManager.kt#L416)
registra que o `Extender` tem prioridade no merge do `CaptureRequest` e congela o valor na
sessão.

**Fontes:**
- Fonte primária local: `javap -cp $ANDROID_SDK/platforms/android-36/android.jar android.hardware.camera2.{CameraMetadata,CameraCharacteristics,CaptureRequest}` — assinaturas transcritas acima verbatim.
- [Camera2 — CaptureRequest.SENSOR_SENSITIVITY](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#SENSOR_SENSITIVITY) — referência da chave.

#### Tópico 3: por que o opt-in do Camera2Interop está inerte

**Consulta:** causa dos 31 `UnsafeOptInUsageError` no baseline de lint e do aviso do
compilador *"Annotation … is not an opt-in requirement marker"*, com o
`@OptIn(ExperimentalCamera2Interop::class)` presente em [CameraManager.kt:98](../../app/src/main/java/com/spacecamera/camera/CameraManager.kt#L98).

**Achado** (verificado por `javap -v` no `camera-camera2-1.4.0.aar` — fonte primária):
`androidx.camera.camera2.interop.ExperimentalCamera2Interop` é uma anotação **Java**
(`SourceFile: "ExperimentalCamera2Interop.java"`, `RetentionPolicy.CLASS`) marcada com
`androidx.annotation.RequiresOptIn` — **não** com `kotlin.RequiresOptIn`. O `@OptIn` do
Kotlin só aceita marcadores anotados com `kotlin.RequiresOptIn`; para marcadores do AndroidX
o consentimento correto é `@androidx.annotation.OptIn(markerClass = ExperimentalCamera2Interop::class)`.
Isso explica os dois sintomas de uma vez: o compilador ignora o `@OptIn` do Kotlin e o lint
segue vendo uso não consentido.

**Impacto no desenho:** vira tarefa de Wave 0 e pré-requisito do modo Pro (que é todo
Camera2Interop). Reduz também parte dos 31 achados do baseline de lint, o que atende ao
NFR-8.

**Fontes:**
- Fonte primária local: `javap -v` sobre `~/.gradle/caches/.../camera-camera2-1.4.0.aar!/classes.jar` → `RuntimeInvisibleAnnotations: androidx.annotation.RequiresOptIn`, `SourceFile: "ExperimentalCamera2Interop.java"`.
- [Add a `@RequiresOptIn` annotation / consuming opt-in APIs](https://developer.android.com/build/dependencies#opt-in-requires) — uso de `androidx.annotation.OptIn(markerClass = …)` em Kotlin para marcadores do AndroidX.

### Elicitação adaptativa (§5.4)

Tópicos obrigatórios: pilha, armazenamento, estilo arquitetural e pontos de integração foram
**identificados no repositório** (tabela do §5.1) e confirmados nas respostas abaixo.
Autenticação e destino de deploy não se aplicam — app local, sem back-end, sem contas.

| # | Pergunta | Resposta | Classificação | Impacto |
|---|---|---|---|---|
| Q4 | O CameraX sobe nesta spec? | Manter 1.4.0 | Bloqueante (resolvida) | ADR-003; Pro cabe na 1.4 via Camera2Interop |
| Q5 | O modo Pro controla o quê? | **Só foto** | Bloqueante (resolvida) | Pro liga Preview+ImageCapture; já exercita bind por modo sem tocar gravação |
| Q6 | Onde se configura quais modos ficam no plano? | **Tela de Configurações** (arrastar no seletor fica para fatia futura) | Bloqueante (resolvida) | FR-7; evita 4º competidor de gesto na pré-visualização |
| Q7 | Rede de segurança da remigração? | **Teste de Compose na JVM (Robolectric)** | Bloqueante (resolvida) | ADR-006; NFR-6; exige `testImplementation` do `compose-ui-test-junit4` |

**Premissas assumidas** (não bloqueantes, registradas para virar AC):

- **P1 — Modo sem suporte não aparece.** Se o aparelho não reporta `MANUAL_SENSOR`, o modo
  Pro não aparece em lugar nenhum (nem no plano, nem na gaveta, nem em Configurações).
  Segue a convenção já documentada no CLAUDE.md: *"se algo não aparece na tela, é porque não
  veio nessa linha [`evt=caps`]"*. Alternativa rejeitada: mostrar desabilitado com aviso —
  cria estado morto na UI e contradiz o resto do app.
- **P2 — Vídeo e Foto não são ocultáveis.** A personalização permite reordenar e mover para a
  gaveta, mas não esconder os dois modos base; caso contrário o usuário pode se trancar fora
  da função principal do app.
- **P3 — Chave de persistência por identificador estável**, não por `ordinal` do enum. Enum
  ordinal já é usado em `SettingsStorage` para presets, mas para modos a lista é aberta e
  reordenável: `ordinal` quebraria a preferência a cada modo novo inserido no meio.
- **P4 — Iso-comportamento é sobre comportamento observável**, não sobre estrutura interna:
  mesma proporção, mesmos controles visíveis, mesma mídia gravada, mesmos campos em
  `evt=bind`. Refatorar o interior é o objetivo, então "idêntico" não pode significar
  "mesmo código".

### Pesquisa de acompanhamento (§5.5)

Disparada por Q7: teste de Compose na JVM é técnica que este projeto nunca executou.

**Achados:** a combinação exige (a) Robolectric ≥ 4.7.3 — o projeto tem **4.13** ✓;
(b) `testOptions.unitTests.isIncludeAndroidResources = true` — **já presente** em
[app/build.gradle.kts:86](../../app/build.gradle.kts#L86) ✓; (c) `compose-ui-test-junit4`
como `testImplementation` — hoje está **só** em `androidTestImplementation`
([linha 182](../../app/build.gradle.kts#L182)), precisa ser adicionado; (d)
`debugImplementation(compose-ui-test-manifest)` — **já presente** (linha 186) ✓;
(e) configuração de sdk do Robolectric — o projeto já usa `@Config(sdk = [34])` nos testes
existentes, mesmo padrão serve. Não existe `app/src/test/resources/robolectric.properties`;
manter o padrão por anotação evita mudar o comportamento dos testes que já passam.

**Riscos conhecidos da combinação**, para as tarefas: `AppNotIdleException` quando há muitos
testes de Compose no mesmo módulo, e `captureToImage` estourando tempo com gráficos nativos.
Mitigação: as asserções desta spec são de **árvore semântica** (nó existe, texto, clique),
nunca de pixel — captura de imagem continua sendo verificação manual em aparelho.

**Fontes:**
- [Robolectric strategies — Test your app on Android](https://developer.android.com/training/testing/local-tests/robolectric) — execução de testes de UI no ambiente JVM.
- [Test your Compose layout](https://developer.android.com/develop/ui/compose/testing) — `createComposeRule()` e asserções por árvore semântica.
- [robolectric/robolectric#7055](https://github.com/robolectric/robolectric/issues/7055) — `AppNotIdleException` com muitos testes de Compose.
- [robolectric/robolectric#8071](https://github.com/robolectric/robolectric/issues/8071) — `captureToImage` estoura tempo com gráficos nativos.

### Confiança inicial: 76%

Sustentada por: problema medido em números concretos (21 pontos de ramificação, bind cego ao
modo, premissa de dois modos no layout vertical); quatro decisões bloqueantes resolvidas pelo
usuário; API do Pro verificada em fonte primária; viabilidade da rede de teste confirmada
contra a configuração real do projeto.

Descontos: o tamanho real do recorte da `CameraScreen` (2.220 linhas) só se conhece ao
recortar; a interação entre `ImageCapture` sozinho no bind e o resto do fluxo de foto
(processamento, EXIF) não foi exercitada; e capacidade de hardware — inclusive
`MANUAL_SENSOR` — só se verifica em aparelho real, com o emulador podendo mascarar defeito e
acerto igualmente.

---

## Gate 1 — Requisitos

17 requisitos funcionais (EARS) e 8 não funcionais (QAS), com 18 critérios de aceite.
Prioridades: 6 Críticos (FR-1, FR-2, FR-3, FR-4, FR-5, FR-6, FR-15 e NFR-1, NFR-2), o resto
Alto/Médio/Baixo.

Decisões de recorte tomadas ao redigir:

- **FR-3 e FR-4 (iso-comportamento) entraram como requisitos**, não como "cuidado durante a
  implementação". Numa refatoração, "não mudar comportamento" é o requisito principal e
  precisa de medida — sem isso não há como reprovar o PR.
- **NFR-2 é a razão de existir da spec** e por isso ganhou medida dura: um modo novo custa
  1 arquivo + 1 linha, comprovado por recibo com modo-exemplo descartável. Um NFR de
  manutenibilidade sem número é decoração.
- **NFR-3 exige zero linha líquida** nos dois arquivos grandes, apesar de a spec adicionar
  três superfícies novas (gaveta, personalização, overlay do Pro). É o mecanismo que impede a
  refatoração de deixar a base pior do que achou.

### Validação por consenso — requirements.md

**Data:** 2026-08-05

**Perspectiva 1 — O Pragmático**
*Forças:* escopo fechado por decisão explícita do usuário nas quatro perguntas bloqueantes;
Pro cabe na CameraX 1.4.0, então não há dependência externa; rede de teste roda na JVM, logo
a verificação não depende de aparelho disponível.
*Preocupações:* (a) **NFR-4 é inverificável na ordem errada** — o baseline de latência tem de
ser medido *antes* de qualquer alteração, senão não existe com o que comparar; (b) o recibo do
NFR-2 com um modo de mentira pode vazar para o ramo de entrega.
*Recomendações:* medir o baseline como primeiríssimo passo do Gate 4; deixar explícito que o
modo-exemplo mora em commit descartável fora do ramo.
*Impacto na confiança:* +3%

**Perspectiva 2 — O Perfeccionista**
*Forças:* EARS respeitado com um comportamento por requisito; QAS com seis componentes e
medida quantitativa em todos os oito; ACs cobrem os caminhos de erro (faixa estourada,
preferência corrompida, capacidade ausente).
*Preocupações:* (a) **contradição real entre FR-13 e NFR-1** — FR-13 acrescenta campos ao
`evt=bind` e NFR-1 exigia "os mesmos campos" nos dois commits; como redigido, era impossível
satisfazer os dois; (b) **lacuna de caso**: FR-5 trata modo sem capacidade e AC-8.1 trata
identificador desconhecido, mas faltava o caso do meio — modo **conhecido e indisponível
neste aparelho** que está na preferência. Sem definição, a implementação pode apagar a
preferência e o usuário perde a configuração ao trocar de aparelho.
*Recomendações:* redigir a medida do NFR-1 em termos dos campos preexistentes; acrescentar AC
para o modo conhecido-indisponível.
*Impacto na confiança:* +2%

**Perspectiva 3 — O Advogado**
*Forças:* o que o usuário pediu explicitamente ("mover modos da gaveta para o plano, como nos
apps famosos") está contemplado como personalização de ordem e visibilidade.
*Preocupações:* a personalização foi para a tela de Configurações por boa razão técnica
(quarto competidor de gesto na pré-visualização), mas **ninguém vai descobri-la lá**. Nos
apps de referência a personalização fica onde os modos estão. O valor entregue fica menor que
o valor pedido se a única porta for Configurações > Modos da câmera.
*Recomendações:* a gaveta precisa oferecer atalho para a tela de personalização.
*Impacto na confiança:* +1%

**Análise de divergência**
*Acordo:* escopo, prioridades e a existência de medida em todos os NFRs.
*Divergência:* o Advogado queria a personalização no seletor (gesto de arrastar); Pragmático e
Perfeccionista sustentaram a decisão Q6 do usuário. Resolvido pelo meio: personalização em
Configurações **com atalho a partir da gaveta** — entrega descoberta sem criar o quarto
gesto sobre a pré-visualização.
*Conflitos não resolvidos:* nenhum.

**Placar**
- Pragmático: 8/10
- Perfeccionista: 8/10
- Advogado: 8/10
- **Média: 8/10**

**Ações — bloqueantes (aplicadas antes de fechar o gate)**
1. ✅ Medida do NFR-1 reescrita: comparação só sobre os campos que já existiam em `evt=bind`;
   campos de FR-13 são aditivos e ficam fora.
2. ✅ **AC-5.3** acrescentado: modo conhecido e indisponível não aparece e **não** apaga a
   preferência.
3. ✅ **FR-17** acrescentado: gaveta oferece atalho para a personalização.
4. ✅ Medida do NFR-2 detalhada: recibo em commit descartável fora do ramo de entrega.

**Ações — não bloqueantes**
1. Medir o baseline de latência como primeira tarefa do Gate 4 (vira Tarefa 1).

**Status final:** APROVADO COM MUDANÇAS (as quatro aplicadas)

### Confiança ao fechar o Gate 1: 82%

---

## Gate 2 — Contexto

Fronteiras em [design.md §1](design.md#1-contexto-c4-nível-1). Nenhuma integração externa
nova: o que muda é a origem da resposta para "o que este modo faz". Duas integrações ganham
conteúdo — o HAL (capacidade `MANUAL_SENSOR` e faixas) e o `SharedPreferences` (ordem e
visibilidade dos modos).

**Ator não humano decisivo:** o HAL determina se o modo Pro existe. Isso coloca o emulador
fora da verificação de capacidade, pela armadilha 4 do CLAUDE.md — sem `MANUAL_SENSOR`, o
comportamento correto (Pro não aparece) é indistinguível do defeituoso (Pro nunca aparece).

**Validação dos diagramas:** não há CLI do Mermaid no projeto nem instalada globalmente
(`node_modules/.bin` sem `mmdc`). A conferência foi estrutural — identificadores, formas,
setas e blocos `subgraph`/`Note` revisados um a um, e um `<novo>` em rótulo de sequência foi
trocado por `modo_novo` porque colchetes angulares em rótulo são interpretados como marcação e
desaparecem na renderização. Renderização visual fica para quem abrir o arquivo no editor.

### Validação por consenso — contexto

**Pragmático (8/10):** fronteira correta e enxuta; aprovou explicitar o HAL como ator que
decide disponibilidade de modo, porque é isso que transforma "gate de capacidade" de detalhe
em requisito de fronteira.
**Perfeccionista (8/10):** cobrou — e obteve — que o diagrama mostrasse `Logcat` como destino,
já que a telemetria é o instrumento de verificação de NFR-1 e NFR-4; sem ela no contexto, a
verificação parece mágica.
**Advogado (9/10):** aprovou "Quem implementa a próxima feature" figurar como ator de primeira
classe — é o beneficiário real desta spec, e deixá-lo fora do desenho seria esconder o motivo
do trabalho.
*Divergência:* nenhuma. *Conflitos:* nenhum.

### Confiança ao fechar o Gate 2: 84%

---

## Gate 3 — Arquitetura

### ADR-001: Registro declarativo de definições de modo, como hierarquia selada

**Status:** Aceito — aprovado pelo usuário em 2026-08-05 (§8.3), na forma híbrida
**Data:** 2026-08-05
**Decisores:** Maicon (dono do projeto), agente de spec

> **Forma final aprovada.** A matriz abaixo comparou três alternativas e a discussão com o
> usuário revelou que o enquadramento inicial confundia duas coisas: *o que o modo declara*
> (dados vs. UI) e *como isso é escrito* (data class vs. hierarquia). A nota baixa de C em
> testabilidade vinha de **uma linha** — `@Composable abstract fun Overlay()` —, não de
> herança. A forma aceita é o **híbrido**:
>
> - `sealed class CameraModeDefinition` com membros abstratos, uma subclasse/objeto por modo
> - **dados** para escolha de conjunto fechado: `useCases`, `requiredCapability`, `controls`,
>   `overlay`, `pinnedByDefault`
> - **método abstrato** para o que é comportamento e varia por modo: ação do disparador,
>   comportamento do flash — devolvendo **descrição** (`ShutterAction`, `FlashBehavior`),
>   nunca executando
> - **proibido** dentro da definição de modo: estado mutável, chamada ao controller,
>   `@Composable`
>
> Dois argumentos decidiram, ambos verificáveis:
>
> 1. **A barra de ações precisa de comportamento, não só de dados.** Dos cinco sites de
>    ramificação examinados, três são escolha fechada (proporção, menu de resolução, EIS na
>    troca) mas dois são comportamento: o flash é tocha em Vídeo e ciclo OFF→AUTO→ON em Foto
>    ([CameraViewModel.kt:459](../../app/src/main/java/com/spacecamera/presentation/viewmodels/CameraViewModel.kt#L459)),
>    e o disparador grava em Vídeo e captura em Foto
>    ([CameraScreen.kt:1244](../../app/src/main/java/com/spacecamera/presentation/screens/CameraScreen.kt#L1244)).
>    Com dados puros, esse "como" continuaria num `when` do ViewModel — e o modo novo voltaria
>    a editar arquivo grande, matando o NFR-2 na prática. Com membro abstrato, o compilador
>    obriga cada modo a responder. E o disparador do time-lapse não é nem gravar nem capturar:
>    é uma terceira coisa que ninguém previu ainda.
> 2. **Migração de biblioteca que quebra o build é melhor que migração que compila calada.**
>    Quando o `SessionConfig` do CameraX 1.6 chegar, cada modo precisará declarar seu grupo de
>    features (`HDR_HLG10`, `FPS_60`, `PREVIEW_STABILIZATION`, `UHD_RECORDING`). Membro
>    abstrato novo **quebra o build em todos os modos** e transforma a migração em checklist;
>    campo novo com valor padrão numa data class compila em silêncio e faz cada modo declarar
>    "nada" — Vídeo deixaria de pedir estabilização e UHD sem ninguém notar. Numa base cujo
>    histórico registra duas rodadas de verificação perdidas por medir o código errado, falha
>    ruidosa vale mais que conveniência.
>
> **Efeito na estratégia de teste** (revisão do ADR-006, apontada pelo usuário): como cada modo
> passa a ser objeto instanciável, a maior parte das asserções sai do Robolectric e vira **JVM
> pura** — use cases, capacidade exigida, ação do disparador, comportamento do flash, gate de
> capacidade e completude do mapeamento de identificadores. Compose fica necessário em **três**
> superfícies (seletor com gaveta, tela de personalização, janela larga com N modos) em vez de
> dez. Suíte mais rápida e menos exposição ao `AppNotIdleException`.

#### Contexto e problema

`CameraMode` é enum de dois valores consultado por `if`/`==`/`when` em 21 pontos de três
arquivos grandes. Não existe mecanismo que force um modo novo a ser considerado em todos eles,
e o `bindCameraUseCases()` não tem como variar o conjunto de use cases por modo. Enquanto
isso, o ROADMAP.md prevê ao menos quatro modos além dos atuais.

#### Motivadores

- NFR-2: um modo novo deve custar 1 arquivo + 1 linha
- NFR-1: remigrar Vídeo e Foto sem mudar comportamento observável
- NFR-5: registro, gate e seletor testáveis na JVM, sem aparelho
- NFR-3: não crescer `CameraScreen.kt` nem `CameraManager.kt`
- Migração futura para `SessionConfig` (CameraX 1.6) sem reescrever as declarações

#### Opções consideradas

1. **A — Enum + `when` exaustivo centralizado.** Mantém o enum e concentra as ramificações
   num `when` por assunto (bind, controles, overlay), usados como **expressão** para o
   compilador cobrar exaustividade.
2. **B — Registro declarativo.** `ModeDefinition` declara id, rótulo, use cases, capacidade,
   controles e overlay; `ModeRegistry` é a lista. UI e controller iteram declarações.
3. **C — Estratégia por herança.** `sealed class` por modo, cada uma sobrescrevendo
   comportamento e expondo seu próprio composable.

Uma quarta opção — **construir sobre o `SessionConfig` do CameraX 1.6** — foi descartada
antes da matriz por ADR-003: exige Kotlin 2.x, que está fora do escopo desta spec.

#### Análise de trade-off (ATAM-Lite)

**Pesos**

| Critério | Peso | Por quê |
|---|:--:|---|
| C1 — Custo de adicionar um modo (NFR-2) | 5 | é a razão de existir da spec; se não melhorar, nada mais importa |
| C2 — Proteção contra modo esquecido (NFR-1) | 5 | defeito silencioso em app de câmera aparece como "sumiu o botão" na mão do usuário |
| C3 — Testabilidade na JVM (NFR-5) | 4 | decisão Q7 do usuário; verificação sem aparelho é o que torna o refactor revisável |
| C4 — Não crescer os arquivos grandes (NFR-3) | 4 | dívida já mapeada; a spec não pode piorá-la |
| C5 — Caminho para `SessionConfig` | 3 | a migração vai acontecer; encarecê-la é dívida futura, não bloqueio hoje |
| C6 — Custo de implementar agora | 3 | projeto pessoal com meta de publicar; prazo importa, mas não manda |

**Matriz** — nota (nota × peso)

| Alternativa | C1 (5) | C2 (5) | C3 (4) | C4 (4) | C5 (3) | C6 (3) | **Total** | Fontes |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|---|
| A — enum + `when` exaustivo | 4 (20) | 5 (25) | 7 (28) | 3 (12) | 4 (12) | 9 (27) | **124** | ¹ ² |
| **B — registro declarativo** | 9 (45) | 8 (40) | 9 (36) | 9 (36) | 9 (27) | 6 (18) | **202** | ² ³ ⁴ |
| C — estratégia por herança | 8 (40) | 7 (35) | 4 (16) | 7 (28) | 6 (18) | 5 (15) | **152** | ⁴ |

**Fontes das notas**

1. [Kotlin — `when` como expressão e como statement](https://kotlinlang.org/docs/control-flow.html#when-expressions-and-statements) — *"If you use `when` as an expression, you must cover all possible cases … If you don't cover all cases, the compiler throws an error"*, mas *"If you use `when` as a statement, you don't need to cover all possible cases … However, no error occurs"*. **É o que limita a nota de A em C2 a 5:** a proteção só existe onde o `when` for expressão, e os 21 pontos atuais são majoritariamente `if`/`==`, que nunca acusam omissão. A disciplina fica com o revisor, não com o compilador.
2. Medição própria no repositório: 21 pontos de ramificação por modo; `bindCameraUseCases()` liga os mesmos três use cases nas três chamadas. Fonte primária (o código).
3. [Introducing CameraX 1.5 — `SessionConfig` e grupos de features](https://developer.android.com/blog/posts/introducing-camera-x-powerful-video-recording-and-pro-level-image-capture) — `SessionConfig(useCases = …, preferredFeatureGroup = …)` + `isFeatureGroupSupported`. **Sustenta a nota 9 de B em C5:** "conjunto de use cases + capacidade exigida" é exatamente a forma que o CameraX assumiu; declarar assim hoje faz a migração trocar o interior do bind sem tocar as declarações.
4. [Arquitetura da camada de UI no Compose](https://developer.android.com/develop/ui/compose/architecture) — elevação de estado e separação entre UI e domínio. **Sustenta a nota 4 de C em C3:** composable dentro da classe de modo põe UI no domínio e tira o registro do alcance do teste JVM puro.

**Análise de sensibilidade**

- *Se C6 (custo agora) subir para 5 e C1 cair para 3* — o cenário "quero o mais rápido
  possível": A = 134, B = 196. **B continua ganhando.** B só perde se C1 cair para 1, o que
  equivale a dizer que a spec não tem motivo para existir.
- *Se C3 cair para 2* (abrir mão do teste na JVM, revertendo Q7): C sobe para 148 e B cai para
  184 — **B ainda ganha**, mas a distância de C encurta. A decisão Q7 do usuário reforça B em
  vez de ser a única razão dela.
- *Melhorar C1 piora algo?* Sim: a indireção "identificador → composable" que dá a B as notas
  de C1 e C3 **piora a legibilidade do caminho** — achar o overlay de um modo passa a exigir
  consultar uma tabela de mapeamento, em vez de seguir uma referência direta. Mitigação:
  tabela única, com nome óbvio, e um teste que falha se algum identificador declarado não
  tiver composable correspondente. Sem esse teste, a indireção troca defeito de compilação por
  defeito de execução — que é pior.

#### Riscos

| Risco | Prob. | Impacto | Mitigação |
|---|---|---|---|
| Declaração incompleta: modo declara overlay/controle sem correspondente na UI | Média | Alto — tela quebrada em execução | Teste JVM que varre o registro e falha se algum identificador não resolver |
| Remigração de Vídeo/Foto muda comportamento sem ninguém notar | Média | Alto | NFR-1: testes atuais sem alteração de asserção + `evt=bind` comparado entre dois `git worktree` |
| Recorte da `CameraScreen` (2.220 linhas) maior que o previsto | Alta | Médio | Fatiar por modo, um PR por fatia, com verificação em aparelho a cada passo |
| Indireção nova vira dívida de legibilidade | Média | Médio | Tabela única de mapeamento + teste de completude; revisar se passar de um arquivo |
| Combinação de use cases recusada pelo aparelho no modo novo | Baixa | Alto — pré-visualização preta | Fluxo 4.2 do design: falha volta ao conjunto do modo anterior; nunca deixar sem bind |

#### Consequências

**Boas**
- Modo novo passa a ser arquivo novo, não edição em seis lugares
- Registro e gate de capacidade viram dados puros — testáveis na JVM, sem CameraX
- A forma declarada casa com o `SessionConfig` do CameraX 1.6: a migração futura troca o
  executor, não as declarações
- O gate de capacidade deixa de ser padrão copiado por feature e passa a ser propriedade do
  modo

**Ruins**
- Uma indireção nova (identificador → composable) que não existia
- Custo maior agora do que centralizar `when`
- O registro é mais um conceito para quem chega entender — pago com a tabela de §3 do design

#### Fontes consultadas

Fontes ¹ a ⁴ da matriz acima. A ¹ e a ² são as que sustentam a rejeição de A; a ³ é a que
sustenta a aposta de compatibilidade futura de B.

#### Tarefas afetadas

Tarefas 2, 6, 7, 8, 9, 11, 13

---

### ADR-002: Conjunto de use cases declarado, com tipo próprio do app

**Status:** Proposto
**Data:** 2026-08-05

#### Contexto e problema

`bindCameraUseCases()` liga `Preview` + `VideoCapture` + `ImageCapture` sempre. Pro precisa de
`Preview` + `ImageCapture`; câmera lenta precisará de outro conjunto ainda. A pergunta de
desenho é *como* o modo expressa esse conjunto.

#### Opções consideradas

1. Declarar com os **tipos do CameraX** (`Preview`, `VideoCapture`, `ImageCapture`)
2. Declarar com um **tipo próprio do app** (`PREVIEW`, `VIDEO_CAPTURE`, `IMAGE_CAPTURE`), que
   o controller traduz

#### Decisão

**Opção 2.** Três razões: (a) mantém o registro testável na JVM sem instanciar nada do
CameraX; (b) o CameraX 1.6 substitui o bind por `SessionConfig`, e a tradução isola essa
mudança num só lugar; (c) impede que configuração de use case (qualidade, bitrate, proporção)
vaze para a declaração do modo, que deve dizer *o quê*, não *como*.

Junto vem a regra de falha do fluxo 4.2 do design: se o `bind` recusar a combinação, o
controller **restaura o conjunto do modo anterior** e reporta falha. Pré-visualização preta é
o pior resultado possível, e a base já viveu um caminho em que `evt=bind` reportava sucesso em
7 ms com a tela preta.

#### Consequências

**Boas** — registro sem dependência de CameraX; ponto único de tradução; falha de bind com
recuperação definida.
**Ruins** — uma camada de tradução a manter; enum de use case do app pode divergir do que o
CameraX oferece se alguém esquecer de estendê-lo.

#### Fontes consultadas

- [Introducing CameraX 1.5 — `SessionConfig`](https://developer.android.com/blog/posts/introducing-camera-x-powerful-video-recording-and-pro-level-image-capture) — a forma futura do bind é declarativa, o que valida isolar a tradução.
- Fonte primária: [CameraManager.kt:406-490](../../app/src/main/java/com/spacecamera/camera/CameraManager.kt#L406) — bind fixo atual.
- CLAUDE.md, armadilha 3 — *"`evt=bind` não prova imagem na tela"*: origem da regra de restaurar o conjunto anterior em caso de falha.

#### Tarefas afetadas

Tarefas 2, 6, 7

---

### ADR-003: Manter CameraX 1.4.0 nesta spec

**Status:** Aceito (decisão do usuário, Q4)
**Data:** 2026-08-05

#### Contexto e problema

O usuário perguntou se era possível ir para CameraX 1.6.1, a estável atual.

#### Decisão

**Manter 1.4.0.** A 1.5+ exige KGP 2.0.0 ou mais novo, e o projeto está em Kotlin 1.9.25 com
`composeCompiler = 1.5.15` amarrado — logo, subir CameraX arrasta migração de linguagem e do
plugin do Compose Compiler. A 1.6.0 ainda troca a stack inteira para CameraPipe e passa a usar
o muxer do Media3 por padrão no `VideoCapture`: mudanças por baixo do `bind`, exatamente onde
esta base já viu regressão. E o modo Pro **não precisa** de nada disso — ISO e obturador saem
por `Camera2CameraControl`, que já está em uso no arquivo para EIS e NR.

O compileSdk 36 e o minSdk 24 do projeto já atendem as duas linhas; **o bloqueio é só o
Kotlin**. Fica registrado como spec própria, na ordem: Kotlin 2.x + Compose Compiler →
CameraX 1.6.1 → câmera lenta e RAW.

#### Consequências

**Boas** — uma variável por vez na verificação em aparelho; Pro entrega sem dependência
externa; o contrato de ADR-002 já nasce compatível com `SessionConfig`.
**Ruins** — câmera lenta, time-lapse e RAW ficam bloqueados até a migração; a base fica mais
um ciclo em CameraX de setembro de 2024.

#### Fontes consultadas

- [CameraX — notas de release](https://developer.android.com/jetpack/androidx/releases/camera) — 1.5.0-beta01: *"Projects released with Kotlin 2.0 require KGP 2.0.0 or newer to be consumed"*; 1.6.0: *"CameraX now uses CameraPipe"*, *"integrates the Media3 Muxer by default within the VideoCapture API"*, *"Remove requirement for compileSdk 37"*; 1.5.0-alpha05: compileSdk 35; 1.5.0-rc01: minSdk 23.
- Fonte primária: [libs.versions.toml:25-27](../../gradle/libs.versions.toml#L25-L27) — AGP 8.13.2, Kotlin 1.9.25, composeCompiler 1.5.15.

#### Tarefas afetadas

None (no implementation tasks) — decisão de não fazer; o registro em ROADMAP.md é o
encaminhamento.

---

### ADR-004: Personalização em Configurações, persistida por identificador estável

**Status:** Aceito (decisão do usuário, Q6, ajustada pelo consenso do Gate 1)
**Data:** 2026-08-05

#### Contexto e problema

O usuário quer o que os apps conhecidos fazem: mover modos entre a gaveta e o plano e escolher
os que aparecem. A entrada natural seria arrastar no próprio seletor — mas essa área já
disputa toque para focar e arraste de exposição, e a pinça está prevista no ROADMAP.md.

#### Decisão

Personalização na **tela de Configurações** (ordem + visibilidade + restaurar padrão),
**com atalho a partir da gaveta** (FR-17, exigido pelo consenso: sem ele ninguém descobre a
função). Arrastar no seletor fica como fatia futura, reusando o mesmo registro e a mesma
persistência.

Persistência **por identificador estável**, não por `ordinal`: a lista de modos é aberta e
reordenável, e `ordinal` quebraria a preferência a cada modo inserido no meio. Quatro casos de
compatibilidade tratados no NFR-8 e no fluxo 4.5 do design — com destaque para o modo
**conhecido e indisponível**, que não aparece e **não** é apagado da preferência.

#### Consequências

**Boas** — sem quarto competidor de gesto; personalização testável na JVM sem câmera;
preferência sobrevive a troca de aparelho e a modos futuros.
**Ruins** — dois toques mais longe do que nos apps de referência; a fatia do arrastar fica
devendo.

#### Fontes consultadas

Decision based on team experience (no external source) — a restrição vem da contagem de
gestos já ligados na pré-visualização desta base ([CameraScreen.kt:880-960](../../app/src/main/java/com/spacecamera/presentation/screens/CameraScreen.kt#L880))
e do ROADMAP.md A2, que reserva a pinça para a mesma área. Sem equivalente público
aplicável.

#### Tarefas afetadas

Tarefas 8, 10

---

### ADR-005: Consentimento de opt-in por `androidx.annotation.OptIn`

**Status:** Aceito
**Data:** 2026-08-05

#### Contexto e problema

`CameraManager.kt` tem `@OptIn(ExperimentalCamera2Interop::class)` no topo, e ainda assim o
lint acusa 31 `UnsafeOptInUsageError` e o compilador avisa que a anotação não é marcador de
opt-in. O modo Pro é inteiramente Camera2Interop — entrar nesse arquivo com o consentimento
inerte é construir sobre aviso ignorado.

#### Decisão

Trocar por **`@androidx.annotation.OptIn(markerClass = ExperimentalCamera2Interop::class)`**.
Causa verificada em fonte primária: `ExperimentalCamera2Interop` é anotação **Java** marcada
com `androidx.annotation.RequiresOptIn`, e o `@OptIn` do Kotlin só reconhece marcadores
anotados com `kotlin.RequiresOptIn`. Depois da troca, reencolher o baseline de lint em vez de
regenerá-lo.

#### Consequências

**Boas** — consentimento passa a valer de fato; 31 achados saem do baseline; o Pro entra em
arquivo sem aviso pendente.
**Ruins** — pode exigir `androidx.annotation:annotation-experimental` explícito no
`build.gradle.kts` se não vier transitivamente.

#### Fontes consultadas

- Fonte primária: `javap -v` sobre `camera-camera2-1.4.0.aar!/classes.jar` →
  `RuntimeInvisibleAnnotations: androidx.annotation.RequiresOptIn`,
  `SourceFile: "ExperimentalCamera2Interop.java"`, `RetentionPolicy.CLASS`.
- [Opt-in em APIs que exigem consentimento](https://developer.android.com/build/dependencies#opt-in-requires) — uso de `androidx.annotation.OptIn(markerClass = …)`.

#### Tarefas afetadas

Tarefas 3, 11

---

### ADR-006: Rede de segurança com Compose na JVM sob Robolectric

**Status:** Aceito (decisão do usuário, Q7) — **revisado** em 2026-08-05 após ADR-001 híbrido
**Data:** 2026-08-05

> **Revisão (apontada pelo usuário).** Com a forma híbrida do ADR-001, cada modo é objeto
> instanciável, então a pirâmide desce um nível: o que era teste de UI passa a ser teste de
> unidade. Divisão final:
>
> | Verificação | Tipo de teste |
> |---|---|
> | use cases declarados por modo; capacidade exigida | **JVM pura** |
> | ação do disparador e comportamento do flash por modo | **JVM pura** |
> | gate de capacidade filtrando o registro | **JVM pura** |
> | completude do mapeamento `OverlayId`/`ControlId` → composable | **JVM pura** (compara chaves do mapa, sem invocar composable) |
> | persistência e os 4 casos de compatibilidade (NFR-8) | Robolectric, sem Compose |
> | seletor com plano e gaveta (FR-6, FR-17) | Robolectric + Compose |
> | tela de personalização (FR-7, FR-16) | Robolectric + Compose |
> | janela larga com N modos (FR-14) | Robolectric + Compose |
>
> Três superfícies com Compose em vez de dez. Isso acelera a suíte (teto de 90 s do NFR-5) e
> reduz a exposição ao `AppNotIdleException`, que é justamente o risco de "muitos testes de
> Compose no mesmo módulo" registrado abaixo. `compose-ui-test-junit4` como
> `testImplementation` continua necessário — só é usado por menos testes.

#### Contexto e problema

A spec recorta uma tela de 2.220 linhas prometendo não mudar comportamento, e hoje **não
existe um único teste de UI** no projeto.

#### Decisão

Testes de Compose rodando na **JVM sob Robolectric**, com asserções de **árvore semântica**
(nó existe, rótulo, clique) — nunca de pixel. Verificado contra a configuração real:
Robolectric 4.13 ✓ (mínimo 4.7.3), `isIncludeAndroidResources = true` já presente ✓,
`compose-ui-test-manifest` já em `debugImplementation` ✓; falta acrescentar
`compose-ui-test-junit4` como `testImplementation` — hoje está só em
`androidTestImplementation`. Configuração de sdk segue o padrão `@Config(sdk = [34])` já usado
nos testes Robolectric existentes, sem introduzir `robolectric.properties`.

#### Consequências

**Boas** — regressão de seletor, gate e persistência falha no `testDebugUnitTest`, sem
aparelho; encaixa no fluxo que já existe.
**Ruins** — não cobre "tem imagem na tela" (isso segue sendo luminância de `screencap` em
aparelho); riscos conhecidos da combinação: `AppNotIdleException` com muitos testes de Compose
no módulo e `captureToImage` estourando tempo com gráficos nativos — evitados por não fazer
asserção de pixel.

#### Fontes consultadas

- [Robolectric strategies](https://developer.android.com/training/testing/local-tests/robolectric)
- [Test your Compose layout](https://developer.android.com/develop/ui/compose/testing) — `createComposeRule()`, asserções semânticas.
- [robolectric#7055](https://github.com/robolectric/robolectric/issues/7055) — `AppNotIdleException` com muitos testes de Compose.
- [robolectric#8071](https://github.com/robolectric/robolectric/issues/8071) — `captureToImage` e gráficos nativos.
- Fonte primária: [app/build.gradle.kts:83-90, 182, 186](../../app/build.gradle.kts#L83) — configuração atual.

#### Tarefas afetadas

Tarefas 2, 4, 8, 10

---

### ADR-007: Modo Pro — foto, ISO e obturador, ausente quando não suportado

**Status:** Aceito (decisão do usuário, Q5 + premissa P1)
**Data:** 2026-08-05

#### Contexto e problema

O Pro é a prova de que a costura funciona. Precisa ser real o bastante para exercitar as
quatro costuras e pequeno o bastante para não virar outra spec.

#### Decisão

Pro é **modo de foto**: liga `Preview` + `ImageCapture` (sem `VideoCapture` — o que já
exercita o bind por modo), com **ISO** e **obturador** manuais, faixas vindas do HAL, aplicados
por `Camera2CameraControl.setCaptureRequestOptions` com `CONTROL_AE_MODE = OFF`. Fora:
balanço de branco, foco manual, RAW e controles durante gravação.

Quando o aparelho **não reporta `MANUAL_SENSOR`**, o modo **não existe** em nenhuma
superfície — nem no plano, nem na gaveta, nem na personalização. Segue a convenção do
CLAUDE.md: se não veio em `evt=caps`, não aparece na tela. Alternativa rejeitada: exibir
desabilitado com aviso, que cria estado morto e contradiz o resto do app.

**Aplicar por `Camera2CameraControl`, nunca por `Camera2Interop.Extender`** no builder: o
comentário em [CameraManager.kt:416-421](../../app/src/main/java/com/spacecamera/camera/CameraManager.kt#L416)
registra que o `Extender` tem prioridade no merge do `CaptureRequest` e congela o valor na
sessão — foi o que já quebrou EIS e NR nesta base.

#### Consequências

**Boas** — exercita as quatro costuras com um modo que não liga `VideoCapture`; zero
dependência de CameraX novo; reusa o caminho de mídia e EXIF do modo Foto.
**Ruins** — sem `MANUAL_SENSOR` no emulador, a verificação do Pro **só existe em aparelho
real**; usuário de aparelho básico não vê o modo.

#### Fontes consultadas

- Fonte primária: `javap` sobre `android.jar` da plataforma 36 — `REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR`, `SENSOR_INFO_SENSITIVITY_RANGE` (`Range<Integer>`), `SENSOR_INFO_EXPOSURE_TIME_RANGE` (`Range<Long>`), `SENSOR_MAX_ANALOG_SENSITIVITY`, `CONTROL_AE_MODE_OFF`, `SENSOR_SENSITIVITY`, `SENSOR_EXPOSURE_TIME`.
- [Camera2 — CaptureRequest.SENSOR_SENSITIVITY](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#SENSOR_SENSITIVITY)
- Fonte primária: `applyEisNrImmediate()` em [CameraManager.kt:591](../../app/src/main/java/com/spacecamera/camera/CameraManager.kt#L591) — precedente de aplicação por `Camera2CameraControl` neste código.

#### Tarefas afetadas

Tarefas 5, 11, 12

---

### Validação por consenso — arquitetura

**Data:** 2026-08-05

**Pragmático (8/10):** aprovou B com a ressalva de que a indireção identificador → composable
só se paga com o teste de completude do registro; sem ele, o desenho troca erro de compilação
por erro de execução, o que numa câmera significa tela quebrada na mão do usuário. Aprovou
ADR-003 sem reservas: *"três fontes de regressão no mesmo diff é como se perdem duas rodadas
de verificação"*.
**Perfeccionista (9/10):** validou a sensibilidade — B só perde se o critério que motiva a
spec for zerado. Cobrou o fluxo de falha de bind (4.2 do design) como parte do ADR-002 e não
como detalhe de implementação, porque "restaura o conjunto anterior" é decisão arquitetural
com precedente ruim nesta base. Atendido.
**Advogado (8/10):** o Pro mínimo entrega valor visível (ISO e obturador funcionando), e não
apenas estrutura. Insistiu que a ausência do modo em aparelho sem `MANUAL_SENSOR` precisa ser
silenciosa, não um erro — atendido pela premissa P1.

*Divergência:* o Perfeccionista queria a tradução de use cases (ADR-002) com verificação em
tempo de compilação; Pragmático apontou que isso exigiria genéricos que o Kotlin 1.9 resolve
mal aqui. Resolvido: verificação em teste, não em tipo.
*Conflitos não resolvidos:* nenhum.

**Ações bloqueantes:** nenhuma pendente.
**Não bloqueantes:** o teste de completude do registro é obrigatório na primeira fatia, não na
última (vira AC da Tarefa 2).

### Confiança ao fechar o Gate 3: 87%

---

## Gate 4 — Tarefas

13 tarefas, fatia vertical **por modo**. Nenhuma `XL` — o ritual do §9.0.4 não foi
necessário. Maior tamanho: `M`.

### Análise de paralelização

- **Método:** script `analyze-dependencies.js` (13 tarefas ≥ limiar de 10 do §2.1)
- **Ondas geradas:** 8 (derivadas das dependências, não do agrupamento por propósito)
- **Caminho crítico:** 2 → 5 → 6 → 7 → 8 → 11 → 12 → 13 — 8 das 13 tarefas
- **Makespan:** 20 unidades de peso com paralelismo ilimitado, contra 26 sequencial (~23% de
  economia teórica, quase toda na Onda 1)
- **Clusters independentes:** 1 — não há frente desenvolvível isolada do resto
- **Hash de dependências:** `45dee818a444f5259ebd588b0efdd5010990bd63fb9016579c9eb87bd867d747`

**A análise corrigiu um erro de decomposição meu.** Na primeira rodada, o script colocou a
Tarefa 10 (personalização) na Onda 1, porque suas dependências declaradas eram só o contrato
(2) e a infra de teste (4). Só que o critério AC-7.2 dessa tarefa é *"reordenar reflete no
seletor"*, e o seletor nasce na Tarefa 8 — a tarefa estava declarando menos dependência do que
realmente tem. Uma fatia cuja capacidade visível não pode ser verificada não é fatia vertical,
é fatia horizontal disfarçada. Duas saídas eram possíveis: mover o AC para a Tarefa 8 (o que
deixaria a 10 como camada de armazenamento sem valor observável — justamente o antipadrão do
§9.0.1) ou declarar a dependência de verdade. Escolhida a segunda: `Dependencies: Task 2,
Task 4, Task 8`. Custo: a 10 sai da Onda 1 e vai para a Onda 6, e o paralelismo cai. Ganho: a
tarefa continua sendo uma capacidade demonstrável.

**Sobre a serialidade das Ondas 3 e 4:** remigrar Vídeo e Foto ao mesmo tempo economizaria 4
unidades de makespan e custaria a capacidade de saber **qual das duas** quebrou o
iso-comportamento — que é o valor inteiro da rede de segurança. A serialidade aqui é escolha,
não acidente, e está registrada no arquivo para não ser "otimizada" depois.

### Validação de rastreabilidade 3-vias

Conferida por script sobre os artefatos, nos dois sentidos:

| Verificação | Resultado |
|---|---|
| Tarefas com `_Requirements:_` | 13/13 |
| Tarefas com `_Decisions:_` quando um ADR as molda | 12/13 (a Tarefa 1 é medição, nenhum ADR a molda — campo corretamente omitido) |
| Requisitos declarados cobertos por ao menos uma tarefa | **25/25**, nenhum órfão |
| `## Tarefas afetadas` de cada ADR × tarefas que o declaram | **7/7 consistentes** nos dois sentidos |
| Tarefas `Size: XL` ou `borderline` | 0 |

### Validação por consenso — tasks.md

**Data:** 2026-08-05

**Perspectiva 1 — O Pragmático**
*Forças:* toda tarefa tem comando de verificação executável, não "testar manualmente"; as
armadilhas do CLAUDE.md entraram como passo de verificação (conferência de dex, `worktree` em
vez de `stash`, luminância em vez de `evt=bind`) em vez de ficarem como conhecimento tribal.
*Preocupações:* 8 ondas significam 8 checkpoints para um desenvolvedor só — o valor aqui é o
ponto de parada com medição, não o paralelismo, que praticamente não existe (um cluster, 8 de
13 no caminho crítico). Chamar isso de "paralelização" seria propaganda enganosa.
*Recomendações:* registrar explicitamente que a estrutura de ondas serve de cadência de
verificação, e não de plano de alocação de pessoas.
*Impacto na confiança:* +2%

**Perspectiva 2 — O Perfeccionista**
*Forças:* 25/25 requisitos com tarefa; ADRs sincronizados nos dois sentidos; nenhum `XL`; cada
NFR com medida tem tarefa que a mede.
*Preocupações:* **NFR-3 exige zero linha líquida em `CameraManager.kt`**, mas as duas maiores
adições da spec — o tradutor de use cases (Tarefa 6) e a aplicação dos controles manuais
(Tarefa 11) — cairiam naturalmente dentro dele. Nenhuma tarefa dizia onde esse código mora, e
a violação só apareceria na Tarefa 13, quando corrigir custa mais caro. Além disso, o NFR só
era medido no fim.
*Recomendações:* nomear os arquivos de destino nos passos das Tarefas 6 e 11, e medir `wc -l`
já nos checkpoints das Ondas 3 e 4 como aviso antecipado.
*Impacto na confiança:* +3%

**Perspectiva 3 — O Advogado**
*Forças:* a Tarefa 11 entrega ISO funcionando de verdade, não um modo vazio de demonstração.
*Preocupações:* nada de visível para o usuário chega antes da Onda 5. As Ondas 1 a 4 são
contrato, sondagem e duas remigrações que, por definição de sucesso, **não mudam nada** na
tela. Em projeto pessoal isso é risco de fôlego, não só de cronograma.
*Recomendações:* deixar registrado que a primeira mudança visível é a Tarefa 8, e que as ondas
anteriores são o preço da rede de segurança — não gordura que dê para cortar.
*Impacto na confiança:* +1%

**Análise de divergência**
*Acordo:* decomposição, rastreabilidade, tamanhos e a serialidade deliberada das Ondas 3 e 4.
*Divergência:* o Advogado queria antecipar algo visível; Pragmático e Perfeccionista
sustentaram que qualquer antecipação implicaria mexer na UI antes de a remigração estar
verificada — exatamente o que a spec existe para evitar. Resolvido a favor da rede de
segurança, com o custo registrado em vez de escondido.
*Conflitos não resolvidos:* nenhum.

**Placar**
- Pragmático: 8/10 · Perfeccionista: 9/10 · Advogado: 8/10 · **Média: 8,3/10**

**Ações — bloqueantes (aplicadas antes de fechar o gate)**
1. ✅ Dependência real declarada na Tarefa 10 (`Task 2, Task 4, Task 8`) e análise refeita.
2. ✅ Passo 1 da Tarefa 6 agora nomeia `ModeBinder` como arquivo próprio, com o motivo
   (NFR-3).
3. ✅ Passo 4 da Tarefa 11 agora nomeia `ManualExposureControls` como arquivo próprio.
4. ✅ Aviso antecipado de NFR-3 acrescentado aos checkpoints das Ondas 3 e 4.

**Ações — não bloqueantes**
1. Registrado: a estrutura de ondas é cadência de verificação, não plano de alocação.
2. Registrado: a primeira mudança visível ao usuário é a Tarefa 8.

**Status final:** APROVADO COM MUDANÇAS (as quatro aplicadas)

### Confiança ao fechar o Gate 4: 91%

---

## Gate 5 — Go/No-Go

### Consistência entre artefatos

| Verificação | Resultado |
|---|---|
| `requirements.md` ↔ `tasks.md` | 25/25 requisitos com ao menos uma tarefa; nenhuma tarefa sem requisito |
| `decisions.md` ↔ `tasks.md` | 7/7 ADRs com `## Tarefas afetadas` consistente nos dois sentidos |
| `design.md` ↔ `requirements.md` | seção 6 do design mapeia cada requisito ao componente que o realiza |
| `design.md` ↔ `decisions.md` | forma do contrato no design reflete o ADR-001 híbrido aprovado, incluindo os guarda-corpos |
| Critérios de aceite referenciados nas tarefas | todos os 18 AC de `requirements.md` aparecem em alguma tarefa |
| Diagramas Mermaid | 8 diagramas, conferência estrutural (sem CLI de Mermaid disponível — registrado no Gate 2) |
| Tarefas `XL` | nenhuma |

### Trajetória de confiança

| Gate | Confiança | Monotônica? |
|---|---:|---|
| 0 — Pesquisa | 76% | — |
| 1 — Requisitos | 82% | ✅ |
| 2 — Contexto | 84% | ✅ |
| 3 — Arquitetura | 87% | ✅ |
| 4 — Tarefas | 91% | ✅ |
| 5 — Go/No-Go | **92%** | ✅ |

Sem regressão em nenhum gate — a regra de monotonicidade do §13.1 não foi acionada.

### De onde vêm os 92%

O Gate 5 é gate de **confirmação**, não de descoberta: os +1% vêm da consistência entre
artefatos conferida por script, da revisão final de bloqueios (nenhum) e da inicialização do
`agent-progress.md`. Nenhum artefato foi retrabalhado aqui.

Sustentação: problema medido em números (21 pontos de ramificação, bind cego ao modo, premissa
de dois modos no seletor vertical); sete decisões arquiteturais registradas, cinco delas com
fonte primária verificada localmente; 25 requisitos com medida; 13 tarefas com comando de
verificação executável; rastreabilidade conferida por script nos dois sentidos.

**O que ainda não dá para saber daqui**, e que a execução vai revelar:

1. **O tamanho real do recorte da `CameraScreen`.** 2.220 linhas com estado local compartilhado
   entre overlays; a spec assume que seletor e gaveta saem limpos. Se o acoplamento for maior,
   a Tarefa 8 cresce.
2. **Se `Preview` + `ImageCapture` sem `VideoCapture` se comporta igual no caminho de foto.**
   O processamento de bitmap e o EXIF nunca rodaram sem o `VideoCapture` ligado na mesma
   sessão.
3. **Se o aparelho de teste reporta `MANUAL_SENSOR`.** Sem isso o Pro é inverificável, e o
   Checkpoint da Onda 2 existe para descobrir isso quatro ondas antes de doer.

Nenhum dos três é resolvível por mais planejamento — os três são medições que exigem código
rodando em aparelho, e cada um tem checkpoint que os pega cedo. É por isso que 92% e não mais:
o que falta de certeza não está no plano, está no aparelho.

### Decisão: **GO**

Especificação completa e pronta para construir. Implementação é responsabilidade da skill
`dev`, começando pela Tarefa 1 — que é medição, e precisa ser feita **antes** de qualquer
alteração de código, senão NFR-1, NFR-3 e NFR-4 ficam sem baseline contra o qual comparar.

---

## Fase de implementação — questões levantadas na execução

### Q-01: FR-4 fala em quatro proporções de foto; o app oferece três

**Levantada por:** Tarefa 1 (baseline), ao capturar uma foto em cada proporção.
**Status:** **resolvida** — usuário escolheu a saída A em 2026-08-05.
**Afeta:** FR-4, AC-4.1, Tarefa 7, NFR-1

FR-4 diz que a Foto tem "proporção conforme a seleção do usuário (9:16, 3:4, 1:1, Full)", e
a verificação da Tarefa 7 manda "capturar foto nas **quatro** proporções". Mas
[CameraScreen.kt:718](../../app/src/main/java/com/spacecamera/presentation/screens/CameraScreen.kt#L718)
declara `photoRatios = listOf("Full", "9:16", "3:4")` — **três**. Confirmado em aparelho: o
ciclo é Full → 9:16 → 3:4 → Full.

O `"1:1"` existe num ramo de
[CameraManager.kt:222](../../app/src/main/java/com/spacecamera/camera/CameraManager.kt#L222)
(`"3:4", "1:1" -> RATIO_4_3`), mas **nenhuma UI o alcança**. É código morto, provavelmente
resto de uma versão anterior.

Como está, a verificação da Tarefa 7 é inexequível, e o risco é pior que isso: quem for
implementar pode **acrescentar** o 1:1 para satisfazer o requisito — e aí a remigração
muda comportamento, que é o oposto do que NFR-1 exige da Tarefa 7.

Três saídas:

| Saída | Efeito |
|---|---|
| **A — corrigir o requisito** (recomendada) | FR-4 e a Tarefa 7 passam a dizer três proporções; a remigração fica iso-comportamento de verdade. O 1:1 vira item de ROADMAP. |
| B — implementar o 1:1 nesta spec | Amplia o escopo e quebra o iso-comportamento da Tarefa 7 de propósito. Precisa de AC próprio. |
| C — implementar o 1:1 em spec separada | Mantém esta spec limpa; o ramo morto do `CameraManager` fica esperando. |

#### Decisão: A — corrigir o requisito

O usuário escolheu corrigir a redação. Aplicado:

- **FR-4** passa a listar três proporções (9:16, 3:4, Full), com nota explicando a correção.
- **Tarefa 7** verifica três proporções, e passa a comparar contra as dimensões do baseline
  (4096×2304, 4096×3072, 1840×4096) em vez de só "conferir a mídia salva".
- O **1:1** vira item de ROADMAP, junto do ramo morto de `cameraXAspectRatio()` que hoje o
  mapeia para `RATIO_4_3` sem ninguém chamar.

*Desvio de processo registrado:* a skill `dev` não edita `requirements.md` — quem faz isso é
a skill `spec`. A edição foi feita aqui porque é correção factual decidida explicitamente
pelo usuário, e deixá-la pendente manteria a Tarefa 7 inexequível. Fica anotado para quem
reabrir a spec.

### Q-02: o método de verificação do NFR-1 precisa de mais que `evt=bind`

**Levantada por:** Tarefa 1 (baseline).
**Status:** resolvida — método emendado e registrado em `baseline/README.md`.
**Afeta:** NFR-1, Tarefas 6, 7 e 13

O NFR-1 manda comparar "os campos de `evt=bind` e `evt=caps`" entre os dois commits. A
medição mostrou que isso é necessário mas **insuficiente**, por duas razões concretas:

1. **O único campo que distingue Vídeo de Foto é o `eis`.** Os outros oito são iguais nos
   dois modos.
2. **`aspect` é a proporção da caixa de pré-visualização, não a de captura** — vem de
   `previewAspectLabel`, que a UI define conforme a janela
   ([CameraManager.kt:548-550](../../app/src/main/java/com/spacecamera/camera/CameraManager.kt#L548), herdado da
   spec do layout adaptativo). Ou seja, **AC-3.1 não é observável em `evt=bind`**.

Emenda ao método, sem mexer no requisito: a comparação de iso-comportamento passa a incluir
as **dimensões da mídia salva** (cabeçalho JPEG e `MediaStore`), que estão no baseline. Foi
com esse instrumento que se confirmou o essencial de AC-3.1 — vídeo em 1920×1080 (16:9) com
a foto configurada em "Full".

`evt=photo size=` não serve: no caminho direto reporta o alvo do preset, e o MAXIMA não tem
alvo, então sai `0x0`.

### Q-03: o anel do logcat descarta a telemetria antes da leitura

**Levantada por:** Tarefa 1 (baseline).
**Status:** resolvida — `adb logcat -G 16M` mais captura ao vivo.
**Afeta:** método de verificação de todas as tarefas com conferência em aparelho

O anel `main` do logcat tem 256 KiB e, com a câmera aberta, o `camerahalserver` o mantém
saturado. As linhas do app são expulsas em segundos: `adb logcat -d` depois do fato
reportou 3 e 4 binds onde tinham ocorrido 10.

Diagnostiquei isso errado duas vezes antes de rodar `adb logcat -g` — e as duas vezes a
conclusão errada era plausível ("o app parou de rebindar"). Como o NFR-1 se verifica
comparando `evt=bind` entre dois commits, isto atinge o instrumento central da spec.

Fica como candidato a entrar no CLAUDE.md junto das outras armadilhas, na Tarefa 13.

### Q-05: `abstract class` em vez de `sealed class` no contrato de modo

**Levantada por:** Tarefa 2, ao escrever o teste do gate de capacidade.
**Status:** resolvida — desvio aplicado e justificado.
**Afeta:** ADR-001, design §3, Tarefa 2

O design §3 e o ADR-001 especificam `sealed class CameraModeDefinition`. Na
implementação, isso impediu o teste do FR-5: o Kotlin proíbe herdar de classe selada a
partir de outro módulo de compilação, e `src/test` é outro módulo. Sem poder declarar um
modo de mentira que exija capacidade, o **caso negativo do gate ficaria sem teste** até a
Tarefa 11 — quatro ondas depois de a lógica existir, e justamente o caso que o AC da
Tarefa 2 cobra.

Três saídas foram consideradas: declarar um modo de teste no código de produção (que
então é empacotado no APK), esperar o Pro da Tarefa 11 (deixando FR-5 sem rede até lá), ou
trocar `sealed` por `abstract`.

**Escolhida a terceira**, porque o que `sealed` oferece aqui é nada:

- O que `sealed` dá é `when` exaustivo sobre os modos. É **exatamente esse `when` que a
  spec existe para eliminar** — o desenho todo é iterar o registro em vez de ramificar por
  modo. Usar a exaustividade seria reintroduzir o problema.
- Fechar a hierarquia contra outros módulos não protege nada: o projeto é módulo único, e
  todo modo mora em `:app`.

E o que o ADR-001 realmente defende continua intacto, porque vem de `abstract`, não de
`sealed`: membro abstrato obrigando cada modo a responder sobre disparador e flash, e
migração futura que **quebra o build em todos os modos** em vez de compilar calada.

Os quatro guarda-corpos seguem valendo, e dois deles agora têm teste por reflexão: nenhum
campo mutável, e nenhum membro de tipo `androidx.camera` ou do controller.

*Para quem reabrir a spec:* vale corrigir a palavra em `design.md` §3 e no ADR-001. Não
mexi porque `design.md` é domínio da skill `spec`, e o desvio está documentado no KDoc da
classe e aqui.

### Q-04: divergência de nome do campo de latência

**Status:** registrada, sem ação.

A spec fala em `latency_ms` (Tarefa 1 e NFR-4); a telemetria emite **`elapsed_ms`**
([CameraTelemetry.kt:40](../../app/src/main/java/com/spacecamera/camera/CameraTelemetry.kt#L40)).
O baseline mediu `elapsed_ms`. Se a FR-13 introduzir `evt=mode` com um campo de latência,
vale usar o mesmo nome já existente em vez de criar um terceiro.

### Q-06: o `aspect` de `evt=bind` sai com a proporção **anterior**

**Levantada por:** baseline novo no Redmi Note 10 (2026-10-07), antes da Tarefa 6.
**Status:** registrada — **não corrigir dentro da Tarefa 6**; ver encaminhamento.
**Afeta:** NFR-1 (é um dos campos comparados), Tarefas 6, 7 e 13

Com os toques localizados por rótulo, a defasagem é sistemática:

| Toque na proporção | `evt=bind aspect=` | Proporção real (`takePhoto` e mídia salva) |
|---|---|---|
| 9:16 → 3:4 | `9:16` | 3:4 — 3264×2448 |
| 3:4 → Full | `3:4` | Full — 1832×3840 |
| Full → 9:16 | `Full` | 9:16 — 3840×2160 |

E ao trocar Foto (em Full) → Vídeo, o bind do Vídeo reporta `aspect=Full`.

**Mecanismo.** `CameraManager.setAspectRatio()` chama `bindCameraUseCases()` na hora, e
o bind registra `previewAspectLabel`. Mas quem atualiza esse rótulo é um
`LaunchedEffect(previewAspectLabel)` em `CameraScreen.kt`, que só roda **depois** da
recomposição — ou seja, depois do bind. A captura está certa (a mídia prova); só a
telemetria mente, e sempre por um passo.

**Por que importa para esta spec.** É o instrumento do NFR-1. Se a Tarefa 6 mudar a ordem
entre "trocar o modo/proporção" e "o rótulo chegar ao controller", o campo passa a sair
certo — e a comparação acusa uma "regressão" que na verdade é correção. Ou o inverso.

**Leitura de uma suspeita antiga.** O `baseline/README.md` de agosto registrou como erro
de medição meu a hipótese de "`selectedAspectRatio` defasado do bind". O que estava errado
ali era a coordenada fixa; a defasagem do **campo de telemetria** existe e é reprodutível.

**Encaminhamento.** Para o NFR-1, `aspect` é comparado como **sequência observada** no
mesmo roteiro, não como valor esperado por proporção. Corrigir (logar o rótulo no momento
em que ele chega ao controller, ou passar a proporção junto com o pedido de bind) é
mudança de comportamento observável da telemetria e fica fora do iso-comportamento das
Tarefas 6 e 7 — candidata a commit próprio depois da Tarefa 7, ou ao ROADMAP.

### Q-07: decisões tomadas na Tarefa 6

**Status:** registradas, nenhuma bloqueante.

1. **"Aviso discreto" da recusa de modo fica para a Tarefa 11.** O design §4.2 manda o
   modo voltar ao anterior **e** mostrar um aviso. A volta foi implementada (controller
   religa, `ModeSession` avisa, ViewModel reverte seletor e EIS — com teste). O aviso
   visual não: Vídeo e Foto declaram o mesmo conjunto de use cases, então nenhuma troca
   entre eles pode ser recusada por combinação. O primeiro modo que pode é o Pro — e é
   na Tarefa 11 que o aviso tem como ser visto e verificado.
2. **`CameraMode` ganhou `definition` como ponte.** O seletor e a tela ainda falam
   `CameraMode` (enum); o ViewModel passa `mode.definition` ao controller. A ponte some
   quando o seletor passar a ler o registro (Tarefa 8).
3. **A regra "vídeo é sempre 16:9" ficou como `modes.current === VideoMode`** em
   `cameraXAspectRatio()`. Comparar identidade de modo é exatamente o tipo de ramificação
   que a spec elimina — mas levar a regra de proporção para a definição é o passo 3 da
   Tarefa 7, e antecipar seria fazer a 7 dentro da 6.
4. **Uma linha do baseline de detekt foi reescrita, não regenerada.** A entrada
   `LongParameterList` de `CameraTelemetry.bind` é identificada pela assinatura, que
   mudou de `mode: CameraMode` para `mode: CameraModeId`. É a mesma dívida, com a mesma
   contagem de parâmetros; regenerar o baseline inteiro é o que o CLAUDE.md proíbe,
   porque esconderia achado novo. Os achados novos de verdade foram corrigidos:
   `TooManyFunctions` (formatadores puros saíram para `TelemetryFields`) e `SpreadOperator`
   (suprimido no ponto, com motivo: a API do `bindToLifecycle` só aceita vararg).
5. **A sondagem de EIS/HDR saiu do `CameraManager`** para `SensorCharacteristicsReader.kt`,
   sem mudar a regra — necessário para o NFR-3 e conferido pelo `evt=caps` idêntico.

### Q-08: modo não é reaplicado quando o controller é recriado

**Levantada por:** Tarefa 6, lendo o código. **Não verificada em aparelho.**
**Status:** aberta — candidata a correção própria depois da Tarefa 7.

Quando a Activity é recriada com outro `LifecycleOwner` (rotação em janela larga), o
ViewModel cria um controller novo, que nasce em `VideoMode`. O modo do ViewModel só
chega ao controller por `setCameraMode` — nunca na criação, nem antes nem depois desta
spec. Se o usuário estiver em Foto 3:4 e a Activity for recriada, a sessão provavelmente
liga com a proporção do vídeo (16:9) enquanto a tela mostra Foto.

Não corrigido aqui de propósito: mudaria comportamento observável numa tarefa cujo
critério é iso-comportamento. Para verificar: em janela larga, Foto 3:4, girar, fotografar
e ler a dimensão no MediaStore.

### Q-09: o que a definição de modo passou a declarar na Tarefa 7

**Status:** decidida na implementação — contrato estendido, nenhuma decisão do usuário
pendente.
**Afeta:** ADR-001 (forma do contrato), design §3, Tarefas 7, 8, 11

Para zerar as ramificações por modo (AC da Tarefa 7), a definição ganhou quatro membros
abstratos, todos **dados de escolha fechada**, como o ADR-001 prevê:

| Membro | Vídeo | Foto | Substitui |
|---|---|---|---|
| `aspectRatio: AspectRatioRule` | `FIXED_16_9` | `USER_SELECTED` | proporção na tela e em `cameraXAspectRatio()` |
| `stabilization: StabilizationRule` | `FOLLOWS_PREFERENCE` | `OFF` | `if PHOTO / else VIDEO` do EIS no ViewModel |
| `output: CaptureOutput` | `VIDEO` | `PHOTO` | miniatura/pausa e desenho do disparador |
| `moreControls: List<ControlId>` | NR, HDR, Mic, Grade, Config. | Melhoria, HDR, Grade, Config. | os `if` da linha expandida |

`ControlId` ganhou os seis controles da linha expandida.

**Por que regras separadas e não só `output`.** Bastaria `output` para decidir tudo, mas
amarraria "foto" a "EIS desligado" e "proporção do usuário" para sempre. O Pro herda as
duas regras por escolha, não por ser foto; um modo de vídeo futuro sem EIS (time-lapse,
por exemplo) declara `OFF` sem precisar de um terceiro tipo de saída.

**A barra deixou de ser escrita à mão.** É montada a partir de `controls` e
`moreControls`, desenhada por um `when` exaustivo sobre `ControlId` — controle declarado
sem desenho **não compila**, o que é mais forte que o teste de completude da tabela
`ModeSurfaces` (que continua valendo para overlays).

**O ADR-001 se pagou aqui.** Os quatro membros novos quebraram o build em todos os
modos, inclusive nos modos de mentira dos testes — e cada um precisou responder, como o
ADR prometeu ("migração que quebra o build em vez de compilar calada").

*Para quem reabrir a spec:* o design §3 lista o contrato da Tarefa 2; estes quatro membros
são extensão dele e valem ser acrescentados à tabela.

### Q-10: decisões da Tarefa 8

**Status:** registradas; a 1 foi do usuário, as demais na implementação.

1. **Gaveta como item "Mais" no fim do carrossel** (decisão do usuário, 2026-10-07), sempre
   visível — mesmo com a gaveta vazia, porque é ali que mora o atalho da personalização
   (FR-17). Vazia, mostra "Todos os modos já estão no seletor."
2. **O carrossel passou a rolar.** Com o modo ativo centralizado, o "Mais" fica dois itens
   à direita (176dp) e sai da vista em telas de 360dp — o teste em janela de 320dp pegou.
   Rolar mantém todo item alcançável e reproduz a centralização de antes (posições de
   Vídeo e Foto idênticas no aparelho). Resolve também o retrato com N modos; a janela
   larga é a Tarefa 9.
3. **A gaveta abre dentro da coluna de controles**, acima do seletor, em vez de flutuar com
   deslocamento fixo — acompanha retrato e janela larga sem número mágico. Um véu
   transparente fecha ao tocar fora; o "voltar" do sistema também fecha.
4. **O enum `CameraMode` foi removido.** O ViewModel guarda a definição
   (`activeMode`/`selectMode`); o arranjo (`arrangedModes`) se refaz quando o controller
   publica as capacidades. O gesto de deslizar usa `ArrangedModes.step`, cíclico como o
   `next()` do enum.
5. **Testes anteriores à spec tocados — mecanicamente.** O NFR-1 pede "nenhuma asserção
   alterada". Duas classes precisaram mudar porque o tipo testado deixou de existir:
   - `CameraViewModelTest`: `setCameraMode(CameraMode.X)` → `selectMode(XMode)` e
     `cameraMode` → `activeMode`, troca de nome um a um (15 linhas, nenhuma asserção
     removida ou enfraquecida).
   - `PresetCyclingTest`: o ciclo de modos saiu de `CameraMode.next()/previous()` para
     `ArrangedModes.step`. Mesmos nomes de teste, mesmos valores esperados, mesmos riscos
     protegidos (volta no último item, índice negativo no primeiro).
   O comportamento verificado é o mesmo; a forma da chamada mudou porque a API mudou.
6. **O ViewModel recebe o registro por construtor** (padrão `ModeRegistry.all`), como os
   outros colaboradores. Sem isso, a mutação "arranjo ignora capacidades" sobrevivia: o
   registro de produção ainda não tem modo exigente.
7. **Agrupar o estado de modos fica para a Tarefa 10.** O ViewModel está em 752 linhas
   (+58 sobre o baseline). `activeMode`, `arrangedModes` e a preferência que chega na
   Tarefa 10 são estado novo, que a própria spec manda agrupar ("esta spec só introduz
   estado agrupado para o que é novo"). Fazer isso junto com a preferência evita agrupar
   duas vezes.

---

## Bloqueios

Nenhum que impeça as Tarefas 2, 3 e 4.

**A Q-01 bloqueia a Tarefa 7** (Foto remigrado) — precisa de decisão antes, e há quatro
ondas de folga até lá.
