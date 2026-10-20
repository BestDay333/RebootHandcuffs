#!/usr/bin/env bash
#
# Скрипт сборки RebootHandcuffs (Linux / macOS / Git Bash), Gradle.
#
# Требования: JDK 25+ (Paper 26.1+ требует Java 25), Gradle 8.10+ (или ./gradlew).
#

set -euo pipefail
cd "$(dirname "$0")"

echo "=== Сборка RebootHandcuffs ==="

# --- Ищем JDK 25+ --------------------------------------------------------
java_major() {
    "$1/bin/java" -version 2>&1 | head -n1 | sed -n 's/.*version "\([0-9]*\).*/\1/p'
}

find_java25() {
    local dirs=()
    [[ -n "${JAVA_HOME:-}" ]] && dirs+=("$JAVA_HOME")
    for root in /usr/lib/jvm /Library/Java/JavaVirtualMachines "$HOME/.jdks"; do
        [[ -d "$root" ]] || continue
        while IFS= read -r -d '' d; do dirs+=("$d"); done < <(find "$root" -maxdepth 1 -type d -name '*jdk*2[5-9]*' -print0 2>/dev/null)
    done
    for d in "${dirs[@]:-}"; do
        [[ -x "$d/bin/java" ]] || continue
        local major; major="$(java_major "$d")"
        [[ -n "$major" && "$major" -ge 25 ]] && { echo "$d"; return 0; }
    done
    return 1
}

JAVA_HOME_FOUND="$(find_java25 || true)"
if [[ -z "$JAVA_HOME_FOUND" ]]; then
    echo "✖ Не найден JDK 25+ (Paper 26.1+ требует Java 25)."
    echo "  Скачайте Temurin 25: https://adoptium.net/ и/или задайте JAVA_HOME."
    exit 1
fi
export JAVA_HOME="$JAVA_HOME_FOUND"
echo "JAVA_HOME = $JAVA_HOME"

# --- Ищем Gradle ----------------------------------------------------------
if [[ -x "./gradlew" ]]; then
    GRADLE="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
    GRADLE="gradle"
else
    echo "✖ Не найден Gradle (установите: https://gradle.org/install/)."
    exit 1
fi
echo "Gradle = $GRADLE"

# --- Сборка ---------------------------------------------------------------
"$GRADLE" --console=plain clean build

JAR="$(ls build/libs/RebootHandcuffs-*.jar 2>/dev/null | head -n1)"
if [[ -z "$JAR" || ! -f "$JAR" ]]; then
    echo "✖ Ошибка: JAR файл не найден в build/libs/"
    exit 1
fi

# Кладём свежий jar в корень проекта для удобства.
cp "$JAR" "./$(basename "$JAR")"

echo ""
echo "✓ Сборка успешна: ./$(basename "$JAR") (дубликат в build/libs/)"
echo "  Плагин готов к установке в папку plugins сервера (Paper 26.3, Java 25)."
