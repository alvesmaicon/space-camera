# Progresso — Layout Adaptativo

## Estado atual

- **Fase:** 9 de 9 tarefas concluídas — Wave 3 fechou as pendências da verificação
- **Gate atual:** E (aguarda autorização de commit da Wave 3)
- **Status:** NFR-1, NFR-2 e NFR-3 assinados com medição; Q-01 e Q-02 resolvidas;
  Ressalva 3 segue aberta por escolha, e o AC-4.3 segue inalcançável neste hardware
- **Confiança:** 95%
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
| 6. Leitura, ponte, integração | 2 | **Concluída** | Ponte removida; largura de leitura em 640dp |
| 7. Controller na recriação | 3 | **Concluída** | Q-02; 2 testes; 4 orientações com imagem, câmera reabre |
| 8. Contraste do desligado | 3 | **Concluída** | Alfa 0,48; medido 4,86:1 no tablet e 4,89:1 no telefone |
| 9. Rolagem de fallback | 3 | **Concluída** | Implementada; AC-4.3 inalcançável neste hardware |

### Sessão 2026-08-04 (continuação) — skill `dev`, Wave 2

- **Gates:** A, B, C, D · **Testes:** 64 (5 novos) · **Detekt e Lint:** limpos

**Task 6 concluída.**

- `readingGutterDp(widthDp)` + `rememberReadingGutter()` em `WindowAxis.kt`, com TDD:
  RED nos dois casos que discriminam (1280dp e 700dp), verde nos de retrato. Aplicado
  como padding no container em vez de `widthIn`, para a área de rolagem continuar
  ocupando a janela toda e só o conteúdo ficar centralizado.
- A mesma margem foi para o `LargeTopAppBar` das duas telas: sem isso o título ficava
  colado na borda esquerda com a lista no centro. Padding no app bar é seguro porque
  o `containerColor` é o próprio fundo da página.
- **Ponte removida** do manifesto, com comentário explicando por que
  `screenOrientation="portrait"` **fica** (ainda é honrado em telefone, e é o que
  trava o caso de uso principal) e por que `configChanges` não deve ser declarado.
  A ausência foi conferida no manifesto compilado do APK, não só no fonte.

**Pendência de verificação:** a medição de 29/29 caixas no telefone é da Wave 1. O
recuo é zero em 411dp (com teste) e a remoção da ponte não afeta tela < 600dp, mas
não foi remedido depois da Task 6.

### Sessão 2026-08-04 (continuação) — skill `dev`, verificação final

- **Gates:** A, D · **Nenhum código de produção mudou** — esta sessão só verificou
- **Emuladores:** Pixel_9_Pro (427×952dp) e Tablet_API36 (1280×800dp)

**Três pendências de verificação fechadas, uma delas virando defeito.**

**1. NFR-1 no telefone — assinado.** A comparação foi feita contra o APK **pré-spec**
(`f5614cd`), construído num `git worktree` separado em vez de `git stash`: o stash é
exatamente o que confundiu o cálculo de atualidade do Gradle duas vezes nesta base, e o
worktree tem diretório de build próprio. Os dois APKs foram conferidos no dex antes de
instalar — o baseline **não** tem `readingGutterDp`, `isWideWindow` nem
`captureRotation`; o HEAD tem os três. A tela Sobre de cada um mostrou o `GIT_SHA`
esperado, o que confirma qual estava instalado em cada rodada.

223 caixas comparadas em cinco estados, todas com bounds idênticos. A única diferença em
todo o conjunto é a largura do glifo do próprio `GIT_SHA`.

**2. AC-9.3 — fechado.** O nível de horizonte foi ligado em tablet e medido em três
estados de sensor. Os dois primeiros (0° e +90°) não discriminam nada: dão linha
horizontal de qualquer forma. O terceiro (+100°, ou seja 90° de janela mais 10°
residuais) é o que separa as hipóteses — linha a 8,68° medidos no pixel, branca. Detalhe
em `tasks.md`, Ressalva 4.

**3. NFR-3 — reprovado. Ver Q-02.** Girar o tablet deixa a pré-visualização preta para
sempre. Três medições: não é o retrato (retrato aberto do zero tem luminância 149,5,
contra 0,1 depois de girar); não é regressão desta spec (reproduz idêntico no APK
pré-spec); e a causa é a câmera fechar no destroy da Activity e **não reabrir**, porque
o `CameraViewModel` sobrevive à recriação carregando um `CameraManager` que ainda aponta
para a Activity destruída.

