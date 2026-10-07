# Progresso do agente — Registro de Modos Extensível

## Estado atual

- **Fase:** Implementação — Onda 6
- **Gate atual:** **Checkpoint da Onda 5 passou** — pronto para a Onda 6
- **Status:** em andamento — próximas: Tarefas 9, 10 e 11 (independentes entre si)
- **Confiança:** 92%
- **Última atualização:** 2026-10-07

## Registro de sessões

### Sessão: 2026-08-05 — planejamento completo (Gates 0 a 5)

- **Agente:** skill `spec`
- **Gates:** 0 → 5 numa sessão

**Ações**

1. **Gate 0 — Pesquisa.** Contexto do repositório lido; três achados que definiram o problema:
   `CameraMode` consultado em 21 pontos de ramificação, `bindCameraUseCases()` cego ao modo
   (liga sempre os mesmos três use cases), e o seletor em janela larga com premissa de dois
   modos. Enquadramento de escopo em 3 perguntas; pesquisa externa em 3 tópicos; elicitação em
   4 perguntas; pesquisa de acompanhamento sobre Compose na JVM.
2. **Gate 1 — Requisitos.** 17 FR (EARS) + 8 NFR (QAS) + 18 AC. Consenso pegou uma contradição
   real entre FR-13 e NFR-1 e uma lacuna de caso (modo conhecido mas indisponível); as duas
   corrigidas, mais FR-17 acrescentado.
3. **Gate 2 — Contexto.** C4 nível 1. Sem integração externa nova; o HAL entrou como ator que
   decide disponibilidade de modo.
4. **Gate 3 — Arquitetura.** ATAM-Lite com 3 alternativas; B (registro declarativo) venceu com
   202 contra 152 e 124. Discussão com o usuário revelou que o enquadramento confundia "o que o
   modo declara" com "como isso é escrito" — decisão final na **forma híbrida**: `sealed class`
   com membros abstratos, dados para escolha fechada e método abstrato para comportamento.
   7 ADRs registrados.
5. **Gate 4 — Tarefas.** 13 tarefas, 8 ondas derivadas por script. A análise corrigiu um erro
   de decomposição (Tarefa 10 declarava menos dependência do que tem). Consenso pegou que
   NFR-3 não tinha dono: os dois maiores acréscimos da spec cairiam dentro do `CameraManager`.
6. **Gate 5 — Go/No-Go.** Consistência entre artefatos conferida por script. GO com 92%.

**Decisões do usuário nesta sessão**

| # | Decisão | Efeito |
|---|---|---|
| Q1 | Costura + Pro mínimo real | escopo da spec |
| Q2 | Gaveta "mais modos" **e** personalização de quais modos ficam no plano | ampliou o escopo — FR-7, FR-8, FR-16, FR-17 |
| Q3/Q4 | Manter CameraX 1.4.0 | ADR-003; Kotlin 2.x + CameraX 1.6.1 viram spec própria |
| Q5 | Pro é modo de **foto** | ADR-007; liga Preview + ImageCapture |
| Q6 | Personalização na tela de Configurações | ADR-004; arrastar no seletor fica para fatia futura |
| Q7 | Teste de Compose na JVM sob Robolectric | ADR-006 |
| §8.3 | Arquitetura **híbrida** (sealed class com membros abstratos) | ADR-001 na forma final |

**Observações para quem for implementar**

- **A Tarefa 1 é medição e vem antes de tudo.** Sem o baseline de `evt=bind`, `evt=caps`, p95 de
  latência e `wc -l`, os NFR-1, NFR-3 e NFR-4 ficam sem referência e deixam de ser verificáveis.
  Ela precisa ser feita em **aparelho real**.
- **O Checkpoint da Onda 2 é um portão de viabilidade**, não formalidade: se o aparelho de teste
  não reportar `manual_sensor=true` em `evt=caps`, a Onda 6 (Pro) não pode ser aceita como
  concluída. Descobrir isso ali é quatro ondas antes de doer.
- **Guarda-corpos do ADR-001** (viram critério de aceite da Tarefa 2): a definição de modo não
  guarda estado, não chama o controller, não expõe `@Composable` e descreve a ação em vez de
  executá-la. Violá-los desfaz a razão do desenho — o registro deixa de ser testável em JVM pura.
- **O teste de completude do registro** (Tarefa 2) passa vazio até a Tarefa 11 e só então é
  exercitado de fato. Não é cerimônia: é o contrapeso obrigatório da indireção
  `identificador → composable`, que troca erro de compilação por erro de execução.
