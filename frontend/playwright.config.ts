import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e', testMatch: 'operations.spec.ts', timeout: 90_000, expect: { timeout: 12_000 }, fullyParallel: false, workers: 1,
  reporter: [['list'], ['json', { outputFile: '../docs/evidence/consistency/e2e-results.json' }], ['html', { outputFolder: '../docs/evidence/consistency/e2e-report', open: 'never' }]],
  use: { baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3001', viewport: { width: 1366, height: 900 }, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  outputDir: '../docs/evidence/consistency/e2e-artifacts',
});

