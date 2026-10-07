# Verificação da Tarefa 8 — Seletor orientado ao registro, com gaveta

Redmi Note 10 (API 31), 2026-10-07. APK conferido pelo dex (texto do estado vazio da gaveta
presente). Mesmo roteiro da Tarefa 7 (`../roteiros/roteiro-t7.sh`), comparado com a rodada
"depois" daquela tarefa.

## O que mudou na tela — e só isso

`diff` da árvore de UI (texto, descrição, clicável, bounds) contra a Tarefa 7, nos 4 estados:

```
== video / video-mais
> Mais||True|[958,1748][1080,1880]
== foto / foto-mais
> Mais||True|[716,1748][848,1880]
```

A única diferença é o item **"Mais"**. Vídeo e Foto continuam exatamente nas mesmas
posições — o carrossel rolável reproduz a centralização do de antes. Mídia nas três
proporções, `evt=bind`, ícone do flash (MD5 nos 4 passos) e latência (p95 41 ms)
idênticos à Tarefa 7.

Com Vídeo ativo o "Mais" fica encostado na borda direita, inteiro (captura de tela
conferida). Em tela mais estreita que 392dp ele sai da vista — por isso o carrossel passou
a rolar, com teste em 320dp.

## Gaveta

| Ação | Resultado |
|---|---|
| Tocar em "Mais" | painel com "Mais modos", "Editar" e "Todos os modos já estão no seletor." (gaveta vazia até o Pro) |
| Tocar fora do painel | fecha, sem focar nem trocar de modo |
| "Voltar" do sistema | fecha, e continua na câmera |
| "Editar" | abre Configurações (FR-17); voltar traz a câmera com a gaveta fechada |

## Gesto de deslizar (substitui `CameraMode.next()/previous()`)

```
evt=mode from=video to=photo   ← esquerda
evt=mode from=photo to=video   ← esquerda, dando a volta
evt=mode from=video to=photo   ← direita, dando a volta para trás
```
