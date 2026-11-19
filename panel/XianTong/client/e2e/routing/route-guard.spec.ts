import { test as base, expect } from '@playwright/test';

import { ACCESS_STORE_KEY, buildAccessState } from '../fixtures/app-storage';

/**
 * Route Guard E2E Tests
 *
 * Validates: Requirements 19.1-19.6
 *
 * Tests cover:
 * - Dynamic menu loading after login (19.1, 19.2)
 * - Unauthorized route access shows 403 page (19.3)
 * - Unauthenticated access redirects to login (19.4)
 * - Logout clears state and redirects to login (19.5)
 * - Browser back after logout stays on login page (19.6)
 *
 * 导航一律用 `domcontentloaded`：本文件不走 `base` fixture（每条用例自己建
 * 一个无登录态的 context），但同样的道理适用——Vite dev server 的按需编译与
 * HMR websocket 会让网络一直有活动，`networkidle` 实测能把一次 goto 卡到几十秒，
 * 直接把用例的 30s 预算吃光（Req 19.3 就是这么红的）。页面是否就绪交给后面
 * 的显式信号判断：`/menu/all` 的响应、侧边栏、或 `waitForURL`。
 */

const BASE_URL = 'http://localhost:5666';
const API_BASE_URL = 'http://localhost:8080';
const LOGIN_PATH = '/auth/login';

/**
 * Create a context that is genuinely free of auth state.
 *
 * `browser.newContext()` inherits `use.storageState` from playwright.config.ts,
 * so a supposedly "fresh" context is in fact already logged in with the session
 * captured by global-setup — the route guard then never redirects and every
 * unauthenticated assertion silently tests the wrong thing. Passing an explicit
 * empty storageState is the only way to start from a logged-out state.
 */
async function bareContext(browser: import('@playwright/test').Browser) {
  return browser.newContext({ storageState: { cookies: [], origins: [] } });
}

/**
 * Authenticate via API and inject tokens into localStorage.
 * Returns the access token for further use.
 */
async function loginViaApi(
  page: import('@playwright/test').Page,
  username = 'admin',
  password = 'admin123',
): Promise<{ accessToken: string; refreshToken: string }> {
  const response = await page.request.post(
    `${API_BASE_URL}/api/admin/auth/login`,
    { data: { username, password } },
  );

  if (!response.ok()) {
    throw new Error(
      `Login failed: ${response.status()} ${await response.text()}`,
    );
  }

  const body = await response.json();
  if (body.code !== 0) {
    throw new Error(`Login API error: ${body.message}`);
  }

  const { accessToken, refreshToken } = body.data;

  // Navigate to origin to set localStorage
  await page.goto(BASE_URL, { waitUntil: 'domcontentloaded' });

  // Inject tokens under the key the app actually reads (see app-storage.ts).
  await page.evaluate(
    ({ key, state }) => {
      localStorage.setItem(key, JSON.stringify(state));
    },
    {
      key: ACCESS_STORE_KEY,
      state: buildAccessState(accessToken, refreshToken),
    },
  );

  return { accessToken, refreshToken };
}

base.describe(
  'Route Guard - Dynamic Menu Loading After Login (Req 19.1, 19.2)',
  () => {
    base(
      'should call /menu/all after login and render sidebar menu',
      async ({ browser }) => {
        // Use a fresh context without pre-existing auth state
        const context = await bareContext(browser);
        const page = await context.newPage();

        try {
          // Login via API and inject tokens
          await loginViaApi(page);

          // Set up listener for /menu/all request before navigating
          const menuRequestPromise = page.waitForRequest(
            (req) => req.url().includes('/menu/all'),
            { timeout: 15_000 },
          );

          // Navigate to the app - this should trigger route guard which fetches menus
          await page.goto(BASE_URL, { waitUntil: 'domcontentloaded' });

          // Verify /menu/all was called
          const menuRequest = await menuRequestPromise;
          expect(menuRequest.url()).toContain('/menu/all');

          // Verify sidebar menu is rendered after menu data loads
          // Vben Admin renders the sidebar with menu items
          const sidebar = page.locator(
            '.vben-layout-sidebar, [class*="sidebar"], aside, nav',
          );
          await expect(sidebar.first()).toBeVisible({ timeout: 15_000 });

          // Verify at least one menu item is rendered in the sidebar
          const menuItems = page.locator(
            '.vben-menu-item, [class*="menu-item"], .ant-menu-item, [role="menuitem"]',
          );
          await expect(menuItems.first()).toBeVisible({ timeout: 10_000 });
        } finally {
          await context.close();
        }
      },
    );
  },
);

