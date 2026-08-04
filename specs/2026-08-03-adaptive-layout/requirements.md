# Requisitos — Layout Adaptativo

## 1. Contexto

### Objetivo de negócio

A partir do `targetSdk 36`, o Android ignora `screenOrientation="portrait"` em telas
com largura mínima ≥ 600dp. Sem adaptação, a `CameraScreen` — desenhada para retrato
— se espalha numa janela de paisagem: barra superior esticada na largura toda,
pré-visualização como faixa estreita ao centro, controles amontoados sobre ela.

O opt-out de manifesto (`PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`) já está no
app e resolve o sintoma, mas o framework o remove no `targetSdk 37`. Esta spec
entrega a solução definitiva antes desse prazo.

### Atores

- **Primário:** quem usa o app em tablet ou dobrável aberto, e quem usa telefone em
  janela dividida
- **Secundário:** quem usa telefone em retrato — não deve perceber mudança nenhuma

### Restrições

- A quebra da `CameraScreen` (~1.950 linhas) está **fora de escopo** (ADR-001)
- Sem dependências novas (ADR-002)
- Dobráveis e modo tabletop **fora de escopo** — decisão do Gate 0
- `minSdk 24`: a solução não pode depender de API acima disso

### Glossário

| Termo | Definição |
|---|---|
| **Janela larga** | Janela cuja largura em dp é maior que a altura em dp |
| **Rotação física** | Inclinação do aparelho medida pelo acelerômetro (`rollDegrees`) |
| **Rotação da janela** | `Display.getRotation()` — muda só quando o sistema gira a janela |

---

## 2. Requisitos funcionais (EARS)

### FR-1: Detecção de janela larga
**Prioridade:** High

O sistema **deve** classificar a janela como larga quando a largura em dp for maior
que a altura em dp, e reavaliar essa classificação a cada mudança de configuração.

> Fonte única de verdade para os demais FRs de layout. Isolada num único ponto para
> permitir a troca por `WindowSizeClass` se um dia houver um terceiro layout (ADR-002).

### FR-2: Inversão de eixo das barras de controle
**Prioridade:** High

**Enquanto** a janela for larga, o sistema **deve** dispor a barra superior no bordo
esquerdo e os controles inferiores no bordo direito, empilhados verticalmente, com a
pré-visualização entre os dois.

**Enquanto** a janela não for larga, o sistema **deve** dispor a barra superior no
topo e os controles inferiores no rodapé, dispostos horizontalmente — o comportamento
atual, sem alteração.

> Traduz-se em trocar `Alignment.TopCenter`/`BottomCenter` por `CenterStart`/
> `CenterEnd` e `Row` por `Column`. O retrato é o caminho default do código, para que
> qualquer falha na condição preserve o comportamento de hoje.

### FR-3: Proporção da pré-visualização em janela larga
**Prioridade:** High

**Enquanto** a janela for larga, o sistema **deve** usar proporção 16:9 (paisagem)
para a caixa de pré-visualização, em vez da 9:16 usada em retrato.

**Quando** a proporção efetiva mudar, o sistema **deve** registrá-la no evento de
telemetria `evt=bind`, no campo `aspect` já existente.

> O campo `aspect` já é emitido pelo `CameraTelemetry.bind`. Sem isso, uma mudança de
> proporção do arquivo gravado seria invisível no diagnóstico.

### FR-4: Inversão das barras horizontais auxiliares
**Prioridade:** Medium

**Enquanto** a janela for larga, o sistema **deve** dispor verticalmente a barra de
presets de zoom e os seletores horizontais de resolução e fps.

### FR-5: Legibilidade dos controles sobre a pré-visualização
**Prioridade:** High

**Enquanto** um grupo de controles estiver sobreposto à pré-visualização, o sistema
**deve** aplicar um fundo de contraste atrás desse grupo.

> Em retrato as barras ficam sobre a faixa preta fora do preview. Em paisagem passam a
> ficar sobre a imagem, e contra fundo claro os ícones somem — observado na captura do
> tablet em 2026-08-03. Medida objetiva em NFR-2.

