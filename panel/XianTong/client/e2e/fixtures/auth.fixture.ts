import { test as base, type Page } from '@playwright/test';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

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
  /**
   * Authenticate with custom credentials and return the page.
   * Useful for testing different permission levels.
   */
  loginAs: (username: string, password: string) => Promise<Page>;
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
  const response = await page.request.post(`${API_BASE_URL}/api/admin/auth/login`, {
    data: { username, password },
  });

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

  // Inject tokens into the browser storage to match the frontend auth flow
  await page.evaluate(
    ({ token, refresh }) => {
      // The Vben Admin access store persists the token via its own mechanism.
      // We set localStorage keys that the app reads on initialization.
      const namespace = 'vben-web-antd';

      // Set the access token in the Vben access store persisted state
      const accessStoreKey = `${namespace}-preferences`;
      // Direct token storage used by the request interceptor
      localStorage.setItem(
        `${namespace}-access-store`,
        JSON.stringify({ accessToken: token }),
      );

      // Set refreshToken (used by refreshTokenApi)
      if (refresh) {
        localStorage.setItem('refreshToken', refresh);
      }
    },
    { token: accessToken, refresh: refreshToken },
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
      await page.goto('http://localhost:5666', { waitUntil: 'domcontentloaded' });
      if (stateData.origins?.[0]?.localStorage) {
        await page.evaluate((entries: Array<{ name: string; value: string }>) => {
          for (const entry of entries) {
            localStorage.setItem(entry.name, entry.value);
          }
        }, stateData.origins[0].localStorage);
      }
    } else {
      // Perform fresh login
      await authenticateViaApi(page, DEFAULT_USERNAME, DEFAULT_PASSWORD);
      await saveAuthState(page);
    }

    // Reload to let the app pick up the stored tokens
    await page.reload({ waitUntil: 'networkidle' });

    await use(page);
  },

  loginAs: async ({ browser }, use) => {
    const loginAsUser = async (username: string, password: string): Promise<Page> => {
      const context = await browser.newContext();
      const page = await context.newPage();
      await authenticateViaApi(page, username, password);
      await page.reload({ waitUntil: 'networkidle' });
      return page;
    };

    await use(loginAsUser);
  },
});

export { expect } from '@playwright/test';
export { AUTH_STATE_PATH, API_BASE_URL };
