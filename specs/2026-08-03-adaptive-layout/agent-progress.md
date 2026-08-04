# Progresso — Layout Adaptativo

## Estado atual

- **Fase:** Implementação — Wave 0
- **Gate atual:** D (documentação da Task 1) / bloqueado na Task 5
- **Status:** Task 1 concluída; Task 5 bloqueada em decisão do usuário (Q-01)
- **Confiança:** 93%
- **Última atualização:** 2026-08-04

## Log de sessões

### Sessão 2026-08-03 — skill `spec`

**Gates:** 0 a 5

**Ações:**

- Contexto do repositório levantado direto do código, não de documentação. Achado
  decisivo: o app **já** trata rotação física mantendo o layout em retrato e girando
  os ícones por acelerômetro (`rollDegrees` → `snappedIconRotation`). O
  comportamento "controles na lateral" já existe; o que falta é o caso da janela
  larga com o aparelho em pé.
- Pesquisa externa com `WebSearch`/`WebFetch` (o `perplexity_research` não estava
  disponível). Três tópicos: escapatória do Android 16, `targetRotation` do CameraX
  com orientação destravada, detecção de janela larga em Compose.
- **Correção relevante:** descoberto que existe opt-out oficial —
  `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`. Uma conclusão anterior desta
  sessão afirmava que não havia; a conclusão vinha de um nome de propriedade
  inventado. Verificado em emulador: com a propriedade, a janela vira
  `Rect(680,0-1880,1600)` e a configuração reporta `w600dp h800dp port`.
- **Correção de escopo pelo usuário:** a pergunta de elicitação sobre "paridade
  total vs entrega parcial" superdimensionava o trabalho. O usuário apontou que
  bastaria detectar janela larga e inverter o layout existente. Verificação no
  código confirmou: os três blocos são âncoras de um `Box` e os overlays são
  posicionados por coordenada. A spec foi reescrita em torno disso (ADR-001).
- **Requisito novo do usuário:** legibilidade dos controles sobre imagem clara,
  observado na captura do tablet. Virou FR-5 + NFR-2, com medida objetiva de
  contraste (4,5:1, WCAG AA) em vez de "colocar um fundo".
- 7 FR e 5 NFR definidos; 3 ADRs; 6 tarefas em 3 ondas; rastreabilidade em três vias
  validada.

**Próximo:** implementação pela skill `dev`, começando pela Wave 0 (Task 1 e Task 5
em paralelo).

### Sessão 2026-08-04 — skill `dev`, Wave 0

- **Agent:** dev
- **Gates:** A, B, C, D
- **Testes:** 45 passando (38 anteriores + 7 novos), 0 falhando
- **Detekt:** limpo, sem novo item de baseline

**Task 1 — concluída (Gate C verde).**

Ciclo TDD respeitado: `WindowAxisTest` escrito primeiro contra um stub
`isWideWindow(...) = false`; RED com 3 `AssertionError` nos casos que discriminam
janela larga (1280×800, 801×800, 891×411) e 4 verdes triviais nos de retrato — o que
confirma que os testes medem o predicado, não a compilação.

Entregue em `presentation/layout/WindowAxis.kt`, arquivo novo em vez de dentro da
`CameraScreen.kt` (ajuda o NFR-5 e permite reuso na Task 6):

- `isWideWindow(widthDp, heightDp)` — pura, JVM
- `rememberIsWideWindow()` — lê `LocalConfiguration` (ADR-002)
- `AxisScope` com `Modifier.axisWeight()` e `AxisContainer(vertical, spacing)` (ADR-003)
- `RowScope.TopBarSlot` → `AxisScope.TopBarSlot`; os dois `Row` da barra superior
  passaram a `AxisContainer(vertical = false)`

Nada consome o predicado ainda — o app se comporta como antes, que é o critério do
checkpoint da Wave 0.

**Task 5 — bloqueada.** Ver Q-01 em `decisions.md`. Três medições em AVD Tablet_API36
(correspondência 1:1 entre `Display.getRotation()` e o acelerômetro nos quatro
estados; inspeção do conteúdo do MP4 e do JPEG salvos; captura de tela com os ícones
em dupla rotação) não reproduzem a premissa do FR-6. A composição especificada
tornaria a mídia torta na janela livre. A ponte foi removida **só localmente** para o
experimento e já está restaurada no manifesto.

## Status das tarefas

| Tarefa | Onda | Status | Observação |
|---|---|---|---|
| 1. Predicado + helper de eixo | 0 | **Concluída** | 7 testes; contrato pronto, Wave 1 destravada |
| 5. Rotação combinada da mídia | 0 | **Bloqueada** | Q-01 — premissa do FR-6 não se reproduz |
| 2. Barra superior | 1 | Pendente | |
| 3. Controles inferiores + proporção | 1 | Pendente | Caminho crítico |
| 4. Barras auxiliares + contraste | 1 | Pendente | |
| 6. Leitura, ponte, integração | 2 | Pendente | Remove o opt-out do manifesto |

## Pendências

- [x] Iniciar a Wave 0
- [ ] Decidir Q-01 (redefinição do FR-6) para desbloquear a Task 5
- [ ] Commit da Task 1 (Gate E — aguarda autorização)

## Bloqueadores

**Q-01** — a premissa do FR-6 não se reproduz em aparelho. Bloqueia a Task 5 e
recomenda-se resolver antes da Wave 1: a dupla rotação dos ícones confunde a
verificação visual das barras laterais.

## Notas para quem implementar

1. **Retrato é o caminho default.** Escreva os condicionais de forma que qualquer
   falha na detecção caia no layout de hoje. NFR-1 é Critical.
2. **A ponte fica até a Task 6.** Enquanto `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`
   estiver no manifesto, o tablet continua pillarboxed e você **não vê** o layout de
   paisagem. Para testar durante as Waves 0 e 1, remova a propriedade localmente e
   não comite.
3. **FR-6 não se verifica na tela.** O arquivo pode sair torto com a pré-visualização
   perfeita. Puxe o arquivo com `adb pull` e inspecione o metadado de rotação.
4. **A telemetria já ajuda.** `scripts/logcat.sh 'evt=bind'` mostra `aspect` e
   `elapsed_ms` — é como se verificam FR-3 e NFR-3 sem instrumentar nada novo.
