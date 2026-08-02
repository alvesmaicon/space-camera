# Scripts de Geração de Assets

Este diretório contém scripts para gerar automaticamente assets (ícones, etc) para o app.

## Gerar Ícones

### Opção 1: Com Node.js (Recomendado - Multiplataforma)

**Instalação das dependências:**
```bash
npm install
```

**Gerar launcher icons:**
```bash
npm run generate:icons
```

**Gerar notification icons:**
```bash
npm run generate:notification-icons
```

**Gerar todos os ícones:**
```bash
npm run generate:all-icons
```

Requer: Node.js 16+

### Opção 2: Com ImageMagick (Bash)

**Instalação:**
```bash
# macOS
brew install imagemagick

# Linux
sudo apt-get install imagemagick

# Windows
# Usar opção Node.js
```

**Executar:**
```bash
chmod +x scripts/generateIcons.sh
./scripts/generateIcons.sh
```

## Como Funcionam os Scripts

### generateIcons.js
- Lê `space-cam.png` da raiz do projeto
- Redimensiona para 6 densidades de tela (ldpi até xxxhdpi)
- Gera `ic_launcher.png` em cada diretório `mipmap-*`
- Suporta background color customizável

### generateNotificationIcons.js
- Gera versões em tons de cinza para notification icons
- Tamanhos menores que launcher icons
- Salva como `ic_notification.png`

## Tamanhos Gerados

| Densidade | Tamanho | Uso |
|-----------|---------|-----|
| ldpi | 36x36 | Telas antigas (obsoleto) |
| mdpi | 48x48 | Baseline Android |
| hdpi | 72x72 | Telas de 5" |
| xhdpi | 96x96 | Telas de 5-6" |
| xxhdpi | 144x144 | Telas de 6-7" |
| xxxhdpi | 192x192 | Telas grandes |

## Personalização

Para modificar os ícones, edite `space-cam.png` e regenere com:

```bash
npm run generate:all-icons
```

## Estrutura de Diretórios Gerada

```
app/src/main/res/
├── mipmap-ldpi/
│   └── ic_launcher.png
├── mipmap-mdpi/
│   └── ic_launcher.png
├── mipmap-hdpi/
│   └── ic_launcher.png
├── mipmap-xhdpi/
│   └── ic_launcher.png
├── mipmap-xxhdpi/
│   └── ic_launcher.png
├── mipmap-xxxhdpi/
│   └── ic_launcher.png
├── drawable-mdpi/
│   └── ic_notification.png
└── ... (outras densidades)
```

## Troubleshooting

### "sharp not found"
```bash
npm install sharp --save-dev
```

### Qualidade de ícone ruim
- Use PNG com background transparente ou sólido
- Resolução mínima recomendada: 512x512

### "convert not found" (ImageMagick)
Use `npm run generate:icons` em vez de bash

## Próximos Passos

Após gerar os ícones:

```bash
# Compilar para ver os ícones no app
npm run build:debug

# Instalar em dispositivo
npm run install:debug
```

## Referências

- [Android App Icon Guidelines](https://developer.android.com/studio/write/image-asset-studio)
- [Material Design Icons](https://fonts.google.com/icons)
- [Adaptive Icons (Android 8+)](https://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive)
