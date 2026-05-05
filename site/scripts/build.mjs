import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

process.env.ASTRO_TELEMETRY_DISABLED = '1';

const astroBin = fileURLToPath(new URL('../node_modules/astro/bin/astro.mjs', import.meta.url));
const child = spawn(process.execPath, [astroBin, 'build'], {
  stdio: 'inherit',
  env: process.env,
});

child.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }
  process.exit(code ?? 1);
});