base.describe('Route Guard - Unauthorized Route Access (Req 19.3)', () => {
  base(
    'should show 403 page when accessing a route without permission',
    async ({ browser }) => {
      // 这条用例要冷启动两次应用（先加载受限菜单、再访问无权限路由），
      // dev server + 多 worker 并发时默认的 30s 不够用。
      base.setTimeout(60_000);

      // Use a fresh context
      const context = await bareContext(browser);
      const page = await context.newPage();

      try {
        // Login via API
        await loginViaApi(page);

        // Intercept the menu/all API to return a limited set of menus
        // This simulates a user who only has access to certain routes
        await page.route('**/menu/all', async (route) => {
          // Return only a minimal menu - the dashboard
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              code: 0,
              data: [
                {
                  component: 'BasicLayout',
                  meta: {
                    order: -1,
                    title: 'page.dashboard.title',
                  },
                  name: 'Dashboard',
                  path: '/',
                  redirect: '/dashboard',
                  children: [
                    {
                      name: 'Analytics',
                      path: '/dashboard',
                      component: '/dashboard/index',
                      meta: {
                        affixTab: true,
                        icon: 'lucide:area-chart',
                        title: 'page.dashboard.analytics',
                      },
                    },
                  ],
                },
              ],
              message: 'success',
            }),
          });
        });

        // Also intercept auth/codes to return limited permissions
        await page.route('**/auth/codes', async (route) => {
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              code: 0,
              data: ['dashboard:view'],
              message: 'success',
            }),
          });
        });

        // Navigate to the app first to load the limited menu
        //
        // 这里原本等 `networkidle` 再 `waitForTimeout(2000)`：前者在 dev server 下
        // 很难成立，后者既慢又不够——两者叠加把 30s 预算吃光，超时报错还被算
        // 到 finally 的 `context.close()` 头上，完全看不出真正卡在哪。改成等
        // `/menu/all` 的响应——它就是「菜单已加载」这件事本身。
        const limitedMenuPromise = page.waitForResponse(
          (res) => res.url().includes('/menu/all'),
          { timeout: 15_000 },
        );
        await page.goto(BASE_URL, { waitUntil: 'domcontentloaded' });
        await limitedMenuPromise;

        // Now try to access a route that is NOT in the limited menu
        // The route guard should redirect to the 403 forbidden page
        await page.goto(`${BASE_URL}/system/user`, {
          waitUntil: 'domcontentloaded',
        });

        // Verify 403 page is shown - Vben Admin's Fallback component renders "403"
        // The page should show a 403 status or redirect to a forbidden page
        //
        // 守卫是在 domcontentloaded 之后才跑的，旧写法立刻抓 `page.content()`
        // 会抓到跳转前的快照；用 poll 等结果，判定条件与原来一致。
        await expect
          .poll(
            async () => {
              const pageContent = await page.content();
              const url = page.url();
              return (
                pageContent.includes('403') ||
                url.includes('403') ||
                url.includes('forbidden')
              );
            },
            { message: '访问无权限路由后应落到 403 页面', timeout: 15_000 },
          )
          .toBe(true);
      } finally {
        await context.close();
      }
    },
  );
});

base.describe('Route Guard - Unauthenticated Access (Req 19.4)', () => {
  base(
    'should redirect to login page when accessing protected route without authentication',
    async ({ browser }) => {
      // Use a fresh context with NO stored auth state
      const context = await bareContext(browser);
      const page = await context.newPage();

      try {
        // Navigate directly to a protected route without any tokens
        await page.goto(`${BASE_URL}/system/user`, {
          waitUntil: 'domcontentloaded',
        });

        // The route guard should redirect to the login page
        await page.waitForURL(`**${LOGIN_PATH}**`, { timeout: 10_000 });

        // Verify we are on the login page
        expect(page.url()).toContain(LOGIN_PATH);

        // Verify login form elements are visible
        const loginForm = page.locator(
          'form, [class*="authentication"], [class*="login"]',
        );
        await expect(loginForm.first()).toBeVisible({ timeout: 10_000 });
      } finally {
        await context.close();
      }
    },
  );

  base(
    'should redirect to login when localStorage is cleared and navigating to protected route',
    async ({ browser }) => {
      // Use a fresh context
      const context = await bareContext(browser);
      const page = await context.newPage();

      try {
        // First navigate to the origin to be able to clear localStorage
        await page.goto(BASE_URL, { waitUntil: 'domcontentloaded' });

        // Explicitly clear all localStorage to simulate unauthenticated state
        await page.evaluate(() => {
          localStorage.clear();
        });

        // Now navigate to a protected route
        await page.goto(`${BASE_URL}/system/user`, {
          waitUntil: 'domcontentloaded',
        });

        // Should be redirected to login
        await page.waitForURL(`**${LOGIN_PATH}**`, { timeout: 10_000 });
        expect(page.url()).toContain(LOGIN_PATH);
      } finally {
        await context.close();
      }
    },
  );
});

