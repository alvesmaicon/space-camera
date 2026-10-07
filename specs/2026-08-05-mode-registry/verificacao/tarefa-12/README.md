# Verificação da Tarefa 12 — Obturador manual e retorno ao automático

Redmi Note 10 (API 31), 2026-10-07. APK conferido pelo dex ("Escala do obturador").

## Obturador muda o brilho (FR-10) — ISO em AUTO, congelado do AE

| Obturador | Luminância |
|---|---:|
| AUTO | 63,0 |
| 1/30 s | 55,4 |
| 1/4 s | 153,7 |
| 1 s | 229,1 |

`evt=ae_frozen exposure_ns=30000000 ae_iso=1772 ae_boost=100` — o ISO automático ficou
congelado no valor do AE, como o tempo na Tarefa 11.

Foto a 1/4 s: **EXIF `ISO=1772`, `1/4 s`**.

## Retorno ao automático (FR-11, AC-11.1)

Obturador em AUTO com o ISO manual → o tempo volta ao congelado do AE (`exposure_ns=33333332`);
ISO também em AUTO → `evt=manual iso=auto` e o AE retoma.

## Exposição longa — medida, e por isso limitada (Q-13)

| Exposição | Tempo até a foto salvar |
|---|---:|
| 1/4 s | 3,7 s (2,7 s na segunda rodada) |
| 1 s | 23 s |
| 4 s | 34 s |
| 28,8 s | **falha** após ~4 min — `evt=photo_failed reason=captura code=3` |

Decisão do usuário: a escala vai até **1/4 s**. Conferido em aparelho: o topo da escala
mostra "1/4" e a captura no teto leva ~3 s. Exposição longa virou item de ROADMAP.

Durante a rodada, a pré-visualização com quadro de 28,8 s deixou o `uiautomator dump` sem
conseguir ler a tela, e as opções do Camera2 só foram confirmadas no fim do quadro
seguinte (30 s depois). Sem ANR: o app seguiu em foco.

## Telemetria de arraste

Arrastar a escala chamava `evt=manual` a cada quadro (~60 linhas/s, enchendo o anel do
logcat — a armadilha da Q-03). Agora registra o valor final 400 ms depois de o gesto parar:
um arraste de 1,2 s gerou **1** linha.

## Observação de método

No meio da rodada apareceram gestos de arrastar nas duas escalas que não eram do roteiro —
o usuário estava experimentando o Pro no aparelho. As medidas afetadas (um "1 s" que virou
o mínimo da escala) foram refeitas com o aparelho intocado.
