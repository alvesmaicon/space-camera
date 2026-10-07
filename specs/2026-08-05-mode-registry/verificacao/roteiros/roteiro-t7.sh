#!/usr/bin/env bash
# Uso: roteiro-t7.sh <apk> <rótulo>. Roda o mesmo roteiro e grava em $S/t7-<rótulo>/.
set -uo pipefail
S="$(dirname "$0")"; APK=$1; R=$2; O=$S/t7-$R; rm -rf $O; mkdir -p $O
U() { python3 -I "$S/ui.py" "$@"; }
snap() { U count >/dev/null; python3 -I "$S/nos.py" "$S/ui.xml" > "$O/ui-$1.txt"; }
ultima(){ adb shell content query --uri content://media/external/images/media --projection width:height:orientation --sort "_id\ DESC" 2>/dev/null | head -1 | sed 's/Row: 0 //'; }
adb install -r "$APK" >/dev/null
adb logcat -c
adb logcat -v time -s SpaceCam:V > "$O/logcat.txt" & LP=$!
adb shell am start -S -n com.spacecamera/.MainActivity --activity-clear-task >/dev/null 2>&1; sleep 5
# 1. árvore de UI nos quatro estados
snap video
adb shell input tap $(U find 'Mais opções'); sleep 1.5; snap video-mais; adb shell input tap $(U find 'Recolher'); sleep 1.5
adb shell input tap $(U find Foto); sleep 3
for _ in 1 2 3; do U find 9:16 >/dev/null 2>&1 && break; adb shell input tap $(U find Full 2>/dev/null || U find 3:4); sleep 3; done
snap foto
adb shell input tap $(U find 'Mais opções'); sleep 1.5; snap foto-mais; adb shell input tap $(U find 'Recolher'); sleep 1.5
# 2. ciclo do flash em foto: recorte do ícone a cada toque (OFF → AUTO → ON → OFF)
FL=$(U find Flash); set -- $FL; FX=$1; FY=$2
for i in 0 1 2 3; do
  adb exec-out screencap -p > $O/tela.png
  sips -c 90 90 --cropOffset $((FY-45)) $((FX-45)) $O/tela.png --out $O/flash-$i.png >/dev/null 2>&1
  [ $i -lt 3 ] && { adb shell input tap $FX $FY; sleep 1; }
done
# 3. fotos nas três proporções (começa em 9:16)
for i in 1 2 3; do
  label=""; for x in 9:16 3:4 Full; do U find "$x" >/dev/null 2>&1 && { label=$x; break; }; done
  sh=$(U shutter); adb shell input tap $sh; sleep 5
  echo "$label $(ultima)" >> $O/midia.txt
  adb shell input tap $(U find $label); sleep 3
done
# 4. EIS: Foto → Vídeo → Foto → Vídeo (os binds registram eis=)
adb shell input tap $(U find Vídeo); sleep 3; adb shell input tap $(U find Foto); sleep 3; adb shell input tap $(U find Vídeo); sleep 3
# 5. latência: 20 trocas
bash "$S/latencia.sh" > $O/latencia-roteiro.txt 2>&1
kill $LP
ini=$(sed -n 's/^início=//p' $O/latencia-roteiro.txt); fim=$(sed -n 's/^fim=\([0-9:]*\).*/\1/p' $O/latencia-roteiro.txt)
awk -v a="$ini" -v b="$fim" '$2>=a && $2<=b' $O/logcat.txt | grep -a 'evt=bind' | sed 's/.*evt=/evt=/' > $O/latencia.txt
grep -aE 'evt=bind' $O/logcat.txt | awk -v a="$ini" '$2<a' | sed -E 's/.*(evt=bind)/\1/; s/ elapsed_ms=[0-9]+//' > $O/binds-antes-da-latencia.txt
echo "feito: $O"
