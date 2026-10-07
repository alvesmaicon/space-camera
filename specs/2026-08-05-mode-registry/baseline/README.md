# Baseline — antes do registro de modos

Medição da Tarefa 1, tirada **antes de qualquer alteração de código** desta spec. É a
referência contra a qual NFR-1 (iso-comportamento), NFR-3 (tamanho) e NFR-4 (latência)
serão comparados na Tarefa 13.

## Condições

| Item | Valor |
|---|---|
| Commit | `f09c091` — `GIT_SHA` conferido dentro do dex por `scripts/backup-apk.sh` |
| Aparelho | Motorola edge 60 neo, Android 16, API 36 — **real, não emulador** |
| Versão | 0.4.1-debug (versionCode 41) |
| Data | 2026-08-05 |
| Tela conferida | câmera, por `content-desc` no dump do `uiautomator` (`Flash`, `Trocar câmera`, `Timer desativado`), 32 nós |
| Cena | aparelho sobre a mesa, câmera voltada para baixo — irrelevante para os campos medidos, mas **inválida para luminância** |

## Capacidades do HAL

```
evt=caps camera=0 eis_supported=true hdr_supported=false zoom=1,0x-10,0x
         options=[4K 30,FHD 30,FHD 60,HD 30]
```

Sondado direto do HAL por `dumpsys media.camera` (ver `capacidades-hal.txt`), porque o
`evt=caps` de hoje ainda não traz controle manual — é a Tarefa 5 que o estende:

| Capacidade | Valor |
|---|---|
| `MANUAL_SENSOR` | **presente** |
| `SENSOR_INFO_SENSITIVITY_RANGE` | 100–19200 |
| `SENSOR_MAX_ANALOG_SENSITIVITY` | 4480 |
| `SENSOR_INFO_EXPOSURE_TIME_RANGE` | 100.000–400.000.000 ns (1/10000 s a 1/2,5 s) |
| Outras | `RAW`, `MANUAL_POST_PROCESSING`, `READ_SENSOR_SETTINGS`, `BURST_CAPTURE` |

**O portão de viabilidade do Checkpoint da Onda 2 está atendido** — este aparelho suporta
controle manual, então a Onda 6 (Pro) é verificável nele. Antecipado aqui de propósito:
descobrir o contrário na Onda 2 custaria a spec inteira.

As faixas reais divergem dos exemplos ilustrativos dos critérios de aceite (AC-9.1 fala em
50–3200; AC-10.1 em 1/8000 s a 1/4 s). Os AC valem pela **regra** — limitar à faixa
reportada —, não pelos números, que são do aparelho.

## Campos de `evt=bind` — o que NFR-1 vai comparar

Estes são os campos **preexistentes**. A comparação da Tarefa 13 é sobre eles; o que a
FR-13 acrescentar é aditivo e fica fora (senão FR-13 e NFR-1 se contradiriam).

```
evt=bind mode=VIDEO quality=FHD fps=30 aspect=9:16 bitrate=7464960 eis=true  nr=true hdr=false front=false
evt=bind mode=PHOTO quality=FHD fps=30 aspect=9:16 bitrate=7464960 eis=false nr=true hdr=false front=false
evt=bind mode=PHOTO quality=FHD fps=30 aspect=3:4  bitrate=7464960 eis=false nr=true hdr=false front=false
evt=bind mode=PHOTO quality=FHD fps=30 aspect=Full bitrate=7464960 eis=false nr=true hdr=false front=false
```

**O único campo que separa Vídeo de Foto é o `eis`.** Vale registrar porque muda o método:
comparar `evt=bind` sozinho é fraco como rede de segurança. Dois cuidados:

