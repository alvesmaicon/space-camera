# Decisões — Layout Adaptativo

## Progresso dos Gates

| Gate | Status | Confiança | Data |
|------|--------|-----------|------|
| 0 — Pesquisa | Concluído | 78% | 2026-08-03 |
| 1 — Requisitos | Concluído | 84% | 2026-08-03 |
| 2 — Contexto | Concluído | 86% | 2026-08-03 |
| 3 — Arquitetura | Concluído | 90% | 2026-08-03 |
| 4 — Tarefas | Concluído | 93% | 2026-08-03 |
| 5 — Go/No-Go | Concluído | 93% | 2026-08-03 |

---

## Gate 0 — Pesquisa

### Contexto do repositório (§5.1)

Levantado direto do código, não de documentação.

| Aspecto | Estado |
|---|---|
| Stack | Kotlin 1.9.25, Jetpack Compose 1.5.4, Material 3 1.1.2, CameraX 1.4.0 |
| Build | AGP 8.13.2, Gradle 8.14.3, compileSdk/targetSdk 36, minSdk 24 |
| Módulos | Único (`:app`) |
| Injeção de dependência | Nenhuma por framework — construtor e factories (`CameraControllerFactory`) |
| Estado | `CameraViewModel` com ~30 `StateFlow`; UI coleta com `collectAsState()` |
| Testes | 38 na JVM (JUnit + Robolectric + `FakeCameraController`) |
| Observabilidade | Timber com tag `SpaceCam`; `CameraTelemetry` com eventos `evt=` |

Arquivo central desta spec: `presentation/screens/CameraScreen.kt` (~1.950 linhas,
composable principal de ~1.100).

**Estrutura de layout atual** — um `Box(fillMaxSize)` com três âncoras:

| Elemento | Âncora | Container |
|---|---|---|
| Barra superior | `align(TopCenter)` + `fillMaxWidth()` | `Column` de `Row`s |
| Pré-visualização | centralizada, `previewSizeModifier` | `Box` com proporção calculada |
| Controles inferiores | `align(BottomCenter)` + `fillMaxWidth()` | `Column` |

Overlays (anel de foco, slider de exposição, contagem regressiva, nível de
horizonte, revisão de foto) são posicionados por **coordenada absoluta**, não por
âncora de layout.

**Mecanismo de rotação existente** — decisivo para o escopo:

- `rollDegrees` vem do acelerômetro (`atan2(x, y)`, filtro passa-baixa 0.7/0.3)
- `snappedIconRotation` trava em 0 / ±90 / 180 e anima em 300ms; cada ícone recebe
  `.rotate(iconRotation)`
- `snappedSurfaceRotation` vira `Surface.ROTATION_*` e alimenta o CameraX no início
  da gravação e na captura de foto — é o que define a orientação do **arquivo**

Ou seja: o app **já** trata rotação física mantendo o layout em retrato e girando
os ícones no lugar. Girando o telefone, a barra superior (fisicamente no topo do
aparelho) aparece na lateral da cena. O comportamento "controles na lateral" já
existe e não precisa ser criado.

### Enquadramento de escopo (§5.2)

**Q1 — Para onde vão os controles em paisagem?**
**A1:** O comportamento de controle na lateral já existe via rotação de ícones por
sensor. O que falta é adaptar ao layout paisagem *default* do tablet.

**Q2 — A quebra da `CameraScreen` entra nesta spec?**
**A2:** Não. Adaptar sobre o código atual.

**Q3 — Quais superfícies cobrir?**
**A3:** `CameraScreen` em paisagem/tablet; rotação de ícones e orientação da mídia
gravada; telas de Configurações e Sobre. **Dobráveis ficam fora.**

**Escopo de pesquisa:** detecção de janela larga em Compose; correção de
`targetRotation` do CameraX quando a orientação não está travada; escapatória
oficial da mudança do Android 16.

### Pesquisa externa (§5.3)

> **Ferramenta:** `perplexity_research` não está disponível nesta sessão. Substituída
> por `WebSearch` + `WebFetch`, com as mesmas regras de citação do §5.3.1.

#### Tópico: escapatória da restrição de orientação no Android 16

**Consulta:** propriedade de manifesto para recusar a mudança de orientação e
resizability do targetSdk 36 em telas ≥ 600dp.

**Achados:**
- A propriedade é `android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`,
  declarada em `<activity>` ou `<application>` com `android:value="true"`.
- Disponível apenas no targetSdk 36: *"The Android framework will eliminate the
  opt-out capability in API level 37."*
- **Não tem constante pública** no `android.jar` do SDK 36 (verificado com `javap`
  em `PackageManager` e `WindowManager`) — é string só de manifesto.

**Verificação empírica** (AVD Tablet_API36, 2560×1600 @320dpi = 800dp):

| Sem a propriedade | Com a propriedade |
|---|---|
| `mBounds=Rect(0, 0 - 2560, 1600)` | `mBounds=Rect(680, 0 - 1880, 1600)` |
| janela em paisagem cheia | `w600dp h800dp port` |
| layout de retrato esticado, quebrado | retrato pillarboxed, íntegro |

**Impacto no design:** a adaptação deixa de ser conserto urgente e vira trabalho
com prazo. O opt-out é ponte válida até o targetSdk 37.

**Correção de registro:** uma análise anterior desta sessão concluiu que "não há
opt-out". A conclusão foi tirada de um nome de propriedade inventado
(`..._RESTRICTED_ORIENTATION`) que não existe. O erro foi de nome, não de existência.

