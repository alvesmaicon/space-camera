# Verificação final — Tarefa 13

Redmi Note 10 (API 31), 2026-10-07.

## Recibo do NFR-2 — o custo de um modo novo

Num ramo descartável (`recibo/modo-demo`, apagado depois), um modo de demonstração
("Noite": foto noturna, só pré-visualização e foto, na gaveta):

```
ModeRegistry.kt  |  1 +
NightMode.kt     | 22 ++++++
2 files changed, 23 insertions(+)
arquivos grandes tocados: 0
```

**1 arquivo novo + 1 linha.** Testes, build e detekt passaram sem outra edição. No
aparelho, o modo apareceu na gaveta, entrou no carrossel ao ser escolhido, ligou só
pré-visualização e foto (`evt=mode from=video to=night use_cases=preview,image_capture`) e
salvou foto.

**A primeira tentativa não passou**, e foi útil: o registro sem vírgula final fazia o diff
marcar 2 linhas, e **cinco testes afirmavam o conteúdo exato do registro de produção**
("a gaveta está vazia"), o que faria todo modo novo editar três arquivos de teste. Os
testes passaram a usar registro explícito ou a afirmar propriedades; a lista ganhou
vírgula final. A segunda tentativa, do zero, é a acima.

## NFR-1 — iso-comportamento ponta a ponta

Mesmo roteiro (`../roteiros/roteiro-t7.sh`) no build de **antes da spec** (`f896a2d`) e no
final, na mesma sessão, aparelho na mesma posição:

| Comparação | Resultado |
|---|---|
| Árvore de UI nos 4 estados | idêntica, exceto o item **"Mais"** (a mudança pretendida) |
| Mídia nas três proporções | idêntica |
| `evt=bind` sem latência (11 linhas, com EIS indo e voltando) | idêntico |
| Ícone do flash a cada toque | idêntico pixel a pixel |
| Testes JVM anteriores à spec | passam; os dois que mudaram de forma estão justificados na Q-10 |

E a árvore de UI final é idêntica à da Tarefa 10 — a extração da barra superior para
`components/` na própria Tarefa 13 não mudou nada na tela.

## NFR-4 — latência

| Rodada (mesma sessão, aparelho a 37,5 °C) | p95 |
|---|---:|
| antes da spec (roteiro completo) | 90 ms |
| final (roteiro completo, logo depois) | 102 ms |
| final (A) | 98 ms |
| antes da spec (B) | 97 ms |
| final (A) | 76 ms |

Com o aparelho quente, a variação entre rodadas do **mesmo** build (98 → 76 ms) é maior que
a diferença entre os builds. Não há efeito atribuível ao código; o NFR-4 (≤ +20% sobre o
"antes" da mesma sessão) é atendido.

## NFR-3 — tamanho

| Arquivo | Baseline | Final | Teto |
|---|---:|---:|---|
| `CameraScreen.kt` | 2.220 | **1.830** | ≤ 2.100 ✓ |
| `CameraManager.kt` | 1.158 | **1.154** | ≤ 1.158 ✓ |
| `CameraViewModel.kt` | 694 | 786 | "não crescer" ✗ (meta do progresso, não do NFR-3) |
| Maior arquivo novo | — | 286 (`TopBarControl.kt`) | ≤ 400 ✓ |

Para fechar o teto da `CameraScreen`, a barra superior saiu inteira para
`components/TopBarControl.kt` + `TopBarButtons.kt`. É também o que faltava ao desenho: a
tabela `ControlId` → composable fora da tela, para um controle novo não editá-la.

## NFR-6 — análise estática

- Detekt: baseline **117 → 114**, 0 entradas acrescentadas (as 3 removidas são dívidas do
  `CameraManager` que a spec pagou).
- Lint: 88 no baseline desde a Tarefa 3 (eram 119); fora dele, só os 18 avisos de
  "versão mais nova disponível", que mudam de texto com o tempo. Não regenerado — trocaria
  texto, não dívida.
