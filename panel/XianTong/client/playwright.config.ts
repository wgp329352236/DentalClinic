import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/**
 * Playwright E2E test configuration for Nova BI Admin Panel.
 *
 * Prerequisites:
 * - Backend running: APP_ENV=dev ./gradlew run (port 8080)
 * - Frontend dev server running: pnpm dev:antd (port 5666)
 *
 * Run: pnpm test:e2e
 */

/** Path to store authenticated session state for reuse across tests. */
const AUTH_STATE_PATH = path.resolve(__dirname, '.auth/admin-session.json');

export default defineConfig({
  /* Test directory */
  testDir: './e2e',

  /* Maximum time one test can run */
  timeout: 30_000,

  /* Expect timeout */
  expect: {
    timeout: 5_000,
  },

  /* Run tests in files in parallel */
  fullyParallel: true,

  /* Fail the build on CI if you accidentally left test.only in the source code */
  forbidOnly: !!process.env.CI,

  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,

  /* Reporter */
  reporter: process.env.CI ? 'dot' : 'html',

  /* Global setup for authentication state reuse */
  globalSetup: './e2e/global-setup.ts',

  /* Shared settings for all projects */
  use: {
    /* Base URL for the frontend dev server */
    baseURL: 'http://localhost:5666',

    /* Collect trace when retrying the failed test */
    trace: 'on-first-retry',

    /* Screenshot on failure */
    screenshot: 'only-on-failure',
  },

  /* Configure projects for Chromium headless */
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        headless: true,
        /* Reuse authenticated state from global setup */
        storageState: AUTH_STATE_PATH,
      },
    },
  ],

  /* Web server configuration - start frontend dev server if not already running */
  webServer: {
    command: 'pnpm dev:antd',
    url: 'http://localhost:5666',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
