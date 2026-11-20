param(
    [switch]$IncludeLocaleCheck = $true
)

$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

$sw = [System.Diagnostics.Stopwatch]::StartNew()

Write-Host "=== [1/3] Suite aislada en C (native-lib / finanzas.c) ===" -ForegroundColor Cyan
$gccCmd = Get-Command gcc -ErrorAction SilentlyContinue
if ($null -ne $gccCmd) {
    $tempExe = Join-Path $env:TEMP "uceva_test_finanzas.exe"
    & gcc -std=c11 -O2 -Wall -Wextra -Werror "native-lib/finanzas.c" "native-lib/test_finanzas.c" -lm -o $tempExe
    if ($LASTEXITCODE -ne 0) { throw "Error compilando test_finanzas en C" }
    & $tempExe
    if ($LASTEXITCODE -ne 0) { throw "Fallo la suite de pruebas en C" }
    Remove-Item $tempExe -ErrorAction SilentlyContinue
} else {
    docker build --target native-builder -f services/calculo-service/Dockerfile .
    if ($LASTEXITCODE -ne 0) { throw "Fallo la suite en C dentro de Docker" }
}

Write-Host "`n=== [2/3] Suites aisladas Java en paralelo (calculo [es_CO], insumo, presupuesto) ===" -ForegroundColor Cyan
$jobs = @()
$jobs += Start-Job -Name "calculo-service (es_CO)" -ScriptBlock {
    param($dir)
    Set-Location $dir
    $out = & mvn -f "services/calculo-service/pom.xml" test "-DargLine=-Duser.language=es -Duser.country=CO" -B 2>&1
    [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = ($out -join "`n") }
} -ArgumentList $RootDir

$jobs += Start-Job -Name "insumo-service" -ScriptBlock {
    param($dir)
    Set-Location $dir
    $out = & mvn -f "services/insumo-service/pom.xml" test -B 2>&1
    [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = ($out -join "`n") }
} -ArgumentList $RootDir

$jobs += Start-Job -Name "presupuesto-service" -ScriptBlock {
    param($dir)
    Set-Location $dir
    $out = & mvn -f "services/presupuesto-service/pom.xml" test -B 2>&1
    [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = ($out -join "`n") }
} -ArgumentList $RootDir

Wait-Job -Job $jobs | Out-Null

$failed = $false
foreach ($j in $jobs) {
    $res = Receive-Job -Job $j
    $lines = $res.Output -split "`n" | Where-Object { $_ -match "Tests run:|BUILD SUCCESS|BUILD FAILURE|\[PASS\]|\[FAIL\]|ERROR" }
    Write-Host "`n--- $($j.Name) ---" -ForegroundColor Yellow
    $lines | ForEach-Object { Write-Host $_ }
    if ($res.ExitCode -ne 0) {
        $failed = $true
        Write-Host "[FAIL] $($j.Name)" -ForegroundColor Red
        Write-Host $res.Output
    } else {
        Write-Host "[PASS] $($j.Name)" -ForegroundColor Green
    }
    Remove-Job -Job $j
}

if ($failed) {
    throw "Una o mas suites Java fallaron."
}

if ($IncludeLocaleCheck) {
    Write-Host "`n=== [3/3] Verificacion de invariancia en Locale en_US (calculo-service) ===" -ForegroundColor Cyan
    $prevPref = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $outUs = & mvn -f "services/calculo-service/pom.xml" surefire:test "-DargLine=-Duser.language=en -Duser.country=US" -B 2>&1
    $exitUs = $LASTEXITCODE
    $ErrorActionPreference = $prevPref
    ($outUs | ForEach-Object { $_.ToString() } | Where-Object { $_ -match "Tests run:|BUILD SUCCESS|BUILD FAILURE|\[PASS\]|\[FAIL\]|ERROR" }) | ForEach-Object { Write-Host $_ }
    if ($exitUs -ne 0) {
        Write-Host (($outUs | ForEach-Object { $_.ToString() }) -join "`n")
        throw "Fallo calculo-service en Locale en_US"
    }
    Write-Host "[PASS] calculo-service (en_US)" -ForegroundColor Green
}

$sw.Stop()
$secs = [math]::Round($sw.Elapsed.TotalSeconds, 2)
Write-Host "`n==============================================================" -ForegroundColor Green
Write-Host "SUITE AISLADA COMPLETA EXITOSA en $secs s (Meta RQ-03 < 60s)" -ForegroundColor Green
Write-Host "==============================================================" -ForegroundColor Green
