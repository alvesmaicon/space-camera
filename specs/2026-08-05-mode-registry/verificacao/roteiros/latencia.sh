#!/usr/bin/env bash
# 20 trocas Vídeo<->Foto. Coordenadas localizadas por rótulo ANTES do laço;
# nada de dump durante (armadilha 4 do baseline de agosto).
set -euo pipefail
S="$(dirname "$0")"; U() { python3 -I "$S/ui.py" "$@"; }
# Garante Foto em 9:16, como no baseline de agosto.
adb shell input tap $(U find Foto); sleep 3
for _ in 1 2 3; do U find 9:16 >/dev/null 2>&1 && break; adb shell input tap $(U find Full 2>/dev/null || U find 3:4); sleep 3; done
U find 9:16 >/dev/null || { echo "não consegui pôr 9:16"; exit 1; }
FOTO_ATIVA_VIDEO=$(U find Vídeo)          # com Foto ativo: onde está "Vídeo"
adb shell input tap $FOTO_ATIVA_VIDEO; sleep 3
VIDEO_ATIVO_FOTO=$(U find Foto)           # com Vídeo ativo: onde está "Foto"
echo "Vídeo (com Foto ativo)=[$FOTO_ATIVA_VIDEO]  Foto (com Vídeo ativo)=[$VIDEO_ATIVO_FOTO]  nós=$(U count)"
INICIO=$(adb shell date +%H:%M:%S)
echo "início=$INICIO"
for i in $(seq 1 10); do
  adb shell input tap $VIDEO_ATIVO_FOTO; sleep 2
  adb shell input tap $FOTO_ATIVA_VIDEO; sleep 2
done
echo "fim=$(adb shell date +%H:%M:%S) nós=$(U count)"