**A lição de método vale mais que o bug.** A Task 3 assinou o NFR-3 medindo
`elapsed_ms` no `evt=bind` — 263, 264 e 317ms, confortavelmente dentro do teto de 800. O
número estava certo e a conclusão errada: `evt=bind` mede o tempo de configurar os use
cases, não que tenha chegado quadro na superfície. No caminho defeituoso ele reporta
sucesso em 7ms. Telemetria de bind não é evidência de imagem na tela; para isso serve
medir luminância da captura de tela, que é o que fechou o caso.

### Sessão 2026-08-04 (continuação) — skill `dev`, Wave 3

- **Gates:** A, B, C, D · **Testes:** 66 (2 novos) · **Detekt e Lint:** limpos
- **`CameraScreen.kt`:** 2.181 linhas, teto do NFR-5 = 2.240

As três decisões da verificação final voltaram do usuário e viraram tarefas.

**Task 7 — Q-02, a tela preta na rotação.** Decisão: corrigir aqui, não empurrar para
outra spec. TDD de verdade: dois testes primeiro, RED com `AssertionError` nas duas
asserções que discriminam (quantos controllers foram construídos, e se o antigo foi
liberado). O segundo teste é o que impede a correção preguiçosa de "recriar sempre" —
voltar de Configurações tem de reaproveitar, senão a câmera pisca a cada volta.

Além do `WeakReference` para o owner, dois cuidados que o teste não pegaria e a leitura
do código sim:

1. Os 16 coletores viraram filhos de um job único. Sem isso os coletores do controller
   antigo continuariam vivos — `collect` em `StateFlow` nunca termina — segurando a
   instância velha e escrevendo nos mesmos `_flows` que a nova.
2. A carga do storage ficou restrita à primeira criação. Deixá-la no caminho da
   recriação faria o ramo de "manter configurações desligado" **resetar os padrões a
   cada virada de tela** — um bug novo, introduzido pela correção de outro.

**Task 8 — contraste.** Decisão do usuário: subir o alfa **nos dois** layouts, não só
em janela larga. O valor saiu de cálculo, não de tentativa: o modelo de luminância da
WCAG reproduziu primeiro os números já registrados na Ressalva 1 (3,51 e 3,39 contra
3,5 e 3,4), e só então foi usado para escolher 0,48.

Medido no pixel depois, nos dois aparelhos:

| Aparelho | Fundo | Contraste | Alfa efetivo |
|---|---|---|---|
| Tablet, janela larga | `#1B1B1D` (scrim) | **4,86:1** | 0,478 |
| Telefone, retrato | `#000000` (faixa) | **4,89:1** | 0,478 |

O alfa efetivo de 0,478 contra 0,480 pretendido é amostragem de p99 em glifo
antisserrilhado, não erro de implementação.

**Task 9 — rolagem de fallback.** Opt-in, uma por grupo ancorado — aninhar duas no
mesmo eixo tornaria o arraste ambíguo, e o disco de zoom já usa arraste. Segue **não
exercitável**: `wm size 2560x700` devolve `sw263dp`, o Android volta a honrar
`portrait` e a janela sai pillarboxed — o layout largo nem ativa. O ganho é garantia,
não comportamento novo.

**NFR-1 reconferido depois de tudo:** os mesmos cinco estados contra o mesmo baseline
`f5614cd`, 233 caixas, só o `GIT_SHA` divergindo. Confirma que alfa não move bounds e
que a rolagem não entra em retrato.

**Correção de registro.** A sessão anterior anotou "223 caixas" com Sobre em 39. A
contagem certa é **233**, com Sobre em 49 — erro de transcrição meu, não de medição; os
dumps sempre tiveram 49. Corrigido em `tasks.md`.

**Duas armadilhas de método desta sessão**, ambas de verificação, não de código:

1. `am start` depois de `am force-stop` **retoma a task preservada em recentes**, não
   abre na tela inicial. A primeira rodada de captura pegou a tela Sobre acreditando ser
   a câmera, e os toques seguintes caíram fora. Só a conferência da contagem de caixas
   (49 onde deviam ser 29) expôs. Agora o roteiro usa `-S --activity-clear-task` e
   **verifica** a tela por `content-desc` antes de cada captura.
2. O emulador em boot frio abre "System UI isn't responding" e o diálogo engole os
   toques. O roteiro passou a dispensá-lo antes de começar.

