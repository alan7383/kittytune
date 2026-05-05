import { defineConfig } from 'astro/config';

export default defineConfig({
  site: 'https://alan7383.github.io',
  base: '/kittytune/',
  output: 'static',
  outDir: '../docs',
  build: {
    format: 'file',
  },
});
