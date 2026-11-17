// Optional TCP adapter. Cloudflare TLS or SSH authentication remains end to end.
import net from 'node:net';
import { once } from 'node:events';
const proxyHost = process.env.SOCKS_HOST || '127.0.0.1';
const proxyPort = Number(process.env.SOCKS_PORT || 10808);
const listenPort = Number(process.env.EDGE_PROXY_PORT || 17844);
const targetHost = process.env.EDGE_TARGET_HOST || 'region1.v2.argotunnel.com';
const targetPort = Number(process.env.EDGE_TARGET_PORT || 7844);
async function read(socket, size) {
  while (true) {
    const chunk = socket.read(size);
    if (chunk) return chunk;
    if (socket.destroyed) throw Error('Proxy closed during handshake');
    await new Promise((resolve, reject) => {
      const cleanup = () => { socket.off('readable', readable); socket.off('close', closed); socket.off('error', failed); };
      const readable = () => { cleanup(); resolve(); };
      const closed = () => { cleanup(); reject(Error('Proxy closed during handshake')); };
      const failed = error => { cleanup(); reject(error); };
      socket.once('readable', readable); socket.once('close', closed); socket.once('error', failed);
      if (socket.destroyed) closed();
    });
  }
}
net.createServer(async client => {
  client.pause();
  const upstream = net.connect(proxyPort, proxyHost);
  const close = () => { client.destroy(); upstream.destroy(); };
  client.on('error', close); client.on('close', close);
  upstream.on('error', close); upstream.on('close', close);
  upstream.setTimeout(15000, close);
  try {
    await once(upstream, 'connect');
    upstream.write(Buffer.from([5, 1, 0]));
    const greeting = await read(upstream, 2);
    if (greeting[0] !== 5 || greeting[1] !== 0) throw Error('Proxy requires authentication');
    // Preserve the destination hostname so the local proxy applies its domain routing policy.
    const edgeHost = Buffer.from(targetHost);
    upstream.write(Buffer.concat([Buffer.from([5, 1, 0, 3, edgeHost.length]), edgeHost, Buffer.from([targetPort >> 8, targetPort & 255])]));
    const response = await read(upstream, 4);
    if (response[0] !== 5 || response[1] !== 0) throw Error('Proxy refused Cloudflare connection');
    const addressSize = response[3] === 1 ? 4 : response[3] === 4 ? 16 : response[3] === 3 ? (await read(upstream, 1))[0] : 0;
    if (!addressSize) throw Error('Invalid proxy response');
    await read(upstream, addressSize + 2);
    upstream.setTimeout(0); upstream.setKeepAlive(true, 30000);
    upstream.pipe(client); client.pipe(upstream); client.resume();
  } catch { close(); }
}).listen(listenPort, '127.0.0.1', () => console.log(`Tunnel adapter listening on ${listenPort}`));
