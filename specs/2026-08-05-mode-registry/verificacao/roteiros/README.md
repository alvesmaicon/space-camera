# Roteiros de verificação em aparelho

Usados nas Tarefas 6 e 7. Localizam controles **por rótulo** a cada passo (os controles
mudam de posição com a proporção — armadilha 2 do baseline de agosto) e nunca fazem dump
de UI dentro de um laço de toques (armadilha 4).

| Arquivo | Para quê |
|---|---|
| `ui.py` | `find <rótulo>`, `shutter`, `count` — centro de um nó a partir do `uiautomator dump` |
| `nos.py` | lista normalizada `texto\|desc\|clicável\|bounds` de um dump, para `diff` entre versões |
| `latencia.sh` | 20 trocas Vídeo↔Foto com a foto em 9:16, coordenadas tomadas antes do laço |
| `roteiro-t7.sh <apk> <rótulo>` | roteiro completo: UI nos 4 estados, flash, fotos nas 3 proporções, EIS, latência |

Para comparar antes e depois: construir o commit anterior num `git worktree` (nunca
`git stash`), rodar `roteiro-t7.sh` nos dois APKs **na mesma sessão** e sem mover o
aparelho, e fazer `diff` das pastas.

Calibrados no Redmi Note 10 (1080×2400): `ui.py shutter` procura o disparador centrado em
x=540. Em outra tela, ajuste esse valor. Os roteiros esperam `adb` no `PATH`, aparelho
desbloqueado e `svc power stayon true`.
