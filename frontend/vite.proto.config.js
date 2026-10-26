import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';
import {resolve} from 'node:path';
import {prototypeWorkerPlugin} from './src/staff-pwa-proto/pwa/buildWorker.js';
export default defineConfig({
 plugins:[react(),prototypeWorkerPlugin()],
 publicDir:resolve(import.meta.dirname,'src/staff-pwa-proto/public'),
 build:{outDir:'dist-staff-pwa-proto',rollupOptions:{input:{prototype:resolve(import.meta.dirname,'staff-pwa-proto/index.html')},output:{entryFileNames:'staff-pwa-proto/assets/[name]-[hash].js',chunkFileNames:'staff-pwa-proto/assets/[name]-[hash].js',assetFileNames:'staff-pwa-proto/assets/[name]-[hash][extname]'}}},
 server:{host:'127.0.0.1',port:5175,strictPort:true,proxy:{'/api':'http://127.0.0.1:8080','/media':'http://127.0.0.1:8080'}},
 preview:{host:'127.0.0.1',port:5176,strictPort:true},
});
