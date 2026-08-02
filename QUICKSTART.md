# Quick Start - Space Camera

Guia rápido para começar a usar o Space Camera.

## ⚡ Setup em 3 Passos

### 1. Instalar Dependências
```bash
npm install
```

### 2. Gerar Ícones
```bash
npm run generate:all-icons
```

### 3. Build e Execução
```bash
npm run build:debug
npm run run:debug
```

---

## 📋 Checklist Inicial

- [ ] Node.js 16+ instalado
- [ ] Android SDK 34 configurado
- [ ] Dispositivo Android conectado ou emulador rodando
- [ ] `npm install` executado
- [ ] Ícones gerados com `npm run generate:all-icons`

---

## 🚀 Comandos Essenciais

```bash
# Gerar ícones (IMPORTANTE - fazer uma vez)
npm run generate:all-icons

# Build
npm run build:debug        # Build debug
npm run build:release      # Build release

# Instalar e rodar
npm run install:debug      # Instalar em dispositivo
npm run run:debug          # Build + Install + Run

# Limpeza
npm run clean              # Limpar build
```

---

## 🛠️ Alternativa: Usando Makefile

Se preferir:

```bash
make help                 # Ver todos os comandos
make install              # npm install
make generate-icons       # Gerar ícones
make run                  # Build + Install + Run
make clean                # Limpar
```

---

## 📱 Após o Build

O app estará disponível em:
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Instalado em**: Dispositivo conectado
- **Ícone**: Visível na tela inicial

---

## 🐛 Problemas Comuns

### "sharp not found"
```bash
npm install sharp --save-dev
```

### "ANDROID_HOME not found"
```bash
export ANDROID_HOME=/Users/SEU_USUARIO/Library/Android/sdk
```

### Ícones não aparecem
```bash
npm run clean
npm run generate:all-icons
npm run build:debug
```

---

## 📖 Documentação Completa

- **README.md** - Documentação principal
- **BUILD.md** - Instruções de build
- **ICON_GENERATION.md** - Geração de ícones
- **ARCHITECTURE.md** - Arquitetura do projeto
- **scripts/README.md** - Scripts de geração

---

## 🎯 Próximos Passos

1. ✅ Setup inicial
2. ✅ Gerar ícones
3. ✅ Build primeira versão
4. 📝 Customizar ícone (editar `space-cam.png`)
5. 🎨 Implementar features
6. 🧪 Adicionar testes
7. 📱 Publicar na Play Store

---

## 💬 Precisa de Ajuda?

```bash
# Ver informações sobre geração de ícones
node scripts/iconHelp.js

# Ver todos os comandos make
make help
```

---

**Boa sorte! 🚀**
