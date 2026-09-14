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
          // 上传素材的读取端点在 C 端命名空间下（/api/v1/files/{fileName}）。
          // 不代理的话，管理端上传成功后拿到的相对地址在 dev 下会 404，
          // 轮播图/门店封面/医生头像的预览全是裂图。
          '/api/v1': {
            changeOrigin: true,
            target: 'http://localhost:8080',
          },
        },
      },
    },
  };
});
