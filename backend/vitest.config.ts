import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    environment: 'node',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      include: ['src/**/*.ts'],
      exclude: ['src/index.ts', 'src/scripts/seed-cli.ts'],
    },
    setupFiles: ['./tests/setup.ts'],
  },
  resolve: {
    alias: {
      // Allow importing .js extensions in TS source (NodeNext convention)
    },
  },
});
