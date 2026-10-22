import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'node:path';
import { staffWorkerPlugin } from './src/staff-pwa/pwa/buildWorker.js';

export default defineConfig({
  plugins: [react(), staffWorkerPlugin()],
  build: { rollupOptions: { input: { website: resolve(import.meta.dirname, 'index.html'), staff: resolve(import.meta.dirname, 'staff/index.html') } } },
  define: { __STAFF_VERSION__: JSON.stringify(process.env.GITHUB_SHA?.slice(0, 12) || 'local') },
  server: { port: 5173, proxy: { '/api': 'http://127.0.0.1:8080', '/media': 'http://127.0.0.1:8080' } },
});
