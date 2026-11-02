[CmdletBinding()]
param(
    [string]$ProjectDirectory = (Split-Path -Parent $PSScriptRoot),
    [switch]$EnableDispatch
)
$ErrorActionPreference='Stop'
$arguments=@((Join-Path $PSScriptRoot 'aws_mysql.py'),'--project',$ProjectDirectory)
if ($EnableDispatch) { $arguments+='--enable-dispatch' }
& python @arguments
if ($LASTEXITCODE -ne 0) { throw 'AWS startup stopped. Inspect the error before starting Pi; no DB was deleted.' }
