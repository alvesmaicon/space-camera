#!/usr/bin/env node

/**
 * Script para gerar notificação e adaptive icons
 * 
 * Uso: node scripts/generateNotificationIcons.js
 */

const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

// Tamanhos para notification icons
const NOTIFICATION_ICONS = [
  { name: 'mdpi', size: 24 },
  { name: 'hdpi', size: 36 },
  { name: 'xhdpi', size: 48 },
  { name: 'xxhdpi', size: 72 },
  { name: 'xxxhdpi', size: 96 },
];

const SOURCE_IMAGE = path.join(__dirname, '../space-cam.png');
const BASE_RES_DIR = path.join(__dirname, '../app/src/main/res');

async function generateNotificationIcons() {
  try {
    if (!fs.existsSync(SOURCE_IMAGE)) {
      throw new Error(`Imagem base não encontrada: ${SOURCE_IMAGE}`);
    }

    console.log('🔔 Gerando notification icons...\n');

    // Gerar notification icons (em branco)
    for (const icon of NOTIFICATION_ICONS) {
      const outputDir = path.join(BASE_RES_DIR, `drawable-${icon.name}`);
      const outputFile = path.join(outputDir, 'ic_notification.png');

      if (!fs.existsSync(outputDir)) {
        fs.mkdirSync(outputDir, { recursive: true });
      }

      await sharp(SOURCE_IMAGE)
        .resize(icon.size, icon.size, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
        .greyscale()
        .negate()
        .png()
        .toFile(outputFile);

      console.log(`✓ Gerado: ic_notification.png (${icon.size}x${icon.size}) → drawable-${icon.name}`);
    }

    console.log('\n✅ Notification icons gerados com sucesso!');

  } catch (error) {
    console.error('❌ Erro:', error.message);
    process.exit(1);
  }
}

try {
  require.resolve('sharp');
  generateNotificationIcons();
} catch (e) {
  console.error('❌ Erro: sharp não está instalado');
  process.exit(1);
}