- **NFR-3 tem dono explícito:** o tradutor de use cases vai em `ModeBinder` (Tarefa 6) e a
  aplicação dos controles manuais em `ManualExposureControls` (Tarefa 11) — nenhum dos dois
  dentro do `CameraManager`. Medido já nos checkpoints das Ondas 3 e 4, não só no fim.

**Próximo passo:** implementação pela skill `dev`, começando pela Tarefa 1.

### Sessão: 2026-08-05 — Gate A e Tarefa 1 (baseline)

- **Agente:** skill `dev`
- **Tarefa:** 1 — Baseline de comportamento e de latência
- **Gate:** A concluído · Tarefa 1 **concluída**
- **Testes:** 100% passando, suíte em 5,9 s
- **Aparelho:** Motorola edge 60 neo, Android 16 (API 36) — real

**Gate A.** Os quatro artefatos lidos por inteiro. Duas condições de ambiente resolvidas
antes de escolher tarefa:

1. **Árvore suja com a Wave 3 da adaptive-layout.** Comitada primeiro, a pedido do usuário,
   em três commits (`17d4685` código, `1bd6105` ROADMAP.md, `3b3043e` esta spec). Sem isso o
   baseline não teria `GIT_SHA` de referência e o `wc -l` sairia de código não comitado.
2. **Lacuna de rastreabilidade na spec anterior.** `CameraPreviewSurface.kt` referenciava
   `Decisions: Q-05`, que nunca havia sido escrita. Redigida a partir das medições que
   estavam só no KDoc, antes de commitar.

**Tarefa 1.** Todos os passos feitos em aparelho real. APK conferido por dex
(`GIT_SHA 3b3043e` presente) antes de qualquer medição. Baseline em
[`baseline/`](baseline/README.md).

**Antecipado: o portão de viabilidade da Onda 2 está atendido.** O aparelho reporta
`MANUAL_SENSOR`, ISO 100–19200 (analógico até 4480) e exposição de 1/10000 s a 1/2,5 s.
Também reporta `RAW`. Isso fecha, quatro ondas antes, o maior risco de execução da spec —
"aparelho de teste sem `MANUAL_SENSOR`, o Pro fica inverificável".

**Quatro questões abertas em `decisions.md`**, uma delas bloqueante:

| # | Assunto | Situação |
|---|---|---|
| Q-01 | FR-4 fala em 4 proporções de foto; o app tem 3 (sem 1:1) | **resolvida** — usuário optou por corrigir o requisito para três |
| Q-02 | `evt=bind` sozinho não verifica iso-comportamento | resolvida: método emendado com dimensão da mídia |
| Q-03 | anel do logcat descarta a telemetria antes da leitura | resolvida: `-G 16M` + captura ao vivo |
| Q-04 | spec diz `latency_ms`, código emite `elapsed_ms` | registrada |

