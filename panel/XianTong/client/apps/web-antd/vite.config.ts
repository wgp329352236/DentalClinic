import { defineConfig } from '@vben/vite-config';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      server: {
        proxy: {
          '/api/admin': {
            changeOrigin: true,
            // 不 rewrite，直接转发到后端（后端路由也是 /api/admin/*）
            target: 'http://localhost:8080',
            ws: true,
          },
        },
      },
    },
  };
});
