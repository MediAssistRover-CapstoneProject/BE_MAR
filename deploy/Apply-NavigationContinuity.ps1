[CmdletBinding()]
param(
    [string]$StageDirectory = (Join-Path $env:USERPROFILE 'Downloads\MAR_EMQX_Demo\BE_MAR\output\pi-navigation-continuity'),
    [string]$ProjectDirectory = (Join-Path $env:USERPROFILE 'Downloads\MAR_EMQX_Demo\BE_MAR'),
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
$stage = (Resolve-Path -LiteralPath $StageDirectory).Path
$projectPath = (Resolve-Path -LiteralPath $ProjectDirectory).Path
function Get-SourceDigest([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    $data = [Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes($Path)).Replace("`r`n", "`n")
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return -join ($algorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes($data)) | ForEach-Object { $_.ToString('x2') }) }
    finally { $algorithm.Dispose() }
}
$pending = @()
foreach ($entry in (Get-Content -LiteralPath (Join-Path $stage 'be-manifest.json') -Raw | ConvertFrom-Json)) {
    if ($entry.path -notmatch '^src/(main/(java|resources)|test/java)/' -or $entry.path -match '(^|/)\.\.(/|$)') { throw 'INVALID_SOURCE_PATH' }
    $source = [IO.Path]::GetFullPath((Join-Path (Join-Path $stage 'be-files') $entry.path))
    $destination = [IO.Path]::GetFullPath((Join-Path $projectPath $entry.path))
    if (-not $destination.StartsWith($projectPath + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'PATH_OUTSIDE_PROJECT' }
    if ((Get-SourceDigest $source) -ne $entry.after) { throw ('PATCH_DIGEST_MISMATCH: ' + $entry.path) }
    $before = Get-SourceDigest $destination
    if ($before -eq $entry.after) { continue }
    if ($entry.accepted -notcontains $before) { throw ('SOURCE_CHANGED: ' + $entry.path + '; no files replaced') }
    $pending += [pscustomobject]@{ Name=$entry.path; Source=$source; Destination=$destination; Before=$before }
}
Write-Output ('BE_FILES_TO_UPDATE ' + $pending.Count)
if (-not $Apply -or $pending.Count -eq 0) { return }
$backup = Join-Path $projectPath ('output\source-backups\navigation-continuity-' + (Get-Date -Format 'yyyyMMddTHHmmssfff'))
$null = New-Item -ItemType Directory -Path $backup -Force
$replaced = @()
try {
    foreach ($item in $pending) {
        if ((Get-SourceDigest $item.Destination) -ne $item.Before) { throw ('SOURCE_CHANGED_DURING_CHECK: ' + $item.Name) }
        if ($null -ne $item.Before) {
            $saved = Join-Path $backup $item.Name
            $null = New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force
            Copy-Item -LiteralPath $item.Destination -Destination $saved
        }
    }
    foreach ($item in $pending) {
        $null = New-Item -ItemType Directory -Path (Split-Path -Parent $item.Destination) -Force
        Copy-Item -LiteralPath $item.Source -Destination $item.Destination -Force
        $replaced += $item
    }
} catch {
    foreach ($item in $replaced) {
        if ($null -eq $item.Before) { Remove-Item -LiteralPath $item.Destination -Force }
        else { Copy-Item -LiteralPath (Join-Path $backup $item.Name) -Destination $item.Destination -Force }
    }
    throw
}
Write-Output ('BE_SOURCE_INSTALLED BACKUP=' + $backup)
Write-Output 'BE was not restarted. Database, .env, MQTT credentials, HTTPS and CA are unchanged.'
