# KittyTune Connect on your PC

The relay forwards encrypted playback state and commands between paired devices. Music plays on the selected device. Pair on LAN first; neither a KittyTune account nor a public home IP is required for Connect.

For a relay running on the same PC as KittyTune, enable **Internet connection → Server runs on this computer** on the desktop. The PC connects to `127.0.0.1:8787`; the public server address remains available for the phone and LAN pairing. This avoids sending desktop control traffic out to the Internet and back to its own server.

On the PC, the devices button opens the **Devices** tab in the right sidebar. On Android it opens the device chooser from the mini player or full player, including Pixel style. The main player shows the selected device's track, queue and timeline, with a “Playing on …” label. Play/pause, previous/next, seek, shuffle and repeat control that device. **Continue on this device** transfers playback locally.

## Docker (recommended for a persistent home server)

Install Docker Desktop with Linux containers. In this directory:

```powershell
docker compose up -d --build
docker compose logs tunnel
```

Copy the `https://…trycloudflare.com` address from the tunnel log into **Playback devices → Internet connection** on the PC. The phone learns an empty relay setting at its next LAN sync; otherwise enter the same address on the phone. Do not add `/v1/connect` yourself. LAN port 47654 stays local; Docker publishes the relay only on localhost:8787.

This default uses a temporary Cloudflare Quick Tunnel. Its address changes after recreation and there is no uptime guarantee. For daily use with a stable address, create a named tunnel and public hostname in Cloudflare, routing it to `http://relay:8787`. Put `TUNNEL_TOKEN=…` in the ignored `.env` file, then:

```powershell
docker compose -f compose.yaml -f compose.named.yaml up -d --build
```

Enter the permanent hostname in both apps. Never commit `.env`. The PC, Docker and tunnel must remain running; a sleeping PC is unavailable.

Stop with `docker compose down`. No music/library data is stored in the container. No login, pairing or playback secrets are logged by the relay.

## Without Docker (Windows)

With Node.js 22+ and npm installed:

```powershell
npm ci
.\start-local.ps1
Get-Content .runtime/tunnel-error.log
```

The launcher downloads the official cloudflared Windows x64 executable and verifies its published SHA256 digest. Processes run in hidden windows. Stop only the launched processes with `./start-local.ps1 -Stop`.

