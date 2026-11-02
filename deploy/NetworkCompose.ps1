# Shared by the network bundle. Never drop the cloud override while renewing HTTPS.
function Get-MarNetworkComposeArguments {
    param([Parameter(Mandatory=$true)][string]$BeFolder)
    $base = @('compose', '-p', 'mar-emqx-demo', '--env-file', (Join-Path $BeFolder '.env'))
    $providerFile = Join-Path $BeFolder 'secrets\database-provider.txt'
    $provider = if (Test-Path -LiteralPath $providerFile) { [IO.File]::ReadAllText($providerFile).Trim() } else { 'aiven' }
    if ($provider -notin @('aws','aiven','local')) { throw 'Unknown database provider; refusing DB fallback.' }
    if ($provider -eq 'aws') {
        $awsEnv = Join-Path $BeFolder '.env.aws'
        $override = Join-Path $BeFolder 'deploy\compose.aws.yaml'
        foreach ($required in @($awsEnv,$override,(Join-Path $BeFolder 'secrets\aws\inspection-receipt.json'),
            (Join-Path $BeFolder 'secrets\aws\ec2-key.pem'),(Join-Path $BeFolder 'secrets\aws\known_hosts'),
            (Join-Path $BeFolder 'secrets\aws\truststore.p12'))) {
            if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "AWS database setup incomplete: $required" }
        }
        return $base + @('--env-file',$awsEnv,'-f',(Join-Path $BeFolder 'compose.yaml'),'-f',$override)
    }
    $cloudEnv = Join-Path $BeFolder '.env.aiven'
    if (Test-Path -LiteralPath $cloudEnv -PathType Leaf) {
        $override = Join-Path $BeFolder 'deploy\compose.aiven.yaml'
        $receipt = Join-Path $BeFolder 'secrets\aiven\import-receipt.json'
        foreach ($required in @($override, $receipt)) {
            if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
                throw "Aiven is configured but a required file is missing: $required. No fallback to local DB."
            }
        }
        $base += @('--env-file', $cloudEnv, '-f', (Join-Path $BeFolder 'compose.yaml'), '-f', $override)
    }
    return $base
}
