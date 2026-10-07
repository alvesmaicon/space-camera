# Guia de contribuição

> **In English:** issues and pull requests are welcome in English or Portuguese. The code
> uses English identifiers; comments, commit messages and internal docs are in Portuguese,
> but you don't need to write Portuguese to contribute. Run
> `./gradlew testDebugUnitTest lint detekt` before opening a PR — CI runs the same.
> UI text never goes straight into Compose: add it to `res/values/strings.xml` (English)
> **and** `res/values-pt/strings.xml` (Portuguese).

## Antes do primeiro commit

Siga o [QUICKSTART.md](QUICKSTART.md) e confirme que o app sobe:

```bash
scripts/smoke.sh
```

Leia o [CLAUDE.md](CLAUDE.md) — ele reúne as convenções e as armadilhas do
projeto num lugar só.

## Branches e commits

`main` é a branch estável. Trabalhe em `feature/*`, `fix/*` ou `chore/*` e abra
pull request no GitHub. A CI roda build, testes, lint e detekt em todo PR.

Commits seguem [Conventional Commits](https://www.conventionalcommits.org/):
`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `build:`, `chore:`.

Mensagem em **português**, no imperativo, explicando **por quê** e não só o quê.
O assunto cabe em 72 caracteres; o corpo, quando existe, é onde mora o motivo.

```
fix: não perder o EIS ao voltar de configurações

O PreviewView é recriado na volta da navegação e o rebind reaplicava os
valores padrão do CameraManager em vez dos que estavam em tela.
```

## Antes de abrir o PR

```bash
./gradlew testDebugUnitTest lint detekt
```

Os três precisam passar. Se o `detekt` acusar algo novo, corrija — não regenere
o baseline para silenciar. O baseline existe apenas para congelar a dívida que
já estava lá.

Checklist:

- [ ] `testDebugUnitTest`, `lint` e `detekt` passando
- [ ] Comportamento novo coberto por teste
- [ ] Testado em aparelho real quando envolve câmera (o emulador tem câmera
      virtual: só HD 30, sem EIS e sem HDR)
- [ ] Documentação ajustada se mudou comando, arquitetura ou comportamento
- [ ] Sem `Log.` nem `printStackTrace` — use `Timber` ou `CameraTelemetry`
- [ ] Sem versão literal em `build.gradle.kts` — só em `libs.versions.toml`
- [ ] Sem código comentado ou sobra de debug

## Padrões de código

Siga as [convenções oficiais do Kotlin](https://kotlinlang.org/docs/coding-conventions.html).
Limite de 140 colunas (o `detekt` verifica).

**Idioma:** identificadores em inglês; comentários, rótulos de UI e mensagens de
commit em português. Nomes de teste em português, entre crases.

**Comentários** explicam o porquê, não o quê. O código já diz o que faz; o
comentário existe para o que não é óbvio — uma restrição da plataforma, uma
ordem que importa, um caso de aparelho específico. Exemplo do próprio projeto:

```kotlin
// IMPORTANTE: EIS e NR NÃO devem ser setados aqui. Camera2Interop tem
// prioridade maior que Camera2CameraControl no merge do CaptureRequest — se
// setarmos EIS aqui, o valor fica gravado na sessão e nunca é sobrescrito.
```

## Testes

O que vale a pena testar aqui é lógica que não depende de sensor: cálculo de
bitrate, ciclos de preset, persistência de configuração, contagem regressiva,
regras de "EIS não se aplica a foto".

- Lógica pura → JUnit direto, rápido
- Persistência e ViewModel → Robolectric, com SharedPreferences de verdade
- Hardware → `FakeCameraController`, mexendo nos `*Flow` públicos para simular
  aparelhos diferentes

Duas armadilhas estão documentadas em [CLAUDE.md](CLAUDE.md#testes): o
cronômetro em `while (true)` e o `android.util.Size` fora do Robolectric.

Não há meta numérica de cobertura. Teste comportamento que quebraria de verdade,
não getters.

## Reportar bug

Inclua sempre o **hash do build** (tela Sobre → Build) e a saída de:

```bash
scripts/logcat.sh 'evt=' > /tmp/spacecam.log
```

Sem isso, problema de aparelho é praticamente irreproduzível.

```markdown
## Descrição
[o que acontece]

## Como reproduzir
1.
2.

## Esperado vs atual

## Ambiente
- Aparelho e versão do Android:
- Build (tela Sobre):

## Telemetria
[cole as linhas evt=caps e evt=bind]
```

## Antes de aumentar um arquivo grande

`CameraScreen.kt`, `CameraManager.kt` e `CameraViewModel.kt` já estão grandes
demais ([REFACTORING.md](REFACTORING.md)). Ao mexer neles, prefira extrair a
parte que você tocou a acrescentar mais linhas — mesmo que seja pouco.
