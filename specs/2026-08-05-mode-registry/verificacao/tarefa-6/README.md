# Verificação da Tarefa 6 — Vídeo remigrado

Redmi Note 10 (API 31), 2026-10-07, contra o [baseline do mesmo aparelho](../../baseline/redmi-note-10/README.md).
APK conferido por dex antes de cada medição (`evt=mode ` presente no novo, ausente no antigo).
Como o código ainda não estava commitado, o `GIT_SHA` dos dois APKs é o mesmo
(`f896a2d`); quem distingue um do outro é o `evt=mode` no primeiro bind.

## Iso-comportamento (NFR-1)

| Verificação | Antes | Depois |
|---|---|---|
| `evt=bind` do Vídeo | `mode=VIDEO quality=FHD fps=30 aspect=9:16 bitrate=7464960 eis=true nr=true hdr=false front=false` | idêntico |
| `evt=bind` da Foto | o mesmo com `eis=false` | idêntico |
| Sequência de `aspect` nas trocas de proporção (Q-06) | 9:16 → 9:16 → 3:4 → Full | idêntica |
| Foto 9:16, aparelho deitado (`targetRotation=3`) | 3840×2160, orientation 180 | 3840×2160, orientation 180 |
| Foto 3:4, aparelho deitado | 3264×2448, orientation 180 | 3264×2448, orientation 180 |
| Vídeo com a foto em Full | 1920×1080 | 1920×1080 (AC-3.1) |
| Imagem na tela (luminância) | 134,1 | 136,9 |
| Testes JVM preexistentes | 114 | 114, **nenhuma asserção alterada** |

O aparelho estava deitado nesta rodada; por isso a comparação de foto usa as fotos
**deitadas** da primeira rodada do baseline, não as em retrato. Full deitado não tem par
no baseline (3840×1832, o recorte girado) e ficou fora da comparação. O vídeo saiu com
`orientation=180` pela mesma razão — o acelerômetro marcava x = −9,94.

## Gravação

Iniciar, pausar, retomar e parar: `evt=rec_start` e `evt=rec_stop` normais, vídeo
1920×1080 salvo. Durante a gravação o seletor de modos **não está na árvore de UI** — a
guarda do FR-15 vale já na tela; o teste `nao troca de modo enquanto grava` cobre a do
ViewModel.

## Telemetria nova (FR-13)

```
evt=mode from=- to=video use_cases=preview,video_capture,image_capture elapsed_ms=135
evt=mode from=video to=photo use_cases=preview,video_capture,image_capture elapsed_ms=43
```

Sai no primeiro bind e a cada troca de modo efetiva — **20 linhas para 20 trocas** — e
nunca nos rebinds por proporção. Log inteiro varrido: 0 ocorrências de caminho, URI ou
serial (NFR-7).

## Latência (NFR-4)

| Medição | p95 | mediana | condições |
|---|---:|---:|---|
| Baseline registrado (`494efc4`) | 57 ms | 51 ms | bateria 31% |
| **Antes, re-medido agora** (`f896a2d` em `git worktree`) | **43 ms** | 40,5 ms | bateria 36%, 34,8 °C |
| **Depois** (Tarefa 6) | **41 ms** | 39,5 ms | bateria 36%, 34,9 °C |

O "depois" saiu 28% mais rápido que o baseline registrado, o que **não** se explica por
nada que a tarefa fez. Por isso o "antes" foi re-medido na mesma sessão, com o commit do
baseline num `git worktree`: 43 ms contra 41 ms, igual dentro do ruído. A diferença para
os 57 ms é de condição de medida (bateria mais baixa naquela sessão), não de código.

**Lição de método:** latência se compara **na mesma sessão**, antes e depois em sequência,
nunca contra um número registrado em outro dia. O teto do NFR-4 continua satisfeito pelas
duas leituras (≤ 68,4 ms pelo baseline; ≤ 51,6 ms pelo "antes" da mesma sessão).
