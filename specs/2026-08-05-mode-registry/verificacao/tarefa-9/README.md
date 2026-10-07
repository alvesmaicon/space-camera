# Verificação da Tarefa 9 — Seletor em janela larga com N modos

O Redmi Note 10 (Android 12) respeita o `screenOrientation="portrait"` do manifesto e nunca
fica em janela larga. A conferência foi no emulador **`Tablet_API36`** (2560×1600, Android
16, que ignora a trava de orientação em tela grande).

## Teste Compose (o caso de muitos modos)

`ModeSelectorWideWindowTest`, janela de 960×480dp: com 6 modos no plano + "Mais", a coluna
tem teto de altura e rola; o ativo no fim é trazido à vista; os demais são alcançáveis
rolando; com 2 modos + "Mais" tudo aparece sem rolar. Mutações (sem trazer à vista, sem
teto, sem rolagem) derrubam 1, 1 e 2 testes.

## Emulador

| Situação | Resultado |
|---|---|
| Vídeo e Foto (gaveta vazia) | coluna Vídeo, Foto, Mais, encostada à direita |
| Gaveta aberta | painel de 280dp ao lado, com o Pro (este emulador reporta `MANUAL_SENSOR`) |
| Pro fixado pela personalização | coluna Vídeo, Foto, Pro, Mais — cabe sem rolar |
| Pro ativo | escalas de obturador e ISO à esquerda da coluna, sem sobrepor; sem flash na barra |

**Achado sobre o método da spec:** este emulador reporta
`manual_sensor=true iso=100-1600 exposure_ns=1000-300000000`. A premissa "o emulador não
tem `MANUAL_SENSOR`" vale para o emulador de telefone usado antes, não para este — o Pro
é exercitável aqui, ainda que sem a física real do sensor.

**Pré-visualização em retrato dentro da caixa 16:9:** é da câmera virtual deste emulador.
O `evt=bind` reporta `aspect=16:9`, e o build de **antes da spec** (`17832b6`), instalado no
mesmo emulador, mostra a mesma imagem — não é regressão.

## Ressalva

Com a gaveta aberta em janela larga, a coluna de controles se alarga para os 280dp do
painel e o seletor, centralizado nela, desliza para a esquerda até a gaveta fechar.
Cosmético e só durante a gaveta aberta; fica registrado.
