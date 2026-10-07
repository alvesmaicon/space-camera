# Verificação da Tarefa 11 — Modo Pro com ISO manual

Redmi Note 10 (API 31), 2026-10-07. APK conferido pelo dex (`evt=manual` presente). O
aparelho reporta `MANUAL_SENSOR` nas duas câmeras — sem isso, como no emulador, o
comportamento certo e o defeituoso coincidiriam (armadilha 4 do CLAUDE.md).

## O Pro entra pela gaveta e liga só o que declara

```
evt=bind mode=PRO quality=FHD fps=30 aspect=9:16 bitrate=7464960 eis=false nr=true hdr=false front=false
evt=mode from=video to=pro use_cases=preview,image_capture
```

- Gaveta mostra "Pro" (padrão: fora do plano, decisão do usuário).
- **AC-2.1:** só `preview,image_capture` — sem `VideoCapture`.
- Carrossel com o Pro ativo: Vídeo, Foto, **Pro**, Mais (o ativo vindo da gaveta aparece).
- Sem flash na barra, sem HDR na linha expandida; slider de ISO na lateral direita.

## ISO muda o brilho (FR-9) — luminância da pré-visualização

| ISO | Luminância | `evt=manual` |
|---|---:|---|
| AUTO | 101,3 | — |
| 104 (mínimo) | 2,3 | `iso=104 exposure_ns=33333332` |
| 564 (meio) | 6,3 | `iso=564 exposure_ns=33333332` |
| 3057 (máximo) | 24,5 | `iso=3057 exposure_ns=33333332` |

Monotônico e ~10× entre os extremos. O tempo de exposição é o que o AE usava, congelado.

**Por que o ISO máximo fica mais escuro que o AUTO — investigado, não é defeito.** A
telemetria nova `evt=ae_frozen` mostra o que o AE usava no instante do congelamento:

```
evt=ae_frozen exposure_ns=32801068 ae_iso=6400 ae_boost=400
```

O AE estava em ISO 6400 com **boost pós-RAW de 4×** — efetivo ~25 600. Com o AE
desligado o Camera2 devolve o boost a 100, e o slider respeita a faixa declarada pelo
sensor (100–3200, AC-9.1). A cena pede mais luz do que ISO 3200 a 1/30 s entrega; o
obturador manual (Tarefa 12) é o que resolve. Achado registrado: o AE deste aparelho
passa da faixa que o próprio sensor declara (6400 > 3200).

## Foto do Pro (FR-12, AC-12.1)

- Salva no MediaStore pelo caminho da Foto: 3840×2160 em 9:16, id novo.
- Miniatura "Última foto" atualizada.
- **EXIF: `ISO=564`, `1/30 s`** — exatamente o ISO manual e o tempo congelado (lido do
  arquivo; as tags vêm de `EXIF_CAMERA_TAGS`).

## Volta ao automático (FR-11 parcial, ver Tarefa 12)

- "AUTO" no slider → `evt=manual iso=auto`, imagem volta ao brilho do AE.
- **Sair do Pro com ISO manual** → `evt=manual iso=auto` antes do bind da Foto;
  luminância 2,2 → 91,5; escala some. A Foto não herda exposição travada.

## Mutação

| Mutação | Testes que falham |
|---|---:|
| Pro sem exigir capacidade | 7 |
| Pro liga `VideoCapture` | 1 |
| ISO sem limite à faixa | 3 |
| Escala linear em vez de logarítmica | 2 |
| Ignora o tempo congelado | 2 |
| Trocar de modo mantém o ISO manual | 1 |
| Faixas do aparelho não chegam à tela | 3 |
| ISO aceito sem capacidade manual | 1 |

O log das rodadas foi varrido: 0 ocorrências de caminho, URI ou serial.
