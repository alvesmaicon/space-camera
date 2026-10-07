# Backlog de funcionalidades

Mapa do que falta para o app se comparar aos de câmera da Play Store. É levantamento,
não plano aprovado — cada item vira spec quando for a vez dele.

Três documentos dividem o assunto e não devem se sobrepor:

| Documento | Cobre |
|---|---|
| **este** | funcionalidades que o app não tem |
| [REFACTORING.md](REFACTORING.md) | dívida estrutural, o que precisa ser aberto antes |
| [BUILD.md](BUILD.md#release-o-que-falta) | o que falta para publicar (R8, assinatura, loja) |

## O que já existe

Registrado para não voltar à mesa como "falta":

vídeo e foto · resolução e fps por capacidade do aparelho · presets de bitrate · EIS
com sondagem de suporte · HDR · redução de ruído · zoom (dial contínuo + presets de
lente) · toque para focar · compensação de exposição por arraste · flash e lanterna ·
grade · nível de horizonte · timer de gravação (3/5/10s) · mudo · espelho da frontal ·
GPS no EXIF (opcional) · proporção · pausar e retomar · miniatura da última mídia ·
layout adaptativo para janela larga · telemetria `evt=caps` / `evt=bind` / `evt=mode` ·
seletor de modos com gaveta "Mais" e personalização em Configurações · **modo Pro** com
ISO e obturador manuais (até 1/4 s).

## Legenda

- **Esforço:** P = até um dia · M = alguns dias · G = mais de uma semana
- **Onde** aponta os arquivos que a fatia atravessa. Serve de insumo para o spec.
- **Como verificar** vale mais que a estimativa: o [CLAUDE.md](CLAUDE.md#como-diagnosticar-problema-de-aparelho)
  lista as armadilhas que já produziram conclusão errada nesta base. Toda feature
  de capacidade de hardware precisa de aparelho real — o que o emulador reporta depende
  da AVD, e quando ele diz "não suportado" o comportamento defeituoso coincide com o
  correto.

---

## Grupo A — paridade básica

Nada aqui é feature nova. É o que todo app de câmera da loja tem e este não, e é o
grupo com maior retorno por hora de trabalho. Nenhum item depende da refatoração.

### A1. Atender os intents de câmera

**Por que.** O [AndroidManifest.xml](app/src/main/AndroidManifest.xml) só declara
`MAIN`/`LAUNCHER`. Sem os intents de captura o app **não pode ser escolhido como
câmera padrão do sistema**, não aparece quando outro app pede uma foto (WhatsApp,
formulários, digitalização) e não abre no duplo-clique do botão de energia. É o item
que mais separa "meu app" de "app de câmera".

**Escopo.** `android.media.action.IMAGE_CAPTURE`, `.VIDEO_CAPTURE` e
`.STILL_IMAGE_CAMERA`. O modo seguro (`IMAGE_CAPTURE_SECURE`, abrir sobre a tela de
bloqueio) é opcional e pede `showWhenLocked` — deixe para depois.

**Onde.** Manifest + [MainActivity.kt](app/src/main/java/com/spacecamera/MainActivity.kt):
ler a ação recebida, forçar o modo correspondente, gravar em `EXTRA_OUTPUT` quando
vier, e devolver `setResult` com a Uri em vez de continuar na tela.

**Esforço** P · **Risco** o contrato de retorno tem casos: com `EXTRA_OUTPUT` escreve
no destino do chamador; sem ele devolve `data` com miniatura. Errar isso quebra o app
chamador, não o seu.

**Como verificar.** `adb shell am start -a android.media.action.IMAGE_CAPTURE` e
conferir a tela pelo `content-desc` do dump do `uiautomator` — `am start` depois de
`force-stop` retoma a task preservada e já enganou uma rodada de verificação aqui.

### A2. Pinça para zoom

**Por que.** Não existe — `detectTransformGestures` não aparece em
[CameraScreen.kt](app/src/main/java/com/spacecamera/presentation/screens/CameraScreen.kt).
Hoje só o dial e os presets. É o primeiro gesto que qualquer pessoa tenta.

**Onde.** O `pointerInput` da pré-visualização já concorre entre toque para focar
(linha ~880) e arraste de exposição (~909). A pinça entra como terceiro competidor:
tem que ganhar do arraste sem roubar o toque simples.

**Esforço** P · **Risco** conflito de gestos, e é onde a coisa fica chata. Vale um
teste em aparelho com dedo grande antes de fechar.

### A3. Botões de volume e fone como disparador

**Onde.** `onKeyDown` na MainActivity encaminhando para o ViewModel. Cuidado com o
volume durante a gravação (não deve mexer no volume) e com o intent de captura do A1,
onde o disparador precisa finalizar e voltar.

**Esforço** P

### A4. Som de obturador e retorno tátil

**Por que.** Não há `MediaActionSound` nem `HapticFeedback` na base. Além da
expectativa do usuário, som de captura é **exigência regulatória em alguns mercados**
(Japão, Coreia) — se a intenção é publicar global, entra antes e não depois.

**Escopo.** Som no disparo, no início e fim de gravação, bip da contagem regressiva;
tátil no disparador, na troca de modo e no encaixe dos presets de zoom.

**Esforço** P

### A5. Acessibilidade

**Por que.** Há `contentDescription = null` nos controles
([CameraScreen.kt:981](app/src/main/java/com/spacecamera/presentation/screens/CameraScreen.kt#L981),
[:995](app/src/main/java/com/spacecamera/presentation/screens/CameraScreen.kt#L995),
[:1820](app/src/main/java/com/spacecamera/presentation/screens/CameraScreen.kt#L1820),
[SettingsScreen.kt:367](app/src/main/java/com/spacecamera/presentation/screens/SettingsScreen.kt#L367)).
Leitor de tela hoje não navega a UI, e o relatório pré-lançamento do Play Console
aponta isso antes de você publicar.

**Escopo.** Descrições nos ícones, alvo mínimo de 48dp, ordem de foco na barra
superior, e o contraste do estado "desligado" que ficou em 3,5:1 contra o limiar de
4,5:1 — ressalva já aberta em
[specs/2026-08-03-adaptive-layout/tasks.md](specs/2026-08-03-adaptive-layout/tasks.md).

**Esforço** P–M

### A6. Extrair textos e traduzir

**Por que.** `app/src/main/res/values/strings.xml` tem 14 linhas: todo o texto de UI
está fixo no Compose. Com duas telas e meia o custo de extrair é baixo; depois de três
modos novos, não é. Só português limita o alcance na loja a uma fração.

**Esforço** P agora, M depois do Grupo B · **Ordem** faça **antes** do Grupo B.

**Feito** em 2026-10-07: inglês como idioma padrão, português em `values-pt/` (Brasil e
Portugal), e `localeConfig` para o seletor de idioma por app do Android 13+. Outros
idiomas entram como mais uma pasta `values-xx/`.

---

## Grupo B — o que as pessoas comparam

Aqui estão as features que aparecem na descrição da loja e na decisão de instalar.
Quase todas dependem da refatoração (ver a seção final).

### B1. Câmera lenta (120/240 fps)

**Por que.** Junto com o time-lapse, é o que quem procura "câmera com controle manual"
espera achar. A ausência é notada na primeira olhada nos modos.

**Onde.** Sessão de alta velocidade **não sai do CameraX 1.4.0** — o suporte veio na
linha 1.5 (`HighSpeedVideoSessionConfig`). **Confirme na nota de release da versão
antes de planejar**, e conte com subir `camerax` no
[libs.versions.toml](gradle/libs.versions.toml). Alternativa sem subir: sessão
Camera2 constrained-high-speed direto, bem mais caro.

**Esforço** M (com CameraX 1.5) a G (Camera2) · **Depende de** R1, R2

### B2. Time-lapse

**Onde.** Duas estratégias: capturar quadros por `ImageCapture` em intervalo e
codificar, ou gravar normal e acelerar na saída. A primeira dá controle de intervalo
real e é a que os apps da loja usam.

**Esforço** M · **Depende de** R1, R2

### B3. Modo pro: ISO, obturador, foco manual, balanço de branco

**Por que.** É a identidade natural do app. Ele já se vende como controle manual de
qualidade, mas para no meio: expõe bitrate, EIS e HDR e não expõe o que um fotógrafo
chamaria de manual.

**Onde.** `Camera2Interop` + `CaptureRequestOptions` no
[CameraManager.kt](app/src/main/java/com/spacecamera/camera/CameraManager.kt). Atenção:
o `@OptIn(ExperimentalCamera2Interop)` do topo do arquivo **não está surtindo efeito**
hoje (31 achados `UnsafeOptInUsageError` no baseline de lint, e o compilador avisa que
a anotação não é marcador de opt-in válido). Resolver isso é pré-requisito e está no
item 5 do REFACTORING.md.

**Esforço** M · **Depende de** R2, R3 · **Verificar** faixas de ISO e exposição vêm do
HAL; a UI tem que se adaptar ao que `evt=caps` reportar, como já faz com resolução.

**Andamento (2026-10-07).** ISO e obturador entregues pela spec de registro de modos —
`ProMode`, na gaveta, só em aparelho com `MANUAL_SENSOR`. Faltam **foco manual** e
**balanço de branco**, que entram como controles do mesmo modo. E **exposição longa**: o
obturador vai até 1/4 s porque, medido no Redmi Note 10, 1 s levou 23 s para capturar, 4 s
levou 34 s e 28,8 s falhou (`ERROR_CAPTURE_FAILED`). Hipótese a verificar: o CameraX, em
`CAPTURE_MODE_MAXIMIZE_QUALITY`, faz varredura de foco antes da foto, e com quadros longos
ela custa dezenas de quadros. Ver Q-13 da spec.

### B3a. Proporção 1:1

O `CameraManager` mapeia `"1:1"` para 4:3 num ramo que nenhuma UI alcança — sobra de uma
versão anterior (Q-01 da spec de registro de modos). Oferecer 1:1 é acrescentar a
proporção ao ciclo do seletor de foto e um recorte quadrado no caminho de bitmap.

**Esforço** P · **Verificar** a mídia salva sai quadrada nas duas câmeras.

### B4. RAW / DNG

**Onde.** Captura RAW entrou no CameraX na linha 1.5 (formato de saída RAW e RAW+JPEG);
confirme na nota de release. Puxa decisões de armazenamento (arquivo grande, MediaStore,
o `MediaSaver` do item 2 do REFACTORING.md).

**Esforço** M · **Depende de** B3 na UI, R2 no armazenamento

### B5. Extensões do fabricante: noite, retrato, suavização

**Por que.** Dependência `camera-extensions` e `ExtensionsManager`: o processamento é
implementado pela OEM, então você ganha modo noturno e bokeh de qualidade de fábrica
onde o aparelho suporta, sem escrever algoritmo.

**Risco.** Conflita com o toggle de HDR atual (a extensão HDR e o HDR do
`CameraManager` são caminhos diferentes para a mesma coisa — precisa decidir qual
manda), não vale para vídeo, e a disponibilidade varia por aparelho. Só se verifica em
aparelho real.

**Esforço** M · **Depende de** R2

### B6. Foto durante a gravação e rajada

**Onde.** A foto durante gravação exige `Preview` + `VideoCapture` + `ImageCapture`
ligados juntos, combinação que depende do nível de hardware. A rajada é segurar o
disparador com fila de captura.

**Esforço** M · **Depende de** R2 · **Verificar** o bind pode falhar por combinação
não suportada; o `evt=bind` já mede exatamente isso.

### B7. Robustez de gravação

**Por que.** É aqui que app amador quebra na mão do usuário, e nada disso existe hoje.

**Escopo.** Limite de tamanho com corte automático (`Recorder.setFileSizeLimit`);
tempo restante estimado a partir do bitrate e do espaço livre; aviso de armazenamento
baixo antes de começar; aviso térmico via `PowerManager.OnThermalStatusChangedListener`
— em 4K/60 com bitrate alto o aparelho estrangula e o app não avisa nada.

**Esforço** M · **Depende de** R2 · **Prioridade** alta apesar de não vender na loja:
é reclamação de nota 1.

### B8. Áudio

**Escopo.** Escolha da fonte (`CAMCORDER` vs `UNPROCESSED`), medidor de nível na UI —
vem pronto em `RecordingStats.audioStats.audioAmplitude`, é quase de graça — e mudo
real durante a pausa.

**Esforço** P–M · **Depende de** R2

### B9. Gravar com a tela apagada ou o app em segundo plano

**Onde.** Exige serviço em primeiro plano com `FOREGROUND_SERVICE_CAMERA` e
`FOREGROUND_SERVICE_MICROPHONE`, notificação persistente com controle de parar, e
justificativa no formulário da loja.

**Esforço** M–G · **Risco** o mais alto do grupo: muda o ciclo de vida da câmera, que
hoje é amarrado ao `LifecycleOwner` da activity. Faça por último.

---

## Grupo C — maiores, decidir se vale

### C1. Galeria interna

Rever, apagar, compartilhar e cortar sem sair do app. Hoje a miniatura dispara um
`ACTION_VIEW` ([CameraScreen.kt:1297](app/src/main/java/com/spacecamera/presentation/screens/CameraScreen.kt#L1297))
e o usuário sai. Puxa também a decisão pendente do item 0 do REFACTORING.md: as
miniaturas só funcionam para mídia do próprio app, e ampliar isso é escolher entre
`READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO` e o photo picker.

**Esforço** G

### C2. Leitor de QR e código de barras

Esperado hoje em câmera de sistema. ML Kit resolve, ao custo de tamanho de APK.

**Esforço** M

### C3. Nicho pro-vídeo: foco por picos, zebras, perfil plano

Só se o público for esse. Se for, é **aqui** que está a diferenciação — competir com a
câmera nativa em foto automática não tem como dar certo.

**Esforço** G

### C4. Marca d'água e data, padrão de nome de arquivo, gravar no cartão

**Esforço** M · A gravação em cartão via SAF é a parte chata.

### C5. Atalhos de sistema

Atalho do launcher ("gravar vídeo" direto), tile de ajustes rápidos, widget.

**Esforço** P cada

---

## Grupo D — fora do código

Não é feature, mas bloqueia publicar. Detalhado em
[BUILD.md](BUILD.md#release-o-que-falta): sem R8, sem regras de ProGuard, sem
assinatura, sem AAB — mais política de privacidade (o app usa câmera, microfone e
localização), formulário de segurança de dados e material gráfico.

Dois complementos que valem entrar junto:

- **Visibilidade de falha.** Play Vitals sai de graça sem escrever código; Crashlytics
  se quiser pilha e breadcrumb. Hoje um travamento em aparelho alheio é invisível.
- **Teste instrumentado e CI.** Não há nenhum teste em aparelho. Um smoke de
  inicialização da câmera e captura, rodando no CI, pega justamente o que o teste JVM
  não pega — e é onde a base já se enganou duas vezes verificando APK velho.

---

## Pré-requisitos de refatoração para fatia vertical

Esta seção é o insumo do spec de refatoração. A pergunta não é "o que está feio", é
**o que impede uma feature nova de entrar como fatia vertical fina**.

Hoje uma fatia como "câmera lenta" atravessa: manifest → `CameraController` →
`CameraManager` (bind, capacidade, gravação) → `CameraViewModel` → `CameraScreen`
(modo, controles, overlays) → teste. Dos seis pontos, quatro são arquivos grandes onde
o novo caso vira mais um `if` no meio de código existente. Quatro costuras precisam
existir antes:

### R1. Modo como conceito extensível

[CameraMode.kt](app/src/main/java/com/spacecamera/camera/CameraMode.kt) é um enum de
dois valores (`VIDEO`, `PHOTO`) consultado por `when` espalhado pela UI e pelo
controller. Cada modo novo — lenta, time-lapse, pro — multiplica esses `when` e
qualquer um esquecido é um bug silencioso.

**Alvo.** Cada modo declara o que precisa: use cases para ligar, controles que aparecem
na barra, overlays, o que a gravação faz, o que persiste. A UI itera a declaração em
vez de ramificar por enum. Um modo novo passa a ser um arquivo novo, não uma edição em
seis.

**Destrava** B1, B2, B3, C3

**Feito** (spec de registro de modos, 2026-10-07). O enum `CameraMode` não existe mais;
cada modo é um `object` que declara tudo o que precisa, e o registro é uma lista.

### R2. Sondagem de capacidade isolada

Item 2 do REFACTORING.md (`CameraCapabilityProbe`). Toda feature do Grupo B começa com
"o aparelho suporta?", e hoje esse padrão está duplicado à mão dentro do
`CameraManager` para EIS, HDR, ultra-wide e resoluções. Cada capacidade nova repete o
copia-e-cola e mais um par de `StateFlow`.

**Alvo.** Uma capacidade se declara em um lugar, alimenta `evt=caps` automaticamente, e
a UI esconde o controle quando não vem — comportamento que o CLAUDE.md já documenta
como o caminho de diagnóstico ("se algo não aparece na tela, é porque não veio nessa
linha") e que hoje depende de disciplina.

**Destrava** todo o Grupo B

**Parcial.** Existe o conceito (`Capability`, `CapabilityProbe`, `DeviceCapabilities`) e o
gate por modo; `MANUAL_SENSOR` passa por ele. EIS e HDR foram tirados do `CameraManager`
para `SensorCharacteristicsReader`, mas ainda não viraram `Capability` — a próxima
capacidade (alta velocidade, para B1) deve entrar pelo caminho novo.

### R3. Estado agrupado no ViewModel

Item 3 do REFACTORING.md. ~30 `StateFlow` privados espelhados em ~30 públicos, mais 15
blocos de repasse. Modo pro sozinho adiciona ISO, obturador, WB, foco manual e os
limites de cada um: são mais de dez.

**Alvo.** `data class` por assunto e `stateIn` em vez de `collect` de repasse.

**Destrava** B3, B4, B8 · **Ordem** depois de R4, porque mudar a forma do estado obriga
a tocar a UI.

**Parcial.** O estado de modos e o do Pro estão agrupados em `CameraModes`; os ~30
`StateFlow` antigos continuam.

### R4. Overlays e controles como componentes

Item 1 do REFACTORING.md. Cada modo novo quer seu overlay (intervalo do time-lapse,
fps da lenta, escalas do pro) e hoje entraria dentro de um composable de ~1.200 linhas.
O recorte mecânico já está mapeado, e `HorizontalPickerBar` e o dial já são reusáveis.

**Destrava** B1, B2, B3, C3

**Parcial.** Seletor, gaveta, barra superior (montada da lista que o modo declara) e
overlays por modo viraram componentes; um overlay novo entra em `ModeOverlays`, sem tocar
a tela. Os overlays antigos (grade, nível, foco, exposição) continuam na `CameraScreen`.

### Como fica a fatia depois

Com R1–R4 no lugar, "adicionar câmera lenta" deve ser: declarar o modo (R1), declarar a
capacidade de alta velocidade (R2), um `data class` de estado do modo (R3), um overlay
de fps (R4), um evento novo em `CameraTelemetry`, teste de ViewModel com
`FakeCameraController` simulando suporte e ausência de suporte, e verificação em
aparelho real. Nenhum arquivo grande crescendo — que é justamente a regra que o
CLAUDE.md pede ao mexer nesses três arquivos.

**Medido.** O recibo da spec de registro de modos criou um modo de demonstração num ramo
descartável: **1 arquivo novo + 1 linha** no registro, zero edição nos arquivos grandes, e
o modo apareceu na gaveta, ligou só pré-visualização e foto e salvou foto no aparelho. O
roteiro está no CLAUDE.md, em "Como adicionar um modo".

## Ordem sugerida

| Onda | Conteúdo | Por que nesta ordem |
|---|---|---|
| 1 | Grupo A inteiro (A6 no fim) | não depende de refatoração, é o que mais muda a percepção de "completo", e A6 fica barato agora |
| 2 | R1–R4 | pré-requisito das features grandes; R3 depois de R4 — **R1 feito, R2–R4 parciais** (2026-10-07) |
| 3 | B3+B4 (pro/RAW), depois B1+B2 (lenta/time-lapse) | pro é a identidade do app; lenta e time-lapse são o que se procura na loja |
| 4 | B7, B8, B5 | robustez e áudio pagam em nota; extensões dependem de aparelho |
| 5 | Grupo D + B9, depois C conforme vontade | publicar antes de crescer mais |

O Grupo D não precisa esperar a onda 5 — R8, assinatura e política de privacidade podem
andar em paralelo com qualquer coisa, e quanto antes o app estiver publicável, mais
cedo o retorno real substitui esta lista de suposições.
