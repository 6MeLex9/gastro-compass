# Сборка и тестирование GastroCompass.
#
# Зачем нужен этот скрипт:
#   1. JVM-воркер unit-тестов Gradle не умеет загружать классы из пути с кириллицей
#      (например, «Документы»), поэтому тесты запускаются в ASCII-зеркале проекта в TEMP.
#   2. Внешний Kotlin-демон не может писать в %LOCALAPPDATA%\kotlin, поэтому
#      GRADLE_USER_HOME указывается на локальную копию кэша зависимостей.
#   3. AGP хочет создать ~/.android/debug.keystore, поэтому подпись задана
#      собственным keystore внутри проекта.
#
# Использование:
#   pwsh -File tools/build.ps1              # тесты + debug APK
#   pwsh -File tools/build.ps1 -SkipTests   # только APK
#   pwsh -File tools/build.ps1 -Clean       # с очисткой зеркала

param(
    [switch]$SkipTests,
    [switch]$Clean,
    [switch]$Release
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$SdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
           elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME }
           else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$Jbr = "C:\Program Files\Android\Android Studio\jbr"
$GradleHome = Join-Path $env:TEMP "gradle-home"
$BuildRoot = Join-Path $env:TEMP "gc-build\GastroCompass"
$DistDir = Join-Path $ProjectRoot "dist"

Write-Host "Проект:        $ProjectRoot"
Write-Host "Зеркало сборки: $BuildRoot"
Write-Host "Android SDK:   $SdkRoot"
Write-Host "JDK:           $Jbr"

if (-not (Test-Path $Jbr)) { throw "Не найден JDK Android Studio: $Jbr" }
if (-not (Test-Path $SdkRoot)) { throw "Не найден Android SDK: $SdkRoot" }
if (-not (Test-Path $GradleHome)) {
    throw "Не найден локальный Gradle-кэш: $GradleHome. Скопируйте ~/.gradle в эту папку (см. README)."
}

if ($Clean -and (Test-Path $BuildRoot)) {
    Write-Host "Очистка зеркала..."
    Remove-Item -Recurse -Force $BuildRoot
}

New-Item -ItemType Directory -Force -Path $BuildRoot | Out-Null

# --- 1. Зеркалируем исходники (build/ в зеркале сохраняется для инкрементальной сборки)
$paths = @(
    "app\src",
    "app\build.gradle.kts",
    "app\proguard-rules.pro",
    "gradle",
    "gradlew",
    "gradlew.bat",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "local.properties",
    "keystore"
)
foreach ($p in $paths) {
    $source = Join-Path $ProjectRoot $p
    if (-not (Test-Path $source)) { continue }
    if ((Get-Item $source).PSIsContainer) {
        & robocopy $source (Join-Path $BuildRoot $p) /E /NFL /NDL /NJH /NJS /R:1 /W:1 | Out-Null
    } else {
        $target = Join-Path $BuildRoot $p
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
        Copy-Item $source $target -Force
    }
}
Write-Host "Исходники синхронизированы."

# --- 2. Переменные окружения для офлайн-сборки
$env:JAVA_HOME = $Jbr
$env:GRADLE_USER_HOME = $GradleHome
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:ANDROID_HOME = $SdkRoot

Push-Location $BuildRoot
try {
    $tasks = @()
    if (-not $SkipTests) { $tasks += ":app:testDebugUnitTest" }
    if ($Release) { $tasks += ":app:assembleRelease" } else { $tasks += ":app:assembleDebug" }

    Write-Host "Запуск Gradle: $($tasks -join ' ')"
    & .\gradlew.bat @tasks --offline --console=plain
    $exit = $LASTEXITCODE

    # --- 3. Забираем артефакты обратно в проект
    New-Item -ItemType Directory -Force -Path $DistDir | Out-Null
    $apkSources = @(
        "app\build\outputs\apk\debug\app-debug.apk",
        "app\build\outputs\apk\release\app-release-unsigned.apk"
    )
    foreach ($apk in $apkSources) {
        $full = Join-Path $BuildRoot $apk
        if (Test-Path $full) {
            $name = if ($apk -like "*release*") { "GastroCompass-release-unsigned.apk" } else { "GastroCompass-debug.apk" }
            Copy-Item $full (Join-Path $DistDir $name) -Force
            Write-Host "APK: $(Join-Path $DistDir $name)"
        }
    }

    $results = Join-Path $BuildRoot "app\build\test-results\testDebugUnitTest"
    if (Test-Path $results) {
        $target = Join-Path $ProjectRoot "build-reports\test-results"
        New-Item -ItemType Directory -Force -Path $target | Out-Null
        Copy-Item "$results\*.xml" $target -Force
        Write-Host "Отчёты тестов: $target"
    }
    $html = Join-Path $BuildRoot "app\build\reports\tests\testDebugUnitTest"
    if (Test-Path $html) {
        $target = Join-Path $ProjectRoot "build-reports\html"
        if (Test-Path $target) { Remove-Item -Recurse -Force $target }
        New-Item -ItemType Directory -Force -Path $target | Out-Null
        Copy-Item "$html\*" $target -Recurse -Force
        Write-Host "HTML-отчёт: $(Join-Path $target 'index.html')"
    }

    exit $exit
} finally {
    Pop-Location
}
