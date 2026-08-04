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