**Duas conclusões erradas minhas, corrigidas por remedição.** Levantei "o disparador não
responde" e "`selectedAspectRatio` está defasado do bind" como defeitos do app. Os dois
eram o mesmo erro meu: **os controles mudam de posição conforme a proporção da foto** — o
disparador vai de y=1921 (3:4) a y=2455 (Full) — e meus toques em coordenada fixa caíam no
vazio, sem log e sem erro. Refeito localizando por rótulo a cada passo, `takePhoto` recebe
exatamente o que a UI mostra nas três proporções. Registrado em
[`baseline/README.md`](baseline/README.md#armadilhas-encontradas-ao-medir) para que o
roteiro das Tarefas 6, 7 e 11 já nasça certo.

### Sessão: 2026-10-07 — Tarefa 5 (sondagem de capacidade)

- **Agente:** skill `dev`
- **Tarefa:** 5 — Sondagem de capacidade e `evt=caps` estendido
- **Gate:** D concluído · Tarefa 5 **concluída** · Checkpoint da Onda 2 **passou**
- **Testes:** 114 passando, 0 falhando (12 novos em `CapabilityProbeTest`)

**Contexto da retomada.** A spec ficou parada de 06/08 a 07/10. O usuário decidiu
concluí-la antes de publicar o repositório no GitHub; a ordem combinada está na memória do
projeto (refatoração → i18n da UI → preparo do repositório → reescrita do histórico).

**Desenho.** Três peças, cada uma no seu arquivo:

| Peça | Arquivo | Testável onde |
|---|---|---|
| Decisão pura (`CapabilityProbe.decide`) + tipos | `camera/mode/CapabilityProbe.kt` | JVM pura |
| Leitura do HAL → tipos Kotlin | `camera/SensorCharacteristicsReader.kt` | só aparelho real |
| Campos novos de `evt=caps` | `CameraTelemetry.manualSensorFields` | JVM pura |

`SensorCharacteristics` é a fronteira dublável: usa `IntRange`/`LongRange`, não
`android.util.Range`, porque o stub do android.jar devolve `null` nos limites fora do
Robolectric (mesma armadilha do `Size` no CLAUDE.md). Publicado como
`CameraController.deviceCapabilities: StateFlow<DeviceCapabilities>`, começando em
`UNKNOWN` (não oferece nada) para que um modo exigente não pisque no seletor antes da
sondagem.

Decisão defensiva: `MANUAL_SENSOR` declarado **sem** as faixas de ISO ou exposição não
oferece a capacidade — o Pro abriria com escala vazia. O limite analógico é opcional no
Camera2 e sai como `-` na telemetria, sem derrubar a capacidade.

**Mutação pegou um teste fraco.** O caso negativo usava um aparelho sem capacidade **e**
sem faixas; tirar a checagem da capacidade passava em tudo. Entrou o caso "faixas
reportadas sem `MANUAL_SENSOR`" (típico de hardware LIMITED) e as três mutações passaram
a morrer.

**NFR-3 — aviso antecipado.** `CameraManager.kt` foi a **1.163** (+5): declaração do flow,
dois imports, a atribuição e o parâmetro da telemetria. Já compensado em parte: a leitura
de `REQUEST_AVAILABLE_CAPABILITIES` para o HDR passou a reusar `sensor.requestCapabilities`
(−2). **A Tarefa 6 precisa devolver essas 5 linhas** ao tirar o bind para o `ModeBinder`.

**Lint.** Nenhum achado nos arquivos novos. Aparecem 18 `GradleDependency`/
`NewerVersionAvailable` fora do baseline, mas são os mesmos do baseline com a versão
"disponível" atualizada no texto — o lint consulta a versão do dia, e a mensagem deixou de
casar. Não é efeito desta tarefa; reencolher na Tarefa 13.

**Conferência em aparelho — outro aparelho.** O usuário conectou um **Redmi Note 10
(M2101K7AG, Android 12 / API 31)**, não o edge 60 neo. Para a Tarefa 5 serve: o critério é o
`evt=caps` dizer a verdade sobre o aparelho, e a verdade foi tirada **independente do
código**, por `adb shell dumpsys media.camera`:

| Câmera | `dumpsys` (referência) | `evt=caps` do app |
|---|---|---|
| 0 traseira | `MANUAL_SENSOR`, ISO 100–3200, analógico 3200, 65424–30071705440 ns | `manual_sensor=true iso=100-3200 iso_analog_max=3200 exposure_ns=65424-30071705440` |
| 1 frontal | `MANUAL_SENSOR`, ISO 100–1550, analógico 1550, 41992–341545334 ns | `manual_sensor=true iso=100-1550 iso_analog_max=1550 exposure_ns=41992-341545334` |

Valor a valor idêntico, e o valor muda ao virar a câmera e volta ao desvirar — prova de que
vem do sensor ativo, não de constante. APK conferido por dex (`manual_sensor=true` presente)
antes; tela conferida por `content-desc` (33 nós); imagem na tela por luminância (150 ± 52).

**Ressalvas registradas:**

1. **Caso `manual_sensor=false` não observado em aparelho** — as duas câmeras deste aparelho
   reportam a capacidade. Coberto pelos testes JVM; o emulador seria o caso real.
2. **O baseline da Tarefa 1 é do edge 60 neo.** As comparações de iso-comportamento das
   Tarefas 6 e 7 (NFR-1, NFR-4) exigem o mesmo aparelho do baseline — ou um baseline novo
   tirado no Redmi **antes** de começar a Tarefa 6, com `git worktree` no commit atual.
3. Ruído do aparelho: `uiautomator dump` imprime stacktrace do MIUI
   (`theme_compatibility.xml`) e ainda assim gera o dump — não é falha.
4. Preexistente, não desta tarefa: `zoom=1,0x-10,0x` sai com vírgula decimal porque o
   `%.1f` usa o locale do aparelho. Quebra quem fatia por vírgula; candidato a `Locale.ROOT`.

### Sessão: 2026-10-07 — baseline novo no Redmi Note 10

- **Agente:** skill `dev`
- **Motivo:** o aparelho disponível é outro; o usuário escolheu re-tirar o baseline em vez de
  depender do edge 60 neo. Detalhes e dados brutos em
  [`baseline/redmi-note-10/`](baseline/redmi-note-10/README.md).

**Números de referência daqui em diante:** p95 de troca **57 ms** (teto NFR-4: 68,4 ms);
mídia em retrato 3840×2160 / 3264×2448 / 1832×3840 / vídeo 1920×1080; luminância 134 com
cena real; `hdr_supported=true` (no edge 60 neo era false — o caminho de HDR passa a ser
exercitado).

**Achado: Q-06.** O `aspect` de `evt=bind` sai com a proporção **anterior**, sempre por um
passo — o bind acontece antes de o `LaunchedEffect` da UI entregar o rótulo novo. A mídia
prova que a captura está certa. Registrado em `decisions.md` com o encaminhamento: comparar
a sequência observada, não corrigir dentro das Tarefas 6 e 7.

**Erros meus nesta medição, corrigidos antes de registrar:** (1) variável com espaço
interno não se divide em palavras no zsh — o primeiro roteiro não tocou em nada; (2) a
primeira rodada de fotos foi com o aparelho deitado (`targetRotation=3`) e a de Full em
retrato — refeito tudo em retrato; (3) anotei o disparador como "cortado" em Full olhando o
dump; a captura de tela mostrou que só o nó de acessibilidade é recortado.

### Sessão: 2026-10-07 — Tarefa 6 (Vídeo remigrado)

- **Agente:** skill `dev`
- **Gate:** D concluído · Tarefa 6 **concluída** · Checkpoint da Onda 3 **passou**
- **Testes:** 139 passando (25 novos), os 114 preexistentes sem asserção alterada
- **Verificação em aparelho:** [`verificacao/tarefa-6/`](verificacao/tarefa-6/README.md)

**O que mudou.** O bind passa a ligar exatamente o conjunto que o modo declara:
`ModeBinder` (puro, genérico, testado na JVM) traduz `AppUseCase` → use case e aplica o
fluxo 4.2 — recusa religa o modo anterior, nunca deixa a sessão vazia, e não repete a
tentativa quando não há anterior ou quando o anterior é o próprio modo. `ModeSession`
guarda modo pedido × ligado, decide quando sai `evt=mode` e avisa o ViewModel pela
recusa. O ViewModel ganhou `onShutter` (a definição diz a ação; o ViewModel executa) e
`toggleFlash` pergunta `flashBehavior()`. O controller troca `setCameraMode(CameraMode)` por
`applyMode(CameraModeDefinition)`.

**NFR-3 recuperado.** A primeira versão deixou o `CameraManager` em 1.192 linhas. Em vez
de enxugar comentário, saíram dois blocos que esta spec já tinha tocado: a contabilidade
de modo (para `ModeSession`) e a sondagem de EIS/HDR (para `SensorCharacteristicsReader`).
Resultado 1.153, abaixo do teto. O ViewModel cresceu 39 linhas (despacho do disparador,
coletor de recusa); a Tarefa 7 tira dele a regra de EIS por modo e deve compensar.

**Latência: desconfiei de uma melhora.** O "depois" deu p95 41 ms contra 57 ms do baseline
registrado — 28% mais rápido sem causa no código. Re-medi o "antes" na mesma sessão, com o
commit do baseline num `git worktree`: 43 ms. Era condição de medida (bateria), não
código. Lição registrada: latência se compara na mesma sessão.

**Decisões e achados:** Q-07 (aviso visual da recusa adiado para a Tarefa 11, ponte
`CameraMode.definition`, regra de 16:9 para a Tarefa 7, uma linha do baseline de detekt
reescrita e por quê) e Q-08 (modo não reaplicado quando o controller é recriado —
defeito antigo, achado lendo o código, não verificado em aparelho).

### Sessão: 2026-10-07 — Tarefa 7 (Foto remigrado)

- **Agente:** skill `dev`
- **Gate:** D concluído · Tarefa 7 **concluída** · Checkpoint da Onda 4 **passou**
- **Testes:** 149 passando (10 novos)
- **Verificação em aparelho:** [`verificacao/tarefa-7/`](verificacao/tarefa-7/README.md); roteiros em
  [`verificacao/roteiros/`](verificacao/roteiros/README.md)

**O que mudou.** A definição de modo ganhou `aspectRatio`, `stabilization`, `output` e
`moreControls` (Q-09). Com eles saíram as últimas ramificações `CameraMode.VIDEO/PHOTO`:
a regra de 16:9 do `CameraManager`, o EIS por modo do ViewModel e os 9 pontos da tela. A
barra superior deixou de ser dois ramos escritos à mão e passou a ser montada da lista que
o modo declara, com `when` exaustivo sobre `ControlId`. A proporção da pré-visualização
virou função pura (`previewAspect`) com tabela de casos.

**Método de verificação melhorado.** Antes e depois na **mesma sessão**, mesmo roteiro, o
commit anterior construído num `git worktree`. Comparado: árvore de UI com bounds nos 4
estados, mídia, `evt=bind`, ícone do flash pixel a pixel e latência — tudo idêntico. A
árvore de UI com bounds é a prova da reescrita da barra: mesmos controles, mesma ordem,
mesma posição.

**NFR-3.** `CameraScreen.kt` caiu para 2.173 (−47 sobre o baseline; teto 2.100 — a Tarefa 8
tira o seletor). `CameraManager.kt` 1.154. **O ViewModel não compensou:** 732, +38 sobre o
baseline. A regra de EIS encolheu só uma linha; o crescimento é o despacho do disparador e o
coletor de recusa da Tarefa 6. Não é teto do NFR-3 (que fixa só tela e controller), mas a
meta "não crescer" do progresso fica registrada como não atingida até aqui.

### Sessão: 2026-10-07 — Tarefa 8 (seletor + gaveta)

- **Agente:** skill `dev`
- **Gate:** D concluído · Tarefa 8 **concluída** · Checkpoint da Onda 5 **passou**
- **Testes:** 165 passando
- **Decisão do usuário:** gaveta como item "Mais" no carrossel, sempre visível
- **Verificação em aparelho:** [`verificacao/tarefa-8/`](verificacao/tarefa-8/README.md)

**Primeira mudança visível.** O seletor saiu da `CameraScreen` para
`components/ModeSelector.kt` e lê do registro; a gaveta é `components/ModeDrawer.kt`. O enum
`CameraMode` deixou de existir. No aparelho, a árvore de UI difere da Tarefa 7 **só** pelo
nó "Mais" — Vídeo e Foto nas mesmas posições.

**O teste pegou um problema de layout real.** Em janela de 320dp o "Mais" ficava fora da
vista (176dp à direita do centro com Vídeo ativo); em celulares de 360dp sairia cortado. O
carrossel passou a rolar mantendo a centralização. Detalhes e as demais decisões em Q-10,
inclusive os dois testes anteriores à spec que mudaram de forma (não de asserção) porque o
tipo que testavam deixou de existir.

## Situação das tarefas

| Onda | Tarefa | Status | Observações |
|---:|---|---|---|
| 1 | 1. Baseline de comportamento e latência | **Concluída** | edge 60 neo; `MANUAL_SENSOR` confirmado — portão da Onda 2 antecipado |
| 1 | 2. Contrato do registro de modos | **Concluída** | 24 testes JVM puros; `abstract` em vez de `sealed` (Q-05) |
| 1 | 3. Corrigir opt-in do Camera2Interop | **Concluída** | 31 → 0; baseline encolheu 31 sem silenciar nada |
| 1 | 4. Infra de teste de Compose na JVM | **Concluída** | 4 testes de fumaça; suíte 7 s |
| 2 | 5. Sondagem de capacidade + `evt=caps` | **Concluída** | 12 testes JVM; `evt=caps` = `dumpsys` nas 2 câmeras do Redmi Note 10 |
| 3 | 6. Vídeo remigrado | **Concluída** | iso-comportamento conferido no Redmi; `CameraManager` 1.153 |
| 4 | 7. Foto remigrado | **Concluída** | UI, mídia, binds e flash idênticos ao commit anterior na mesma sessão |
| 5 | 8. Seletor + gaveta | **Concluída** | item "Mais" + gaveta; `CameraMode` removido; carrossel rolável |
| 6 | 9. Janela larga com N modos | Pendente | folga 4 |
| 6 | 10. Personalização em Configurações | Pendente | folga 4 |
| 6 | 11. Pro com ISO manual | Pendente | caminho crítico |
| 7 | 12. Obturador manual | Pendente | caminho crítico |
| 8 | 13. Recibo do NFR-2 e fechamento | Pendente | prova que a spec entregou o que prometeu |

## Medições a registrar durante a execução

Preencher conforme as tarefas forem feitas — são a evidência dos NFRs:

| Medida | Baseline (Tarefa 1) | Final (Tarefa 13) | Limite |
|---|---|---|---|
| `CameraScreen.kt` (linhas) | **2.220** ✓ | 2.215 após T6 · 2.173 após T7 · **2.144** após T8 | ≤ 2.100 |
| `CameraManager.kt` (linhas) | **1.158** ✓ | 1.163 após T5 · 1.153 após T6 · **1.154** após T7 | ≤ 1.158 |
| `CameraViewModel.kt` (linhas) | **694** ✓ | 733 após T6 · 732 após T7 · **752** após T8 — agrupar na T10 (Q-10) | não crescer |
| Maior arquivo novo (linhas) | — | | ≤ 400 |
| p95 de `elapsed_ms` na troca de modo | ~~38 ms~~ edge 60 neo · **57 ms** Redmi Note 10 (20 amostras) | | **≤ 68,4 ms** (Redmi) |
| Tempo da suíte `testDebugUnitTest` | **5,9 s** | 7 s (Onda 1) · 10 s (Tarefa 5, 114 testes) | ≤ 90 s |
| `UnsafeOptInUsageError` no `CameraManager` | **31** ✓ | **0** (Tarefa 3) | 0 |
| Recibo do modo-exemplo (`git diff --stat`) | — | | ≤ 1 arquivo + 1 linha |

Campos de `evt=bind`, mídia gerada e capacidades do HAL em
[`baseline/README.md`](baseline/README.md). O campo é `elapsed_ms`, não `latency_ms` (Q-04).

## Análise de paralelização

- **Método:** script `analyze-dependencies.js`
- **Tarefas:** 13 · **Ondas:** 8 · **Caminho crítico:** 8 tarefas
- **Makespan:** 20 unidades (paralelo) contra 26 (sequencial)
- **Clusters independentes:** 1
- **Hash de dependências:** `45dee818a444f5259ebd588b0efdd5010990bd63fb9016579c9eb87bd867d747`
  — se as dependências mudarem, o hash muda e a análise precisa ser refeita.
- **Leitura correta:** a estrutura de ondas é **cadência de verificação**, não plano de
  alocação de pessoas. Com um cluster só e 8 de 13 tarefas no caminho crítico, o paralelismo
  real está quase todo na Onda 1.

## Pendências

- [x] Iniciar a implementação pela Tarefa 1 (skill `dev`)
- [x] Confirmar `MANUAL_SENSOR` no aparelho de teste antes de contar com a Onda 6 —
      confirmado no baseline, quatro ondas antes do checkpoint que o exigia
- [x] **Q-01 decidida** — requisito corrigido para três proporções; 1:1 vai para o ROADMAP
- [x] Tarefa 2 — contrato do registro
- [x] Tarefa 3 — opt-in corrigido
- [x] Tarefa 4 — infra de teste Compose na JVM
- [x] Checkpoint da Onda 1 — app conferido em aparelho, sem mudança de comportamento
- [x] Tarefa 5 — sondagem de capacidade e `evt=caps` estendido
- [x] Checkpoint da Onda 2 — conferido em aparelho real (Redmi Note 10)
- [x] **Antes da Tarefa 6:** baseline novo no Redmi Note 10 — decisão do usuário; ver [`baseline/redmi-note-10/`](baseline/redmi-note-10/README.md)
- [x] Tarefa 6 — Vídeo remigrado; Checkpoint da Onda 3
- [x] Tarefa 7 — Foto remigrado; Checkpoint da Onda 4
- [x] Tarefa 8 — seletor + gaveta; Checkpoint da Onda 5
- [ ] Agrupar o estado de modos do ViewModel na Tarefa 10 (Q-10)
- [ ] Q-06 (`aspect` defasado em `evt=bind`): corrigir em commit próprio depois da Tarefa 7, ou levar ao ROADMAP
- [ ] Q-08 (modo não reaplicado ao recriar o controller): verificar em aparelho e corrigir depois da Tarefa 7
- [ ] Aviso visual da recusa de modo — na Tarefa 11 (Q-07)
- [ ] Levar as armadilhas de medição do baseline para o CLAUDE.md na Tarefa 13
- [ ] Levar o 1:1 para o ROADMAP.md, junto do ramo morto em `cameraXAspectRatio()`

## Bloqueios

Nenhum. A Q-01, que bloqueava a Tarefa 7, foi decidida na mesma sessão em que apareceu.
