# Verificação da Tarefa 7 — Foto remigrado

Redmi Note 10 (API 31), 2026-10-07. **Antes e depois na mesma sessão**, com o mesmo
roteiro (`roteiro-t7.sh`) rodando primeiro no APK de `7946a06` (Tarefa 6, construído num
`git worktree`) e depois no APK desta tarefa — a lição de método da Tarefa 6. APKs
distinguidos pelo dex (`NOISE_REDUCTION` aparece 1× no antigo, 4× no novo). Aparelho
deitado, sem ser tocado entre as duas rodadas.

## O que se comparou

| Comparação | Resultado |
|---|---|
| Árvore de UI — texto, descrição, clicável **e bounds** — em Vídeo, Vídeo + "mais opções", Foto, Foto + "mais opções" | **idêntica** nos 4 estados (20, 30, 19, 27 nós) — `antes/ui-*.txt` × `depois/ui-*.txt` |
| Mídia nas três proporções (FR-4) | **idêntica**: 3840×2160 / 3264×2448 / 3840×1832 |
| `evt=bind` sem `elapsed_ms`, 11 linhas, incluindo Foto→Vídeo→Foto→Vídeo | **idêntico** — EIS desliga na Foto e volta ligado no Vídeo (AC-4.1) |
| Ícone do flash na Foto, recortado da tela a cada toque (AC: OFF→AUTO→ON→OFF) | **idêntico pixel a pixel** — mesmo MD5 nos 4 passos, e o 4º igual ao 1º |
| Latência, 20 trocas (NFR-4) | p95 **43 ms** antes, **43 ms** depois |
| Imagem na tela | luminância 145,3 |

A árvore de UI é a prova principal desta tarefa: a barra superior deixou de ser dois
ramos escritos à mão (Vídeo e Foto) e passou a ser montada a partir de
`definition.controls` e `definition.moreControls`. Bounds idênticos significam mesmos
controles, mesma ordem e mesma posição.

## Mutação

| Mutação | Testes que falham |
|---|---:|
| Foto passa a seguir a preferência de EIS | 3 |
| Regra `OFF` liga o EIS no ViewModel | 2 |
| 3:4 deixa de girar em janela larga | 1 |
| Vídeo passa a seguir a proporção da foto | 1 |
| Vídeo perde o microfone da linha expandida | 1 |

Os logs das duas rodadas foram varridos: 0 ocorrências de caminho, URI ou serial.
