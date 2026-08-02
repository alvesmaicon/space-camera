#!/bin/bash

# Setup script para Space Camera project

set -e

echo "🚀 Iniciando setup do Space Camera..."

# Cores para output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Verificar se Android SDK está instalado
if [ -z "$ANDROID_HOME" ]; then
    echo -e "${YELLOW}⚠️  ANDROID_HOME não configurado${NC}"
    echo "Configure com: export ANDROID_HOME=/path/to/android-sdk"
    exit 1
fi

echo -e "${BLUE}✓ ANDROID_HOME: $ANDROID_HOME${NC}"

# Criar local.properties se não existir
if [ ! -f "local.properties" ]; then
    echo -e "${BLUE}📝 Criando local.properties...${NC}"
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    echo -e "${GREEN}✓ local.properties criado${NC}"
else
    echo -e "${GREEN}✓ local.properties já existe${NC}"
fi

# Verificar se Node.js está instalado
if command -v node &> /dev/null; then
    NODE_VERSION=$(node -v)
    echo -e "${GREEN}✓ Node.js $NODE_VERSION encontrado${NC}"
else
    echo -e "${YELLOW}⚠️  Node.js não encontrado. Yarn scripts não funcionarão.${NC}"
fi

# Fazer chmod no gradlew
if [ -f "gradlew" ]; then
    chmod +x gradlew
    echo -e "${GREEN}✓ gradlew permissões atualizadas${NC}"
fi

echo -e "${GREEN}✅ Setup concluído!${NC}"
echo ""
echo -e "${BLUE}Próximos passos:${NC}"
echo "1. Abra o projeto no Android Studio: ."
echo "2. Ou compile via terminal:"
echo "   ./gradlew assembleDebug"
echo ""
echo -e "${BLUE}Comandos disponíveis:${NC}"
echo "  npm run build:debug       - Build APK debug"
echo "  npm run build:release     - Build APK release"
echo "  npm run install:debug     - Instalar em dispositivo"
echo "  npm run run:debug         - Compilar e executar"
echo "  npm run clean             - Limpar build"
echo ""
