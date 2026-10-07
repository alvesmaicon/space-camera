# Verificação da Tarefa 10 — Personalização de modos

Redmi Note 10 (API 31), 2026-10-07. APK conferido pelo dex ("Restaurar padrão" presente).

## Roteiro em aparelho

| Passo | Resultado |
|---|---|
| Gaveta → "Editar" | abre Configurações com **"Modos da câmera" no topo** |
| Vídeo e Foto | "Sempre no seletor", **sem interruptor** (AC-7.1) |
| Pro | "Em Mais modos", com interruptor e setas |
| Ligar o Pro e subir duas vezes | personalização: Pro, Vídeo, Foto |
| Voltar à câmera | seletor **Pro, Vídeo, Foto, Mais** (AC-7.2) |
| Fechar e reabrir o app | **Pro, Vídeo, Foto, Mais** — persistiu |
| Reinstalar o APK | persistiu |
| "Restaurar padrão" | Vídeo, Foto (Pro de volta em Mais modos); seletor Vídeo, Foto, Mais |
| Fechar e reabrir | Vídeo, Foto, Mais — o padrão também persistiu (AC-16.1) |

Defeito visual encontrado e corrigido na própria rodada: as setas de Vídeo e Foto saíam
deslocadas para a direita das do Pro, porque essas linhas não têm interruptor. O espaço
do interruptor passou a ser reservado; conferido no dump — as setas das três linhas no
mesmo x.

## Cross-onda: Vídeo e Foto inalterados

Mesmo roteiro das Tarefas 7 e 8 (`../roteiros/roteiro-t7.sh`), comparado com a Tarefa 8:
árvore de UI idêntica nos 4 estados, `evt=bind` idêntico, mídia idêntica, ícone do flash
idêntico pixel a pixel, latência p95 34 ms.

## Mutação

| Mutação | Testes que falham |
|---|---:|
| Mover por cima de um modo indisponível | 1 |
| Essencial removível | 1 |
| Registro sem essenciais | 1 |
| Preferência corrompida estoura | 1 |
| Personalização não é gravada | 1 |
| Preferência não é carregada ao abrir | 1 |
| Personalizar perde o plano padrão | 1 |
