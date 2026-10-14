param([switch]$Stop, [ValidateSet('auto','http2','quic')][string]$Protocol = 'http2',
    [ValidateSet('cloudflare','localhost')][string]$Provider = 'cloudflare',
    [string]$NodeExecutable, [string]$VpnConfigPath, [string]$VpnExecutablePath,
    [string]$VpnOutboundTag = 'proxy', [int]$SocksProxyPort = 0)
$ErrorActionPreference = 'Stop'
$relayDirectory = $PSScriptRoot
$runtimeDirectory = Join-Path $relayDirectory '.runtime'
$stateFile = Join-Path $runtimeDirectory 'processes.json'

if ($Stop) {
    if (Test-Path -LiteralPath $stateFile) {
        $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
        foreach ($entry in @($state.tunnel, $state.edgeProxy, $state.vpn, $state.relay)) {
            if (!$entry) { continue }
            $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
            if ($process -and $process.Path -eq $entry.path -and $process.StartTime.ToUniversalTime().Ticks -eq ([DateTimeOffset]$entry.started).UtcDateTime.Ticks) {
                Stop-Process -Id $process.Id
                $process.WaitForExit(5000) | Out-Null
            }
        }
    }
    $privateConfig = Join-Path $runtimeDirectory 'vpn-edge-only.json'
    if (Test-Path -LiteralPath $privateConfig) { Remove-Item -LiteralPath $privateConfig }
    Write-Output 'Local relay and tunnel stopped.'
    exit
}

