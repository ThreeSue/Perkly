import { defineConfig } from 'vite';
import { resolve } from 'node:path';
export default defineConfig({
  // Upstream example lives inside a package of the same name. Resolve explicitly
  // to installed, immutable release files instead of that source package's absent dist/.
  resolve: { alias: [
    { find: /^simple-liquid-glass\/interactive$/, replacement: resolve('node_modules/simple-liquid-glass/dist/interactive.esm.js') },
    { find: /^simple-liquid-glass$/, replacement: resolve('node_modules/simple-liquid-glass/dist/index.esm.js') }
  ] },
  esbuild: { jsx: 'automatic' },
  build: { cssMinify: false, rollupOptions: { input: { lab: resolve('index.html'), original: resolve('original.html'), snapshot: resolve('snapshot.html') } } },
  server: { fs: { strict: true } }
});