### FR-6: Orientação da mídia gravada
**Prioridade:** Critical
**Revisado:** 2026-08-04 — ver Q-01 em [decisions.md](decisions.md)

**Quando** uma gravação de vídeo ou captura de foto for iniciada, o sistema **deve**
derivar a rotação alvo do CameraX da rotação física do aparelho medida pelo
acelerômetro, e **não deve** compô-la com a rotação da janela.

> **A redação anterior deste requisito estava errada.** Ela mandava somar a rotação
> física com a rotação da janela, partindo de que as duas eram informações
> complementares. A medição em AVD Tablet_API36 refutou isso: enquanto a janela
> acompanha o aparelho, `Display.getRotation()` **é** a rotação física — bate 1:1 com
> o `atan2(x, y)` do acelerômetro nos quatro estados de rotação. Somar produziria
> rotação dobrada, ou seja, exatamente o arquivo torto que o requisito queria evitar.
>
> A inspeção do conteúdo do MP4 e do JPEG salvos confirmou que a derivação só pelo
> sensor já entrega a cena de pé nas duas pontas. O requisito passa a **proteger** o
> comportamento atual contra a composição, em vez de pedi-la.
>
> **Pendência conhecida, aceita:** com a rotação automática **desligada** pelo
> usuário, a janela fica presa e o arquivo deixa de acompanhar o enquadramento. É o
> espelho do AC-6.3, que escolheu deliberadamente seguir o aparelho — atender um
> contradiz o outro. Fica como decisão de produto para outra spec.
>
> **Critical** apesar da estratégia de ponte: é o único requisito cujo defeito não
> aparece na tela — só olhando o arquivo salvo.

### FR-9: Compensação de rotação da interface
**Prioridade:** High
**Origem:** Q-01, 2026-08-04

**Enquanto** a janela puder girar junto com o aparelho, o sistema **deve** descontar
a rotação já aplicada pelo compositor ao girar ícones e o indicador de nível, de modo
que não recebam rotação em dobro.

**Enquanto** a janela estiver travada, o sistema **deve** girar os ícones pela
rotação física do aparelho — o comportamento atual, sem alteração.

> Esta é a metade do antigo FR-6 que a medição sustenta. O defeito é visível: com a
> janela em retrato no tablet, o compositor já deixou a UI de pé e o app girava cada
> ícone outra vez — `HD 30`, `EIS`, flash e temporizador apareciam deitados, enquanto
> `Vídeo`/`Foto` e `1x 2x 5x`, que não recebem rotação, ficavam retos.
>
> O indicador de nível precisa do ângulo **contínuo**, não travado em quadrante: numa
> janela girada, o ângulo cru deixaria a linha 90° fora. Já a decisão de acender
> verde ("nivelado") continua física — é propriedade do enquadramento, não da janela.

### FR-7: Remoção da ponte de compatibilidade
**Prioridade:** Medium

**Quando** os FR-1 a FR-6 e o FR-9 estiverem implementados e verificados em tablet, o
sistema **deve** deixar de declarar
`android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` no manifesto.

> Sem isso o app continua pillarboxed e o trabalho fica inerte — a adaptação nunca é
> exercitada por usuário real.

### FR-8: Largura de leitura nas telas de Configurações e Sobre
**Prioridade:** Low

**Enquanto** a janela for larga, o sistema **deve** limitar a largura do conteúdo das
telas de Configurações e Sobre e centralizá-lo.

> Hoje são `LazyColumn` de largura total. Em tablet, cada linha de configuração se
> estica por 2.560px e a leitura fica ruim.

---

## 3. Requisitos não funcionais (QAS)

### NFR-1: Sem regressão em retrato
- **Atributo:** Manutenibilidade / Corretude
- **Prioridade:** Critical
- **Fonte:** desenvolvedor alterando o layout
- **Estímulo:** implementação dos FR-1 a FR-6
- **Artefato:** `CameraScreen.kt`
- **Ambiente:** telefone em retrato, o caso de uso principal
- **Resposta:** o posicionamento em retrato permanece idêntico ao anterior
- **Medida:** os 38 testes existentes continuam passando; comparação visual de captura
  antes/depois em telefone API 36 sem diferença perceptível nas âncoras