New-Item -ItemType Directory -Force -Path $runtimeDirectory | Out-Null
if (!$NodeExecutable) { $NodeExecutable = (Get-Command node -ErrorAction Stop).Source }
$nodeExecutable = (Get-Command $NodeExecutable -ErrorAction Stop).Source
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
$state = @{ relay = @{ pid = $relay.Id; path = $nodeExecutable; started = $relay.StartTime.ToUniversalTime().ToString('o') } }
function Save-State { $state | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $stateFile }
Save-State
try {
if ($VpnConfigPath) {
    if (!$VpnExecutablePath) { throw 'VPN routing requires VpnExecutablePath.' }
    $vpnExecutable = (Get-Command $VpnExecutablePath -ErrorAction Stop).Source
    $vpnConfig = Get-Content -LiteralPath $VpnConfigPath -Raw | ConvertFrom-Json
    $outbound = @($vpnConfig.outbounds | Where-Object tag -eq $VpnOutboundTag)
    if ($outbound.Count -ne 1 -or $outbound[0].protocol -in @('freedom','blackhole')) { throw 'Select one VPN outbound; chained profiles need a dedicated config.' }
    if ($outbound[0].proxySettings.tag -or $outbound[0].streamSettings.sockopt.dialerProxy) { throw 'Chained VPN profiles need a dedicated config.' }
    $SocksProxyPort = 17846
    if (Get-NetTCPConnection -State Listen -LocalPort $SocksProxyPort -ErrorAction SilentlyContinue) { throw 'Isolated VPN port is already in use.' }
    $privateConfig = Join-Path $runtimeDirectory 'vpn-edge-only.json'
    New-Item -ItemType File -Force -Path $privateConfig | Out-Null
    $acl = Get-Acl -LiteralPath $privateConfig
    $acl.SetAccessRuleProtection($true, $false)
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl.SetAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity,'FullControl','Allow'))
    Set-Acl -LiteralPath $privateConfig -AclObject $acl
    @{ log = @{ loglevel = 'warning' }; inbounds = @(@{ tag = 'connect-edge'; listen = '127.0.0.1'; port = $SocksProxyPort; protocol = 'socks'; settings = @{ auth = 'noauth'; udp = $false } }); outbounds = $outbound } | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $privateConfig -Encoding utf8
    $vpn = Start-Process -FilePath $vpnExecutable -ArgumentList @('run','-c',('"' + $privateConfig + '"')) -WorkingDirectory (Split-Path -Parent $VpnConfigPath) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDirectory 'vpn-edge.log') -RedirectStandardError (Join-Path $runtimeDirectory 'vpn-edge-error.log')
    $state.vpn = @{ pid = $vpn.Id; path = $vpnExecutable; started = $vpn.StartTime.ToUniversalTime().ToString('o') }
    Save-State
}
if ($SocksProxyPort) {
    if ($Provider -eq 'cloudflare' -and $Protocol -eq 'quic') { throw 'SOCKS routing requires the Cloudflare HTTP2 protocol.' }
    if ($SocksProxyPort -lt 1 -or $SocksProxyPort -gt 65535) { throw 'Invalid SOCKS port.' }
    if (Get-NetTCPConnection -State Listen -LocalPort 17844 -ErrorAction SilentlyContinue) { throw 'Cloudflare adapter port is already in use.' }
    $previousSocksPort = $env:SOCKS_PORT
    $previousTargetHost = $env:EDGE_TARGET_HOST
    $previousTargetPort = $env:EDGE_TARGET_PORT
    $previousListenPort = $env:EDGE_PROXY_PORT
    try {
        $env:SOCKS_PORT = "$SocksProxyPort"
        $env:EDGE_PROXY_PORT = '17844'
        $env:EDGE_TARGET_HOST = if ($Provider -eq 'localhost') { 'localhost.run' } else { 'region1.v2.argotunnel.com' }
        $env:EDGE_TARGET_PORT = if ($Provider -eq 'localhost') { '22' } else { '7844' }
        $edgeProxy = Start-Process -FilePath $nodeExecutable -ArgumentList 'edge-proxy.mjs' -WorkingDirectory $relayDirectory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDirectory 'edge-proxy.log') -RedirectStandardError (Join-Path $runtimeDirectory 'edge-proxy-error.log')
    } finally {
        $env:SOCKS_PORT = $previousSocksPort; $env:EDGE_TARGET_HOST = $previousTargetHost
        $env:EDGE_TARGET_PORT = $previousTargetPort; $env:EDGE_PROXY_PORT = $previousListenPort
    }
    $state.edgeProxy = @{ pid = $edgeProxy.Id; path = $nodeExecutable; started = $edgeProxy.StartTime.ToUniversalTime().ToString('o') }
    Save-State
}
$tunnelArguments = if ($Provider -eq 'localhost') {
    $sshRoute = if ($SocksProxyPort) { @('-p','17844','-o','HostKeyAlias=localhost.run') } else { @() }
    $sshDestination = if ($SocksProxyPort) { 'nokey@127.0.0.1' } else { 'nokey@localhost.run' }
    @('-T') + $sshRoute + @('-o','BatchMode=yes','-o','IdentitiesOnly=yes','-o','IdentityFile=none',
        '-o','IdentityAgent=none','-o','StrictHostKeyChecking=accept-new',
        '-o', ('UserKnownHostsFile="' + (Join-Path $runtimeDirectory 'known_hosts') + '"'),
        '-o','ServerAliveInterval=60','-o','ServerAliveCountMax=3','-o','ExitOnForwardFailure=yes',
        '-o','ConnectTimeout=15','-R','80:127.0.0.1:8787',$sshDestination,'--','--output','json')
} else {
    @('tunnel','--no-autoupdate','--protocol',$Protocol,'--url','http://127.0.0.1:8787')
}
$previousEdge = $env:TUNNEL_EDGE
try {
    if ($SocksProxyPort -and $Provider -eq 'cloudflare') { $env:TUNNEL_EDGE = '127.0.0.1:17844' }
    $tunnel = Start-Process -FilePath $tunnelExecutable -ArgumentList $tunnelArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDirectory 'tunnel.log') -RedirectStandardError (Join-Path $runtimeDirectory 'tunnel-error.log')
} finally { $env:TUNNEL_EDGE = $previousEdge }
$state.tunnel = @{ pid = $tunnel.Id; path = $tunnelExecutable; started = $tunnel.StartTime.ToUniversalTime().ToString('o') }
Save-State
Write-Output 'Started. Public HTTPS address will appear in .runtime/tunnel.log or tunnel-error.log.'
} catch {
    & $PSCommandPath -Stop
    throw
}
