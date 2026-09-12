import { defineConfig } from 'vite';
import { resolve } from 'node:path';
import { readdirSync } from 'node:fs';

// 多页应用：根目录每个 HTML 是一个入口（通过 fs 扫描，页面迁移期间新增页面即自动纳入）。
// 构建产物直接输出到 Spring Boot 静态资源目录，URL 形如 /capture.html。
const htmlInputs = Object.fromEntries(
  readdirSync(__dirname)
    .filter((name) => name.endsWith('.html'))
    .map((name) => [name.replace(/\.html$/, ''), resolve(__dirname, name)])
);

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  },
  build: {
    rollupOptions: {
      input: htmlInputs
    },
    outDir: resolve(__dirname, '../backend/src/main/resources/static'),
    emptyOutDir: true,
    assetsInlineLimit: 8192
  }
});
