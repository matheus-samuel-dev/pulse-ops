import { defineConfig } from '@playwright/test';
export default defineConfig({
 testDir:'./e2e',testMatch:'consistency.spec.ts',timeout:240_000,expect:{timeout:15_000},workers:1,fullyParallel:false,
 reporter:[['list'],['json',{outputFile:'../docs/evidence/consistency/e2e-consistency.json'}]],
 use:{baseURL:process.env.E2E_BASE_URL??'http://localhost:3001',viewport:{width:1366,height:900},trace:'retain-on-failure',screenshot:'only-on-failure'},
 outputDir:'../docs/evidence/consistency/e2e-consistency-artifacts',
});
