[CmdletBinding()]
param(
    [string]$ProjectDirectory = (Split-Path -Parent $PSScriptRoot),
    [Parameter(Mandatory=$true)][string]$SqlFile,
    [Parameter(Mandatory=$true)][string]$CaCertificate,
    [string]$DbHost='mysql-2c54a41d-fpt-9265.i.aivencloud.com',
    [int]$DbPort=12636,
    [string]$DbUser='avnadmin',
    [switch]$VerifyExisting
)
$ErrorActionPreference='Stop'
$arguments=@((Join-Path $PSScriptRoot 'cloud_mysql.py'),'--project',$ProjectDirectory,'import','--sql',$SqlFile,'--ca',$CaCertificate,'--host',$DbHost,'--port',"$DbPort",'--user',$DbUser)
if ($VerifyExisting) { $arguments+='--verify-existing' }
& python @arguments
if ($LASTEXITCODE -ne 0) { throw 'Import stopped. Do not start BE against the cloud or rerun over existing tables until the reason is resolved.' }