**Fontes:**
- [App orientation, aspect ratio, and resizability — Adaptive Apps](https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability) — *"The Android framework will eliminate the opt-out capability in API level 37."*
- [Behavior changes: Apps targeting Android 16 or higher](https://developer.android.com/about/versions/16/behavior-changes-16)
- Verificação local: `adb shell dumpsys activity activities` no AVD Tablet_API36, 2026-08-03

#### Tópico: `targetRotation` do CameraX com orientação destravada

**Consulta:** boa prática para `targetRotation` quando a activity não está travada
em retrato.

**Achados:**
- `Display.getRotation()` e `OrientationEventListener` são **complementares**.
  Com a orientação travada, `Display.getRotation()` fica fixo e só o listener
  detecta a mudança física.
- Com a orientação destravada, a janela gira junto e `Display.getRotation()` varia.

**Impacto no design:** o app deriva `snappedSurfaceRotation` **apenas** do
acelerômetro, o que era correto enquanto a janela era sempre retrato. Numa janela
que pode girar, as duas rotações se somam e o arquivo sai torto. Origem do FR-6.

**Fontes:**
- [CameraX use case rotations](https://developer.android.com/media/camera/camerax/orientation-rotation)
- [camerax-developers — Getting current device orientation for CameraX UI rotation](https://groups.google.com/a/android.com/g/camerax-developers/c/ujCOxdWqAro)

#### Tópico: detecção de janela larga em Compose

**Consulta:** compatibilidade de `material3-window-size-class` com Compose 1.5.4 e
Material 3 1.1.2.

**Achados:**
- `androidx.compose.material3:material3-window-size-class:1.1.2` existe e é
  compatível com o stack atual; expõe `calculateWindowSizeClass(activity)` com
  faixas Compact/Medium/Expanded.
- A recomendação oficial para apps adaptativos cita Compose, ConstraintLayout e
  WindowSizeClass.

**Impacto no design:** vira a alternativa B do ADR-002. `LocalConfiguration`, já
usado no arquivo para dimensionar a pré-visualização, resolve o mesmo problema sem
dependência nova.

**Fontes:**
- [Compose Material 3 — releases](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- [Maven — material3-window-size-class](https://mvnrepository.com/artifact/androidx.compose.material3/material3-window-size-class)

### Elicitação adaptativa (§5.4)

Tópicos obrigatórios (stack, armazenamento, autenticação, deploy, estilo
arquitetural, integrações) foram **identificados do repositório** e confirmados, não
perguntados em aberto — projeto existente, conforme a regra do §5.4.

| # | Pergunta | Resposta | Classificação | Impacto |
|---|---|---|---|---|
| 1 | Estratégia com o opt-out funcionando | Opt-out agora como ponte, adaptativo depois | Não bloqueante | Prioridades viram High/Medium em vez de Critical; FR-7 registra a ponte |
| 2 | Proporção em paisagem | 16:9 | Bloqueante → resolvido | FR-3 |
| 3 | Orientação do arquivo gravado | Aparelho físico corrigido pela rotação da janela | Bloqueante → resolvido | FR-6 |
| 4 | Critério de pronto em paisagem | *"não é só verificar se é mais largo que alto e inverter o layout que já existe?"* | Correção de escopo | Ver abaixo |
| 5 | Barras horizontais (zoom, pickers) | Viram verticais junto com o resto | Bloqueante → resolvido | FR-4 |
| 6 | Detecção de janela larga | `LocalConfiguration`, comparar largura e altura | Bloqueante → resolvido | ADR-002 |
| 7 | Legibilidade da barra sobre imagem clara | *"ficou ilegível com essa imagem clara; talvez colocar uma transparência"* | Requisito novo | NFR-2 |

**Sobre a Q4 — correção de escopo aceita.** A pergunta original opunha "paridade
total" a "entrega parcial", o que superdimensionava o trabalho. Verificação no
código confirma a leitura do usuário:

- os três blocos são âncoras de um `Box`; inverter é trocar `TopCenter`/
  `BottomCenter` por `CenterStart`/`CenterEnd` e `Row` por `Column`
- os overlays são posicionados por coordenada e **não precisam de mudança**
- único atrito real: `RowScope.TopBarSlot` usa `Modifier.weight(1f)`; `weight`
  existe nos dois escopos, mas como funções de extensão distintas — o helper
  precisa virar agnóstico de escopo (ADR-003)

A spec foi reescrita em torno de "inverter o layout existente", não de "construir um
layout de paisagem".

### Pesquisa de acompanhamento (§5.5)

**Status:** não necessária.
**Justificativa:** a elicitação não trouxe tecnologia, padrão ou restrição fora do
que o §5.3 já cobriu. A única novidade (NFR-2, legibilidade) é aplicação de
contraste WCAG, conhecimento consolidado.

### Confiança inicial: 78%

Alta para Gate 0 porque o repositório é conhecido em profundidade, o comportamento
de rotação foi lido no código e a mudança do Android 16 foi verificada em emulador,
não só em documentação. Os 22% restantes são o comportamento real das barras
invertidas em aparelho, que só se sabe rodando.

---

## Gate 1 — Requisitos

Ver [requirements.md](requirements.md): 9 FR e 5 NFR.

> **Emenda de 2026-08-04 (Q-01).** O FR-6 foi reescrito e o FR-9 criado depois da
> medição em aparelho. A contagem original registrada aqui era "7 FR", o que já
> divergia do arquivo — `requirements.md` sempre definiu FR-1 a FR-8. Corrigido.
>
> A emenda não passou por nova validação de consenso: as três personas do Gate 1
> avaliaram a *formulação* do requisito, e o que mudou foi um **fato medido** sobre o
> comportamento do aparelho. Consenso não decide fato. As três recomendações
> originais seguem valendo — em particular a do Pragmático, "exigir verificação do
> arquivo salvo, não da tela" (AC-6.2), que é justamente o que expôs o erro.

### Validação por consenso — requirements.md

**Data:** 2026-08-03

**O Pragmático**
Forças: o escopo cabe no código atual, sem depender da quebra do monolito; FR-1 é
um booleano derivado do que já existe.
Preocupações: FR-6 é o único com risco real de sair errado sem ninguém perceber,
porque não aparece na pré-visualização.
Recomendações: exigir verificação do arquivo salvo, não da tela. **+4%**

**O Perfeccionista**
Forças: NFR-2 tem medida objetiva (contraste 4.5:1) em vez de "colocar um fundo".
Preocupações: FR-4 diz que as barras viram verticais mas não define o que acontece
quando não cabem — tablet em paisagem tem pouca altura útil.
Recomendações: critério de aceite para transbordo. **+2%**

**O Advogado**
Forças: manter o opt-out como ponte protege o usuário — nada piora enquanto a spec
não é implementada.
Preocupações: FR-3 muda a proporção do vídeo conforme a orientação; quem espera
sempre 9:16 pode se surpreender.
Recomendações: registrar na telemetria, que já emite `aspect` no `evt=bind`. **+0%**

**Divergência:** nenhuma. As três recomendações foram incorporadas (AC-6.2, AC-4.3 e
a nota de telemetria em FR-3).

**Notas:** Pragmático 9/10 · Perfeccionista 8/10 · Advogado 9/10 · **Média 8,7/10**
**Status:** APROVADO COM MUDANÇAS (as três já aplicadas) · **Confiança: 84%**

---

## Gate 2 — Contexto

Ver [design.md](design.md).

Fronteira: a spec não cria integração nova. Atua entre o sistema de janelas do
Android (que passa a entregar janelas largas) e o CameraX (que precisa receber a
rotação certa). Nenhum serviço externo, nenhuma persistência nova.

### Validação por consenso — contexto

**Data:** 2026-08-03

Os três concordam que o diagrama é simples porque a mudança é interna à camada de
apresentação. O Perfeccionista registra que o acelerômetro e o `Display` aparecem
como fontes distintas, o que explicita o FR-6 — a soma indevida das duas é o defeito.

**Notas:** 9/10 · 9/10 · 8/10 · **Média 8,7/10** · **Status:** APROVADO
**Confiança: 86%**

---

## Gate 3 — Arquitetura

### ADR-001: Inverter o layout existente em vez de construir um layout de paisagem

**Status:** Aceito · **Data:** 2026-08-03 · **Decisores:** Maicon Alves

#### Contexto e problema

A partir do targetSdk 36 o Android entrega janelas em paisagem em telas ≥ 600dp. O
layout da `CameraScreen` é desenhado para retrato e se espalha.

A leitura inicial supunha ser preciso construir um layout de paisagem novo, com
controles laterais. O usuário questionou: *"não é só verificar se é mais largo que
alto e inverter o layout que já existe?"*

#### Motivadores

- A `CameraScreen` tem ~1.950 linhas e a quebra do monolito está **fora** desta spec
- Controles na lateral já existem por outro caminho (rotação de ícones por sensor)
- Menor mudança possível reduz risco de regressão em retrato, o caso de uso principal

#### Opções consideradas

1. **Layout de paisagem novo** — composables dedicados, posicionamento próprio
2. **Inverter o layout existente** — mesmos composables, eixo e âncoras trocados
3. **Auto-letterbox** — limitar largura e centralizar, sem adaptar

#### Decisão

**Opção 2.** A verificação no código confirma a hipótese do usuário: as três seções
são âncoras de um `Box`, e inverter é trocar eixo e âncora. Os overlays são
posicionados por coordenada e não mudam.

A opção 3 foi descartada como solução final por desperdiçar tela — mas sua função
(não deixar o tablet quebrado) já está atendida pelo opt-out de manifesto.

#### Análise de trade-off (ATAM-Lite)

| Critério | Peso | Justificativa do peso |
|---|---|---|
| Risco de regressão em retrato | 5 | Retrato é o caso principal; quebrar é inaceitável |
| Esforço de implementação | 4 | Não há prazo curto (opt-out cobre até targetSdk 37) |
| Aproveitamento da tela | 3 | Objetivo declarado, mas tablet é secundário |
| Testabilidade sem device | 3 | A suíte é JVM; o que não for testável vira dívida |
| Custo de manutenção | 4 | O arquivo já é grande demais; não pode dobrar |

| Alternativa | Regressão (5) | Esforço (4) | Tela (3) | Testes (3) | Manutenção (4) | **Total** | Fontes |
|---|---|---|---|---|---|---|---|
| 1: Layout novo | 5 (25) | 3 (12) | 9 (27) | 6 (18) | 4 (16) | **98** | ¹ |
| 2: Inverter | 8 (40) | 8 (32) | 8 (24) | 7 (21) | 7 (28) | **145** | ², experiência |
| 3: Auto-letterbox | 9 (45) | 9 (36) | 2 (6) | 8 (24) | 9 (36) | **147** | experiência |

1. [Adaptive apps — orientation, aspect ratio, resizability](https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability) — layout dedicado é o caminho recomendado, com custo proporcional
2. Verificação no código desta sessão: os três blocos são âncoras de um `Box`; overlays por coordenada não mudam

**Sensibilidade.** A opção 3 pontua marginalmente acima da 2 (147 × 145), mas só
porque "aproveitamento da tela" tem peso 3. Subindo esse peso para 4 — defensável,
já que é o objetivo declarado da spec — a 2 assume (148 × 141). Mais decisivo: a
opção 3 não é alternativa real, porque o opt-out de manifesto já entrega o mesmo
resultado com custo zero. Comparar as duas é comparar trabalho com nada.

Piora cruzada: melhorar "aproveitamento da tela" piora "risco de regressão" nas três
opções, porque todo pixel a mais vem de mexer no posicionamento que hoje funciona.

#### Riscos

| Risco | Prob. | Impacto | Mitigação |
|---|---|---|---|
| Regressão silenciosa em retrato | Média | Alto | Retrato como caminho default no código; teste de que `isWideWindow=false` reproduz as âncoras atuais |
| Barras não caberem em paisagem | Média | Médio | AC-4.3 exige rolagem ou redução; verificar em tablet |
| Arquivo gravado torto | Média | Alto | FR-6 + AC-6.2 exigem conferir o arquivo salvo, não a tela |

#### Consequências

**Boas:** mudança contida; retrato intocado por construção; sem dependência nova.
**Ruins:** condicionais de orientação passam a viver no composable de ~1.100 linhas,
que cresce. Aceito conscientemente — a quebra é o item 1 do `REFACTORING.md`.

#### Fontes consultadas

- [Adaptive apps — orientation, aspect ratio, resizability](https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability)
- Verificação no código do repositório (2026-08-03): âncoras da `CameraScreen`, `RowScope.TopBarSlot`, overlays por coordenada

#### Tarefas afetadas

Task 1, Task 2, Task 3, Task 4

---

### ADR-002: `LocalConfiguration` em vez de `WindowSizeClass`

**Status:** Aceito · **Data:** 2026-08-03 · **Decisores:** Maicon Alves

#### Contexto e problema

É preciso saber se a janela está larga. Duas APIs resolvem.

#### Opções consideradas

1. **`LocalConfiguration`** — comparar `screenWidthDp` com `screenHeightDp`
2. **`WindowSizeClass`** — `material3-window-size-class:1.1.2`, faixas Compact/Medium/Expanded

#### Decisão

**Opção 1.** A spec precisa de **um booleano**, não de faixas de largura. O arquivo
já usa `LocalConfiguration` para calcular `previewSizeModifier`, então é a mesma
fonte de verdade, recompõe sozinho quando a janela muda e não adiciona dependência.

`WindowSizeClass` seria a escolha certa com três ou mais layouts, ou breakpoints por
largura absoluta. Não é o caso.

#### Análise de trade-off

| Critério | Peso | Opção 1 | Opção 2 |
|---|---|---|---|
| Adequação ao problema (1 booleano) | 5 | 9 (45) | 6 (30) |
| Dependências novas | 4 | 10 (40) | 5 (20) |
| Consistência com o código atual | 4 | 9 (36) | 5 (20) |
| Extensibilidade futura | 2 | 5 (10) | 9 (18) |
| **Total** | | **131** | **88** |

Fontes: [Compose Material 3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3) (compatibilidade da 1.1.2); leitura do `CameraScreen.kt` (uso atual de `LocalConfiguration`).

**Sensibilidade:** mesmo dobrando o peso de "extensibilidade futura" para 4, a opção
1 permanece à frente (131 × 106). A decisão não é sensível a esse critério.

#### Consequências

**Boas:** zero dependência; consistente com o cálculo de preview que já existe.
**Ruins:** se surgir um terceiro layout, migrar para `WindowSizeClass`. Reversível —
o predicado fica isolado num único ponto (Task 1).

#### Fontes consultadas

- [Compose Material 3 — releases](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- Também apoiada em experiência: `LocalConfiguration` já é a fonte usada no arquivo

#### Tarefas afetadas

Task 1

---

### ADR-003: Helper de eixo agnóstico de escopo

**Status:** Aceito · **Data:** 2026-08-03 · **Decisores:** Maicon Alves

#### Contexto e problema

`RowScope.TopBarSlot` usa `Modifier.weight(1f)`. `weight` existe em `RowScope` e em
`ColumnScope`, mas como funções de extensão **distintas** — o mesmo composable não
serve aos dois eixos sem tratamento. É o único atrito técnico real da inversão.

#### Opções consideradas

1. **Duplicar** — `RowScope.TopBarSlot` e `ColumnScope.TopBarSlot`
2. **Escopo próprio** — interface `AxisScope` com `Modifier.axisWeight(f)` delegando ao escopo concreto
3. **Sem `weight`** — distribuir com `Arrangement.SpaceEvenly` e tamanho fixo

#### Decisão

**Opção 2.** Um `AxisScope` fino com `Modifier.axisWeight()`, mais um composable
`AxisContainer(vertical: Boolean)` que instancia `Row` ou `Column` e fornece o
escopo. Os call sites ficam idênticos aos de hoje.

A opção 1 duplica cinco call sites e convida à divergência. A opção 3 abre mão da
distribuição proporcional, que é o que mantém a barra bem espaçada em larguras
diferentes.

#### Consequências

**Boas:** um helper resolve barra superior, linha expansível, `ZoomPresetBar` e
`HorizontalPickerBar`. Testável isoladamente.
**Ruins:** uma abstração nova num arquivo que já tem muitas.

#### Fontes consultadas

Decisão baseada em experiência (sem fonte externa) — razão: é consequência direta da
tipagem de `RowScope`/`ColumnScope` no Compose 1.5.4, verificada no código do próprio
repositório. Não há padrão publicado a citar.

#### Tarefas afetadas

Task 2, Task 3, Task 4

---

### Validação por consenso — arquitetura

**Data:** 2026-08-03

**O Pragmático** — ADR-001 e ADR-002 são a menor mudança que resolve. ADR-003
preocupa: abstração nova em arquivo grande. Mitigação: o helper é pequeno e
substitui código que existiria duplicado de qualquer forma. **+3%**

**O Perfeccionista** — Aprova a sensibilidade explícita do ADR-001, que mostra por
que o vencedor bruto (auto-letterbox) não é a resposta. Registra que ADR-003 não tem
fonte externa; aceito com a declaração explícita de experiência. **+2%**

**O Advogado** — Manter o opt-out enquanto a spec não é implementada é o ponto mais
forte: o usuário nunca vê o app pior. **+1%**

**Divergência:** Pragmático e Perfeccionista discordam sobre ADR-003 introduzir
abstração. Resolvido: o helper é pré-requisito da inversão, não escolha estética.

**Notas:** 8/10 · 9/10 · 9/10 · **Média 8,7/10** · **Status:** APROVADO
**Confiança: 90%**

---

## Gate 4 — Tarefas

Ver [tasks.md](tasks.md). 6 tarefas, nenhuma XL, três ondas.

### Validação por consenso — tarefas

**Data:** 2026-08-03

**O Pragmático** — Task 1 como contrato de Wave 0 é o que permite as demais em
paralelo. Task 5 (mídia) é independente das de layout e poderia vir antes. **+2%**

**O Perfeccionista** — Cada tarefa tem `Verification` com comando executável, não
"testar manualmente". Task 6 fecha com verificação em dois aparelhos reais. **+1%**

**O Advogado** — Fatias verticais: cada tarefa entrega algo observável. A Task 2
sozinha já deixa a barra superior correta em paisagem. **+0%**

**Notas:** 9/10 · 9/10 · 9/10 · **Média 9,0/10** · **Status:** APROVADO
**Confiança: 93%**

---

## Gate 5 — Go/No-Go

**Confiança: 93%** (≥ 90% exigido)

Consistência entre artefatos conferida:

- Todo FR/NFR de `requirements.md` aparece em pelo menos um `_Requirements:_` de `tasks.md`
- Todo ADR tem `## Tarefas afetadas` coerente com os `_Decisions:_` das tarefas
- Os diagramas de `design.md` refletem as decisões dos ADRs

**Bloqueadores em aberto:** nenhum.

**Pendências assumidas (não bloqueantes):**

1. Dobráveis fora de escopo por decisão. Modo tabletop não é tratado.
2. A inversão adiciona condicionais ao composable de ~1.100 linhas. Dívida
   consciente; a quebra é o item 1 do `REFACTORING.md`.
3. O opt-out de manifesto precisa ser **removido** quando a spec for implementada e
   validada — senão o trabalho fica inerte. É a Task 6.

**Status:** pronto para construir.

---

## Questão aberta (levantada na implementação, 2026-08-04)

### Q-01: a premissa do FR-6 não se reproduz — a Task 5 está bloqueada

**Levantada por:** skill `dev`, durante a Wave 0
**Status:** **Resolvida** em 2026-08-04 — decisão do usuário: dividir em duas
grandezas. FR-6 reescrito para *proibir* a composição; FR-9 criado para a compensação
da UI; AC-6.1 reescrito, AC-6.4 e AC-9.1 a AC-9.3 acrescentados; `design.md` §1, §2,
§3, §5.2 e §7 corrigidos. Implementado e verificado nos dois AVDs.
**Afeta:** FR-6, FR-9, AC-6.1, AC-6.4, AC-9.x, NFR-4, Task 5, design.md §1/§2/§3/§5.2/§7

O FR-6 afirma que a rotação física e a rotação da janela "se somam e o arquivo sai
torto". Ao implementar a Task 5, a medição em aparelho **não reproduz** isso, e a
composição especificada introduziria o defeito que ela pretende corrigir.

#### Medição 1 — as duas fontes são a mesma grandeza

AVD Tablet_API36, janela livre (ponte removida localmente), quatro estados de rotação.
`Display.getRotation()` via `dumpsys window displays`; `rollDegrees` calculado de
`adb emu sensor get acceleration` com o mesmo `atan2(x, y)` do app:

| `Display.getRotation()` | acelerômetro (x:y:z) | `rollDegrees` | mapeamento do app |
|---|---|---|---|
| ROTATION_0 | `0 : 9.81 : 0` | 0° | ROTATION_0 |
| ROTATION_270 | `-9.81 : ~0 : 0` | −90° | ROTATION_270 |
| ROTATION_180 | `~0 : -9.81 : 0` | −180° | ROTATION_180 |
| ROTATION_90 | `9.81 : ~0 : 0` | +90° | ROTATION_90 |

Correspondência 1:1 nos quatro estados, mesma convenção. Enquanto a janela acompanha
o aparelho, `Display.getRotation()` **é** a rotação física — não uma segunda
informação. Somar as duas produz rotação dobrada.

#### Medição 2 — a mídia atual já sai de pé

Verificado no arquivo, como o AC-6.2 exige, com inspeção de conteúdo além do metadado:

| Estado | `targetRotation` usado | Arquivo | Conteúdo |
|---|---|---|---|
| janela paisagem (W=0), aparelho natural | ROTATION_0 (só sensor) | MP4 1280×720 + `rotate=90` → 720×1280 | cena **de pé**: céu no topo, chão embaixo |
| janela retrato (W=90), aparelho girado 90° | ROTATION_90 (só sensor) | JPEG 1280×720, EXIF orientation=1 | cena **de pé** |

A fórmula do CameraX foi confirmada pelos dois pontos:
`rotação de saída = (SENSOR_ORIENTATION − targetRotation) mod 360`, com
`SENSOR_ORIENTATION = 90` neste AVD (`dumpsys media.camera`). O arquivo sair retrato
com a janela em paisagem **não é** erro de rotação: é consequência do sensor montado
a 90° num aparelho de orientação natural paisagem — o campo de visão físico é mesmo
mais alto que largo. É assunto de proporção (FR-3), não de orientação.

#### Medição 3 — existe um defeito real, mas é na UI, não na mídia

Captura de tela do tablet com a janela em retrato (1600×2560): o compositor já
deixou a UI de pé, mas `rollDegrees = +90` gira **cada ícone outra vez**. `HD 30`,
`EIS`, flash e temporizador aparecem de lado; `Vídeo`/`Foto` e `1x 2x 5x`, que não
recebem `iconRotation`, ficam retos. É dupla rotação, visível na tela, e aparece
exatamente quando a ponte sai do manifesto (Task 6).

A grandeza que precisa da rotação da janela é a **compensação da UI**, não a rotação
de captura:

- ícone: `iconDegrees(rollDegrees) − displayRotation × 90`, normalizado para −180..180
  - telefone travado em retrato: `W = 0` → resultado inalterado (AC-6.3 preservado)
  - tablet com janela livre: `W = S` → resultado 0, ícones retos
- captura: permanece `f(rollDegrees)` — a evidência não sustenta a composição

O mesmo vale para o nível de horizonte e o slider de exposição, que também derivam de
`rollDegrees`.

#### Caso em que a captura de fato divergiria

`W ≠ S` acontece em dois cenários: janela travada com o aparelho girado (telefone
hoje — AC-6.3 escolheu deliberadamente seguir o aparelho) e **rotação automática
desligada pelo usuário** no tablet. No segundo, a janela fica presa e o arquivo não
acompanha o enquadramento. É o espelho do AC-6.3, e resolver um contradiz o outro —
por isso é decisão de produto, não de implementação.

#### Recomendação

Redefinir o FR-6 em duas partes e devolver ao Gate 1/3 da skill `spec`:

1. **Compensação de rotação da UI** — `uiRotation(rollDegrees, displayRotation)`,
   pura, testável na JVM. Corrige defeito medido e visível. Deveria vir **antes** da
   Wave 1: sem ela, a verificação visual das barras laterais fica confundida por
   ícones tortos.
2. **Rotação de captura** — extrair `captureRotation(rollDegrees)` como função pura
   (ganho de NFR-4, comportamento idêntico ao de hoje), sem compor com a janela.
   Registrar o caso "rotação automática desligada" como pendência conhecida.

A Task 5 fica **bloqueada** até essa decisão. Implementar a composição como está
especificada tornaria a mídia torta na janela livre — o oposto do objetivo do FR-6.

**Nota de método:** a primeira leitura desta sessão concluiu, por raciocínio, que o
arquivo saía torto; a segunda concluiu o contrário, também por raciocínio. As duas
foram descartadas. O que sustenta o registro acima são as três medições, incluindo
inspeção do conteúdo dos arquivos — o AC-6.2 existe exatamente por isso.

---

### Q-02: girar o tablet deixa a pré-visualização preta para sempre

**Levantada por:** skill `dev`, verificação final da Task 6, 2026-08-04
**Status:** **Resolvida** em 2026-08-04 — decisão do usuário: corrigir dentro desta
spec, como Task 7. Implementada e verificada em aparelho; NFR-3 passa a atendido nas
duas medidas.
**Afeta:** NFR-3, Task 3, Task 7, `CameraViewModel.initializeCamera`, `CameraManager`

Em Tablet_API36, com a ponte já removida, girar o aparelho deixa a pré-visualização
**permanentemente preta** — não é o piscar de um rebind, não recupera sozinha, e só
volta reabrindo o app. Isso reprova a segunda medida do NFR-3 ("sem tela preta visível
por mais de 1 segundo"), que nunca havia sido exercitada: a assinatura da Task 3 mediu
só o `elapsed_ms`.

#### Medição 1 — não é o retrato, é a transição

Luminância média do centro da tela (0 = preto):

| Situação | Janela | Luminância |
|---|---|---|
| Paisagem, aberto do zero | 2560×1600 | 189,8 |
| **Retrato, aberto do zero** | 1600×2560 | **149,5 — com imagem** |
| Retrato, depois de girar | 1600×2560 | **0,1 — preto** |
| Paisagem, depois de girar de volta | 2560×1600 | **0,2 — preto** |

Retrato em tablet funciona quando é o estado inicial. O defeito é a mudança de
configuração.

#### Medição 2 — não é regressão desta spec

O mesmo roteiro no APK **pré-spec** (`f5614cd`, sem nenhum código desta spec e sem a
ponte no manifesto): 189,9 → **0,2** → **0,1**. Reproduz idêntico. O bug é
pré-existente; o que esta spec faz é torná-lo **alcançável**, porque a remoção da ponte
(FR-7) é o que permite o tablet girar.

#### Medição 3 — a câmera fecha e não reabre

Eventos do `Camera2ClientBase` do servidor de câmera, contrastando os dois caminhos:

| | ao girar (PID 5220) | ao abrir do zero (PID 5318) |
|---|---|---|
| abre | 20.255 `Camera 1: Opened` | 57.782 `Camera 1: Opened` |
| fecha | 20.344 disconnect · 20.867 dtor | 58.225 disconnect |
| **reabre** | **nunca** | **58.424 `Camera 1: Opened`** |

Nos dois casos há dois binds seguidos (o `scheduleBind` dispara duas vezes). No início
limpo o segundo bind **reabre** a câmera; depois de girar, não reabre — e ainda assim
`evt=bind` reporta sucesso com `elapsed_ms` de 7 e 16ms. **A telemetria de bind não
prova que chegou quadro na superfície**, e foi exatamente essa leitura que fez a Task 3
assinar o NFR-3.

#### Causa

`CameraManager` recebe o `lifecycleOwner` no construtor e o guarda
(`CameraManager.kt:101`); `bindToLifecycle(lifecycleOwner, …)` em `:485` usa esse campo.
O `CameraViewModel` é obtido com `viewModel()` na `MainActivity`, então sobrevive à
recriação por mudança de configuração — e com ele o `CameraManager` **e a referência à
Activity destruída**. Ao girar, a `CameraScreen` recompõe com um `previewView` novo, cai
no ramo `else` do `LaunchedEffect(permissionGranted, previewView)`
(`CameraScreen.kt:359-366`) e chama `updateSurfaceProvider` + `rebindCamera` — que
rebinda contra um ciclo de vida já DESTROYED. O CameraX registra o binding e nunca o
ativa, sem lançar exceção.

Isso também explica por que o mesmo caminho **funciona** ao voltar de Configurações: lá
a navegação é do Compose, dentro da mesma Activity, e o `lifecycleOwner` continua vivo.

#### Recomendação

Fazer o `CameraViewModel` detectar `lifecycleOwner` diferente do que criou o
`CameraManager` e reconstruir o controller nesse caso, em vez de reaproveitar. É
decidível na JVM com o `FakeCameraController` (NFR-4), então dá para escrever o teste
antes (Gate B). O contra-argumento é de escopo: o defeito mora em `camera/`, não no
layout, e não é regressão desta spec.

#### Decisão e resultado

**Corrigir dentro desta spec, como Task 7** (usuário, 2026-08-04). O argumento que
pesou: a remoção da ponte é a Task 6 **desta** spec, então é esta spec que coloca o
defeito no caminho do usuário — fechar com NFR-3 reprovado seria entregar o tablet com
a câmera morta depois da primeira virada.

O `CameraViewModel` passou a guardar em `WeakReference` qual `LifecycleOwner` criou o
controller, e `initializeCamera` decide entre religar a surface (mesmo owner) e
reconstruir (owner diferente). Depois: quatro orientações com imagem, `Camera 1: Opened`
707ms e 624ms após cada `disconnect`, e amostras a 1,2s / 2,0s / 3,0s da virada já com
imagem. Detalhes em `tasks.md`, Task 7.

Efeito colateral bom: o vazamento da Activity destruída, que existia por acidente
através do controller retido, deixou de existir.

#### O que fica como aprendizado

`evt=bind` mede o tempo de configurar os use cases, **não** que tenha chegado quadro na
superfície — no caminho defeituoso ele reportava sucesso em 7ms. A assinatura original
do NFR-3 (Task 3) leu 263–317ms e concluiu "atendido", quando metade da medida do
requisito nunca havia sido exercitada. Para "tem imagem na tela" o instrumento é
luminância de captura de tela, não telemetria de bind.

---

### Q-03: a Task 7 desligou o EIS em aparelho que o suporta

**Levantada por:** skill `dev`, primeira instalação em aparelho **físico**, 2026-08-05
**Status:** **Resolvida** no mesmo dia — regressão introduzida pela Task 7, corrigida
com teste de regressão.
**Afeta:** NFR-1, Task 7, `CameraViewModel.initializeCamera`

Motorola edge 60 neo, Android 16 (API 36), 427×949dp. Primeira vez que este app roda em
hardware real nesta spec, e a telemetria mostrou contradição na primeira linha:

```
evt=caps  eis_supported=true
evt=bind  eis=false
```

EIS suportado pelo hardware e **desligado** pelo app — com o desligamento **persistido**
no `SharedPreferences`.

#### Causa: ordem de inicialização, introduzida por mim na Task 7

`_isEisSupported` do `CameraManager` começa `MutableStateFlow(false)`, e esse `false`
significa "ainda não sondado" — indistinguível de "não suportado". A sondagem só ocorre
dentro de `initializeCamera`, que é o bind.

O coletor do ViewModel reage a `!supported` desligando o EIS e gravando no storage. Antes
da Task 7, os 15 coletores subiam **depois** de `initializeCamera`, então ele já
encontrava `true` em hardware capaz. Ao agrupá-los num job único, eu os movi para
**antes** do bind — e o coletor passou a receber o `false` inicial.

Não há janela de sorte: `viewModelScope` usa `Dispatchers.Main.immediate`, que executa o
`launch` de forma *eager* até a primeira suspensão.

#### A/B que confirmou

Mesmo aparelho, dados do app limpos entre as rodadas:

| Build | `eis_supported` | `eis` aplicado |
|---|---|---|
| `5c05e41` (com a Task 7) | true | **false** |
| `34783d9` (pré-Task 7) | true | **true** |

#### Correção

Os coletores voltaram para depois do bind, com comentário explicando por que a ordem não
é cosmética. Depois: `eis=true` no `evt=bind`.

#### Por que nem emulador nem teste pegaram — e o que mudou

- **Emulador:** o EIS realmente não é suportado lá, então `eis_supported=false` nos dois
  casos e o comportamento defeituoso coincide com o correto. Só hardware real distingue.
- **Teste:** o `FakeCameraController` nascia com `isEisSupportedFlow = true`, mais
  otimista que a produção, que nasce `false`. O dublê tornava a ordem irrelevante.
  Ganhou o gancho `aoInicializar`, que reproduz a transição `false → true` no momento do
  bind.
- **Armadilha de dispatcher:** a primeira versão do teste **passou com o bug presente**.
  O `StandardTestDispatcher` da suíte enfileira o `launch` em vez de executá-lo eager, o
  que inverte justamente a ordem sob teste. O teste usa `UnconfinedTestDispatcher` para
  modelar o `Main.immediate` de produção.

**Lição que vale além deste bug:** dublê mais otimista que a produção e dispatcher de
teste diferente do de produção **escondem defeitos de ordem de inicialização**. As duas
coisas juntas deram um verde falso.

---

### Q-04: CPU alta com a câmera aberta, e o nível tremendo com o aparelho na mesa

**Levantada por:** usuário, 2026-08-05 — *"ele está aquecendo um pouco por estar com a
câmera aberta"* e *"o nível fica mexendo mesmo com o telefone sobre a mesa"*
**Status:** parcialmente resolvida — o tremor foi corrigido; a causa dominante da CPU
está identificada e **não** foi atacada.
**Afeta:** NFR-1, `CameraScreen`, `DeviceRotation.kt`
**Escopo:** adjacente a esta spec. Não vem de FR nenhum, mas mexe em `rollDegrees` e em
`DeviceRotation.kt`, que nasceram da Q-01, e foi descoberta verificando o resultado dela
em aparelho físico.

#### Onde a CPU está

Motorola edge 60 neo, medido com `top` e `dumpsys gfxinfo`:

| Estado | CPU do processo | Quadros/s |
|---|---|---|
| Tela da câmera aberta | 80–120% (varia com a cena) | ~100 |
| Tela de Configurações | 0–1% | 0 |
| Tela apagada | 0–0,5%, câmera liberada | — |

Térmica sob uso: SoC 52°C, pele 36,8°C, `Thermal Status: 0` — sem throttling, e o
primeiro limiar de pele é 40°C. Aquecimento real, dentro da faixa normal.

Nenhum vazamento: ao apagar a tela a câmera é liberada e o consumo vai a zero.

**A causa dominante é o caminho da pré-visualização, não a UI.** `PreviewView` está em
`ImplementationMode.COMPATIBLE`, que usa `TextureView`: cada quadro da câmera atravessa a
hierarquia de views para o app desenhar. Os ~100 quadros/s são iguais com e sem as
mudanças abaixo, o que mostra que o redesenho não é dirigido pelo sensor.

`COMPATIBLE` não é escolha gratuita, e é a razão de isto ficar em aberto: o `AndroidView`
aplica `graphicsLayer { scaleX = -1f }` para espelhar a câmera frontal, e `SurfaceView`
(modo `PERFORMANCE`, que compõe em overlay sem o app desenhar) não aceita bem essa
transformação. Trocar exige mover o espelhamento para o CameraX (`setMirrorMode`).

#### O tremor do nível — três defeitos somados

1. **Filtro fraco.** `rollDegrees * 0.7 + newRoll * 0.3` toma 30% da amostra nova a cada
   evento, a ~17 eventos/s. Passa ruído.
2. **Escrita de estado em toda amostra.** `Float` filtrado nunca converge de fato, então
   sempre difere do anterior e sempre invalida quem o lê — e `rollDegrees` era lido no
   corpo do composable de ~1.100 linhas.
3. **Filtrava o ângulo, não o vetor.** Média de ângulo atravessa a descontinuidade de
   ±180° pelo lado errado: com o aparelho de cabeça para baixo, `+179°` e `−179°` (2° de
   diferença física) têm média ~0°, e o nível daria meia-volta em vez de convergir.
   Defeito de correção, achado ao escrever o teste.

Correções: suavizar o **vetor** de gravidade e só então tirar o `atan2`; zona morta antes
de publicar; `derivedStateOf` nos valores travados em quadrante, para as escritas
restantes não invalidarem o composable inteiro.

#### Ajuste da zona morta, por observação do usuário

0,25° foi calculado para ser imperceptível (menos de meio pixel na ponta da linha) e
**não bastou**: com o telefone na mesa e alguém digitando ao lado, a vibração passava.
Subiu para **0,8°**.

Subir a zona morta é preferível a fortalecer o filtro, e a razão é o FR-6: a zona morta
**não adiciona atraso** a uma rotação real, que a excede de imediato, enquanto o filtro
atrasaria tudo — inclusive `captureRotation`, que decide a orientação do arquivo gravado.
Girar o telefone e apertar gravar produziria vídeo torto.

Invariante nova em teste: a zona morta tem de ficar abaixo de metade da tolerância de
±2° que acende o verde de "nivelado", senão passaria a **esconder** desnivelamento real.
Para isso valer, o `2f` que estava literal na tela virou `LEVEL_TOLERANCE` e a produção
passou a usá-lo — invariante contra constante que ninguém usa é teatro.

#### Efeito na CPU, e três conclusões erradas antes da certa

| Medição | Desenho | Resultado | Vale? |
|---|---|---|---|
| 1 | antes e depois em **cenas diferentes** | "53,5% → 25% no main" | **não** |
| 2 | mesma sessão, **uma amostra** de cada | 78,5% vs 82,0% → "sem efeito" | **não** |
| 3 | **alternada** A,B,A,B, mediana de 6 | 117%/122% vs 96,5%/92,8% | **sim** |

A medição 3 é a única com desenho que suporta conclusão: ordem alternada elimina deriva,
e a separação é consistente entre rodadas. Há redução de ~20 pontos — real, mas longe de
dominante, já que sobram ~95%.

**Lição de método:** neste ambiente o valor absoluto de `top` varia mais que o efeito
sob teste — as medições 1 e 2 tinham valores absolutos incompatíveis entre si (78% e
117%) para o *mesmo* código. Comparação de amostra única aqui não é medição; é sorteio.
Ordem alternada e mediana são o mínimo.

#### Segunda rodada: separar exibição de física

Com a zona morta em 0,8° o usuário reportou *"a linha tá tremendo menos mas ainda treme
um pouco"* e, decisivo, *"ela acompanha o movimento bem"* — ou seja, havia margem para
filtrar mais, e o que restava não era problema de limiar.

Aumentar mais a zona morta seria a resposta errada: ela **converte** tremor contínuo em
saltos, porque só publica nos picos. Ficaria menos frequente e cada salto maior. E o
teto da invariante (metade de ±2°) já estava perto.

Vibração de teclado é alta frequência, e o instrumento para isso é filtro. Mas filtrar
mais o valor único atrasaria `captureRotation`: medido em ~4,3 amostras (~250ms) para
cruzar o limiar de 45° com `GRAVITY_SMOOTHING = 0,15`, contra ~8,3 (~490ms) a 0,08.

Solução: **duas grandezas**, como a Q-01 já fez para captura e UI.

| Caminho | Filtro | Zona morta | Alimenta |
|---|---|---|---|
| Física | 0,15 | 0,8° | `captureRotation`, `uiRotation` |
| Exibição | **0,05** | 0,25° | ângulo da linha e o verde de nivelado |

O verde passou a vir do caminho de exibição junto com a linha, senão os dois
discordariam durante o movimento. Continua sendo inclinação **física**, como o AC-9.3
exige — só menos ruidosa.

Duas invariantes novas em teste, porque é o tipo de acoplamento que alguém desfaz sem
perceber: o filtro de exibição tem de ser mais forte que o físico, e o físico tem de
cruzar o limiar de quadrante em ≤ 6 amostras. Se alguém afrouxar o físico "para a linha
ficar mais lisa", o teste falha antes de a mídia sair torta.

**Confirmado pelo usuário:** *"tá bem melhor"*. Resolvido.

#### Aprendizado sobre o teto do NFR-5

Os comentários desta rodada levaram `CameraScreen.kt` a **2.241 linhas**, uma acima do
teto de 2.240. A causa era restatear em comentário o raciocínio que já vive aqui; apontar
para a Q-04 resolveu e devolveu o arquivo a 2.230.

Sobram **10 linhas** de folga. O NFR-5 prescreve extrair componentes acima do teto, e o
candidato pronto é o `DisposableEffect` do acelerômetro, que já é autocontido: viraria
`rememberDeviceTilt()` em `presentation/layout/`. A próxima mudança que tocar este
arquivo provavelmente precisa fazer isso primeiro.

#### Pendente

- **Modo `PERFORMANCE`** continua bloqueado pelo espelho no `graphicsLayer`. É onde está
  o ganho grande de CPU e bateria: ~95% do consumo com a câmera aberta é o `TextureView`
  desenhando cada quadro na hierarquia de views.
  → **Resolvido na Q-05**, por caminho que não estava previsto aqui: em vez de mover o
  espelho para o CameraX, escolher o modo por situação.

---

### Q-05: destravar o modo `PERFORMANCE` sem mover o espelho

**Levantada por:** a pendência da Q-04 — o ganho de CPU estava identificado e parado
atrás do espelhamento no `graphicsLayer`.
**Status:** resolvida.
**Afeta:** NFR-1, NFR-5, `CameraScreen`, `CameraPreviewSurface`

#### A saída que a Q-04 não tinha visto

A Q-04 enquadrou como escolha única para o app inteiro: ou `COMPATIBLE` com espelho, ou
`PERFORMANCE` sem ele, e destravar exigiria mover o espelhamento para o `setMirrorMode`
do CameraX. Mas o espelho da frontal é **opcional e vem desligado**, então a escolha não
precisa ser única — pode ser por situação:

| Situação | Modo | Por quê |
|---|---|---|
| sem espelho (padrão) | `PERFORMANCE` (`SurfaceView`) | compõe em overlay; o app quase não desenha |
| com espelho | `COMPATIBLE` (`TextureView`) | `graphicsLayer` não transforma `SurfaceView` |

O custo antigo passa a ser pago só por quem liga o espelho **e** está na frontal. O
caminho comum fica barato sem que ninguém mexa em `setMirrorMode`.

#### As duas medições

A/B alternado em Motorola edge 60 neo:

| Modo | CPU do processo | Quadros/s desenhados pelo app |
|---|---|---|
| `COMPATIBLE` | 60% | ~700 |
| `PERFORMANCE` | 36–48% | ~100 |

*Comparar com a tabela da Q-04 seria erro* — são rodadas distintas, e esta spec já
registrou que valores absolutos de rodadas diferentes não se comparam. O que vale aqui é
o A/B interno, alternado, na mesma rodada.

Que `graphicsLayer` **não** espelha `SurfaceView` também foi medido, não suposto:
comparando a tela com espelho ligado contra o **reflexo horizontal** da tela com ele
desligado, o reflexo *piorava* a semelhança (29,4 contra 15,8, sobre piso de ruído de
3,7) — ou seja, a transformação estava sendo ignorada. Sem essa medição, o
`PERFORMANCE` com espelho passaria como funcionando.

#### Duas armadilhas do caminho

1. **`implementationMode` só é honrado antes de a superfície existir.** Trocar o modo em
   `update` não faz efeito; exige um `PreviewView` novo, o que a `key(mirrored)` força.
2. **Recriar o `PreviewView` reabre o caminho da Q-02** — "`PreviewView` novo, mesmo
   `LifecycleOwner`". Por isso `onPreviewView` é chamado também a cada recriação, e não
   só na criação.

Nada disto afeta a mídia gravada: quem espelha o arquivo é o `setMirrorMode` aplicado ao
`VideoCapture` no `CameraManager`.

#### Por que virou arquivo próprio

`CameraScreen.kt` estava a 10 linhas do teto do NFR-5 (ver acima). O bloco da
pré-visualização deixou de ser trivial ao ganhar a escolha de modo e as duas armadilhas
acima, então foi ele que saiu — `CameraPreviewSurface.kt`, 82 linhas. O candidato que a
nota anterior previa (`rememberDeviceTilt()`) continua disponível para a próxima vez.

---

## Decisões de produto — ressalvas da Task 4

**Data:** 2026-08-04 · **Decisor:** Maicon Alves

Levadas ao usuário depois da verificação final, com as três opções de cada uma.

### Ressalva 1 (contraste do estado desligado) → subir o alfa nos dois layouts

Escolhido `OFF_CONTROL_ALPHA = 0,48` em retrato **e** em janela larga, em vez de
condicionar ao layout ou aceitar 3,5:1 como dívida. Vira a Task 8.

A alternativa "só em janela larga" preservaria o retrato pixel-idêntico; a escolhida
aceita a mudança visível em retrato em troca de um valor só, sem condicional, e de o
pior caso do retrato (3,39:1 sobre a faixa preta, que já existia antes desta spec) subir
para 4,89:1.

Isso **não** conflita com o NFR-1, e a Ressalva 1 errava nesse ponto: o NFR-1 mede
âncoras e posicionamento, não cor. A comparação de 223 caixas continua valendo porque
alfa não altera bounds.

### Ressalva 2 (transbordo) → rolagem simples no eixo vertical

Escolhido `verticalScroll` opt-in nos grupos ancorados, em vez de aceitar o transbordo
como inalcançável ou construir um container que meça e escolha entre peso e rolagem.
Vira a Task 9.

O que se ganha é garantia, não comportamento observável: o transbordo segue inalcançável
neste hardware — janela larga exige `sw ≥ 600dp`, logo ≥ 600dp de altura, e nessa altura
os grupos cabem. O que muda é que numa janela livre curta os controles passam a rolar em
vez de se sobrepor. Custo de 3 linhas úteis, dentro da folga do NFR-5.

### Ressalva 3 permanece aberta

Não foi objeto de decisão nesta rodada. Em retrato o texto `Vídeo`/`Foto` não tem fundo
de contraste próprio e desaparece sobre cena clara — e a Task 8 **não** resolve isso:
subir o alfa do branco sobre fundo claro piora. Fechar exigiria scrim em retrato.
