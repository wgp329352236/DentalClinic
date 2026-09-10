import { test as base } from '@playwright/test';
import type { Page } from '@playwright/test';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

import { ACCESS_STORE_KEY, buildAccessState } from './app-storage';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/**
 * API base URL for direct backend requests (bypassing frontend proxy).
 */
const API_BASE_URL = 'http://localhost:8080';

/**
 * Default test credentials — should match a seeded admin user in the dev database.
 */
const DEFAULT_USERNAME = 'admin';
const DEFAULT_PASSWORD = 'admin123';

/**
 * Path to store authenticated session state for reuse across tests.
 */
const AUTH_STATE_PATH = path.resolve(
  __dirname,
  '../../.auth/admin-session.json',
);

export interface AuthFixtures {
  /** A page that is already authenticated as the default admin user. */
  authenticatedPage: Page;
}

/**
 * Authenticate via the backend API and inject the token into browser storage
 * so the frontend recognizes the session without going through the login UI.
 */
async function authenticateViaApi(
  page: Page,
  username: string,
  password: string,
): Promise<void> {
  // Call the login API directly
  const response = await page.request.post(
    `${API_BASE_URL}/api/admin/auth/login`,
    {
      data: { username, password },
    },
  );

  if (!response.ok()) {
    throw new Error(
      `Login failed for user "${username}": ${response.status()} ${await response.text()}`,
    );
  }

  const body = await response.json();

  if (body.code !== 0) {
    throw new Error(
      `Login API returned error for user "${username}": ${body.message}`,
    );
  }

  const { accessToken, refreshToken } = body.data;

  if (!accessToken) {
    throw new Error('Login response missing accessToken');
  }

  // Navigate to the app origin so we can set localStorage
  await page.goto('http://localhost:5666', { waitUntil: 'domcontentloaded' });

  // Inject the access store under the exact key the app reads on boot. This has
  // to stay in sync with global-setup.ts, which is why both go through the
  // shared app-storage helper instead of spelling the key out locally.
  await page.evaluate(
    ({ key, state }) => {
      localStorage.setItem(key, JSON.stringify(state));
    },
    {
      key: ACCESS_STORE_KEY,
      state: buildAccessState(accessToken, refreshToken),
    },
  );
}

/**
 * Save the authenticated storage state to a file for reuse in other tests.
 */
async function saveAuthState(page: Page): Promise<void> {
  const dir = path.dirname(AUTH_STATE_PATH);
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  await page.context().storageState({ path: AUTH_STATE_PATH });
}

/**
 * Check if a saved auth state file exists and is recent (less than 30 minutes old).
 */
function hasValidAuthState(): boolean {
  if (!fs.existsSync(AUTH_STATE_PATH)) {
    return false;
  }
  const stats = fs.statSync(AUTH_STATE_PATH);
  const ageMs = Date.now() - stats.mtimeMs;
  const thirtyMinutes = 30 * 60 * 1000;
  return ageMs < thirtyMinutes;
}

/**
 * Extended Playwright test fixture that provides authenticated pages.
 */
export const test = base.extend<AuthFixtures>({
  authenticatedPage: async ({ page }, use) => {
    if (hasValidAuthState()) {
      // Reuse saved state by navigating with existing storage
      const stateData = JSON.parse(fs.readFileSync(AUTH_STATE_PATH, 'utf-8'));
      // Apply stored localStorage entries
      await page.goto('http://localhost:5666', {
        waitUntil: 'domcontentloaded',
      });
      if (stateData.origins?.[0]?.localStorage) {
        await page.evaluate(
          (entries: Array<{ name: string; value: string }>) => {
            for (const entry of entries) {
              localStorage.setItem(entry.name, entry.value);
            }
          },
          stateData.origins[0].localStorage,
        );
      }
    } else {
      // Perform fresh login
      await authenticateViaApi(page, DEFAULT_USERNAME, DEFAULT_PASSWORD);
      await saveAuthState(page);
    }

    // Reload to let the app pick up the stored tokens.
    // 不等 networkidle：Vite dev server 的按需编译与 HMR websocket 会让网络
    // 一直有活动，而这个 fixture 每个用例都要跑一次，等不到就是白耗超时。
    await page.reload({ waitUntil: 'domcontentloaded' });

    await use(page);
  },
});

export { expect } from '@playwright/test';
export { AUTH_STATE_PATH, API_BASE_URL };
