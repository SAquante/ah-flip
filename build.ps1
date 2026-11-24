# Builds the release APK reliably.
# The Gradle daemon on this machine keeps a lock on classes.dex, so mergeDexRelease fails
# silently and the previously built APK gets installed instead. Kill the daemons first and
# fail loudly if the APK timestamp did not change.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$env:JAVA_HOME = "C:\tools\jdk-21.0.12.1+1"
$env:ANDROID_HOME = "C:\Users\Poluektovi\Documents\deepseek-harness\default-workspace\iptv-tv\_tools\android-sdk"
$gradle = "C:\Users\Poluektovi\Documents\deepseek-harness\default-workspace\iptv-tv\_tools\gradle-8.11.1\bin\gradle.bat"
$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\release\app-release.apk"

Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

$before = if (Test-Path $apk) { (Get-Item $apk).LastWriteTime } else { [datetime]::MinValue }

& $gradle :app:assembleRelease --console=plain 2>&1 | Tee-Object -Variable out | Out-Null
$out | Select-String -Pattern "^e:|FAILED|BUILD SUCCESSFUL" | ForEach-Object { $_.Line }

if (-not (Test-Path $apk)) { throw "APK not built" }
$after = (Get-Item $apk).LastWriteTime
if ($after -le $before) { throw "APK was not rebuilt (still $after) - check the build log" }
Write-Output "APK rebuilt: $after  $([math]::Round((Get-Item $apk).Length / 1MB, 2)) MB"