### Sessão 2026-08-05 — skill `dev`, primeiro aparelho físico

- **Gates:** A, B, C, D · **Testes:** 67 (1 novo) · **Detekt e Lint:** limpos
- **Aparelho:** Motorola edge 60 neo, Android 16 (API 36), 427×949dp, serial [serial omitido]

Objetivo era só gerar um APK de backup e instalar. Criado
[`scripts/backup-apk.sh`](../../scripts/backup-apk.sh) — o repo não tinha nada disso, e
`assembleRelease` não serve porque não há `signingConfigs`, então sai
`app-release-unsigned.apk`, que nenhum aparelho instala.

**O que o hardware real revelou de imediato.** Primeira linha de telemetria já
contraditória: `evt=caps eis_supported=true` com `evt=bind eis=false`. Virou Q-03 — e a
causa era **regressão minha da Task 7**: ao agrupar os coletores num job único, eu os
movi para antes do bind, e o coletor de `isEisSupported` passou a receber o `false`
inicial (que significa "ainda não sondado"), desligando o EIS e persistindo isso. A/B com
o APK pré-Task 7 no mesmo aparelho confirmou. Coletores voltaram para depois do bind;
`eis=true` depois.

**Três coisas que o emulador nunca poderia ter mostrado:**

| | Emulador | edge 60 neo |
|---|---|---|
| Resoluções | `[HD 30]` | `[4K 30, FHD 30, FHD 60, HD 30]` |
| EIS | não suportado | **suportado** |
| HDR | não suportado | não suportado |

É a razão de o defeito ter passado por toda a spec: com EIS não suportado, o
comportamento defeituoso coincide com o correto.

**Dois erros de método meus nesta sessão, ambos instrutivos:**

1. **O script falhou na primeira execução por bug meu**, e o modo de falha era enganoso:
   `set -o pipefail` com `cat *.dex | grep -q` faz o pipeline retornar erro **quando o
   grep acha** o match — o `grep` sai no primeiro acerto e o `cat` toma SIGPIPE. A
   verificação reprovava exatamente o caso bom. Corrigido lendo os arquivos sem pipe.
2. **A primeira versão do teste de regressão passou com o bug presente.** O
   `StandardTestDispatcher` da suíte enfileira o `launch` em vez de rodá-lo eager, o que
   inverte a ordem sob teste. Precisou de `UnconfinedTestDispatcher` para modelar o
   `Dispatchers.Main.immediate` de produção. Somado ao `FakeCameraController` nascendo com
   `isEisSupportedFlow = true` (mais otimista que a produção, que nasce `false`), eram
   dois motivos independentes para o verde ser falso.

**Achado menor, não corrigido:** `evt=caps` sai com vírgula decimal (`zoom=1,0x-10,0x`)
neste aparelho, porque `Timber.i(fmt, args)` usa `String.format` com o locale padrão.
A telemetria é canal de diagnóstico feito para `grep` e comparação entre aparelhos, então
depender de locale é frágil. Falta `Locale.ROOT`.

### Sessão 2026-08-05 (continuação) — Q-04, CPU e suavização do nível

- **Testes:** 73 (6 novos) · **Detekt e Lint:** limpos
- Levantada pelo usuário a partir de dois sintomas: o aparelho aquecendo com a câmera
  aberta, e o nível tremendo com o telefone parado na mesa. Os dois têm a mesma origem
  parcial, e o diagnóstico está em Q-04.

Entregue: suavização do **vetor** de gravidade em vez do ângulo (que corrige um defeito
de ±180° achado ao escrever o teste), zona morta antes de publicar, `derivedStateOf` nos
valores travados em quadrante, e `LEVEL_TOLERANCE` extraída para a invariante ter o que
guardar.

**Três conclusões erradas sobre CPU antes da certa**, e é o registro mais útil desta
sessão. Comparei cenas diferentes (deu "queda de 53% para 25%"), depois uma amostra de
cada (deu "sem efeito nenhum"), e só a terceira — ordem alternada A,B,A,B com mediana de
6 amostras — sustenta conclusão: redução de ~20 pontos, real mas não dominante. O sinal
de que as duas primeiras não valiam estava à vista e eu não olhei: os valores absolutos
eram incompatíveis entre si (78% e 117%) para o **mesmo** código. Neste ambiente, `top`
varia mais que o efeito sob teste; comparação de amostra única é sorteio, não medição.

