param()
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$checksRoot=Join-Path $projectRoot ('android\build\native-input-checks-'+[guid]::NewGuid().ToString('N'))
& node (Join-Path $PSScriptRoot 'Generate-NativeInputChecks.mjs') $checksRoot
if($LASTEXITCODE -ne 0){throw 'Native input fixture generation failed'}
$sources=@(Get-ChildItem -LiteralPath $checksRoot -Recurse -Filter '*.java' -File | ForEach-Object {$_.FullName})
& javac -encoding UTF-8 -d (Join-Path $checksRoot 'classes') @sources
if($LASTEXITCODE -ne 0){throw 'Native input protocol compilation failed'}
& java -cp (Join-Path $checksRoot 'classes') fr.phonemcp.NativeInputProtocolTest
if($LASTEXITCODE -ne 0){throw 'Native input protocol check failed'}