# Shared by the network bundle. Never drop the cloud override while renewing HTTPS.
function Get-MarNetworkComposeArguments {
    param([Parameter(Mandatory=$true)][string]$BeFolder)
    $base = @('compose', '-p', 'mar-emqx-demo', '--env-file', (Join-Path $BeFolder '.env'))
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
