#!/bin/bash

# Скрипт сборки плагина RebootHandcuffs
# Собирает проект в .jar и копирует в корневую папку проекта

set -e

echo "=== Сборка RebootHandcuffs ==="

# Получаем имя артефакта и версию из pom.xml
ARTIFACT_ID=$(grep -oP '(?<=<artifactId>)[^<]+' pom.xml | head -1)
VERSION=$(grep -oP '(?<=<version>)[^<]+' pom.xml | head -1)

JAR_NAME="${ARTIFACT_ID}-${VERSION}.jar"
TARGET_DIR="target"
OUTPUT_JAR="${TARGET_DIR}/${JAR_NAME}"

echo "Артефакт: ${ARTIFACT_ID}"
echo "Версия: ${VERSION}"
echo "JAR файл: ${JAR_NAME}"

# Запуск сборки Maven
echo ""
echo "Запуск mvn clean package..."
mvn clean package -q

# Проверка успешности сборки
if [ -f "$OUTPUT_JAR" ]; then
    echo ""
    echo "✓ Сборка успешна!"
    echo "✓ JAR файл создан: ${OUTPUT_JAR}"
    
    # Копирование в корневую папку проекта
    cp "$OUTPUT_JAR" "./${JAR_NAME}"
    echo "✓ Файл скопирован в корневую папку: ./${JAR_NAME}"
    
    echo ""
    echo "=== Готово! ==="
    echo "Плагин готов к установке: ${JAR_NAME}"
else
    echo ""
    echo "✖ Ошибка: JAR файл не найден после сборки"
    exit 1
fi