### NFR-2: Contraste dos controles sobre a imagem
- **Atributo:** Usabilidade / Acessibilidade
- **Prioridade:** High
- **Fonte:** usuário apontando a câmera para cena clara (céu, parede branca)
- **Estímulo:** controles sobrepostos à pré-visualização
- **Artefato:** barras de controle da `CameraScreen`
- **Ambiente:** pré-visualização com luminância alta
- **Resposta:** ícones e rótulos permanecem legíveis
- **Medida:** contraste ≥ **4,5:1** entre o ícone e o fundo composto (imagem + fundo
  de contraste), no pior caso de preview totalmente branco — limiar WCAG 2.1 AA

### NFR-3: Custo da mudança de orientação
- **Atributo:** Desempenho
- **Prioridade:** Medium
- **Fonte:** usuário girando um tablet
- **Estímulo:** mudança de configuração que troca a janela de retrato para paisagem
- **Artefato:** `CameraScreen` e sessão do CameraX
- **Ambiente:** tablet API 36
- **Resposta:** a UI se reorganiza e a pré-visualização volta a exibir imagem
- **Medida:** `elapsed_ms` do `evt=bind` subsequente ≤ **800ms**; sem tela preta
  visível por mais de 1 segundo

### NFR-4: Testabilidade sem aparelho
- **Atributo:** Testabilidade
- **Prioridade:** High
- **Fonte:** suíte de testes
- **Estímulo:** execução de `./gradlew testDebugUnitTest`
- **Artefato:** predicado de janela larga, rotação de captura e compensação da UI
- **Ambiente:** JVM, sem emulador
- **Resposta:** a lógica de decisão de layout é exercitada
- **Medida:** ≥ **6 testes novos** cobrindo o predicado (FR-1), a rotação de captura
  (FR-6) e a compensação da UI (FR-9), rodando na JVM

> A cobertura na JVM tem limite conhecido, medido em 2026-08-04: a primeira ligação
> do FR-9 passava nos 12 testes e não funcionava no aparelho, porque `view.display` é
> nulo até a View ser anexada. Função pura correta não garante ligação correta — daí
> a verificação em aparelho continuar obrigatória para FR-6 e FR-9.

### NFR-5: Não crescer a dívida de tamanho
- **Atributo:** Manutenibilidade
- **Prioridade:** Medium
- **Fonte:** desenvolvedor implementando a spec
- **Estímulo:** adição dos condicionais de orientação
- **Artefato:** `CameraScreen.kt`
- **Ambiente:** revisão de código
- **Resposta:** o arquivo não cresce desproporcionalmente
- **Medida:** aumento ≤ **15%** sobre as ~1.950 linhas atuais (teto ≈ 2.240). Acima
  disso, extrair componentes — o que antecipa parte do item 1 do `REFACTORING.md`

---

## 4. Critérios de aceite

### AC-1.1: Detecção em retrato
- **DADO** uma janela de 411dp × 891dp
- **QUANDO** o predicado de janela larga é avaliado
- **ENTÃO** retorna falso

### AC-1.2: Detecção em paisagem
- **DADO** uma janela de 1280dp × 800dp
- **QUANDO** o predicado de janela larga é avaliado
- **ENTÃO** retorna verdadeiro

### AC-1.3: Janela quadrada
- **DADO** uma janela de 800dp × 800dp
- **QUANDO** o predicado é avaliado
- **ENTÃO** retorna falso — o desempate favorece retrato, que é o layout testado

### AC-2.1: Âncoras em paisagem
- **DADO** o app numa janela larga
- **QUANDO** a `CameraScreen` é composta
- **ENTÃO** a barra superior está no bordo esquerdo, os controles no bordo direito, e
  a pré-visualização entre os dois

### AC-2.2: Retrato inalterado
- **DADO** o app numa janela em retrato
- **QUANDO** a `CameraScreen` é composta
- **ENTÃO** as âncoras são as mesmas de antes desta spec

### AC-3.1: Proporção em paisagem
- **DADO** o app numa janela larga em modo vídeo
- **QUANDO** a pré-visualização é dimensionada
- **ENTÃO** a caixa usa proporção 16:9

