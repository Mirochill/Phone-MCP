param()
$ErrorActionPreference = 'Stop'
$policyProject = Split-Path -Parent $PSScriptRoot
$mainSource = Join-Path $policyProject 'android\app\src\main\java'
$policyTests = Join-Path $policyProject 'android\policy-test'
$runFolder = Join-Path $policyProject ('android\build\policy-checks-' + [guid]::NewGuid().ToString('N'))
$classesFolder = Join-Path $runFolder 'classes'
New-Item -ItemType Directory -Path $classesFolder -Force | Out-Null
$caseFiles = @(Get-ChildItem -LiteralPath $policyTests -Filter '*Test.java' -File | Sort-Object Name)
$testSources = @($caseFiles | ForEach-Object { $_.FullName })
& javac -encoding UTF-8 -d $classesFolder -sourcepath ($mainSource + ';' + $policyTests) @testSources
if ($LASTEXITCODE -ne 0) { throw 'Pure JVM policy compilation failed.' }
foreach ($caseFile in $caseFiles) {
    $testClass = 'fr.phonemcp.' + $caseFile.BaseName
    if ($caseFile.BaseName -eq 'UploadReceiverTest') {
        & java -cp $classesFolder $testClass (Join-Path $runFolder 'receiver-case')
    } else {
        & java -cp $classesFolder $testClass
    }
    if ($LASTEXITCODE -ne 0) { throw ('Policy regression failed: ' + $testClass) }
}
Write-Output ('All ' + $caseFiles.Count + ' policy families passed.')