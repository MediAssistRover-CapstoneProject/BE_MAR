[CmdletBinding()]
param(
    [string]$ProjectDirectory = (Split-Path -Parent $PSScriptRoot),
    [switch]$EnableDispatch
)
$ErrorActionPreference='Stop'
$arguments=@((Join-Path $PSScriptRoot 'cloud_mysql.py'),'--project',$ProjectDirectory,'use')
if ($EnableDispatch) { $arguments+='--enable-dispatch' }
& python @arguments
if ($LASTEXITCODE -ne 0) { throw 'Cloud BE startup stopped. Inspect the error; do not run another backend or start robot tasks.' }
