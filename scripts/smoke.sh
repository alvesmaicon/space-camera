#!/usr/bin/env bash
#
# Smoke test: compila, instala, abre o app e confere que a câmera realmente
# ligou — lendo os eventos de telemetria, não só "o processo subiu".
#
#   scripts/smoke.sh
#
# Sai com código 0 se um `evt=bind` apareceu dentro do tempo limite; 1 caso
# contrário, imprimindo o que saiu no logcat para diagnóstico.
#
# Não substitui teste em aparelho real: o emulador usa câmera virtual, então
# EIS/HDR aparecem como não suportados e 4K/60 não existe. Serve para pegar
# regressão de inicialização, permissão e crash na subida.
set -euo pipefail

cd "$(dirname "$0")/.."

PACOTE="com.spacecamera"
ACTIVITY="$PACOTE/.MainActivity"
TIMEOUT_SEG="${TIMEOUT_SEG:-40}"

ADB="${ADB:-adb}"
command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Library/Android/sdk/platform-tools/adb"

if ! "$ADB" get-state >/dev/null 2>&1; then
    echo "Nenhum device/emulador conectado." >&2
    exit 1
fi

echo "▸ build + install"
./gradlew installDebug --console=plain -q

echo "▸ concedendo permissões (evita o diálogo travar o teste)"
for p in CAMERA RECORD_AUDIO ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION; do
    "$ADB" shell pm grant "$PACOTE" "android.permission.$p" 2>/dev/null || true
done

echo "▸ reiniciando o app"
"$ADB" logcat -c
"$ADB" shell am force-stop "$PACOTE"
"$ADB" shell am start -n "$ACTIVITY" >/dev/null

echo "▸ aguardando evt=bind (até ${TIMEOUT_SEG}s)"
SAIDA=$(mktemp)
"$ADB" logcat -v time -s SpaceCam:V > "$SAIDA" &
LOGCAT_PID=$!
# `wait` engole o aviso "Terminated" que o shell imprimiria ao matar o logcat.
limpar() { kill "$LOGCAT_PID" 2>/dev/null || true; wait "$LOGCAT_PID" 2>/dev/null || true; rm -f "$SAIDA"; }
trap limpar EXIT

for _ in $(seq "$TIMEOUT_SEG"); do
    if grep -q "evt=bind " "$SAIDA"; then
        echo
        echo "✓ câmera inicializou"
        grep -E "evt=(bind|caps) " "$SAIDA" | sed 's/^/  /'
        exit 0
    fi
    if grep -qE "evt=(bind_failed|camera_init_failed)" "$SAIDA"; then
        echo
        echo "✗ falha ao inicializar a câmera" >&2
        sed 's/^/  /' "$SAIDA" >&2
        exit 1
    fi
    sleep 1
done

echo
echo "✗ nenhum evt=bind em ${TIMEOUT_SEG}s. Logcat completo:" >&2
sed 's/^/  /' "$SAIDA" >&2
exit 1
