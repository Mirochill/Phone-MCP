param(
    [string]$SdkPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    [string]$Destination,
    [switch]$SkipUpdateAssets
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $projectRoot 'android'
$toolsRoot = Join-Path $SdkPath 'build-tools\36.0.0'
foreach ($name in @('aapt.exe','apksigner.bat','zipalign.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $toolsRoot $name))) { throw "Missing Android build tool: $name" }
}
Push-Location $androidRoot
try {
    & '.\gradlew.bat' assembleRelease lintRelease
    if ($LASTEXITCODE -ne 0) { throw 'Android build or lint failed.' }
} finally { Pop-Location }
$apk = Join-Path $androidRoot 'app\build\outputs\apk\release\app-release.apk'
& (Join-Path $toolsRoot 'apksigner.bat') verify $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
& (Join-Path $toolsRoot 'zipalign.exe') -c 4 $apk
if ($LASTEXITCODE -ne 0) { throw 'APK alignment verification failed.' }
$badging = & (Join-Path $toolsRoot 'aapt.exe') dump badging $apk
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the compiled APK.' }
$packageLine = $badging | Where-Object { $_ -like 'package:*' } | Select-Object -First 1
if ($packageLine -notmatch "name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'") { throw 'APK package metadata is missing.' }
$packageName=$Matches[1]; $versionCode=[int]$Matches[2]; $versionName=$Matches[3]
if ($packageName -ne 'fr.phonemcp') { throw 'Unexpected APK package.' }
$manifest = [ordered]@{
    packageName=$packageName
    versionName=$versionName
    versionCode=$versionCode
    sha256=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    sizeBytes=(Get-Item -LiteralPath $apk).Length
}
if (-not $SkipUpdateAssets) {
$assets = Join-Path $projectRoot 'cloudflare\assets'
New-Item -ItemType Directory -Path $assets -Force | Out-Null
Copy-Item -LiteralPath $apk -Destination (Join-Path $assets 'phone-mcp.apk') -Force
[IO.File]::WriteAllText((Join-Path $assets 'release.json'),($manifest | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
}
if ($Destination) {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    Copy-Item -LiteralPath $apk -Destination (Join-Path $Destination "Phone-MCP-$versionName.apk") -Force
}
$manifest | ConvertTo-Json
if ($SkipUpdateAssets) { Write-Host 'Verified local APK is ready. Paired update assets were left untouched.' } else { Write-Host 'Verified APK and update manifest are ready. Deploy the existing Worker to make the update available.' }
