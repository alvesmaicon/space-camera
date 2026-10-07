# Baseline — Redmi Note 10, antes da Tarefa 6

Baseline **substituto** do de agosto ([`../README.md`](../README.md), edge 60 neo), tirado
porque o aparelho disponível para as Tarefas 6 em diante é outro. Decisão do usuário em
2026-10-07: daqui para frente, NFR-1 e NFR-4 se comparam **neste** aparelho. O de agosto
continua valendo como registro histórico e para o NFR-3.

## Condições

| Item | Valor |
|---|---|
| Commit | `7903b4f` (Tarefa 5) — `GIT_SHA` conferido dentro do dex por `scripts/backup-apk.sh` |
| Por que este commit serve de "antes" | a Tarefa 5 só **acrescentou** campos ao fim de `evt=caps`; nenhum use case, modo ou bind mudou |
| Aparelho | Xiaomi Redmi Note 10 (M2101K7AG), Android 12 / API 31, MIUI — **real** |
| Versão | 0.4.1-debug (versionCode 41) |
| Data | 2026-10-07 |
| Tela conferida | câmera, por `content-desc` (`Flash`, `Trocar câmera`, `Timer desativado`); 32–36 nós conforme modo e miniatura |
| Posição | **retrato** (`targetRotation=0`), câmera traseira voltada para uma cena iluminada |
| Bateria na latência | 31%, no carregador, 34,6 °C |
| Logcat | `adb logcat -G 16M` + captura ao vivo (Q-03) |

## Capacidades do HAL

```
evt=caps camera=0 eis_supported=true hdr_supported=true zoom=1,0x-10,0x
         options=[4K 30,FHD 30,HD 30]
         manual_sensor=true iso=100-3200 iso_analog_max=3200 exposure_ns=65424-30071705440
```

Diferenças contra o edge 60 neo que importam para as próximas tarefas:

| | edge 60 neo | Redmi Note 10 |
|---|---|---|
| `hdr_supported` | false | **true** — o caminho de HDR passa a ser exercitado |
| Opções de vídeo | 4K 30, FHD 30, **FHD 60**, HD 30 | 4K 30, FHD 30, HD 30 |
| ISO | 100–19200 (analógico 4480) | 100–3200 (analógico 3200) |
| Exposição | 1/10000 s a 1/2,5 s | ≈1/15000 s a **30 s** |
| Frontal | — | também `MANUAL_SENSOR`: ISO 100–1550, 41992–341545334 ns |

Os valores de controle manual foram conferidos contra `dumpsys media.camera`
(`capacidades-hal.txt`) — idênticos. O Pro (Onda 6) é verificável neste aparelho, nas
duas câmeras.

## Campos de `evt=bind`

```
evt=bind mode=VIDEO quality=FHD fps=30 aspect=9:16 bitrate=7464960 eis=true  nr=true hdr=false front=false
evt=bind mode=PHOTO quality=FHD fps=30 aspect=9:16 bitrate=7464960 eis=false nr=true hdr=false front=false
```

Como no edge 60 neo, **o único campo que separa Vídeo de Foto é `eis`**.

**`aspect` sai defasado em um passo** — ver Q-06 em `decisions.md`. Ao trocar a proporção,
o bind registra a proporção **anterior**; ao ir de Foto em Full para Vídeo, o Vídeo registra
`aspect=Full`. Para o NFR-1, compare a **sequência** deste roteiro, não o valor esperado
por proporção:

| Passo do roteiro | `aspect` registrado |
|---|---|
| abrir (Vídeo) | 9:16 |
| Vídeo → Foto | 9:16 |
| 9:16 → 3:4 | 9:16 |
| 3:4 → Full | 3:4 |
| Full → 9:16 | Full |
| Foto em Full → Vídeo | Full |

## Mídia gerada — o instrumento real de iso-comportamento

Lida do `MediaStore` (`content query`), em retrato:

| Modo | Proporção na UI | Arquivo | `orientation` |
|---|---|---|---|
| Foto | 9:16 | 3840×2160 | 90 |
| Foto | 3:4 | 3264×2448 | 90 |
| Foto | Full | 1832×3840 | — (caminho bitmap, já rotacionado) |
| Vídeo | — (foto em **Full**) | 1920×1080, 6,1 s | 90 |

O vídeo saiu 16:9 com a foto configurada em Full — AC-3.1 no "antes".

**A posição do aparelho muda a mídia.** Numa primeira rodada o aparelho estava deitado
(`targetRotation=3`) e as fotos saíram com `orientation=180`. Refeito em retrato. Na
comparação da Tarefa 6/7, **confira `targetRotation` no `takePhoto`** antes de comparar
`orientation`.

`evt=photo size=` segue inútil fora do caminho bitmap (`size=0x0` em 9:16 e 3:4), como em
agosto.

## Gravação

```
evt=rec_start quality=FHD fps=30 bitrate=7464960 mic=true
evt=rec_stop  media=1000000080 duration_ms=6060 size_bytes=5848552
```

Só o id do MediaStore, sem caminho. O log inteiro deste baseline (`logcat-completo.txt`)
foi varrido: 0 ocorrências de `/storage`, `/sdcard`, `content://`, `.jpg`, `.mp4` ou serial.

## Latência da troca de modo (NFR-4)

20 trocas Vídeo↔Foto, foto em 9:16, coordenadas localizadas por rótulo **antes** do laço
e nenhum dump durante. 20 binds para 20 toques, alternando sem falha.

| Medida | Valor |
|---|---|
| Amostras | 20 |
| Mínimo | 48 ms |
| Mediana | 51 ms |
| Média | 52,0 ms |
| p90 | 56 ms |
| **p95** | **57 ms** (posto mais próximo) |
| Máximo | 61 ms |

**Teto do NFR-4 neste aparelho: p95 ≤ 68,4 ms** (57 + 20%). O edge 60 neo fazia 38 ms; os
dois números não se comparam entre si.

## Imagem na tela

Luminância média da faixa central da captura de tela: **134,1** (desvio 38,7), com cena
real. É a referência para "tem imagem" nas Tarefas 6 e 7 — `evt=bind` com sucesso não
prova isso.

## Tamanho dos arquivos (NFR-3) em `7903b4f`

| Arquivo | Linhas | Teto |
|---|---:|---|
| `CameraScreen.kt` | 2.220 | ≤ 2.100 |
| `CameraManager.kt` | **1.163** | ≤ 1.158 — a Tarefa 6 precisa devolver 5 |
| `CameraViewModel.kt` | 694 | não crescer |

## Armadilhas novas, específicas deste aparelho

1. **A tela apaga e entra em Always-On do MIUI.** O dump do `uiautomator` passa a trazer 2
   nós do `com.miui.aod`. Use `adb shell svc power stayon true` durante a sessão (e
   `false` no fim). O aparelho tem bloqueio: quem desbloqueia é o dono, não o roteiro.
2. **`uiautomator dump` imprime stacktrace do MIUI** (`theme_compatibility.xml`) e gera o
   dump mesmo assim — não é falha.
3. **Em Full, o nó do disparador é recortado pela janela** (220×160 em vez de 220×220),
   embora o botão apareça inteiro na tela. Localizar o disparador por quadratura falha;
   use largura e centro.
4. **Bateria abaixo de 30% com a câmera aberta sobe devagar** — na casa de 1% a cada
   vários minutos, mesmo no carregador. Planeje a latência para o fim, ou carregue antes.
