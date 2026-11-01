[CmdletBinding()]
param(
    [string]$ProjectDirectory = (Split-Path -Parent $PSScriptRoot),
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [switch]$FreezeSource
)
$ErrorActionPreference='Stop'
$arguments=@((Join-Path $PSScriptRoot 'cloud_mysql.py'),'--project',$ProjectDirectory,'export','--output',$OutputDirectory)
if ($FreezeSource) { $arguments+='--freeze-source' }
& python @arguments
if ($LASTEXITCODE -ne 0) { throw 'Export stopped. Read the reason above; no cloud data was changed.' }
