param([switch]$Stop, [string]$Domain, [string]$NodeExecutable,
    [int]$SocksProxyPort = 0, [string]$NgrokExecutable)
$ErrorActionPreference = 'Stop'
$runtime = Join-Path $PSScriptRoot '.runtime'
$statePath = Join-Path $runtime 'processes.json'
$settingsPath = Join-Path $runtime 'ngrok-settings.json'
$state = if (Test-Path -LiteralPath $statePath) { Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json -AsHashtable } else { @{} }
function Stop-Owned($entry) {
    if (!$entry) { return }
    $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
    if ($process -and $process.Path -eq $entry.path -and $process.StartTime.ToUniversalTime().Ticks -eq ([DateTimeOffset]$entry.started).UtcDateTime.Ticks) {
        Stop-Process -Id $process.Id
        $process.WaitForExit(5000) | Out-Null
    }
}
function Save-State { $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath }
if ($Stop) {
    if ($state.tunnel.path -like '*ngrok.exe') { Stop-Owned $state.tunnel; $state.Remove('tunnel'); Save-State }
    Write-Output 'ngrok tunnel stopped; local relay preserved.'
    exit
}
if (!$NgrokExecutable) { $NgrokExecutable = Join-Path $runtime 'ngrok-bin/ngrok.exe' }
$NgrokExecutable = (Get-Command $NgrokExecutable -ErrorAction Stop).Source
$settings = if (Test-Path -LiteralPath $settingsPath) { Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json } else { $null }
if (!$Domain) { $Domain = $settings.domain }
if (!$PSBoundParameters.ContainsKey('SocksProxyPort') -and $settings.socksProxyPort) { $SocksProxyPort = [int]$settings.socksProxyPort }
if ($Domain) {
    $Domain = $Domain.Trim().TrimEnd('/').Replace('wss://','https://')
    $uri = [uri]$Domain
    if ($uri.Scheme -ne 'https' -or $uri.AbsolutePath -ne '/' -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) { throw 'Use an HTTPS domain without a path.' }
}
if ($SocksProxyPort -lt 0 -or $SocksProxyPort -gt 65535) { throw 'Invalid SOCKS port.' }
$tokenFile = Join-Path $runtime 'ngrok-token.dpapi'
if (!(Test-Path -LiteralPath $tokenFile)) { throw 'Run configure-ngrok.ps1 and save Your Authtoken locally first.' }
$credential = [PSCredential]::new('ngrok', (ConvertTo-SecureString (Get-Content -LiteralPath $tokenFile -Raw).Trim()))
if (!(Get-NetTCPConnection -State Listen -LocalPort 8787 -ErrorAction SilentlyContinue)) {
    if (!$NodeExecutable) { $NodeExecutable = (Get-Command node -ErrorAction Stop).Source }
    $relay = Start-Process -FilePath $NodeExecutable -ArgumentList @('server.mjs') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'relay.log') -RedirectStandardError (Join-Path $runtime 'relay-error.log')
    $state.relay = @{pid=$relay.Id;path=$NodeExecutable;started=$relay.StartTime.ToUniversalTime().ToString('o')}
    Save-State
} elseif (!$state.relay -or !(Get-Process -Id $state.relay.pid -ErrorAction SilentlyContinue)) { throw 'Port 8787 belongs to an unmanaged process.' }
# This file has no authentication token. Only the child process receives the decrypted token.
$configPath = Join-Path $runtime 'ngrok-agent.yml'
$lines = @('version: 3','agent:','  console_ui: false','  inspect_db_size: -1','  remote_management: false','  web_addr: 127.0.0.1:4040')
if ($SocksProxyPort) { $lines += "  proxy_url: socks5://127.0.0.1:$SocksProxyPort" }
$lines | Set-Content -LiteralPath $configPath -Encoding utf8
Stop-Owned $state.tunnel
$previousToken = $env:NGROK_AUTHTOKEN
try {
    $env:NGROK_AUTHTOKEN = $credential.GetNetworkCredential().Password
    $args = @('http','http://127.0.0.1:8787','--config',('"'+$configPath+'"'),'--log=stdout','--log-format=json','--inspect=false')
    if ($Domain) { $args += ('--url='+$Domain) }
    $tunnel = Start-Process -FilePath $NgrokExecutable -ArgumentList $args -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'ngrok.log') -RedirectStandardError (Join-Path $runtime 'ngrok-error.log')
    $state.tunnel = @{pid=$tunnel.Id;path=$NgrokExecutable;started=$tunnel.StartTime.ToUniversalTime().ToString('o')}
    Save-State
} finally { $env:NGROK_AUTHTOKEN = $previousToken; $credential = $null }
$deadline = [DateTime]::UtcNow.AddSeconds(30)
do {
    if ($tunnel.HasExited) {
        $errorLog = Join-Path $runtime 'ngrok-error.log'
        if ((Test-Path -LiteralPath $errorLog) -and (Select-String -LiteralPath $errorLog -SimpleMatch 'ERR_NGROK_9010' -Quiet)) {
            throw 'This ngrok account does not support the agent SOCKS proxy. Retry with -SocksProxyPort 0 for a direct connection.'
        }
        throw 'ngrok exited. Check the local ngrok-error.log for the account error.'
    }
    $url = $null
    try { $url = (Invoke-RestMethod 'http://127.0.0.1:4040/api/tunnels' -TimeoutSec 2).tunnels | Where-Object public_url -like 'https://*' | Select-Object -First 1 -ExpandProperty public_url } catch {}
    if ($url) { break }
    Start-Sleep -Milliseconds 300
} while ([DateTime]::UtcNow -lt $deadline)
if (!$url) { Stop-Owned $state.tunnel; throw 'ngrok did not establish an endpoint in time.' }
# Pin the account's assigned domain explicitly for subsequent restarts.
@{domain=$url;socksProxyPort=$SocksProxyPort} | ConvertTo-Json | Set-Content -LiteralPath $settingsPath
Write-Output "Relay address: $url"
Write-Output 'The assigned domain is saved for future starts. The relay stays on this PC.'