A causa dominante da CPU **não** foi atacada: é o `TextureView` da pré-visualização
(`ImplementationMode.COMPATIBLE`), e trocar para `PERFORMANCE` exige tirar o espelho da
câmera frontal do `graphicsLayer`. Fica registrado em Q-04.

**Segunda rodada, com o usuário no aparelho.** O primeiro ajuste (zona morta 0,25° →
0,8°) foi comitado sem confirmação e o retorno dele decidiu o resto: *"tremendo menos mas
ainda treme um pouco"* e *"acompanha o movimento bem"*. A segunda frase é que orientou —
havia margem para filtrar mais.

Subir mais a zona morta seria errado: ela converte tremor em saltos, porque só publica
nos picos. E filtrar mais o valor único atrasaria a orientação do arquivo gravado. A
saída foi separar exibição de física, o mesmo padrão da Q-01, com invariantes em teste
para que ninguém iguale os dois depois. **Confirmado:** *"tá bem melhor"*.

**O teto do NFR-5 encostou.** Meus comentários levaram o arquivo a 2.241 linhas, uma
acima do limite — eu estava repetindo em comentário o que a Q-04 já registra. Enxugando
para apontar em vez de restatear, voltou a 2.230. Sobram 10 linhas; a próxima mudança
neste arquivo provavelmente precisa extrair o bloco do acelerômetro antes.

**E encostou de novo na mudança seguinte, que virou a Q-05.** A pendência da Q-04 (modo
`PERFORMANCE` bloqueado pelo espelho) caiu por um caminho que ela não tinha considerado:
escolher o modo por situação, já que o espelho é opcional e vem desligado. O bloco da
pré-visualização saiu para `CameraPreviewSurface.kt` (82 linhas), e `CameraScreen.kt`
fechou em **2.220**.

### Lacuna de rastreabilidade encontrada depois

`CameraPreviewSurface.kt` foi escrito com `Decisions: Q-05` no cabeçalho, mas a Q-05
**nunca foi escrita** em `decisions.md` — as medições que justificam a extração (o A/B de
CPU e a comparação por reflexo horizontal) existiam só no KDoc do arquivo. A referência
foi encontrada pendurada no Gate A da spec seguinte, ao conferir a árvore antes de
commitar. A Q-05 foi então redigida a partir do que o KDoc já registrava.

Vale como lembrete: link de rastreabilidade escrito em código não se verifica sozinho.
Este só apareceu porque outra spec foi ler a árvore antes de começar.

## Pendências

- [x] Iniciar a Wave 0
- [x] Decidir Q-01 — usuário escolheu dividir em duas grandezas
- [x] Commit da Task 1 (`8caf152`)
- [x] Commit da Task 5 (`2e2039c`)
- [x] Emenda do FR-6 / criação do FR-9 em `requirements.md`
- [x] Commit da Wave 1 (`892d131`)
- [x] Commit da Task 6 (`34783d9`)
- [x] Iniciar a Wave 1 (Tasks 2, 3, 4)
- [x] Decidir as duas ressalvas da Task 4 — viraram Task 8 e Task 9
- [x] Wave 2 — Task 6
- [x] Reconferir o telefone depois da Task 6 (NFR-1) — 233 caixas, sem diferença
- [x] Exercitar o AC-9.3 (Ressalva 4)
- [x] Decidir a Q-02 — corrigir nesta spec (Task 7); NFR-3 passa a atendido
- [x] Wave 3 — Tasks 7, 8 e 9
- [x] Wave 3 comitada (Tasks 7, 8 e 9)
- [x] Q-05 — `PERFORMANCE` destravado por escolha de modo por situação
- [ ] Ressalva 3 (texto `Vídeo`/`Foto` sem fundo em retrato) — segue aberta por escolha

## Bloqueadores

Nenhum. `./gradlew assembleDebug testDebugUnitTest detekt lint` verde com 66 testes.

`CameraScreen.kt` está em **2.181 linhas** contra o teto de 2.240 do NFR-5 — 59 linhas
de folga.

Duas pendências assumidas, nenhuma bloqueante:

- **AC-4.3 (transbordo)** tem fallback implementado mas não exercitável: janela larga
  exige `sw ≥ 600dp` neste hardware, e nessa altura os controles cabem.
- **Ressalva 3** — em retrato o texto `Vídeo`/`Foto` não tem fundo de contraste próprio
  e desaparece sobre cena clara. Já acontecia antes desta spec, e a Task 8 não resolve:
  ali subir o alfa do branco piora. Exigiria scrim em retrato.

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
