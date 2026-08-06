# Requisitos — Registro de Modos Extensível

## 1. Contexto

### Objetivo

Tornar "modo de câmera" um conceito declarado num registro único, para que uma
funcionalidade nova de câmera entre como **fatia vertical fina** — um arquivo novo mais uma
linha de registro — em vez de mais um `if` espalhado por três arquivos grandes. A prova de
que a costura funciona é o **modo Pro** (ISO e obturador manuais) entrando por ela, com
Vídeo e Foto remigrados sem mudança de comportamento observável.

O problema medido hoje: `CameraMode` é um enum de dois valores consultado em **21 pontos** de
ramificação; `bindCameraUseCases()` liga sempre os mesmos três use cases,
independentemente do modo; e o seletor em janela larga assume que só existem dois modos.

Origem: pré-requisitos **R1–R4** de [ROADMAP.md](../../ROADMAP.md#pré-requisitos-de-refatoração-para-fatia-vertical),
que por sua vez destravam os itens B1–B4 do mesmo documento (câmera lenta, time-lapse, Pro
completo, RAW).

### Atores

| Ator | Interesse |
|---|---|
| **Usuário do app** | escolher o modo rápido, e decidir quais modos ficam à mão |
| **Fotógrafo** (persona do Pro) | controlar ISO e obturador manualmente ao fotografar |
| **Quem implementa a próxima feature** | adicionar um modo sem reabrir `CameraScreen`/`CameraManager`/`CameraViewModel` |
| **HAL da câmera** | responde o que o aparelho suporta; determina se o Pro existe |

### Restrições

| Restrição | Origem |
|---|---|
| CameraX permanece **1.4.0**; nenhuma API de 1.5/1.6 pode ser pressuposta | ADR-003 (Kotlin 1.9.25 não consome CameraX 1.5+) |
| Kotlin 1.9.25, Compose 1.5.4, módulo único, sem DI por framework | pilha atual do projeto |
| Persistência em `SharedPreferences` via `SettingsStorage` | convenção do projeto |
| Pro é modo de **foto** — controles manuais não valem para gravação nesta spec | decisão do usuário (Q5) |
| Personalização de modos mora na tela de **Configurações**, não por arrastar no seletor | decisão do usuário (Q6) |
| Verificação de UI roda na **JVM** (Robolectric + Compose), sem aparelho | decisão do usuário (Q7) |
| Comentários, rótulos e commits em português; identificadores em inglês | CLAUDE.md |
| Capacidade de hardware só se verifica em **aparelho real** | CLAUDE.md (o emulador faz defeito e acerto coincidirem) |

### Glossário

| Termo | Significado nesta spec |
|---|---|
| **Modo** | uma forma de usar a câmera com conjunto próprio de use cases, controles e overlays: Vídeo, Foto, Pro |
| **Registro de modos** | a lista única de definições de modo que a UI e o controller consultam |
| **Definição de modo** | o que um modo declara: identificador, rótulo, use cases, capacidade exigida, controles, overlay |
| **Capacidade** | algo que o aparelho pode ou não oferecer (EIS, HDR, `MANUAL_SENSOR`), sondado no HAL |
| **Plano** | a faixa de modos visível direto no seletor, sem abrir a gaveta |
| **Gaveta** | o "mais modos" — onde ficam os modos que não estão no plano |
| **Iso-comportamento** | comportamento observável idêntico ao anterior: mesma proporção, mesmos controles, mesma mídia, mesmos campos em `evt=bind` |

---

## 2. Requisitos funcionais (EARS)

### FR-1: Registro declarativo de modos
**Prioridade:** Crítica

O sistema deve manter um registro único em que cada modo de câmera declara seu identificador
estável, rótulo de exibição, conjunto de use cases a ligar, capacidade de hardware exigida,
controles visíveis e overlay próprio.

### FR-2: Bind pelo conjunto declarado
**Prioridade:** Crítica

Quando o modo ativo mudar, o sistema deve ligar na sessão da câmera exatamente o conjunto de
use cases declarado por esse modo, e nenhum outro.

### FR-3: Iso-comportamento do modo Vídeo
**Prioridade:** Crítica

Enquanto o modo ativo for Vídeo, o sistema deve apresentar comportamento observável idêntico
ao da versão anterior à refatoração: proporção de captura 16:9, EIS conforme o valor
persistido, seletor de resolução e fps disponível, gravação com pausar/retomar, cronômetro e
timer de contagem regressiva.

### FR-4: Iso-comportamento do modo Foto
**Prioridade:** Crítica

Enquanto o modo ativo for Foto, o sistema deve apresentar comportamento observável idêntico
ao da versão anterior à refatoração: proporção conforme a seleção do usuário (9:16, 3:4 e
Full), EIS desligado, modo de flash em ciclo OFF→AUTO→ON, preset de qualidade de foto
aplicado e revisão da foto capturada.

> **Corrigido em 2026-08-05 (Q-01, decisão do usuário).** A redação original listava quatro
> proporções, incluindo 1:1. O app oferece **três** — `photoRatios = listOf("Full", "9:16",
> "3:4")`, confirmado em aparelho. O `1:1` só existe num ramo inalcançável do
> `CameraManager` e virou item de ROADMAP. Manter "quatro" aqui faria a remigração da
> Tarefa 7 acrescentar uma proporção nova, que é o oposto do iso-comportamento que este
> mesmo requisito exige.

### FR-5: Gate de capacidade por modo
**Prioridade:** Crítica

Onde a definição de um modo declarar capacidade de hardware que o aparelho não reporta, o
sistema não deve oferecer esse modo em nenhuma superfície — nem no plano, nem na gaveta, nem
na tela de personalização.

### FR-6: Seletor orientado ao registro, com gaveta
**Prioridade:** Crítica

O sistema deve montar o seletor de modos a partir do registro, exibindo no plano os modos
marcados como visíveis, na ordem configurada, e um acesso à gaveta com os demais modos
disponíveis.

### FR-7: Personalização de ordem e visibilidade
**Prioridade:** Alta

Onde a tela de Configurações for aberta, o sistema deve permitir reordenar os modos
disponíveis e marcar quais ficam no plano, exceto Vídeo e Foto, que não podem ser removidos
do plano.

### FR-8: Persistência por identificador estável
**Prioridade:** Alta

O sistema deve persistir a ordem e a visibilidade dos modos usando o identificador estável de
cada modo, nunca a posição ordinal, e deve ignorar identificadores desconhecidos ao ler a
preferência.

### FR-9: Modo Pro — ISO manual
**Prioridade:** Alta

Enquanto o modo ativo for Pro, quando o usuário ajustar o ISO, o sistema deve aplicar a
sensibilidade escolhida ao sensor, restrita à faixa que o aparelho reportou.

### FR-10: Modo Pro — obturador manual
**Prioridade:** Alta

Enquanto o modo ativo for Pro, quando o usuário ajustar o tempo de exposição, o sistema deve
aplicá-lo ao sensor, restrito à faixa que o aparelho reportou.

### FR-11: Modo Pro — retorno ao automático
**Prioridade:** Alta

Enquanto o modo ativo for Pro, quando o usuário marcar ISO ou obturador como automático, o
sistema deve devolver o controle da exposição ao automático da câmera.

### FR-12: Modo Pro — captura de foto
**Prioridade:** Alta

Quando o usuário acionar o disparador no modo Pro, o sistema deve capturar uma foto com os
valores manuais em vigor, salvando-a pelo mesmo caminho de mídia e EXIF do modo Foto.

### FR-13: Telemetria de modo e capacidade
**Prioridade:** Média

Quando o modo ativo mudar, o sistema deve registrar em telemetria o modo anterior, o novo
modo, os use cases ligados e a latência do bind; e a linha `evt=caps` deve passar a incluir o
suporte a controle manual e as faixas reportadas.

### FR-14: Seletor em janela larga com N modos
**Prioridade:** Alta

Enquanto a janela for larga, o sistema deve apresentar o seletor de modos sem pressupor
quantidade fixa de modos, mantendo o modo ativo visível e alcançável quando os modos não
couberem no eixo.

### FR-15: Troca de modo durante gravação
**Prioridade:** Crítica

Enquanto uma gravação estiver em andamento, o sistema deve recusar a troca de modo.

### FR-16: Restaurar padrão dos modos
**Prioridade:** Baixa

Onde a tela de personalização de modos for exibida, o sistema deve oferecer uma ação que
restaura a ordem e a visibilidade padrão dos modos.

### FR-17: Atalho da gaveta para a personalização
**Prioridade:** Média

Onde a gaveta de modos estiver aberta, o sistema deve oferecer acesso direto à tela de
personalização de modos.

---

## 3. Requisitos não funcionais (QAS)

### NFR-1: Iso-comportamento verificável
**Atributo:** Correção · **Prioridade:** Crítica

| Componente | Valor |
|---|---|
| **Origem** | quem revisa o PR de remigração de Vídeo/Foto |
| **Estímulo** | comparar o app antes e depois da refatoração |
| **Artefato** | modos Vídeo e Foto, ponta a ponta |
| **Ambiente** | aparelho real, mesma cena, com `git worktree` para os dois commits (nunca `git stash`) |
| **Resposta** | os campos de `evt=bind` e `evt=caps` coincidem, e a mídia gerada tem a mesma resolução, proporção e orientação |
| **Medida** | **100%** dos testes JVM existentes passam **sem alteração de asserção**; nos dois commits, os campos de `evt=bind` **que já existiam antes** têm os mesmos valores para as duas transições Vídeo↔Foto. Os campos acrescentados por FR-13 (modo, use cases ligados) são aditivos e ficam fora da comparação — do contrário FR-13 e este NFR se contradiriam |

### NFR-2: Custo de adicionar um modo
**Atributo:** Manutenibilidade · **Prioridade:** Crítica

| Componente | Valor |
|---|---|
| **Origem** | quem implementa a próxima feature de câmera (câmera lenta, time-lapse) |
| **Estímulo** | adicionar um modo novo ao app |
| **Artefato** | registro de modos e seus consumidores |
| **Ambiente** | base já refatorada por esta spec |
| **Resposta** | o modo aparece no seletor, liga seus use cases, respeita gate de capacidade e some quando não suportado |
| **Medida** | **≤ 1 arquivo novo + ≤ 1 linha** no registro; **0 edições** em `CameraScreen.kt`, `CameraViewModel.kt` e `CameraManager.kt`. Comprovado por recibo: um modo-exemplo (`ModoDemo`) é criado num **commit descartável fora do ramo de entrega**, o `git diff --stat` é anotado em `agent-progress.md` e o commit é descartado. Se o recibo passar de 1 arquivo + 1 linha, a costura não está pronta — é sinal de reabrir o desenho, não de relaxar a medida |

### NFR-3: Não crescer a dívida de tamanho
**Atributo:** Manutenibilidade · **Prioridade:** Alta

| Componente | Valor |
|---|---|
| **Origem** | dívida mapeada em REFACTORING.md |
| **Estímulo** | a spec adiciona gaveta, tela de personalização e overlay do Pro |
| **Artefato** | os três arquivos grandes |
| **Ambiente** | fim da spec, ramo pronto para merge |
| **Resposta** | o código novo mora em arquivos novos; o que foi tocado nos grandes saiu deles |
| **Medida** | `wc -l` ao fim: `CameraScreen.kt` **≤ 2.100** (hoje 2.220) e `CameraManager.kt` **≤ 1.158** (hoje 1.158) — ou seja, **nenhuma linha líquida adicionada** aos dois, apesar das três superfícies novas; e **nenhum arquivo novo > 400 linhas** |

### NFR-4: Latência da troca de modo
**Atributo:** Desempenho · **Prioridade:** Média

| Componente | Valor |
|---|---|
| **Origem** | usuário trocando de modo no seletor |
| **Estímulo** | 10 trocas de modo consecutivas alternando Vídeo→Foto→Pro |
| **Artefato** | caminho de bind da sessão da câmera |
| **Ambiente** | aparelho real, app em primeiro plano, bateria acima de 30% |
| **Resposta** | a pré-visualização volta a exibir imagem sem piscar preto além do que já piscava |
| **Medida** | p95 da latência em `evt=bind` **≤ baseline medido antes da refatoração + 20%**; o baseline é medido e registrado como primeiro passo da spec |

### NFR-5: Testabilidade sem aparelho
**Atributo:** Testabilidade · **Prioridade:** Alta

| Componente | Valor |
|---|---|
| **Origem** | quem roda a verificação antes do commit |
| **Estímulo** | `./gradlew testDebugUnitTest` |
| **Artefato** | registro, gate de capacidade, persistência, seletor e estado do Pro |
| **Ambiente** | JVM, sem aparelho e sem emulador |
| **Resposta** | regressão de seletor, de gate ou de persistência falha o build |
| **Medida** | **≥ 1 teste JVM por FR** de FR-1, FR-2, FR-5, FR-6, FR-7, FR-8, FR-9, FR-10, FR-11, FR-15 e FR-16; suíte completa em **≤ 90 s** na máquina de desenvolvimento; **0** testes novos exigindo aparelho. Divisão por tipo (ADR-006 revisado): **JVM pura** para registro, gate de capacidade, ação do disparador, comportamento do flash e completude do mapeamento; **Robolectric sem Compose** para persistência; **Robolectric + Compose** só para as três superfícies de UI (seletor com gaveta, personalização, janela larga) |

### NFR-6: Análise estática sem achado novo
**Atributo:** Manutenibilidade · **Prioridade:** Alta

| Componente | Valor |
|---|---|
| **Origem** | `./gradlew detekt lint` |
| **Estímulo** | análise sobre o código da spec |
| **Artefato** | baselines `config/detekt/detekt-baseline.xml` e `app/lint-baseline.xml` |
| **Ambiente** | build local e verificação de PR |
| **Resposta** | nenhum achado novo; os baselines encolhem em vez de crescer |
| **Medida** | **0** achados novos de detekt e lint; `UnsafeOptInUsageError` cai de **31** para **0** no `CameraManager`; nenhum baseline regenerado para silenciar achado |

### NFR-7: Privacidade no log
**Atributo:** Segurança · **Prioridade:** Alta

| Componente | Valor |
|---|---|
| **Origem** | telemetria nova de modo e de capacidade |
| **Estímulo** | `scripts/logcat.sh evt=` durante uso normal |
| **Artefato** | `CameraTelemetry` |
| **Ambiente** | build de debug e de release |
| **Resposta** | só identificador de modo, nomes de use case, capacidades e faixas numéricas |
| **Medida** | **0** ocorrências de caminho de arquivo, conteúdo de mídia ou dado pessoal nas linhas novas; em release, DEBUG/VERBOSE seguem descartados pelo Timber |

### NFR-8: Compatibilidade da preferência de modos
**Atributo:** Robustez · **Prioridade:** Média

| Componente | Valor |
|---|---|
| **Origem** | app atualizado para uma versão com modos diferentes |
| **Estímulo** | ler preferência gravada por outra versão: modo removido, modo novo ausente da lista, valor corrompido, chave inexistente |
| **Artefato** | leitura da preferência de modos em `SettingsStorage` |
| **Ambiente** | teste Robolectric com `SharedPreferences` de verdade |
| **Resposta** | a leitura degrada para o padrão sem exceção e sem perder os modos válidos |
| **Medida** | **4/4** cenários acima resultam em estado utilizável; **0** exceções propagadas; modos válidos preservados na ordem gravada |

---

## 4. Critérios de aceite

### AC-1.1: Registro é a única fonte dos modos
**DADO** o app aberto na câmera
**QUANDO** o seletor de modos é montado
**ENTÃO** os modos exibidos vêm do registro, e não de `CameraMode.entries`

### AC-2.1: Pro liga só Preview e ImageCapture
**DADO** o modo Pro ativo
**QUANDO** a sessão da câmera é ligada
**ENTÃO** `VideoCapture` não está entre os use cases ligados, e `evt=bind` lista apenas
`Preview` e `ImageCapture`

### AC-2.2: Vídeo continua ligando VideoCapture
**DADO** o modo Vídeo ativo
**QUANDO** a sessão da câmera é ligada
**ENTÃO** `evt=bind` lista `Preview`, `VideoCapture` e `ImageCapture`, como antes da
refatoração

### AC-3.1: Vídeo preserva a proporção
**DADO** o modo Vídeo ativo em qualquer seleção de proporção de foto
**QUANDO** a pré-visualização é ligada
**ENTÃO** a proporção de captura é 16:9

### AC-4.1: Foto preserva o EIS desligado
**DADO** o modo Vídeo com EIS ligado
**QUANDO** o usuário troca para Foto e volta para Vídeo
**ENTÃO** o EIS fica desligado em Foto e volta ao valor persistido em Vídeo

### AC-5.1: Modo sem capacidade não aparece
**DADO** um aparelho que não reporta `MANUAL_SENSOR`
**QUANDO** o seletor, a gaveta e a tela de personalização são exibidos
**ENTÃO** o modo Pro não aparece em nenhum dos três

### AC-5.2: Modo com capacidade aparece
**DADO** um aparelho que reporta `MANUAL_SENSOR` com faixas de ISO e exposição
**QUANDO** o registro é consultado
**ENTÃO** o modo Pro está disponível e as faixas ofertadas na UI são as reportadas

### AC-5.3: Modo conhecido mas indisponível não apaga a preferência
**DADO** uma preferência que inclui Pro no plano, e um aparelho que não reporta
`MANUAL_SENSOR`
**QUANDO** o app monta o seletor
**ENTÃO** Pro não aparece, **e** a preferência gravada permanece intacta — ao abrir o mesmo
perfil num aparelho que suporta, Pro volta na posição configurada

### AC-6.1: Gaveta mostra o que não está no plano
**DADO** três modos disponíveis e dois marcados como no plano
**QUANDO** o seletor é exibido
**ENTÃO** dois modos aparecem no plano e a gaveta contém o terceiro

### AC-7.1: Vídeo e Foto não saem do plano
**DADO** a tela de personalização de modos
**QUANDO** o usuário tenta desmarcar Vídeo ou Foto
**ENTÃO** a ação não é oferecida

### AC-7.2: Reordenar reflete no seletor
**DADO** a ordem Vídeo, Foto, Pro
**QUANDO** o usuário move Pro para a primeira posição e volta para a câmera
**ENTÃO** o seletor mostra Pro, Vídeo, Foto nessa ordem

### AC-8.1: Identificador desconhecido é ignorado
**DADO** uma preferência gravada contendo um identificador de modo que não existe mais
**QUANDO** o app lê a preferência
**ENTÃO** o modo desconhecido é descartado, os válidos são preservados na ordem gravada e
nenhuma exceção é lançada

### AC-8.2: Preferência ausente cai no padrão
**DADO** nenhuma preferência de modos gravada
**QUANDO** o app lê a preferência
**ENTÃO** a ordem e a visibilidade padrão do registro são usadas

### AC-9.1: ISO fora da faixa é limitado
**DADO** um aparelho com faixa de ISO de 50 a 3200
**QUANDO** um valor de 6400 é solicitado
**ENTÃO** o valor aplicado é 3200

### AC-10.1: Obturador fora da faixa é limitado
**DADO** um aparelho com faixa de exposição de 1/8000 s a 1/4 s
**QUANDO** um tempo de 1 s é solicitado
**ENTÃO** o valor aplicado é o máximo da faixa reportada

### AC-11.1: Automático devolve o AE
**DADO** o modo Pro com ISO e obturador manuais em vigor
**QUANDO** o usuário marca os dois como automático
**ENTÃO** o modo de exposição automática volta a ficar ativo

### AC-12.1: Foto do Pro segue o mesmo caminho de mídia
**DADO** o modo Pro com valores manuais em vigor
**QUANDO** o usuário dispara
**ENTÃO** a foto é salva no MediaStore com o mesmo padrão de nome e EXIF do modo Foto, e a
miniatura da última foto é atualizada

### AC-15.1: Gravação bloqueia a troca
**DADO** uma gravação em andamento no modo Vídeo
**QUANDO** o usuário toca em outro modo no seletor
**ENTÃO** o modo ativo não muda

### AC-16.1: Restaurar padrão
**DADO** ordem e visibilidade personalizadas
**QUANDO** o usuário aciona restaurar padrão
**ENTÃO** a ordem e a visibilidade voltam ao padrão do registro e persistem assim

---

## 5. Fora de escopo

Registrado para não voltar como "faltou":

| Fora | Onde entra |
|---|---|
| Balanço de branco, foco manual e RAW no Pro | ROADMAP.md B3/B4, fatias seguintes |
| Câmera lenta e time-lapse | ROADMAP.md B1/B2, dependem de CameraX 1.5+ |
| Migração Kotlin 2.x + CameraX 1.6.1 | spec própria (ADR-003) |
| Arrastar modo da gaveta para o plano no próprio seletor | fatia futura, mesmo registro e mesma persistência (Q6) |
| Agrupar todos os ~30 `StateFlow` do ViewModel (R3 completo) | esta spec só introduz estado agrupado para o que é novo |
| Controles manuais durante gravação de vídeo | decisão Q5 — Pro é modo de foto |
