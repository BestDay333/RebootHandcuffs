#!/usr/bin/env pwsh
<#
  Скрипт сборки RebootHandcuffs для Windows (Gradle).

  Требования:
    - JDK 25 или новее (Paper 26.1+ работает только на Java 25)
    - Gradle 8.10+ (или wrapper: .\gradlew.bat)

  Использование:
    .\build.ps1
#>

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

function Test-Java25 {
    param([string]$Dir)
    $java = Join-Path $Dir "bin\java.exe"
    if (-not (Test-Path $java)) { return $false }
    # java -version пишет в stderr — объединяем потоки через cmd /c,
    # чтобы PowerShell не превращал вывод в ошибку при EAP=Stop.
    $ver = (cmd /c "`"$java`" -version 2>&1") -join "`n"
    return ($ver -match 'version "(\d+)' -and [int]$Matches[1] -ge 25)
}

function Find-Java25 {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }

    $roots = @(
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Java",
        "C:\Program Files\Microsoft",
        "C:\Program Files\Zulu",
        "C:\Program Files\Amazon Corretto",
        "$env:USERPROFILE\.jdks"
    )
    foreach ($root in $roots) {
        if (Test-Path $root) {
            $candidates += Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match "jdk-?2[5-9]" } |
                ForEach-Object { $_.FullName }
        }
    }

    foreach ($dir in $candidates) {
        if (Test-Java25 $dir) { return $dir }
    }
    return $null
}

function Find-Gradle {
    if (Test-Path (Join-Path $PSScriptRoot "gradlew.bat")) { return "gradlew.bat" }
    $cmd = Get-Command gradle -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($path in @("C:\gradle\bin\gradle.bat")) {
        if (Test-Path $path) { return $path }
    }
    return $null
}

Write-Host "=== Сборка RebootHandcuffs ===" -ForegroundColor Cyan

$javaHome = Find-Java25
if (-not $javaHome) {
    Write-Host "✖ Не найден JDK 25+ (Paper 26.1+ требует Java 25)." -ForegroundColor Red
    Write-Host "  Скачайте Temurin 25: https://adoptium.net/ и/или задайте JAVA_HOME."
    exit 1
}
$env:JAVA_HOME = $javaHome
Write-Host "JAVA_HOME = $javaHome"

$gradle = Find-Gradle
if (-not $gradle) {
    Write-Host "✖ Не найден Gradle (установите: https://gradle.org/install/)." -ForegroundColor Red
    exit 1
}
Write-Host "Gradle = $gradle"

# Запускаем через cmd /c с объединением потоков: так вывод Gradle (в т.ч. ошибки
# в stderr) гарантированно виден, а код возврата корректно пробрасывается.
& cmd /c "`"$gradle`" --console=plain clean build 2>&1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "✖ Ошибка сборки (код $LASTEXITCODE)" -ForegroundColor Red
    exit $LASTEXITCODE
}

$builtJar = Get-ChildItem (Join-Path $PSScriptRoot "build\libs") -Filter "RebootHandcuffs-*.jar" -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $builtJar) {
    Write-Host "✖ JAR не найден в build\libs\" -ForegroundColor Red
    exit 1
}

# Кладём свежий jar в корень проекта для удобства.
Copy-Item $builtJar.FullName -Destination (Join-Path $PSScriptRoot $builtJar.Name) -Force

Write-Host ""
Write-Host ("✓ Сборка успешна: .\" + $builtJar.Name + " (дубликат в build\libs\)") -ForegroundColor Green
Write-Host "  Плагин готов к установке в папку plugins сервера (Paper 26.3, Java 25)."
