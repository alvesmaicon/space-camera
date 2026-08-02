#!/usr/bin/env node

/**
 * Script para gerar ícones do Android a partir de uma imagem base
 * 
 * Uso: node scripts/generateIcons.js
 * 
 * Tamanhos gerados:
 * - ldpi: 36x36
 * - mdpi: 48x48
 * - hdpi: 72x72
 * - xhdpi: 96x96
 * - xxhdpi: 144x144
 * - xxxhdpi: 192x192
 */

const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

// Definição dos ícones a serem gerados
const ICONS = [
  { name: 'ldpi', size: 36 },
  { name: 'mdpi', size: 48 },
  { name: 'hdpi', size: 72 },
  { name: 'xhdpi', size: 96 },
  { name: 'xxhdpi', size: 144 },
  { name: 'xxxhdpi', size: 192 },
];

// Caminho da imagem base
const SOURCE_IMAGE = path.join(__dirname, '../space-cam-v2.png');
const BASE_RES_DIR = path.join(__dirname, '../app/src/main/res');

async function generateIcons() {
  try {
    // Verificar se a imagem base existe
    if (!fs.existsSync(SOURCE_IMAGE)) {
      throw new Error(`Imagem base não encontrada: ${SOURCE_IMAGE}`);
    }

    console.log('🎨 Iniciando geração de ícones...\n');
    console.log(`📁 Imagem base: ${SOURCE_IMAGE}`);
    console.log(`📍 Diretório destino: ${BASE_RES_DIR}\n`);

    // Gerar cada tamanho de ícone
    for (const icon of ICONS) {
      const outputDir = path.join(BASE_RES_DIR, `mipmap-${icon.name}`);
      const outputFile = path.join(outputDir, 'ic_launcher.png');

      // Criar diretório se não existir
      if (!fs.existsSync(outputDir)) {
        fs.mkdirSync(outputDir, { recursive: true });
        console.log(`✓ Criado diretório: mipmap-${icon.name}`);
      }

      // Redimensionar e salvar imagem
      // Zoom de 30% para trazer o ícone para frente sem cortar o conteúdo
      const zoomFactor = 1.30;
      const zoomedSize = Math.round(icon.size * zoomFactor);
      const cropOffset = Math.round((zoomedSize - icon.size) / 2);
      await sharp(SOURCE_IMAGE)
        .resize(zoomedSize, zoomedSize, {
          fit: 'contain',
          position: 'center',
          background: { r: 235, g: 235, b: 235, alpha: 1 }
        })
        .extract({ left: cropOffset, top: cropOffset, width: icon.size, height: icon.size })
        .png()
        .toFile(outputFile);

      console.log(`✓ Gerado: ic_launcher.png (${icon.size}x${icon.size}) → mipmap-${icon.name}`);
    }

    console.log('\n✅ Ícones gerados com sucesso!');
    console.log('\n📌 Próximos passos:');
    console.log('   1. Compile o projeto: npm run build:debug');
    console.log('   2. Os ícones aparecerao no launcher do app');
    console.log('   3. Para adaptive icons, edite AndroidManifest.xml\n');

  } catch (error) {
    console.error('❌ Erro ao gerar ícones:', error.message);
    process.exit(1);
  }
}

// Verificar se sharp está instalado
try {
  require.resolve('sharp');
  generateIcons();
} catch (e) {
  console.error('❌ Erro: sharp não está instalado');
  console.log('\nInstale com: npm install sharp --save-dev\n');
  process.exit(1);
}
