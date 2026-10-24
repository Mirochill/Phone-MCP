param([Parameter(Position=0)][string]$Path,[string]$RequestId)
$ErrorActionPreference='Stop'
if (-not $Path) {
    Add-Type -AssemblyName System.Windows.Forms
    $picker=New-Object System.Windows.Forms.OpenFileDialog
    $picker.Title='Envoyer un fichier avec Phone MCP'
    $picker.Filter='Tous les fichiers (*.*)|*.*'
    if ($picker.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) { return }
    $Path=$picker.FileName
    $picker.Dispose()
}
$sourceFile=Get-Item -LiteralPath $Path
if ($sourceFile.PSIsContainer) { throw 'Selectionnez un fichier, pas un dossier.' }
$senderScript=Join-Path $PSScriptRoot '../server/src/send-file.js'
$senderArguments=@($senderScript,$sourceFile.FullName)
if ($RequestId) { $senderArguments+=@('--request-id',$RequestId) }
& node @senderArguments
if ($LASTEXITCODE -ne 0) { throw "Le transfert n’a pas été confirmé. Consultez le message ci-dessus avant de recommencer." }
