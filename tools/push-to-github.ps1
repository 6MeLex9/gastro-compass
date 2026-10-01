# Публикация проекта ГастроКомпас на GitHub.
#
# Скрипт делает две вещи: настраивает remote origin и отправляет ветку main.
# Токен (если передан) используется только для одной команды push и нигде не сохраняется.
#
# Использование:
#   # 1. Репозиторий уже создан на GitHub, авторизация через Git Credential Manager
#   powershell -ExecutionPolicy Bypass -File tools/push-to-github.ps1 -RepoUrl https://github.com/USER/REPO.git
#
#   # 2. С токеном (fine-grained PAT, права Contents: Read and write)
#   powershell -ExecutionPolicy Bypass -File tools/push-to-github.ps1 `
#       -RepoUrl https://github.com/USER/REPO.git -Token ghp_xxx
#
#   # 3. Только настроить remote, без пуша
#   powershell -ExecutionPolicy Bypass -File tools/push-to-github.ps1 -RepoUrl ... -SetRemoteOnly

param(
    [Parameter(Mandatory = $true)]
    [string]$RepoUrl,

    [string]$Token,

    [string]$Branch = "main",

    [switch]$SetRemoteOnly,

    [switch]$Force
)

$ErrorActionPreference = "Stop"
$env:GIT_PAGER = "cat"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

# --- проверки ---------------------------------------------------------------

if ($RepoUrl -notmatch '^https://github\.com/[\w.-]+/[\w.-]+?(\.git)?$') {
    throw "Ожидается адрес вида https://github.com/ПОЛЬЗОВАТЕЛЬ/РЕПОЗИТОРИЙ.git — получено: $RepoUrl"
}

if (-not (Test-Path (Join-Path $ProjectRoot ".git"))) {
    throw "В проекте нет git-репозитория. Выполните: git init -b main; git add -A; git commit -m 'Initial commit'"
}

$cleanUrl = $RepoUrl -replace '\.git$', ''
$cleanUrlGit = "$cleanUrl.git"

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "git не найден в PATH"
}

# --- remote -----------------------------------------------------------------

$existing = & git remote 2>$null
if ($existing -contains "origin") {
    & git remote set-url origin $cleanUrlGit
} else {
    & git remote add origin $cleanUrlGit
}
Write-Host "origin => $cleanUrlGit"

if ($SetRemoteOnly) {
    Write-Host "Remote настроен. Отправка не выполнялась (-SetRemoteOnly)."
    exit 0
}

# --- проверка доступа без отправки ------------------------------------------

Write-Host "Проверяю доступ к репозиторию..."
if ($Token) {
    $probeUrl = "https://x-access-token:$Token@github.com/" + ($cleanUrl -replace '^https://github\.com/', '') + ".git"
    & git ls-remote --exit-code $probeUrl 2>&1 | Out-Null
} else {
    & git ls-remote --exit-code $cleanUrlGit 2>&1 | Out-Null
}

if ($LASTEXITCODE -ne 0) {
    Write-Warning "Не удалось прочитать репозиторий. Проверьте, что он создан на GitHub и что у вас есть права."
    Write-Warning "Если репозиторий пустой, это нормально — продолжаю отправку."
}

# --- отправка ---------------------------------------------------------------

$pushArgs = @("push")
if ($Force) { $pushArgs += "--force" }
$pushArgs += @("-u")

if ($Token) {
    # Токен подставляется только в одну команду и не попадает в .git/config
    $tokenUrl = "https://x-access-token:$Token@github.com/" + ($cleanUrl -replace '^https://github\.com/', '') + ".git"
    $pushArgs += $tokenUrl
    $pushArgs += $Branch
    $env:GIT_TERMINAL_PROMPT = "0"
    & git @pushArgs
    $code = $LASTEXITCODE
    $env:GIT_TERMINAL_PROMPT = ""
} else {
    $pushArgs += @("origin", $Branch)
    & git @pushArgs
    $code = $LASTEXITCODE
}

if ($code -ne 0) {
    Write-Error "git push завершился с кодом $code"
    exit $code
}

Write-Host ""
Write-Host "Готово: $cleanUrl" -ForegroundColor Green
Write-Host ""
Write-Host "Что дальше:"
Write-Host "  1. Приложить APK к релизу: GitHub → Releases → Draft a new release →"
Write-Host "     tag v1.0.0, заголовок, и вложить файл dist\GastroCompass-debug.apk"
Write-Host "  2. Проверить, что workflow 'Android CI' прошёл зелёным (вкладка Actions)."
Write-Host "  3. При желании добавить описание и темы репозитория: android, gerd, gerd-app,"
Write-Host "     nutrition, kotlin, jetpack-compose, health, offline-first."
