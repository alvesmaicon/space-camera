.PHONY: help install build generate-icons clean

help:
	@echo ""
	@echo "╔════════════════════════════════════════════════════════════════╗"
	@echo "║              Space Camera - Comandos Disponíveis               ║"
	@echo "╚════════════════════════════════════════════════════════════════╝"
	@echo ""
	@echo "📦 SETUP:"
	@echo "  make install              - Instalar dependências Node.js"
	@echo "  make setup                - Executar setup.sh"
	@echo ""
	@echo "🎨 ÍCONES:"
	@echo "  make generate-icons       - Gerar launcher icons"
	@echo "  make generate-all-icons   - Gerar todos os ícones"
	@echo ""
	@echo "🏗️  BUILD:"
	@echo "  make build                - Build debug APK"
	@echo "  make build-release        - Build release APK"
	@echo "  make install              - Instalar APK em dispositivo"
	@echo "  make run                  - Compilar, instalar e executar"
	@echo ""
	@echo "🧹 LIMPEZA:"
	@echo "  make clean                - Limpar builds"
	@echo "  make clean-all            - Limpeza total (build + gradle)"
	@echo ""
	@echo "🧪 TESTES:"
	@echo "  make test                 - Executar testes"
	@echo "  make lint                 - Rodar lint"
	@echo ""

install:
	npm install

setup:
	chmod +x setup.sh
	./setup.sh

generate-icons:
	npm run generate:icons

generate-all-icons:
	npm run generate:all-icons

build:
	npm run build:debug

build-release:
	npm run build:release

run-install:
	npm run install:debug

run: build run-install
	adb shell am start -n com.spacecamera/.MainActivity

clean:
	npm run clean

clean-all: clean
	rm -rf .gradle
	rm -rf build
	rm -rf app/build

test:
	npm run test

lint:
	npm run lint

.DEFAULT_GOAL := help
