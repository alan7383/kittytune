param([switch]$Stop)
$ErrorActionPreference = 'Stop'
$relayDirectory = $PSScriptRoot
$runtimeDirectory = Join-Path $relayDirectory '.runtime'
$stateFile = Join-Path $runtimeDirectory 'processes.json'

if ($Stop) {
    if (Test-Path -LiteralPath $stateFile) {
        $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
        foreach ($entry in @($state.relay, $state.tunnel)) {
            $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
            if ($process -and $process.Path -eq $entry.path -and $process.StartTime.ToUniversalTime().ToString('o') -eq $entry.started) {
                Stop-Process -Id $process.Id
            }
        }
    }
    Write-Output 'Local relay and tunnel stopped.'
    exit
}

New-Item -ItemType Directory -Force -Path $runtimeDirectory | Out-Null
$nodeExecutable = (Get-Command node -ErrorAction Stop).Source
if (!(Test-Path -LiteralPath (Join-Path $relayDirectory 'node_modules/ws/package.json'))) {
    throw 'First run npm ci in the relay directory, then start this script.'
}
if (Get-NetTCPConnection -State Listen -LocalPort 8787 -ErrorAction SilentlyContinue) {
    throw 'Port 8787 is already in use. Stop the existing relay before starting another.'
}
$tunnelExecutable = Join-Path $runtimeDirectory 'cloudflared.exe'
if (!(Test-Path -LiteralPath $tunnelExecutable)) {
    $release = Invoke-RestMethod 'https://api.github.com/repos/cloudflare/cloudflared/releases/latest'
    $asset = $release.assets | Where-Object name -eq 'cloudflared-windows-amd64.exe' | Select-Object -First 1
    if (!$asset -or !$asset.digest -or !$asset.digest.StartsWith('sha256:')) { throw 'Official download has no SHA256 digest.' }
    Invoke-WebRequest $asset.browser_download_url -OutFile $tunnelExecutable
    $actual = (Get-FileHash -LiteralPath $tunnelExecutable -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $asset.digest.Substring(7)) { throw 'cloudflared checksum mismatch.' }
}
$relay = Start-Process -FilePath $nodeExecutable -ArgumentList @('server.mjs') -WorkingDirectory $relayDirectory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDirectory 'relay.log') -RedirectStandardError (Join-Path $runtimeDirectory 'relay-error.log')
$tunnel = Start-Process -FilePath $tunnelExecutable -ArgumentList @('tunnel','--no-autoupdate','--protocol','http2','--url','http://127.0.0.1:8787') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDirectory 'tunnel.log') -RedirectStandardError (Join-Path $runtimeDirectory 'tunnel-error.log')
@{
    relay = @{ pid = $relay.Id; path = $relay.Path; started = $relay.StartTime.ToUniversalTime().ToString('o') }
    tunnel = @{ pid = $tunnel.Id; path = $tunnel.Path; started = $tunnel.StartTime.ToUniversalTime().ToString('o') }
} | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $stateFile
Write-Output 'Started. Public HTTPS address will appear in .runtime/tunnel-error.log.'
