# Progresso do agente — Registro de Modos Extensível

## Estado atual

- **Fase:** Planejamento concluído
- **Gate atual:** 5 — Go/No-Go
- **Status:** **GO** — pronto para construir
- **Confiança:** 92%
- **Última atualização:** 2026-08-05

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

## Situação das tarefas

| Onda | Tarefa | Status | Observações |
|---:|---|---|---|
| 1 | 1. Baseline de comportamento e latência | Pendente | **em aparelho real**, antes de qualquer código |
| 1 | 2. Contrato do registro de modos | Pendente | sem consumidores; não muda o app |
| 1 | 3. Corrigir opt-in do Camera2Interop | Pendente | pré-requisito do Pro |
| 1 | 4. Infra de teste de Compose na JVM | Pendente | falta `testImplementation` do `compose-ui-test-junit4` |
| 2 | 5. Sondagem de capacidade + `evt=caps` | Pendente | portão de viabilidade do Pro |
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
| `CameraScreen.kt` (linhas) | 2.220 (a confirmar) | | ≤ 2.100 |
| `CameraManager.kt` (linhas) | 1.158 (a confirmar) | | ≤ 1.158 |
| `CameraViewModel.kt` (linhas) | 694 (a confirmar) | | não crescer |
| Maior arquivo novo (linhas) | — | | ≤ 400 |
| p95 de `latency_ms` na troca de modo | | | ≤ baseline + 20% |
| Tempo da suíte `testDebugUnitTest` | | | ≤ 90 s |
| `UnsafeOptInUsageError` no `CameraManager` | 31 | | 0 |
| Recibo do modo-exemplo (`git diff --stat`) | — | | ≤ 1 arquivo + 1 linha |

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

- [ ] Iniciar a implementação pela Tarefa 1 (skill `dev`)
- [ ] Confirmar `manual_sensor=true` no aparelho de teste antes de contar com a Onda 6

## Bloqueios

Nenhum — especificação aprovada.
