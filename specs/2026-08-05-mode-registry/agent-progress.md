# Progresso do agente — Registro de Modos Extensível

## Estado atual

- **Fase:** Implementação — Onda 3
- **Gate atual:** **Checkpoint da Onda 2 passou** — pronto para a Onda 3
- **Status:** em andamento — próxima é a Tarefa 6 (Vídeo remigrado)
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

## Situação das tarefas

| Onda | Tarefa | Status | Observações |
|---:|---|---|---|
| 1 | 1. Baseline de comportamento e latência | **Concluída** | edge 60 neo; `MANUAL_SENSOR` confirmado — portão da Onda 2 antecipado |
| 1 | 2. Contrato do registro de modos | **Concluída** | 24 testes JVM puros; `abstract` em vez de `sealed` (Q-05) |
| 1 | 3. Corrigir opt-in do Camera2Interop | **Concluída** | 31 → 0; baseline encolheu 31 sem silenciar nada |
| 1 | 4. Infra de teste de Compose na JVM | **Concluída** | 4 testes de fumaça; suíte 7 s |
| 2 | 5. Sondagem de capacidade + `evt=caps` | **Concluída** | 12 testes JVM; `evt=caps` = `dumpsys` nas 2 câmeras do Redmi Note 10 |
| 3 | 6. Vídeo remigrado | Pendente | maior risco da spec |
| 4 | 7. Foto remigrado | Pendente | |
| 5 | 8. Seletor + gaveta | Pendente | **primeira mudança visível ao usuário** |
| 6 | 9. Janela larga com N modos | Pendente | folga 4 |
| 6 | 10. Personalização em Configurações | Pendente | folga 4 |
| 6 | 11. Pro com ISO manual | Pendente | caminho crítico |
| 7 | 12. Obturador manual | Pendente | caminho crítico |
| 8 | 13. Recibo do NFR-2 e fechamento | Pendente | prova que a spec entregou o que prometeu |

## Medições a registrar durante a execução

Preencher conforme as tarefas forem feitas — são a evidência dos NFRs:

| Medida | Baseline (Tarefa 1) | Final (Tarefa 13) | Limite |
|---|---|---|---|
| `CameraScreen.kt` (linhas) | **2.220** ✓ | | ≤ 2.100 |
| `CameraManager.kt` (linhas) | **1.158** ✓ | 1.163 após Tarefa 5 (+5) | ≤ 1.158 |
| `CameraViewModel.kt` (linhas) | **694** ✓ | | não crescer |
| Maior arquivo novo (linhas) | — | | ≤ 400 |
| p95 de `elapsed_ms` na troca de modo | **38 ms** (20 amostras) | | **≤ 45,6 ms** |
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
- [ ] **Antes da Tarefa 6:** decidir aparelho de comparação — edge 60 neo (baseline existente) ou baseline novo no Redmi
- [ ] Levar as armadilhas de medição do baseline para o CLAUDE.md na Tarefa 13
- [ ] Levar o 1:1 para o ROADMAP.md, junto do ramo morto em `cameraXAspectRatio()`

## Bloqueios

Nenhum. A Q-01, que bloqueava a Tarefa 7, foi decidida na mesma sessão em que apareceu.
