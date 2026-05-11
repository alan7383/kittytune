import { defineConfig } from 'astro/config';

import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  site: 'https://alan7383.github.io',
  base: '/kittytune/',

  build: {
    format: 'file',
  },

  vite: {
    plugins: [tailwindcss()],
  },
});