### AC-3.2: Telemetria da proporção
- **DADO** uma mudança de retrato para paisagem
- **QUANDO** o rebind do CameraX ocorre
- **ENTÃO** o `evt=bind` seguinte traz o campo `aspect` com o valor efetivo

### AC-4.3: Transbordo das barras verticais
- **DADO** uma janela larga de altura insuficiente para todos os controles empilhados
- **QUANDO** a barra vertical é composta
- **ENTÃO** ela rola ou reduz o espaçamento, sem cortar nem sobrepor controles

### AC-5.1: Legibilidade sobre cena clara
- **DADO** a câmera apontada para uma superfície branca
- **QUANDO** os controles estão sobrepostos à pré-visualização
- **ENTÃO** o contraste entre ícone e fundo composto é ≥ 4,5:1

### AC-6.1: Vídeo em tablet com janela livre
- **DADO** um tablet com a janela livre para girar
- **QUANDO** um vídeo é gravado e salvo
- **ENTÃO** o arquivo abre de pé — o horizonte na horizontal

> Reescrito em 2026-08-04. A versão anterior exigia "arquivo em paisagem", o que
> confundia orientação com proporção: num aparelho de orientação natural paisagem e
> `SENSOR_ORIENTATION` de 90°, o campo de visão físico é mais alto que largo, e o
> arquivo sair retrato é correto. O que precisa ser verificado é a cena estar de pé.

### AC-6.2: Verificação no arquivo, não na tela
- **DADO** qualquer combinação de rotação de janela e rotação física
- **QUANDO** a mídia é salva
- **ENTÃO** a orientação é conferida no **arquivo salvo** (metadado de rotação do MP4
  ou EXIF do JPEG), não na pré-visualização

> Este AC existe porque o defeito de FR-6 é invisível na tela.

### AC-6.3: Telefone travado em retrato, girado na mão
- **DADO** um telefone cuja janela permanece em retrato
- **QUANDO** o aparelho é girado 90° e um vídeo é gravado
- **ENTÃO** o arquivo sai em paisagem — o comportamento atual, preservado

### AC-6.4: A rotação da janela não entra na captura
- **DADO** o aparelho parado numa mesma inclinação física
- **QUANDO** só a rotação da janela muda
- **ENTÃO** a rotação alvo do CameraX não muda

### AC-7.1: Ponte removida
- **DADO** os FR-1 a FR-6 e o FR-9 verificados em tablet
- **QUANDO** o manifesto é inspecionado
- **ENTÃO** `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` não está mais declarada, e
  o `dumpsys` mostra a activity ocupando a janela inteira

### AC-8.1: Largura de leitura
- **DADO** a tela de Configurações numa janela larga
- **QUANDO** a lista é exibida
- **ENTÃO** o conteúdo tem largura limitada e está centralizado

### AC-9.1: Janela livre não gira o ícone de novo
- **DADO** uma janela que acompanhou o aparelho ao girar
- **QUANDO** os ícones da barra são compostos
- **ENTÃO** não recebem rotação adicional — aparecem de pé

### AC-9.2: Janela travada preserva o giro atual
- **DADO** um telefone cuja janela permanece em retrato
- **QUANDO** o aparelho é girado 90°
- **ENTÃO** os ícones giram, como antes desta spec

### AC-9.3: Ângulo do nível é relativo à janela
- **DADO** uma janela girada em relação ao aparelho
- **QUANDO** o indicador de nível é desenhado
- **ENTÃO** a linha usa a inclinação residual dentro da janela, e o verde de
  "nivelado" continua decidido pela inclinação física

---

## 5. Fora de escopo

| Item | Motivo |
|---|---|
| Quebra da `CameraScreen` em componentes | ADR-001; item 1 do `REFACTORING.md` |
| Dobráveis e modo tabletop | Decisão do Gate 0; exigiria `androidx.window` |
| Modo multi-janela e arrastar-e-soltar | Não solicitado |
| Layout adaptativo com três ou mais breakpoints | ADR-002; só dois layouts |
