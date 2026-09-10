import type { FullConfig } from '@playwright/test';

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { chromium } from '@playwright/test';

import { ACCESS_STORE_KEY, buildAccessState } from './fixtures/app-storage';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/**
 * Path to store authenticated session state for reuse across tests.
 * This matches the path used in playwright.config.ts and auth.fixture.ts.
 */
const AUTH_STATE_PATH = path.resolve(__dirname, '../.auth/admin-session.json');

/**
 * Backend API base URL for direct authentication requests.
 */
const API_BASE_URL = 'http://localhost:8080';

/**
 * Frontend base URL.
 */
const BASE_URL = 'http://localhost:5666';

/**
 * Default admin credentials (must match seeded data in dev database).
 */
const DEFAULT_USERNAME = 'admin';
const DEFAULT_PASSWORD = 'admin123';

/**
 * Global setup for Playwright E2E tests.
 *
 * Authenticates with the admin backend via API and saves the browser storage
 * state (localStorage with tokens) so all tests can reuse the authenticated
 * session without logging in individually.
 *
 * This approach:
 * 1. Calls the login API directly (fast, no UI interaction needed)
 * 2. Injects tokens into localStorage on the frontend origin
 * 3. Saves the storage state to a JSON file
 * 4. All test projects reference this file via storageState config
 */
async function globalSetup(_config: FullConfig) {
  // Ensure .auth directory exists
  const authDir = path.dirname(AUTH_STATE_PATH);
  if (!fs.existsSync(authDir)) {
    fs.mkdirSync(authDir, { recursive: true });
  }

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  // Authenticate via backend API
  const response = await page.request.post(
    `${API_BASE_URL}/api/admin/auth/login`,
    {
      data: {
        username: DEFAULT_USERNAME,
        password: DEFAULT_PASSWORD,
      },
    },
  );

  if (!response.ok()) {
    const text = await response.text();
    await browser.close();
    throw new Error(
      `Global setup: Login failed (${response.status()}): ${text}`,
    );
  }

  const body = await response.json();

  if (body.code !== 0) {
    await browser.close();
    throw new Error(`Global setup: Login API error: ${body.message}`);
  }

  const { accessToken, refreshToken } = body.data;

  if (!accessToken) {
    await browser.close();
    throw new Error('Global setup: Login response missing accessToken');
  }

  // Navigate to the frontend origin to set localStorage
  await page.goto(BASE_URL, { waitUntil: 'domcontentloaded' });

  // Inject the access store under the exact key the app reads on boot. A wrong
  // key here does not fail loudly: the app simply treats the session as logged
  // out and bounces every test to /auth/login.
  await page.evaluate(
    ({ key, state }) => {
      localStorage.setItem(key, JSON.stringify(state));
    },
    {
      key: ACCESS_STORE_KEY,
      state: buildAccessState(accessToken, refreshToken),
    },
  );

  // Save the authenticated storage state for reuse by all test projects
  await context.storageState({ path: AUTH_STATE_PATH });

  await browser.close();
}

export default globalSetup;
