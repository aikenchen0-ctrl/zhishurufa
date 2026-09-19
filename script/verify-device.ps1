param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [ValidateSet('debug', 'release')][string]$Variant = 'debug',
    [ValidateSet('', 'arm64-v8a', 'x86_64')][string]$InstallAbi = '',
    [string]$Adb = 'adb',
    [ValidateRange(1, 65535)][int]$AdbPort = 5037,
    [switch]$ConfigureIme
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$app = Join-Path $root "sample-host/build/outputs/apk/$Variant/sample-host-$Variant.apk"
$test = Join-Path $root "sample-host/build/outputs/apk/androidTest/$Variant/sample-host-$Variant-androidTest.apk"
foreach ($file in @($app, $test)) {
    if (!(Test-Path -LiteralPath $file)) { throw "请先构建测试产物：$file" }
}
$abiName = if ($InstallAbi) { $InstallAbi } else { 'native' }
$log = Join-Path $root "build/device-$Variant-$abiName.log"
$environmentLog = Join-Path $root "build/device-$Variant-$abiName-environment.log"

$install = @('-P', "$AdbPort", '-s', $Serial, 'install', '-r')
if ($InstallAbi) { $install += @('--abi', $InstallAbi) }
& $Adb @install $app
if ($LASTEXITCODE -ne 0) { throw '宿主安装失败' }
& $Adb @install $test
if ($LASTEXITCODE -ne 0) { throw '测试 APK 安装失败' }
& $Adb -P $AdbPort -s $Serial shell getprop ro.build.fingerprint | Out-File $environmentLog
& $Adb -P $AdbPort -s $Serial shell getprop ro.product.cpu.abilist | Out-File $environmentLog -Append
& $Adb -P $AdbPort -s $Serial shell dumpsys package com.zhishurufa.sample |
    Select-String 'primaryCpuAbi|secondaryCpuAbi' | Out-File $environmentLog -Append

$command = @('-P', "$AdbPort", '-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r')
if ($ConfigureIme) { $command += @('-e', 'configureIme', 'true') }
$command += @('-e', 'class', 'com.zhishurufa.sample.ChineseInputInstrumentedTest',
    'com.zhishurufa.sample.test/androidx.test.runner.AndroidJUnitRunner')
& $Adb @command *> $log
$exitCode = $LASTEXITCODE
$output = Get-Content -Raw -LiteralPath $log
if ($exitCode -ne 0 -or $output -notmatch 'OK \(18 tests\)' -or $output -match 'Process crashed|FAILURES!!!|INSTRUMENTATION_FAILED') {
    Get-Content -LiteralPath $log -Tail 50
    & $Adb -P $AdbPort -s $Serial logcat -d -b crash | Out-File "$log.crash.txt"
    throw "设备验收未通过，日志：$log"
}
Write-Output "通过：18 组设备测试（$Variant，$abiName）。日志：$log"
