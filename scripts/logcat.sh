#!/usr/bin/env bash
#
# Logcat filtrado no Space Camera.
#
# O app planta uma Timber tree com tag fixa "SpaceCam" (ver SpaceCameraApp.kt),
# então este script pega tudo do app e nada do resto do sistema.
#
#   scripts/logcat.sh                # tudo do app
#   scripts/logcat.sh evt=           # só os eventos estruturados de telemetria
#   scripts/logcat.sh 'evt=bind'     # só os binds do CameraX
#   scripts/logcat.sh --erros        # só W/E
#   scripts/logcat.sh --limpar       # zera o buffer antes de seguir
#
# Eventos disponíveis (ver CameraTelemetry.kt):
#   evt=bind          parâmetros aplicados na sessão + tempo do bind
#   evt=caps          capacidades sondadas do HAL — responde "por que 4K/60 não aparece"
#   evt=rec_start     gravação iniciada
#   evt=rec_stop      gravação finalizada com duração e tamanho
#   evt=photo         foto capturada com preset, dimensões e latência
#   evt=*_failed      falhas, com stacktrace
set -euo pipefail

TAG="SpaceCam"
ADB="${ADB:-adb}"
command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Library/Android/sdk/platform-tools/adb"

if ! "$ADB" get-state >/dev/null 2>&1; then
    echo "Nenhum device/emulador conectado. Rode: $ADB devices" >&2
    exit 1
fi

FILTRO=""
NIVEL="$TAG:V"

for arg in "$@"; do
    case "$arg" in
        --limpar) "$ADB" logcat -c ;;
        --erros)  NIVEL="$TAG:W" ;;
        *)        FILTRO="$arg" ;;
    esac
done

echo "── logcat [$TAG]${FILTRO:+ filtro: $FILTRO} — Ctrl+C para sair" >&2

if [ -n "$FILTRO" ]; then
    "$ADB" logcat -v time -s "$NIVEL" | grep --line-buffered -- "$FILTRO"
else
    "$ADB" logcat -v time -s "$NIVEL"
fi
