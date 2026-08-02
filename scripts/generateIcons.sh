#!/bin/bash

# Script bash para gerar ícones (alternativa ao Node.js)
# Requer: ImageMagick (convert) instalado
# macOS: brew install imagemagick
# Linux: sudo apt-get install imagemagick
# Windows: Usar generateIcons.js (Node.js)

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

SOURCE_IMAGE="$PROJECT_ROOT/space-cam.png"
BASE_RES_DIR="$PROJECT_ROOT/app/src/main/res"

ICONS=(
  "ldpi:36"
  "mdpi:48"
  "hdpi:72"
  "xhdpi:96"
  "xxhdpi:144"
  "xxxhdpi:192"
)

echo "🎨 Gerando ícones Android..."
echo "📁 Imagem base: $SOURCE_IMAGE"
echo ""

# Verificar se ImageMagick está instalado
if ! command -v convert &> /dev/null; then
    echo "❌ Erro: ImageMagick não encontrado!"
    echo ""
    echo "Instale com:"
    echo "  macOS: brew install imagemagick"
    echo "  Linux: sudo apt-get install imagemagick"
    echo "  Windows: Use npm run generate:icons"
    echo ""
    exit 1
fi

# Verificar se imagem base existe
if [ ! -f "$SOURCE_IMAGE" ]; then
    echo "❌ Erro: Imagem não encontrada: $SOURCE_IMAGE"
    exit 1
fi

# Gerar ícones para cada densidade
for icon in "${ICONS[@]}"; do
    IFS=':' read -r name size <<< "$icon"
    output_dir="$BASE_RES_DIR/mipmap-$name"
    output_file="$output_dir/ic_launcher.png"

    mkdir -p "$output_dir"

    convert "$SOURCE_IMAGE" \
        -resize "${size}x${size}" \
        -gravity center \
        -extent "${size}x${size}" \
        -background '#1f1f1f' \
        "$output_file"

    echo "✓ Gerado: ic_launcher.png (${size}x${size}) → mipmap-$name"
done

echo ""
echo "✅ Ícones gerados com sucesso!"
