import http from 'node:http';
import { createHash, timingSafeEqual } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { WebSocketServer, WebSocket } from 'ws';

/** Opaque two-device rooms. No music, history, credentials, commands or persistent state are stored. */
export function createRelay({ maxRooms = 128, maxConnections = 256, heartbeatMs = 60000, trace = () => {} } = {}) {
  const rooms = new Map();
  const server = http.createServer((req, res) => {
    res.writeHead(req.url === '/health' ? 200 : 404, { 'Content-Type': 'application/json' });
    res.end(req.url === '/health' ? '{"ok":true,"protocol":1}' : '{"error":"not_found"}');
  });
  const wss = new WebSocketServer({ noServer: true, maxPayload: 2 * 1024 * 1024, perMessageDeflate: false });
  server.on('upgrade', (req, socket, head) => {
    if (req.url !== '/v1/connect' || wss.clients.size >= maxConnections) { socket.destroy(); return; }
    wss.handleUpgrade(req, socket, head, ws => wss.emit('connection', ws));
  });
  wss.on('connection', ws => {
    trace('socket_open');
    let roomId, device, room;
    let bytes = 0, messages = 0, windowAt = Date.now();
    ws.alive = true;
    const authTimer = setTimeout(() => ws.close(1008, 'Join required'), 5000);
    ws.on('pong', () => { ws.alive = true; });
    ws.on('error', () => {});
    ws.on('message', (data, binary) => {
      if (binary) { ws.close(1003, 'Text only'); return; }
      if (Date.now() - windowAt > 60000) { bytes = 0; messages = 0; windowAt = Date.now(); }
      bytes += data.length; messages++;
      if (bytes > 8 * 1024 * 1024 || messages > 240) { ws.close(1008, 'Rate limit'); return; }
      if (!room) {
        try {
          if (data.length > 1024) throw Error();
          const join = JSON.parse(data.toString());
          if (join.type !== 'join' || !/^[a-f0-9]{64}$/.test(join.room) || !/^[a-f0-9]{64}$/.test(join.token) ||
              !/^[a-zA-Z0-9-]{16,64}$/.test(join.device)) throw Error();
          const tokenHash = createHash('sha256').update(join.token).digest();
          room = rooms.get(join.room);
          if (room && !timingSafeEqual(room.tokenHash, tokenHash)) { room = undefined; throw Error(); }
          if (!room) {
            if (rooms.size >= maxRooms) throw Error();
            room = { tokenHash, devices: new Map() };
            rooms.set(join.room, room);
          }
          if (!room.devices.has(join.device) && room.devices.size >= 2) { room = undefined; throw Error(); }
          roomId = join.room; device = join.device;
          room.devices.get(device)?.close(1000, 'Reconnected');
          room.devices.set(device, ws);
          clearTimeout(authTimer);
          trace(`joined endpoints=${room.devices.size}`);
          // Notify both endpoints so a newly joined device gets a full state without cached ciphertext.
          for (const endpoint of room.devices.values()) if (endpoint.readyState === WebSocket.OPEN) endpoint.send('{"type":"ready"}');
        } catch { ws.close(1008, 'Unauthorized'); }
        return;
      }
      // Frames are AEAD-encrypted by the clients. The relay cannot interpret or forge them.
      trace(`frame bytes=${data.length} endpoints=${room.devices.size}`);
      for (const [id, endpoint] of room.devices) {
        if (id !== device && endpoint.readyState === WebSocket.OPEN) {
          if (endpoint.bufferedAmount > 2 * 1024 * 1024) endpoint.close(1013, 'Slow client');
          else endpoint.send(data, { binary: false });
        }
      }
    });
    ws.on('close', () => {
      trace('socket_close');
      clearTimeout(authTimer);
      if (!room || room.devices.get(device) !== ws) return;
      room.devices.delete(device);
      if (room.devices.size === 0) rooms.delete(roomId);
      else for (const endpoint of room.devices.values()) if (endpoint.readyState === WebSocket.OPEN) endpoint.send('{"type":"peer_left"}');
    });
  });
  const heartbeat = setInterval(() => {
    for (const ws of wss.clients) {
      if (!ws.alive) { ws.terminate(); continue; }
      ws.alive = false; ws.ping();
    }
  }, heartbeatMs);
  heartbeat.unref();
  async function close() {
    clearInterval(heartbeat);
    for (const ws of wss.clients) ws.terminate();
    await new Promise(resolve => wss.close(resolve));
    await new Promise(resolve => server.close(resolve));
  }
  return { server, close };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  // Opt-in diagnostics contain counts only, never room IDs, device IDs, tokens or frames.
  const relay = createRelay({ trace: process.env.KITTY_CONNECT_TRACE === '1'
    ? event => console.log(new Date().toISOString(), event) : () => {} });
  const port = Number(process.env.PORT || 8787);
  relay.server.listen(port, process.env.BIND || '127.0.0.1', () => console.log(`KittyTune relay listening on ${port}`));
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, async () => { await relay.close(); process.exit(0); });
}
