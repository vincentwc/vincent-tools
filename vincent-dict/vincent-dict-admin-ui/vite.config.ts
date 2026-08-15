import vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [vue()],
  base: '/dict-admin/',
  build: {
    outDir: 'target/classes/META-INF/resources/dict-admin',
    emptyOutDir: true,
    assetsDir: 'assets'
  },
  test: {
    environment: 'happy-dom',
    environmentMatchGlobs: [
      ['src/api/**', 'node']
    ],
    setupFiles: ['./src/test/setup.ts']
  }
});
