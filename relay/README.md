# KittyTune Connect on your PC

The relay forwards encrypted playback state and commands between paired devices. Music plays on the selected device. Pair on LAN first; neither a KittyTune account nor a public home IP is required for Connect.

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

## Battery behavior

Android has a live socket only while KittyTune is visible or the phone is playing music. Leaving an idle controller closes the socket and stops sync timers/LAN discovery. On return, a fresh full snapshot is requested. There are no Connect wake locks or dedicated foreground services.

Track, pause, seek, shuffle, repeat and queue changes are events. Position is projected locally using a monotonic clock, with a correction at most once a minute during steady playback. The queue is omitted from unchanged state updates. Keepalive is 60 seconds; failed connections back off to two minutes with jitter. These are design choices, not measured battery claims.

## Verification

```powershell
npm test
```

Android and desktop `ConnectWireTest` cover encryption, key derivation, replay rejection and idle-background connection policy. Relay tests cover routing, token rejection, room isolation, departure and room cleanup. For real network verification: control the PC, lock the paused phone and check the socket closes, reopen it and confirm a fresh state, then switch the phone to mobile data and repeat using the public endpoint.

Cloudflare documentation: https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/

Protocol: two endpoints join a random pair-specific room; the relay stores only a hash of its routing token. Clients authenticate AES-256-GCM frames with fresh per-connection challenges, session IDs and ordered sequence numbers. Forgotten peers are checked against current pairing data before commands are accepted. Queue operations are guarded by a queue fingerprint; seeks are guarded by track ID. Commands get acknowledgements and are not silently retried after a timeout.
