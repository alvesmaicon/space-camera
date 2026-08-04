# Progresso — Layout Adaptativo

## Estado atual

- **Fase:** Implementação — Waves 0 e 1 concluídas
- **Gate atual:** E (aguarda autorização de commit da Wave 1)
- **Status:** 5 de 6 tarefas concluídas; falta a Task 6
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

**Task 5 — concluída, redefinida pela Q-01.**

Três medições em AVD Tablet_API36 (correspondência 1:1 entre `Display.getRotation()`
e o acelerômetro nos quatro estados; inspeção do conteúdo do MP4 e do JPEG salvos;
captura de tela com os ícones em dupla rotação) não reproduzem a premissa do FR-6. A
composição especificada tornaria a mídia torta na janela livre. Decisão do usuário:
dividir em duas grandezas.

Entregue em `presentation/layout/DeviceRotation.kt`, 12 testes novos:

- `captureRotation(rollDegrees)` — só o sensor, comportamento idêntico ao anterior.
  Teste de fronteira garante que os limiares de 45°/135° não se deslocaram.
- `uiRotation(rollDegrees, displayRotation)` — travada em quadrante, para os ícones.
- `windowRelativeRoll(...)` — contínua, para o ângulo do nível de horizonte.
- `isLevel` permanece em `rollDegrees`: nivelamento é físico, e usar o valor relativo
  perderia o caso de retrato travado em paisagem, que hoje acende verde.

**Armadilha que só o aparelho pegou.** A primeira ligação lia
`LocalView.current.display?.rotation` direto na composição. Compila, passa nos
testes, e **não funciona**: `view.display` é nulo até a View ser anexada, então o
fallback `ROTATION_0` congela e nada dispara reavaliação — os ícones continuaram
tortos no tablet. A correção é `displayRotation` como estado, atualizado no listener
do acelerômetro, que é o mesmo lugar onde `rollDegrees` muda e cobre também a virada
de 180°, que não altera a `Configuration`.

**Verificação em dois aparelhos:**

| Aparelho | Janela | `mCurrentRotation` | Ícones |
|---|---|---|---|
| Tablet_API36, ponte removida | livre, retrato 1600×2560 | ROTATION_90 | de pé ✓ |
| Pixel_9_Pro, ponte ativa | travada em retrato | ROTATION_0 | giram ✓ (NFR-1) |

A ponte foi removida **só localmente** para o experimento e está restaurada no
manifesto.

### Sessão 2026-08-04 (continuação) — skill `dev`, Wave 1

- **Agent:** dev
- **Gates:** A, C, D (Gate B não se aplica: são mudanças de layout Compose, sem
  lógica nova testável na JVM — o que era testável, o predicado, veio na Task 1)
- **Testes:** 59 passando (2 novos, de regressão do `previewAspectLabel`)
- **Detekt e Lint:** limpos

**Tasks 2, 3 e 4 concluídas.** Em janela larga: barra superior no bordo esquerdo,
controles no direito, pré-visualização 16:9 entre os dois, presets de zoom e seletor
de modo verticais, disco de zoom girado para o mesmo eixo.

**Duas correções vindas de feedback visual do usuário** (Gate D.1), não de teste:

1. *"nossa ta muito feio"* — a primeira versão usava `fillMaxHeight()` mais
   `axisWeight()` nos slots, então os cinco ícones se espalhavam pelos 1600px e a
   barra virava um paredão vazio. Corrigido para **grupos compactos**: em paisagem os
   containers envolvem o próprio conteúdo e a âncora `CenterStart`/`CenterEnd` os
   centraliza. `AxisScope` passou a expor `vertical` para o slot saber que peso só
   faz sentido no eixo horizontal.
2. *"o disco de zoom decimal esta na horizontal mas os seletores estao na barra
   lateral"* — o `LensZoomDial` tinha passado batido. Agora o arco é desenhado sempre
   "deitado" e girado −90° por `Modifier.rotate` quando a janela é larga, dentro de
   uma caixa externa que reserva o espaço na lateral. `requiredSize` é necessário
   porque o conteúdo continua medindo o comprimento do trilho no eixo maior. Como
   `Modifier.rotate` também transforma as coordenadas de toque, o arraste segue lido
   em `dragAmount.x` — no eixo do próprio disco. O rótulo do zoom e os números das
   marcas contrarrotacionam +90° para continuar legíveis.

**Bug encontrado e trancado com teste.** `setPreviewAspectLabel` só repassava
(`cameraManager?.previewAspectLabel = label`), mas a UI chama isso na primeira
composição, antes de `initializeCamera` criar o controller — a atribuição se perdia
em silêncio e o `evt=bind` reportava `9:16` mesmo em tablet. Passou a guardar em campo
e semear no `.also` da criação, como os outros ajustes já faziam. Dois testes novos,
um deles verificado por mutação (removendo a linha de semeadura, só ele falha).