Cloudflare may be unavailable on Russian networks: documented restrictions can stop connections after about 16 KB, even when `/health` and a WebSocket handshake succeed. See [Cloudflare's service disruption notice](https://developers.cloudflare.com/support/troubleshooting/general-troubleshooting/service-disruption/). A VPN route for the tunnel alone does not fix a client's direct connection to the public Cloudflare address. Verify a full queue and a command, or select a different tunnel provider.

If Cloudflare cannot maintain a connection on your network, use the SSH tunnel fallback:

```powershell
.\start-local.ps1 -Stop
.\start-local.ps1 -Provider localhost
```

This uses Windows OpenSSH and localhost.run without sending your SSH keys. The HTTPS address appears in `.runtime/tunnel.log`; enter it in both apps. The free address is temporary and can change on reconnect. The relay and encrypted Connect protocol are identical with either provider. Stop either variant with `-Stop`.

### Routing the tunnel through an existing VPN on the PC

If the tunnel's direct connection repeatedly drops, the Windows launcher can route it through a loopback SOCKS proxy:

```powershell
.\start-local.ps1 -SocksProxyPort 10808
# Or route the SSH tunnel through the proxy:
.\start-local.ps1 -Provider localhost -SocksProxyPort 10808
```

The SOCKS proxy must actually route Cloudflare through the VPN. A profile that routes this domain directly will have the same connection problem. For an existing Xray profile, an isolated helper can use just its `proxy` outbound:

```powershell
.\start-local.ps1 -VpnExecutablePath 'C:\path\xray.exe' -VpnConfigPath 'C:\path\config.json'
```

Use `-Provider localhost` to combine the isolated VPN helper with the SSH tunnel, `-VpnOutboundTag` if the outbound has another tag, and `-NodeExecutable` if Node is not on PATH. The helper listens only on loopback, leaves the original VPN process and routing settings intact, and stores a private copy of the selected outbound in ignored `.runtime/vpn-edge-only.json`, restricted to the current Windows user. Chained outbounds are rejected. `-Stop` stops the relay, tunnel and both helpers using their process identities, then removes the private copy. Cloudflare still verifies TLS and SSH still verifies its host key; the adapter forwards opaque bytes. The phone's access to the public endpoint must be checked separately.

localhost.run free domain names can change while the SSH process remains connected. A live SSH process is not proof that an older hostname still works. Read the latest registration event from the log. For longer domain life, the provider [requires registering an account and SSH key](https://localhost.run/docs/forever-free/); a permanent custom domain is a separate paid option. This launcher does not register accounts or upload existing SSH keys.

Opt-in relay diagnostics (`KITTY_CONNECT_TRACE=1`) log connection events and frame byte counts only. They never log room IDs, device IDs, tokens or frame contents.

## Battery behavior

Android has a live socket only while KittyTune is visible or the phone is playing music. Leaving an idle controller closes the socket and stops sync timers/LAN discovery. On return, a fresh full snapshot is requested. There are no Connect wake locks or dedicated foreground services.

Track, pause, seek, shuffle, repeat and queue changes are events. Position is projected locally using a monotonic clock, with a correction at most once a minute during steady playback. The queue is omitted from unchanged state updates. Keepalive is 60 seconds; failed connections back off to two minutes with jitter. These are design choices, not measured battery claims.

Queue entries contain track IDs/source links and small display fields, without descriptions, statistics or audio transcoding responses. When both peers support it, larger packets are gzip-compressed before encryption; older peers still receive the compact JSON format. Decompression is bounded by the protocol payload limit. Network changes trigger immediate reconnect while Android is active, without periodic connectivity polling.

## Verification

```powershell
npm test
```

Android and desktop `ConnectWireTest` cover encryption, key derivation, replay rejection and idle-background connection policy. Relay tests cover routing, token rejection, room isolation, departure and room cleanup. For real network verification: control the PC, lock the paused phone and check the socket closes, reopen it and confirm a fresh state, then switch the phone to mobile data and repeat using the public endpoint.

Cloudflare documentation: https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/

Protocol: two endpoints join a random pair-specific room; the relay stores only a hash of its routing token. Clients authenticate AES-256-GCM frames with fresh per-connection challenges, session IDs and ordered sequence numbers. Forgotten peers are checked against current pairing data before commands are accepted. Queue operations are guarded by a queue fingerprint; seeks are guarded by track ID. Commands get acknowledgements and are not silently retried after a timeout.

## Switching outputs and independent playback

The player device button opens available outputs. Clicking an output moves the currently displayed queue and position there (also from one remote output to another). The target prepares silently, acknowledges its queue, then the source pauses; the final position is sent before starting the target. A paused source stays paused. Transfers are serialized; a failed preparation leaves the source running. If starting fails, the source resumes only after the destination acknowledges a pause.

**Play independently** is a per-device setting. It disables Connect commands and playback-state sync, clears remote control selection, and preserves that device's local queue. History/library synchronization remains enabled. Disable it before transferring playback again. It does not disable streaming Internet access or download tracks.

Android's **Continue with headphones** defaults to enabled and can be switched off in device settings. Audio-device callbacks cover wired, USB and Bluetooth outputs without discovery polling or device-name pairing lists. Android reports A2DP speakers/car audio as the same category, so those can trigger this setting too.

| Event | Behavior |
| --- | --- |
| Connect headphones while the app is visible and a paired PC is playing | Transfer its track, final position and queue to the phone |
| Open the app with headphones already connected | One bounded wait for a fresh playing-PC state, then transfer |
| Source paused / phone already playing / independent mode / active audio call | No automatic takeover |
| Disconnect all headphone outputs | Pause the phone's local renderer; no PC auto-resume |
| User chooses an output manually | Suppress automatic takeover until headphones disconnect |
| Wi-Fi/mobile-data change or a remote output disappears | Reconnect with backoff; do not autoplay on speakers or steal another renderer |
| Destination cannot open a local-only source file | Show an error; leave source playback intact |

Callbacks are registered while the app is visible or its own audio is playing, and removed when idle in the background. No additional wake lock, Bluetooth scan, background service or recurring task is added. A stopped/killed app must be reopened for automatic takeover. Stream preparation has a bounded timeout; hardware headphone behavior needs verification on each Android audio stack.
