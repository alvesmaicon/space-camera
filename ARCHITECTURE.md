# Arquitetura

Documento do que o código **é** hoje. A versão anterior descrevia `usecase/`,
`VideoProcessor.kt`, Hilt e Room, que nunca existiram no projeto; use este como
referência e desconfie de qualquer doc que ainda cite aquilo.

## Visão geral

Módulo único (`:app`), MVVM com Jetpack Compose. Sem framework de injeção de
dependência: o que precisa ser trocável é passado por construtor ou factory.

```
CameraScreen ──observa──> CameraViewModel ──chama──> CameraController
  (Compose)                (StateFlow)                 (interface)
                                                            │
                                                     CameraManager
                                                    (CameraX + Camera2)
```

O sentido da dependência é sempre para dentro: a UI conhece o ViewModel, que
conhece a interface do controller, que não conhece ninguém acima.

## Camadas

### `camera/` — acesso ao hardware

| Arquivo | Papel |
|---|---|
| `CameraController.kt` | interface que o ViewModel enxerga, mais `CameraControllerFactory` |
| `CameraManager.kt` | implementação real sobre CameraX e Camera2 interop |
| `CameraTelemetry.kt` | eventos estruturados dos momentos decisivos |
| `CameraMode.kt` | `VIDEO` \| `PHOTO` |

`CameraController` existe por testabilidade. Enquanto o ViewModel instanciava o
`CameraManager` diretamente, nenhuma lógica dele podia ser exercitada sem sensor
real — o arquivo de teste que existia continha só `TODO`. Com a interface, o
teste injeta `FakeCameraController` e roda na JVM.

`CameraManager` também é o ponto onde as **capacidades do aparelho** são
descobertas: quais resoluções e fps existem, se há EIS, se há HDR, qual o alcance
do zoom e se existe lente ultra-wide. Nada disso é assumido — tudo é sondado via
`Camera2CameraInfo` e publicado em `StateFlow`, e é isso que popula os seletores
da UI.

### `presentation/` — UI e estado

`CameraViewModel` é a fachada entre a tela e a câmera. Ele:

1. carrega as preferências do `SettingsStorage` **antes** de criar o controller,
   e semeia o controller com elas — importante porque parte da configuração só
   pode ser aplicada no bind;
2. espelha em `StateFlow` próprios tudo o que o controller publica;
3. reage a capacidade ausente — se o aparelho não suporta EIS ou HDR, desliga a
   opção e persiste o desligamento;
4. cuida do que é puramente de apresentação: contagem regressiva, cronômetro de
   gravação, ciclo do flash de foto.

As telas (`CameraScreen`, `SettingsScreen`, `AboutScreen`) são Compose puro e
navegam por `NavHost` em `MainActivity`. `SettingsScreen` e `AboutScreen` usam
Material 3 com cor dinâmica (`dynamicDarkColorScheme` / `dynamicLightColorScheme`
a partir do Android 12), acompanhando o tema do sistema. `CameraScreen` é
deliberadamente escura e fora do esquema de cores — é visor de câmera.

### `data/` e `domain/`

- `SettingsStorage` — SharedPreferences. Enums gravados por `ordinal`, com
  fallback para o default se o valor lido não existir mais.
- `VideoRepository` / `VideoRepositoryImpl` — repasse fino para o controller.
  Ver a ressalva em [REFACTORING.md](REFACTORING.md#4-videorepository-não-paga-o-próprio-custo).
- `VideoStorage` — atualmente sem uso; a gravação escreve direto no MediaStore
  pelo `CameraManager`.

## Decisões que valem conhecer

**Bind com debounce.** Trocar resolução, proporção ou EIS exige religar os use
cases do CameraX. Toggles rápidos em sequência causariam corrida, então
`scheduleBind()` agrupa os pedidos num `Handler` da main thread.

**EIS e NR fora do `Camera2Interop`.** No merge do `CaptureRequest`, o
`Camera2Interop` tem prioridade sobre o `Camera2CameraControl`: um valor fixado
na criação da sessão não pode mais ser sobrescrito depois. Por isso só o
`AE_TARGET_FPS_RANGE` é definido no builder; EIS e redução de ruído são aplicados
via `applyEisNrImmediate()` depois que a sessão abre.

**EIS desligado no modo foto.** Não se aplica a captura de imagem. O
desligamento é temporário e não é persistido: ao voltar para vídeo, o valor salvo
é restaurado.

**Dois caminhos de captura de foto.** Com melhoria de imagem ou proporção Full,
a foto passa por bitmap para permitir processamento e recorte, e o EXIF é
recopiado à mão. Sem isso, o CameraX salva direto — mais rápido e sem perda de
metadados.

## Estado atual

O que este documento descreve está correto, mas três arquivos concentram
responsabilidade demais: `CameraScreen.kt` (~1.950 linhas),
`CameraManager.kt` (~1.130) e `CameraViewModel.kt` (~615, com ~30 `StateFlow`).
O caminho de saída está em [REFACTORING.md](REFACTORING.md).

## Testes

Ver [CLAUDE.md](CLAUDE.md#testes). Em resumo: lógica pura em JUnit direto,
persistência e ViewModel sob Robolectric com SharedPreferences reais, e
`FakeCameraController` para simular hardware que não se tem à mão.

## Referências

- [CameraX](https://developer.android.com/training/camerax)
- [Camera2 interop](https://developer.android.com/reference/androidx/camera/camera2/interop/package-summary)
- [Guia de arquitetura Android](https://developer.android.com/topic/architecture)