**Nota de método — o Gradle mentiu duas vezes.** Depois dos ciclos de `git stash` do
A/B, `assembleDebug` reportou `UP-TO-DATE` com o APK do build anterior no disco, e
duas rodadas de verificação em aparelho foram feitas contra código velho. Também
`./gradlew assembleDebug detekt` aborta antes de empacotar quando o detekt falha, e
o `adb install` seguinte instala o APK antigo sem reclamar. Desde então, todo APK é
conferido por marcador no dex (`unzip classes*.dex | grep`) antes de instalar. Vale
virar hábito.

**Dívida do Detekt.** Adicionar parâmetro muda a assinatura, que é a chave das
entradas do baseline — o achado antigo reaparece como novo. `ZoomPresetBar` foi
**paga**: `zoomPresets` e `zoomLabel` extraídas, complexidade abaixo do limite e a
entrada removida do baseline (118 → 117 IDs). `LensZoomDial` (152 linhas,
complexidade 30) é grande demais para pagar de passagem, então só as duas chaves
foram atualizadas para a nova assinatura — a dívida segue registrada, nada novo foi
congelado. Descoberta útil: a chave embute comentários da lista de parâmetros, então
KDoc de parâmetro ali torna a entrada frágil.

## Status das tarefas

| Tarefa | Onda | Status | Observação |
|---|---|---|---|
| 1. Predicado + helper de eixo | 0 | **Concluída** | 7 testes; contrato pronto, Wave 1 destravada |
| 5. Captura vs compensação da UI | 0 | **Concluída** | 12 testes; redefinida pela Q-01, verificada nos dois AVDs |
| 2. Barra superior | 1 | **Concluída** | Grupo compacto no bordo esquerdo, x 22–119 de 2560 |
| 3. Controles inferiores + proporção | 1 | **Concluída** | `aspect=16:9`, caixa 1280×720dp, bind em 263–317ms |
| 4. Barras auxiliares + contraste | 1 | **Concluída** | Duas ressalvas em tasks.md: contraste do estado desligado e transbordo |
| 6. Leitura, ponte, integração | 2 | Pendente | Remove o opt-out do manifesto |

## Pendências

- [x] Iniciar a Wave 0
- [x] Decidir Q-01 — usuário escolheu dividir em duas grandezas
- [x] Commit da Task 1 (`8caf152`)
- [x] Commit da Task 5 (`2e2039c`)
- [x] Emenda do FR-6 / criação do FR-9 em `requirements.md`
- [ ] Commit da Wave 1 (Gate E — aguarda autorização)
- [x] Iniciar a Wave 1 (Tasks 2, 3, 4)
- [ ] Decidir as duas ressalvas da Task 4 (contraste do estado desligado, transbordo)
- [ ] Wave 2 — Task 6: largura de leitura em Configurações/Sobre, remoção da ponte,
      verificação integrada nos dois aparelhos

## Bloqueadores

Nenhum. `./gradlew assembleDebug testDebugUnitTest detekt lint` verde com 59 testes.

`CameraScreen.kt` está em **2.141 linhas** contra o teto de 2.240 do NFR-5 — 99 linhas
de folga. A Task 6 ainda precisa mexer nele e vai conferir isso; se apertar, a saída é
extrair a barra superior, o que antecipa parte do item 1 do `REFACTORING.md`.

## Notas para quem implementar

0. **A proporção da câmera é fixada no bind, e o que salva o app é não declarar
   `configChanges`.** Levantado pelo usuário de memória em 2026-08-04 e confirmado:
   `Preview.Builder().setTargetAspectRatio()` e `Recorder.Builder().setAspectRatio()`
   (`CameraManager.kt:415` e `:443`) são definidos **uma vez**, interpretados em
   relação à `targetRotation` daquele instante, e nada os reavalia sem rebind. O
   `Preview` também nunca recebe `setTargetRotation` — medido: a `TransformationInfo`
   reporta `getTargetRotation=-1`.

   Hoje isso não causa problema porque o manifesto **não** declara
   `android:configChanges`: girar recria a activity e rebinda. Medido em janela larga
   `aspect=16:9`, e após a virada `aspect=9:16`, com `evt=bind` novo nas duas.

   **A armadilha:** declarar `configChanges="orientation|screenSize"` — otimização
   tentadora para acelerar a virada e melhorar o NFR-3 — congelaria a proporção. A
   UI se adaptaria, o buffer não, e a pré-visualização ficaria com faixas até um
   rebind manual. Se alguém fizer isso, tem de chamar `rebindCamera()` na mudança de
   configuração.

   Nota relacionada: no AVD Tablet_API36 o preview aparece com faixas laterais mesmo
   em janela larga, mas a causa é outra — `SENSOR_ORIENTATION: 90` num aparelho de
   orientação natural paisagem, então o campo de visão físico é mais alto que largo
   (`getCropRect=1280x720` com `getRotationDegrees=90`). Num tablet com o sensor
   montado a 0° ou 270° a imagem preenche a caixa 16:9.

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