base.describe('Route Guard - Logout Clears State (Req 19.5)', () => {
  base(
    'should clear stores and redirect to login on logout',
    async ({ browser }) => {
      // Use a fresh context
      const context = await bareContext(browser);
      const page = await context.newPage();

      try {
        // Login and navigate to the app
        await loginViaApi(page);
        await page.goto(BASE_URL, { waitUntil: 'domcontentloaded' });

        // Wait for the app to fully load with sidebar
        // 侧边栏属于布局，与子路由能不能解析无关，用 `attached` 就够；
        // 固定的 waitForTimeout(3000) 既慢又不可靠。
        await page
          .locator('aside')
          .first()
          .waitFor({ state: 'attached', timeout: 15_000 });

        // Find and click the user avatar/dropdown to trigger logout
        // Vben Admin typically has a user dropdown in the header
        const userDropdown = page.locator(
          '[class*="user-dropdown"], .vben-avatar, [class*="header"] .ant-avatar, [class*="header"] .ant-dropdown-trigger',
        );

        if (
          await userDropdown
            .first()
            .isVisible({ timeout: 5000 })
            .catch(() => false)
        ) {
          await userDropdown.first().click();

          // Wait for dropdown menu to appear and click logout
          const logoutBtn = page.getByText(/退出登录|登出|Logout/i);
          if (await logoutBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
            await logoutBtn.click();

            // Handle any confirmation dialog
            const confirmBtn = page.locator(
              '.ant-modal-confirm .ant-btn-primary, .ant-popconfirm .ant-btn-primary',
            );
            if (
              await confirmBtn.isVisible({ timeout: 2000 }).catch(() => false)
            ) {
              await confirmBtn.click();
            }
          }
        } else {
          // Alternative: call logout API directly and clear state programmatically
          // This simulates what the logout button does
          await page.evaluate(() => {
            localStorage.clear();
            sessionStorage.clear();
          });
          await page.goto(`${BASE_URL}${LOGIN_PATH}`, {
            waitUntil: 'domcontentloaded',
          });
        }

        // Wait for redirect to login page
        await page.waitForURL(`**${LOGIN_PATH}**`, { timeout: 10_000 });
        expect(page.url()).toContain(LOGIN_PATH);

        // Verify stores are cleared - read the key the app actually uses
        const accessStoreData = await page.evaluate((key) => {
          const data = localStorage.getItem(key);
          return data ? (JSON.parse(data) as Record<string, unknown>) : null;
        }, ACCESS_STORE_KEY);

        // After logout, the access token should be null/cleared
        expect(accessStoreData?.accessToken).toBeFalsy();

        // Verify refreshToken is cleared as well
        expect(accessStoreData?.refreshToken).toBeFalsy();
      } finally {
        await context.close();
      }
    },
  );
});

base.describe('Route Guard - Browser Back After Logout (Req 19.6)', () => {
  base(
    'should stay on login page when pressing browser back after logout',
    async ({ browser }) => {
      // Use a fresh context
      const context = await bareContext(browser);
      const page = await context.newPage();

      try {
        // Login and navigate to a protected page
        await loginViaApi(page);
        await page.goto(`${BASE_URL}/system/user`, {
          waitUntil: 'domcontentloaded',
        });

        // Wait for the page to fully load（同 Req 19.5：用侧边栏代替固定 sleep）
        await page
          .locator('aside')
          .first()
          .waitFor({ state: 'attached', timeout: 15_000 });

        // Simulate logout by clearing tokens and navigating to login
        await page.evaluate(() => {
          localStorage.clear();
          sessionStorage.clear();
        });

        // Navigate to login page (simulating what logout does)
        await page.goto(`${BASE_URL}${LOGIN_PATH}`, {
          waitUntil: 'networkidle',
        });

        // Verify we're on login page
        expect(page.url()).toContain(LOGIN_PATH);

        // Press browser back button
        await page.goBack({ waitUntil: 'domcontentloaded' });

        // 下面的 waitForURL 就是「守卫已处理完」的信号，不需要再固定等 2 秒

        // The route guard should redirect back to login since tokens are cleared
        // Either we stay on login or get redirected back to login
        await page.waitForURL(`**${LOGIN_PATH}**`, { timeout: 10_000 });
        expect(page.url()).toContain(LOGIN_PATH);
      } finally {
        await context.close();
      }
    },
  );
});
