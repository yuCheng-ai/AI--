import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const proxy = {
    '/api': { target: env.DRAMA_API_TARGET || 'http://127.0.0.1:18080', changeOrigin: true },
  };
  return {
    plugins: [vue()],
    server: { host: '127.0.0.1', port: 15173, strictPort: true, proxy },
    preview: { proxy },
    build: { sourcemap: false },
  };
});
