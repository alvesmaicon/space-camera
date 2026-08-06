#!/usr/bin/env bash
#
# Gera um APK de backup, nomeado pela versão e pelo commit, e opcionalmente o
# instala num aparelho.
#
#   scripts/backup-apk.sh                      # só arquiva em backups/
#   scripts/backup-apk.sh --instalar           # arquiva e instala
#   scripts/backup-apk.sh --instalar -s SERIAL # escolhe o aparelho
#   scripts/backup-apk.sh --listar             # lista os backups existentes
#
# Por que **debug** e não release: o `build.gradle.kts` não tem `signingConfigs`,
# então `assembleRelease` produz `app-release-unsigned.apk`, que nenhum aparelho
# instala. Enquanto a assinatura não existir, o APK distribuível é o debug — que é
# assinado com a chave de debug e serve para guardar e reinstalar.
#
# O arquivo vai para `backups/`, já ignorado pelo git (binário não se versiona).
set -euo pipefail

cd "$(dirname "$0")/.."

PACOTE="com.spacecamera"
DESTINO="backups"

ADB="${ADB:-adb}"
command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Library/Android/sdk/platform-tools/adb"

INSTALAR=0
SERIAL=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --instalar|-i) INSTALAR=1; shift ;;
        -s) SERIAL="${2:?-s exige um serial}"; shift 2 ;;
        --listar|-l)
            ls -lh "$DESTINO"/*.apk 2>/dev/null || echo "Nenhum backup em $DESTINO/."
            exit 0 ;;
        -h|--help) sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Opção desconhecida: $1" >&2; exit 2 ;;
    esac
done

# ── Identidade do build ─────────────────────────────────────────────────────
versao ()  { grep -E "^$1 *=" gradle/libs.versions.toml | head -1 | sed 's/.*"\([^"]*\)".*/\1/'; }
NOME_VERSAO="$(versao appVersionName)"
COD_VERSAO="$(versao appVersionCode)"
SHA="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
SUFIXO=""
if ! git diff-index --quiet HEAD -- 2>/dev/null; then
    SUFIXO="-sujo"
    echo "⚠ árvore de trabalho com alterações não comitadas — o backup levará o sufixo '-sujo',"
    echo "  porque o commit $SHA não identifica sozinho o que está dentro deste APK."
fi

ARQUIVO="$DESTINO/space-camera-${NOME_VERSAO}-${COD_VERSAO}-${SHA}${SUFIXO}-$(date +%Y%m%d-%H%M).apk"

# ── Build ───────────────────────────────────────────────────────────────────
echo "▸ build debug"
./gradlew assembleDebug --console=plain -q

ORIGEM="app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$ORIGEM" ]] || { echo "APK não encontrado em $ORIGEM" >&2; exit 1; }

# Confere que o APK no disco é deste commit, e não um sobrevivente de build
# anterior. `assembleDebug` já reportou UP-TO-DATE mantendo APK velho nesta base
# (ver CLAUDE.md); o `adb install` seguinte instala o antigo sem reclamar.
echo "▸ conferindo que o APK é deste código"
TMP_DEX="$(mktemp -d)"
trap 'rm -rf "$TMP_DEX"' EXIT
unzip -qo "$ORIGEM" 'classes*.dex' -d "$TMP_DEX"
# `grep` lê os .dex direto, sem `cat |`: com `pipefail`, o `grep -q` sai no primeiro
# match, o `cat` toma SIGPIPE e o pipeline retorna erro — ou seja, a verificação
# falharia exatamente quando o SHA **está** presente. Custou uma rodada aqui.
if [[ "$SHA" != "unknown" ]] && ! grep -qa "$SHA" "$TMP_DEX"/*.dex; then
    echo "✗ o APK não contém o GIT_SHA $SHA — build velho no disco." >&2
    echo "  Rode './gradlew clean assembleDebug' e tente de novo." >&2
    exit 1
fi
echo "  ok: GIT_SHA $SHA presente no dex"

mkdir -p "$DESTINO"
cp "$ORIGEM" "$ARQUIVO"
echo "▸ arquivado: $ARQUIVO ($(du -h "$ARQUIVO" | cut -f1))"

[[ "$INSTALAR" -eq 1 ]] || exit 0

# ── Escolha do aparelho ─────────────────────────────────────────────────────
if [[ -z "$SERIAL" ]]; then
    # Sem `mapfile`: é builtin de bash 4+, e o macOS traz o 3.2 em /bin/bash.
    DEVICES=()
    while IFS= read -r d; do [[ -n "$d" ]] && DEVICES+=("$d"); done < <(
        "$ADB" devices | awk '$2=="device" {print $1}'
    )
    case "${#DEVICES[@]}" in
        0) echo "Nenhum aparelho conectado." >&2; exit 1 ;;
        1) SERIAL="${DEVICES[0]}" ;;
        *)
            # Com emulador e aparelho juntos, `adb install` falha por ambiguidade.
            # Preferir o físico, que é o alvo desta tarefa.
            FISICOS=()
            for d in "${DEVICES[@]}"; do [[ "$d" == emulator-* ]] || FISICOS+=("$d"); done
            if [[ "${#FISICOS[@]}" -eq 1 ]]; then
                SERIAL="${FISICOS[0]}"
                echo "▸ vários aparelhos; escolhido o físico: $SERIAL"
            else
                echo "Mais de um aparelho candidato. Use -s SERIAL:" >&2
                printf '  %s\n' "${DEVICES[@]}" >&2
                exit 1
            fi ;;
    esac
fi

MODELO="$("$ADB" -s "$SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
ANDROID="$("$ADB" -s "$SERIAL" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
API="$("$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
echo "▸ instalando em $SERIAL — $MODELO, Android $ANDROID (API $API)"

if ! "$ADB" -s "$SERIAL" install -r "$ARQUIVO"; then
    echo >&2
    echo "✗ instalação falhou. Causa mais comum: já existe um $PACOTE instalado com" >&2
    echo "  assinatura diferente (outra máquina, ou build de release)." >&2
    echo "  Nesse caso: adb -s $SERIAL uninstall $PACOTE   (apaga os dados do app)" >&2
    exit 1
fi

INSTALADO="$("$ADB" -s "$SERIAL" shell dumpsys package "$PACOTE" | grep -m1 versionName | tr -d ' \r')"
echo "✓ instalado — $INSTALADO"
echo
echo "Para conferir qual build está no aparelho, a tela Sobre mostra o GIT_SHA ($SHA)."
echo "Próximo passo sugerido: scripts/logcat.sh 'evt=caps' e abrir o app, para ver"
echo "o que este hardware realmente suporta — 4K/60, EIS e HDR só aparecem em"
echo "aparelho real; o emulador reporta tudo como não suportado."
