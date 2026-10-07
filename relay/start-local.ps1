param([switch]$Stop, [ValidateSet('auto','http2','quic')][string]$Protocol = 'http2',
    [ValidateSet('cloudflare','localhost')][string]$Provider = 'cloudflare')
$ErrorActionPreference = 'Stop'
$relayDirectory = $PSScriptRoot
$runtimeDirectory = Join-Path $relayDirectory '.runtime'
$stateFile = Join-Path $runtimeDirectory 'processes.json'

if ($Stop) {
    if (Test-Path -LiteralPath $stateFile) {
        $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
        foreach ($entry in @($state.relay, $state.tunnel)) {
            $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
            if ($process -and $process.Path -eq $entry.path -and $process.StartTime.ToUniversalTime().Ticks -eq ([DateTimeOffset]$entry.started).UtcDateTime.Ticks) {
                Stop-Process -Id $process.Id
                $process.WaitForExit(5000) | Out-Null
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
$tunnelExecutable = if ($Provider -eq 'localhost') { (Get-Command ssh -ErrorAction Stop).Source } else { Join-Path $runtimeDirectory 'cloudflared.exe' }
if ($Provider -eq 'cloudflare' -and !(Test-Path -LiteralPath $tunnelExecutable)) {
    $release = Invoke-RestMethod 'https://api.github.com/repos/cloudflare/cloudflared/releases/latest'
    $asset = $release.assets | Where-Object name -eq 'cloudflared-windows-amd64.exe' | Select-Object -First 1
    if (!$asset -or !$asset.digest -or !$asset.digest.StartsWith('sha256:')) { throw 'Official download has no SHA256 digest.' }
    Invoke-WebRequest $asset.browser_download_url -OutFile $tunnelExecutable
    $actual = (Get-FileHash -LiteralPath $tunnelExecutable -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $asset.digest.Substring(7)) { throw 'cloudflared checksum mismatch.' }
}
$relay = Start-Process -FilePath $nodeExecutable -ArgumentList @('server.mjs') -WorkingDirectory $relayDirectory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDirectory 'relay.log') -RedirectStandardError (Join-Path $runtimeDirectory 'relay-error.log')
$tunnelArguments = if ($Provider -eq 'localhost') {
    @('-T','-o','BatchMode=yes','-o','IdentitiesOnly=yes','-o','IdentityFile=none',
        '-o','IdentityAgent=none','-o','StrictHostKeyChecking=accept-new',
        '-o', ('UserKnownHostsFile="' + (Join-Path $runtimeDirectory 'known_hosts') + '"'),
        '-o','ServerAliveInterval=60','-o','ServerAliveCountMax=3','-o','ExitOnForwardFailure=yes',
        '-o','ConnectTimeout=15','-R','80:127.0.0.1:8787','nokey@localhost.run','--','--output','json')
} else {
    @('tunnel','--no-autoupdate','--protocol',$Protocol,'--url','http://127.0.0.1:8787')
}
$tunnel = Start-Process -FilePath $tunnelExecutable -ArgumentList $tunnelArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDirectory 'tunnel.log') -RedirectStandardError (Join-Path $runtimeDirectory 'tunnel-error.log')
@{
    relay = @{ pid = $relay.Id; path = $relay.Path; started = $relay.StartTime.ToUniversalTime().ToString('o') }
    tunnel = @{ pid = $tunnel.Id; path = $tunnel.Path; started = $tunnel.StartTime.ToUniversalTime().ToString('o') }
} | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $stateFile
Write-Output 'Started. Public HTTPS address will appear in .runtime/tunnel.log or tunnel-error.log.'
