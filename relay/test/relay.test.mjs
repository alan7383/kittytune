import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { WebSocket } from 'ws';
import { createRelay } from '../server.mjs';

const room = 'a'.repeat(64), token = 'b'.repeat(64);
async function setup(t) {
  const relay = createRelay();
  relay.server.listen(0, '127.0.0.1');
  await once(relay.server, 'listening');
  t.after(() => relay.close());
  return `ws://127.0.0.1:${relay.server.address().port}/v1/connect`;
}
async function join(url, device, overrides = {}) {
  const ws = new WebSocket(url);
  await once(ws, 'open');
  const response = once(ws, 'message');
  ws.send(JSON.stringify({ type: 'join', room, token, device, ...overrides }));
  assert.equal((await response)[0].toString(), '{"type":"ready"}');
  return ws;
}

test('routes opaque frames only between the paired devices, signals departure', async t => {
  const url = await setup(t);
  const a = await join(url, 'phone-1234567890123');
  const readyAgain = once(a, 'message');
  const b = await join(url, 'desktop-1234567890123');
  await readyAgain;
  const receive = once(b, 'message');
  a.send('opaque-encrypted-frame');
  assert.equal((await receive)[0].toString(), 'opaque-encrypted-frame');
  const departure = once(a, 'message');
  b.close();
  assert.equal((await departure)[0].toString(), '{"type":"peer_left"}');
});
test('wrong room token cannot join or displace an existing device', async t => {
  const url = await setup(t);
  await join(url, 'phone-1234567890123');
  const impostor = new WebSocket(url);
  await once(impostor, 'open');
  const close = once(impostor, 'close');
  impostor.send(JSON.stringify({ type: 'join', room, token: 'c'.repeat(64), device: 'phone-1234567890123' }));
  assert.equal((await close)[0], 1008);
  await join(url, 'desktop-1234567890123');
});
test('a third device is rejected and empty rooms are reclaimed', async t => {
  const url = await setup(t);
  const a = await join(url, 'phone-1234567890123');
  const b = await join(url, 'desktop-1234567890123');
  const third = new WebSocket(url);
  await once(third, 'open');
  const rejected = once(third, 'close');
  third.send(JSON.stringify({ type: 'join', room, token, device: 'third-1234567890123' }));
  assert.equal((await rejected)[0], 1008);
  const closedA = once(a, 'close'), closedB = once(b, 'close');
  a.close(); b.close(); await Promise.all([closedA, closedB]);
  await join(url, 'replacement-1234567890123', { token: 'c'.repeat(64) });
});
test('rooms are isolated', async t => {
  const url = await setup(t);
  const a = await join(url, 'phone-1234567890123');
  const b = await join(url, 'desktop-1234567890123', { room: 'd'.repeat(64) });
  let received = false;
  b.on('message', () => { received = true; });
  a.send('secret-frame');
  await new Promise(resolve => setTimeout(resolve, 50));
  assert.equal(received, false);
});
