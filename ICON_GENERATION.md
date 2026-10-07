# Geração de Ícones - Guia Completo

Este guia explica como gerar ícones para o Space Camera app usando a imagem `space-cam.png`.

## 📋 Pré-requisitos

- Node.js 16+ instalado (para método recomendado)
- OU ImageMagick instalado (para método alternativo bash)

## 🚀 Método 1: Node.js (Recomendado)

### 1. Instale as dependências

```bash
cd space-camera
npm install
```

Isso instalará o `sharp` (biblioteca de processamento de imagens).

### 2. Gere os ícones

```bash
# Apenas launcher icons
npm run generate:icons

# Apenas notification icons  
npm run generate:notification-icons

# Todos os ícones
npm run generate:all-icons
```

### 3. Resultado

Os ícones serão criados em:
```
app/src/main/res/
├── mipmap-ldpi/ic_launcher.png      (36x36)
├── mipmap-mdpi/ic_launcher.png      (48x48)
├── mipmap-hdpi/ic_launcher.png      (72x72)
├── mipmap-xhdpi/ic_launcher.png     (96x96)
├── mipmap-xxhdpi/ic_launcher.png    (144x144)
├── mipmap-xxxhdpi/ic_launcher.png   (192x192)
├── drawable-mdpi/ic_notification.png
├── drawable-hdpi/ic_notification.png
├── drawable-xhdpi/ic_notification.png
├── drawable-xxhdpi/ic_notification.png
└── drawable-xxxhdpi/ic_notification.png
```

**Vantagens:**
✅ Funciona em Windows, macOS e Linux
✅ Sem dependências do sistema
✅ Qualidade superior
✅ Rápido

---

## 🖼️ Método 2: Bash com ImageMagick

### 1. Instale ImageMagick

**macOS (com Homebrew):**
```bash
brew install imagemagick
```

**Linux (Debian/Ubuntu):**
```bash
sudo apt-get install imagemagick
```

**Linux (Fedora/RHEL):**
```bash
sudo dnf install ImageMagick
```

### 2. Confirme a instalação

```bash
convert --version
```

### 3. Execute o script

```bash
chmod +x scripts/generateIcons.sh
./scripts/generateIcons.sh
```

**Vantagens:**
✅ Sem dependências Node.js
✅ Rápido
✅ Controle granular

**Desvantagens:**
❌ Só funciona em macOS/Linux
❌ Requer instalação adicional

---

## 🎨 Customizar a Imagem Base

O script usa `space-cam.png` como imagem base. Para personalizar:

### 1. Prepare sua imagem

- **Formato**: PNG, JPG ou qualquer formato comum
- **Resolução**: Mínimo 512x512 (recomendado 1024x1024 ou maior)
- **Aspecto**: Quadrada é ideal
- **Fundo**: Pode ter transparência ou cor sólida

### 2. Coloque na raiz do projeto

```bash
# Copiar ou movimentar a imagem
cp /caminho/para/seu/icone.png space-cam.png
```

### 3. Regenere os ícones

```bash
npm run generate:all-icons
```

---

## 🔧 Personalização Avançada

### Modificar Cores de Fundo

Edite `scripts/generateIcons.js`:

```javascript
// Linha ~40: Alterare cor de fundo para azul
background: { r: 0, g: 100, b: 200, alpha: 1 }
```

RGB valores (0-255).

### Gerar em Lote (Python)

Se precisar gerar para múltiplos apps:

```python
import os
import subprocess

images = ['app1.png', 'app2.png', 'app3.png']

for img in images:
    os.system(f'npm run generate:icons -- {img}')
```

---

## ✅ Verificar os Ícones Gerados

### 1. Via Terminal

```bash
ls -la app/src/main/res/mipmap-*/
```

### 2. Via Gradle

```bash
./gradlew assembleDebug
```

### 3. Via Android Studio

Abra: `File > Open > Android > app/src/main/res`

---

## 📱 Compilar e Testar

Após gerar os ícones:

```bash
# Compilar debug
npm run build:debug

# Instalar em dispositivo
npm run install:debug

# Executar app
npm run run:debug
```

O ícone deve aparecer na tela inicial do Android com o launcher icons de diferentes tamanhos.

---

## 🐛 Troubleshooting

### Erro: "sharp not found"

```bash
npm install sharp --save-dev
```

### Erro: "convert: command not found"

Você está usando o script bash sem ImageMagick instalado. Use:

```bash
npm run generate:icons
```

### Ícones com qualidade ruim

- Use imagem com resolução ≥512x512
- Tente usar PNG em vez de JPG
- Remova fundo complexo/gradiente

### Ícones não aparecem no app

1. Limpe build: `npm run clean`
2. Regenere: `npm run generate:all-icons`
3. Recompile: `npm run build:debug`
4. Reinstale: `adb uninstall com.spacecamera && npm run run:debug`

### Erro de permissão (chmod)

```bash
sudo chmod +x scripts/generateIcons.sh
```

---

## 📚 Recursos Úteis

- [Android Icon Guidelines](https://developer.android.com/studio/write/image-asset-studio)
- [Material Design Icons](https://fonts.google.com/icons)
- [Adaptive Icons (Android 8+)](https://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive)
- [Sharp Documentation](https://sharp.pixelplumbing.com/)

---

## 📝 Notas Importantes

1. **Ícones gerados não são commitados** - Eles estão no `.gitignore` de `app/src/main/res/`
2. **Use a mesma imagem base** - Mantenha `space-cam.png` na raiz para futuras regenerações
3. **Teste em diferentes dispositivos** - Ícones devem ser legíveis em todos os tamanhos
4. **Backup** - Faça backup de `space-cam.png` antes de modificar

---

## 🆘 Precisa de Ajuda?

1. Verifique o [scripts/README.md](./README.md)
2. Veja os logs: `npm run generate:icons 2>&1 | tee icon-generation.log`
3. Abra uma issue no repositório

Boa sorte! 🚀
