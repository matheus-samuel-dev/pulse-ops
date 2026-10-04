import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'ui-vendor': ['@emotion/react', '@emotion/styled', '@mui/icons-material', '@mui/material'],
          'charts-vendor': ['recharts'],
          'forms-vendor': ['@hookform/resolvers', 'react-hook-form', 'zod'],
          'api-vendor': ['axios'],
        },
      },
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  preview: {
    host: '0.0.0.0',
    port: 4173,
  },
  test: {
    include: ['src/**/*.test.{ts,tsx}'],
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    restoreMocks: true,
    maxWorkers: 2,
    testTimeout: 15_000,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/main.tsx', 'src/types/**', 'src/vite-env.d.ts'],
      reporter: ['text', 'json-summary', 'html'],
      reportsDirectory: '../docs/evidence/consistency/frontend-coverage',
    },
  },
});

