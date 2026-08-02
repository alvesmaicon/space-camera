#!/usr/bin/env node

/**
 * Script auxiliar para informações sobre geração de ícones
 * Uso: node scripts/iconHelp.js
 */

console.log(`
╔════════════════════════════════════════════════════════════════╗
║                                                                ║
║          🎨 SPACE CAMERA - ICON GENERATION HELPER 🎨          ║
║                                                                ║
╚════════════════════════════════════════════════════════════════╝

📁 Imagem Base: space-cam.png (na raiz do projeto)

🎯 COMANDOS RÁPIDOS:

  npm run generate:icons
    └─ Gera launcher icons (ldpi, mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)

  npm run generate:notification-icons  
    └─ Gera notification icons em tons de cinza

  npm run generate:all-icons
    └─ Gera todos os ícones (launcher + notification)

📊 TAMANHOS GERADOS:

  Launcher Icons:
    • ldpi:      36x36
    • mdpi:      48x48
    • hdpi:      72x72
    • xhdpi:     96x96
    • xxhdpi:    144x144
    • xxxhdpi:   192x192

  Notification Icons:
    • mdpi:      24x24
    • hdpi:      36x36
    • xhdpi:     48x48
    • xxhdpi:    72x72
    • xxxhdpi:   96x96

📁 DESTINO:

  app/src/main/res/
  ├── mipmap-ldpi/ic_launcher.png
  ├── mipmap-mdpi/ic_launcher.png
  ├── ... (mais densidades)
  └── drawable-*/ic_notification.png

💡 DICAS:

  1. Prepare sua imagem (PNG, JPG)
  2. Coloque como 'space-cam.png' na raiz
  3. Execute: npm run generate:all-icons
  4. Compile: npm run build:debug
  5. Instale: npm run install:debug

⚠️  REQUISITOS:

  • Node.js 16+
  • Sharp (instalado via: npm install)

📖 DOCUMENTAÇÃO COMPLETA:

  Veja ICON_GENERATION.md para mais detalhes e troubleshooting

════════════════════════════════════════════════════════════════

`);
