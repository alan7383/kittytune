import { defineConfig } from 'astro/config';

import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  site: 'https://alan7383.github.io',
  base: '/kittytune/',

  devToolbar: {
    enabled: false
  },

  build: {
    format: 'file',
  },

  vite: {
    plugins: [tailwindcss()],
  },
});