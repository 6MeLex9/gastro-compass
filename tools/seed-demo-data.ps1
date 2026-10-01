# Заполнение демонстрационных данных в приложении на эмуляторе/устройстве.
#
# Зачем: чтобы проверить и показать экраны «Сегодня», «Дневник», «Подсказки» и «Анализ»
# без ручного прохождения онбординга и ввода продуктов.
#
# Использование:
#   powershell -ExecutionPolicy Bypass -File tools/seed-demo-data.ps1
#
# Требования: устройство подключено по adb, установлен debug-APK
# (run-as работает только с debuggable-сборкой).

$ErrorActionPreference = "Stop"

$Adb = if ($env:ANDROID_SDK_ROOT) { Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe" }
       elseif ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" }
       else { Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe" }

$Package = "com.gastrocare.compass"
$PrefsPath = "/data/data/$Package/shared_prefs/gastro_compass.xml"

if (-not (Test-Path $Adb)) { throw "Не найден adb: $Adb" }

function XmlEscape([string]$value) {
    return $value.Replace("&", "&amp;").Replace("<", "&lt;").Replace(">", "&gt;")
}

# Время берём у самого устройства: часы эмулятора могут отличаться от часов хоста,
# и записи, рассчитанные по хосту, попали бы «не в сегодня» и не отобразились в дневнике.
$DeviceNowMs = [long]((& $Adb shell date +%s) | Select-Object -First 1) * 1000L
Write-Host "Время устройства: $([DateTimeOffset]::FromUnixTimeMilliseconds($DeviceNowMs).ToString('u'))"

function HoursAgo([double]$hours) {
    return [long]($DeviceNowMs - ($hours * 3600000L))
}

function DaysAgo([int]$days, [double]$hours = 0) {
    return [long]($DeviceNowMs - ($days * 86400000L) - ($hours * 3600000L))
}

function New-Entry {
    param(
        [string]$Id, [string]$FoodId, [string]$Name, [double]$Grams,
        [string]$Slot, [long]$Timestamp, [int]$Risk, [string]$Level,
        [double]$Kcal, [double]$Protein, [double]$Fat, [double]$Carbs,
        [string[]]$Factors
    )
    $nutrition = [ordered]@{
        calories = $Kcal; protein = $Protein; fat = $Fat; carbs = $Carbs
        saturatedFat = 0.0; sugar = 0.0; fiber = 0.0; salt = 0.0; caffeineMg = 0.0
    }
    return [ordered]@{
        id = $Id; foodId = $FoodId; foodName = $Name; grams = $Grams; slot = $Slot
        timestamp = $Timestamp; riskScore = $Risk; riskLevel = $Level
        factors = $Factors; nutrition = $nutrition
    }
}

# --- профиль: ГЭРБ + гастрит с повышенной кислотностью, цель — снижение веса
$profile = [ordered]@{
    name = "Анна"
    sex = "FEMALE"
    age = 38
    heightCm = 168
    weightKg = 78.5
    targetWeightKg = 70.0
    activity = "LIGHT"
    goal = "LOSE_WEIGHT"
    diagnoses = @("GERD", "GASTRITIS_HIGH")
    symptoms = @("HEARTBURN", "REGURGITATION", "BLOATING")
    personalTriggers = @("CITRUS", "CAFFEINE")
    lactoseFree = $false
    glutenFree = $false
    lowFodmap = $false
    sleepHour = 23
    mealsPerDay = 5
    calorieAdjustment = 0
    redFlags = @()
    onboarded = $true
}

# --- дневник за сегодня: завтрак, обед и два перекуса с реальными КБЖУ
# Записи размещаются в пределах последних 5 часов, чтобы гарантированно попасть в «сегодня».
$entries = @(
    (New-Entry -Id "e1" -FoodId "oatmeal-milk" -Name "Овсяная каша на молоке 2,5%" -Grams 250 -Slot "BREAKFAST" `
        -Timestamp (HoursAgo 4.2) -Risk 8 -Level "SAFE" -Kcal 255 -Protein 8 -Fat 10.5 -Carbs 36 -Factors @("LACTOSE", "FAT")),
    (New-Entry -Id "e2" -FoodId "banana" -Name "Банан спелый" -Grams 120 -Slot "SNACK" `
        -Timestamp (HoursAgo 3.4) -Risk 4 -Level "SAFE" -Kcal 115 -Protein 1.8 -Fat 0.2 -Carbs 25 -Factors @("FODMAP", "SUGAR")),
    (New-Entry -Id "e3" -FoodId "borscht" -Name "Борщ" -Grams 300 -Slot "LUNCH" `
        -Timestamp (HoursAgo 2.6) -Risk 14 -Level "SAFE" -Kcal 180 -Protein 6 -Fat 7.5 -Carbs 24 -Factors @("ACID", "FAT")),
    (New-Entry -Id "e4" -FoodId "cutlet-fried" -Name "Котлета жареная" -Grams 150 -Slot "LUNCH" `
        -Timestamp (HoursAgo 2.5) -Risk 62 -Level "RISKY" -Kcal 375 -Protein 21 -Fat 27 -Carbs 12 -Factors @("FAT", "FRIED", "GLUTEN")),
    (New-Entry -Id "e5" -FoodId "chocolate-milk" -Name "Шоколад молочный" -Grams 40 -Slot "SNACK" `
        -Timestamp (HoursAgo 1.0) -Risk 58 -Level "RISKY" -Kcal 214 -Protein 3 -Fat 12 -Carbs 23.6 -Factors @("CHOCOLATE", "FAT", "SUGAR", "CAFFEINE"))
)

# --- симптомы: изжога после обеда и вздутие позже
$symptoms = @(
    [ordered]@{ id = "s1"; symptom = "HEARTBURN"; severity = 2; timestamp = (HoursAgo 1.8) },
    [ordered]@{ id = "s2"; symptom = "BLOATING"; severity = 1; timestamp = (HoursAgo 0.6) }
)

# --- взвешивания за три недели: тренд около −0,6 кг в неделю
$weights = @(
    [ordered]@{ id = "w1"; timestamp = (DaysAgo 21); kg = 80.4 },
    [ordered]@{ id = "w2"; timestamp = (DaysAgo 14); kg = 79.8 },
    [ordered]@{ id = "w3"; timestamp = (DaysAgo 7); kg = 79.1 },
    [ordered]@{ id = "w4"; timestamp = (DaysAgo 0); kg = 78.5 }
)

function ToJsonArray($value) {
    $json = $value | ConvertTo-Json -Compress -Depth 8
    if (-not $json.StartsWith("[")) { $json = "[$json]" }
    return $json
}

$profileJson = $profile | ConvertTo-Json -Compress -Depth 6
$diaryJson = ToJsonArray $entries
$symptomsJson = ToJsonArray $symptoms
$weightsJson = ToJsonArray $weights

$xml = @"
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="profile">$(XmlEscape $profileJson)</string>
    <string name="diary">$(XmlEscape $diaryJson)</string>
    <string name="symptoms">$(XmlEscape $symptomsJson)</string>
    <string name="weights">$(XmlEscape $weightsJson)</string>
</map>
"@

$tempFile = Join-Path $env:TEMP "gastro_compass_prefs.xml"
# UTF-8 без BOM: SharedPreferences-парсер Android чувствителен к BOM перед объявлением XML,
# а PowerShell 5.1 в -Encoding UTF8 пишет BOM.
[System.IO.File]::WriteAllText($tempFile, $xml, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Подготовлен файл настроек: $tempFile"

& $Adb shell run-as $Package mkdir -p "/data/data/$Package/shared_prefs" | Out-Null

# На Android 10+ приложение не может прочитать файл из /sdcard (scoped storage).
# /data/local/tmp доступен на чтение и не портит кодировку, в отличие от передачи через stdin,
# где PowerShell 5.1 перекодирует текст в ANSI и заменяет кириллицу на «?».
& $Adb push $tempFile /data/local/tmp/gastro_compass.xml | Out-Null
& $Adb shell "run-as $Package sh -c 'cat /data/local/tmp/gastro_compass.xml > $PrefsPath'"
& $Adb shell rm /data/local/tmp/gastro_compass.xml | Out-Null

$check = & $Adb shell "run-as $Package ls -l $PrefsPath"
Write-Host "Файл настроек на устройстве: $check"
$nameCheck = & $Adb shell "run-as $Package grep -o 'Анна' $PrefsPath"
Write-Host "Проверка кодировки (ожидается «Анна»): $nameCheck"

& $Adb shell am force-stop $Package | Out-Null
& $Adb shell am start -n "$Package/.MainActivity" | Out-Null
Write-Host "Демо-данные загружены, приложение перезапущено."
