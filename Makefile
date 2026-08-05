.PHONY: help install build generate-icons clean test lint detekt check smoke logcat logcat-eventos backup-apk backup-apk-install backups

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
	@echo "  make build-release        - Build release APK (SEM assinatura: não instala)"
	@echo "  make install              - Instalar APK em dispositivo"
	@echo "  make run                  - Compilar, instalar e executar"
	@echo ""
	@echo "💾 BACKUP DE APK:"
	@echo "  make backup-apk           - Arquiva APK nomeado por versão + commit"
	@echo "  make backup-apk-install   - Arquiva e instala no aparelho conectado"
	@echo "  make backups              - Lista os backups já gerados"
	@echo ""
	@echo "🧹 LIMPEZA:"
	@echo "  make clean                - Limpar builds"
	@echo "  make clean-all            - Limpeza total (build + gradle)"
	@echo ""
	@echo "🧪 QUALIDADE:"
	@echo "  make test                 - Testes unitários (JVM, sem device)"
	@echo "  make lint                 - Android Lint"
	@echo "  make detekt               - Análise estática Kotlin"
	@echo "  make check                - test + lint + detekt"
	@echo ""
	@echo "🔍 DIAGNÓSTICO:"
	@echo "  make smoke                - Build, instala e confere que a câmera ligou"
	@echo "  make logcat               - Logcat filtrado só no app"
	@echo "  make logcat-eventos       - Só os eventos de telemetria"
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
	./gradlew testDebugUnitTest

lint:
	./gradlew lint

detekt:
	./gradlew detekt

check:
	./gradlew testDebugUnitTest lint detekt

# Invocado via `bash` e não `./`: o arquivo é novo e pode chegar sem bit de
# execução dependendo de como veio para a máquina.
backup-apk:
	bash scripts/backup-apk.sh

backup-apk-install:
	bash scripts/backup-apk.sh --instalar

backups:
	bash scripts/backup-apk.sh --listar

smoke:
	./scripts/smoke.sh

logcat:
	./scripts/logcat.sh

logcat-eventos:
	./scripts/logcat.sh 'evt='

.DEFAULT_GOAL := help