1. **`aspect` é a proporção da caixa de pré-visualização, não a de captura.**
   [CameraManager.kt:548-550](../../../app/src/main/java/com/spacecamera/camera/CameraManager.kt#L548)
   é explícito: vem de `previewAspectLabel`, que a UI define conforme a janela. Ele
   acompanha a seleção de proporção da foto, mas **não prova** AC-3.1 ("a proporção de
   captura é 16:9"). Para isso, o instrumento é a mídia salva.
2. **Capturar o baseline com a foto em 9:16 esconde a diferença**, porque aí os dois modos
   reportam `aspect=9:16`. Um defeito que fizesse a Foto herdar a proporção do Vídeo passaria
   invisível. Por isso as três proporções estão acima.

## Mídia gerada — o instrumento real de iso-comportamento

Medido no arquivo salvo (cabeçalho JPEG e `MediaStore`), não na telemetria:

| Modo | Proporção na UI | Arquivo | Proporção real |
|---|---|---|---|
| Foto | 9:16 | 4096×2304 | 16:9 |
| Foto | 3:4 | 4096×3072 | 4:3 |
| Foto | Full | 1840×4096 | recorte para a tela |
| Vídeo | — | 1920×1080 | 16:9 |

O vídeo saiu 16:9 com a foto configurada em "Full" — que é exatamente o que AC-3.1 exige e
o que a remigração da Tarefa 6 não pode quebrar.

`evt=photo size=` **não serve** como instrumento: no caminho direto (sem realce e fora do
"Full") o código reporta o alvo do preset, e o preset MAXIMA não tem alvo, então sai
`size=0x0` ([CameraManager.kt:919-921](../../../app/src/main/java/com/spacecamera/camera/CameraManager.kt#L919)).
Só o caminho bitmap ("Full" ou realce ligado) reporta o tamanho de verdade — daí o
`size=1840x4096` aparecer no log só nesse caso.

## Gravação

```
evt=rec_start quality=FHD fps=30 bitrate=7464960 mic=true
evt=rec_stop  media=26018 duration_ms=6796 size_bytes=6579659
```

`media=` é o id do MediaStore, sem caminho — conforme a regra de privacidade do NFR-7, que
o código novo precisa manter.

## Latência da troca de modo (NFR-4)

20 trocas Vídeo↔Foto consecutivas, app em primeiro plano. **Atenção ao nome do campo:** a
spec chama de `latency_ms`, mas a telemetria emite **`elapsed_ms`**
([CameraTelemetry.kt:40](../../../app/src/main/java/com/spacecamera/camera/CameraTelemetry.kt#L40)).

| Medida | Valor |
|---|---|
| Amostras | 20 (as 2 do arranque ficaram de fora) |
| Mínimo | 29 ms |
| Mediana | 36 ms |
| Média | 34,9 ms |
| p90 | 38 ms |
| **p95** | **38 ms** |
| Máximo | 42 ms |
| Arranque da câmera (referência) | 97 ms e 34 ms |

**Teto do NFR-4: p95 ≤ 45,6 ms** (38 + 20%).

Valores brutos em `evt-bind-latencia.txt`. Bateria acima de 30% e app em primeiro plano nas
duas medições, como o NFR-4 exige.

## Tamanho dos arquivos (NFR-3)

`wc -l` no commit `f09c091`:

| Arquivo | Linhas | Teto do NFR-3 |
|---|---:|---|
| `CameraScreen.kt` | 2.220 | ≤ 2.100 |
| `CameraManager.kt` | 1.158 | ≤ 1.158 (nenhuma linha líquida) |
| `CameraViewModel.kt` | 694 | não crescer |
| Maior arquivo novo | — | ≤ 400 |

Os números da spec batem com o commit. Vale notar que 2.220 saiu da árvore **depois** de
comitado o trabalho pendente da adaptive-layout; no commit anterior eram 2.216.

## Análise estática (NFR-6)

- `UnsafeOptInUsageError` em `app/lint-baseline.xml`: **31**, todos em `CameraManager.kt`.
  Confere com o que a spec previu. Alvo da Tarefa 3: **0**.

## Suíte de testes (NFR-5)

`./gradlew cleanTestDebugUnitTest testDebugUnitTest`: **5,9 s**. Teto do NFR-5: 90 s.
Folga larga para os testes de Compose sob Robolectric que a Tarefa 4 vai introduzir.

---

## Armadilhas encontradas ao medir

Custaram tempo real nesta sessão e valem para toda tarefa com verificação em aparelho.
Nenhuma está no CLAUDE.md ainda.

### 1. O anel do logcat é de 256 KiB e o `camerahalserver` o esvazia em segundos

Com a câmera aberta, o HAL despeja log continuamente. O anel de 256 KiB chega a 253 KiB
consumidos, e as linhas do app são **expulsas antes de você conseguir dumpar**.

Sintoma: `adb logcat -d` depois do fato reportou 3 e 4 binds onde tinham ocorrido 10. Não
é o app perdendo evento — é o buffer girando. Diagnostiquei errado duas vezes antes de
rodar `adb logcat -g`.

Isso ameaça diretamente o método do NFR-1, que compara `evt=bind` entre dois commits.

```bash
adb logcat -G 16M      # antes de qualquer medição
adb logcat -v time -s SpaceCam:V > saida.txt &   # e capture ao vivo, não com -d
```

### 2. Todos os controles se movem com a proporção da foto

O disparador desce e sobe conforme a caixa de pré-visualização muda de forma:

| Proporção | Disparador |
|---|---|
| 3:4 | (600, 1921) |
| 9:16 | (600, 2188) |
| Full | (600, 2455) |

O seletor de modos também: em Full ele está em y=2246, não y=1979. Roteiro com coordenada
fixa toca no vazio e **falha em silêncio** — nenhum log, nenhum erro.

Isto me levou a levantar dois defeitos que não existiam: "o disparador não responde" e "o
`selectedAspectRatio` está defasado do bind". Refeito com as coordenadas localizadas por
rótulo a cada passo, `takePhoto` recebe exatamente o que a UI mostra nas três proporções.
Localize por rótulo, nunca por coordenada gravada.

### 3. O seletor de modos é um carrossel que recentra o modo ativo

O modo ativo fica sempre em x=600. Então "tocar em Foto" é x=848 vindo do Vídeo, mas x=600
quando Foto já está ativo — e tocar no ativo não faz nada. Um roteiro que alterna entre
duas coordenadas fixas trava depois do primeiro toque.

Importa além do roteiro: FR-6 (plano e gaveta) e FR-14 (janela larga com N modos) são
escritos sobre um seletor que hoje recentra o ativo.

### 4. `uiautomator dump` com a pré-visualização rodando atrapalha

Intercalar dumps entre os toques fez a UI trocar de modo sem a sessão rebindar. Fora do
laço, funciona. Use o dump para localizar e conferir **antes e depois** da medição, não
durante.
