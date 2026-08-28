import { test, expect } from '../fixtures/playwright-base';
import { Selectors, API_BASE_URL } from '../fixtures/playwright-base';

/**
 * UI State E2E Tests
 *
 * Validates: Requirements 22.1-22.7
 *
 * Tests cover:
 * - Loading spinner during data load (22.1)
 * - Empty state when table has no data (22.2)
 * - Error message on API failure (22.3)
 * - Success message on API success (22.4)
 * - Game detail load failure message (22.5)
 * - Save button loading state (22.6)
 * - Feishu test button loading state (22.7)
 *
 * Uses route interception to simulate loading delays, empty responses, and API failures.
 */

const GAME_LIST_PATH = '/game/list';

test.describe('UI State - Loading Spinner (Req 22.1)', () => {
  test('should display Spin component while game detail data is loading', async ({ base }) => {
    // Intercept the game detail API with a delay to observe the loading spinner
    await base.page.route('**/api/admin/games/*', async (route) => {
      // Only delay GET requests for game detail (not status endpoint)
      if (route.request().method() === 'GET' && !route.request().url().includes('/status')) {
        // Add a 2-second delay to ensure spinner is visible
        await new Promise((resolve) => setTimeout(resolve, 2000));
        await route.continue();
      } else {
        await route.continue();
      }
    });

    // Navigate to a game detail page - first get a valid game code
    const loginResp = await base.page.request.post(`${API_BASE_URL}/api/admin/auth/login`, {
      data: { username: 'admin', password: 'admin123' },
    });
    const loginBody = await loginResp.json();
    const token = loginBody.data.accessToken;

    const gamesResp = await base.page.request.get(`${API_BASE_URL}/api/admin/games?page=1&pageSize=1`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const gamesBody = await gamesResp.json();
    const gameCode = gamesBody.data.list[0]?.code;

    if (!gameCode) {
      test.skip();
      return;
    }

    // Navigate to game detail - the spinner should appear during loading
    await base.page.goto(`http://localhost:5666/game/detail/${gameCode}`, {
      waitUntil: 'domcontentloaded',
    });

    // Verify the Spin component is visible during loading
    const spinner = base.page.locator(Selectors.spinSpinning);
    await expect(spinner).toBeVisible({ timeout: 5000 });
  });

  test('should display loading state in game list table during data load', async ({ base }) => {
    // Intercept the game list API with a delay
    await base.page.route('**/api/admin/games**', async (route) => {
      if (route.request().method() === 'GET') {
        await new Promise((resolve) => setTimeout(resolve, 2000));
        await route.continue();
      } else {
        await route.continue();
      }
    });

    // Navigate to game list - the vxe-grid has its own loading indicator
    await base.page.goto(`http://localhost:5666${GAME_LIST_PATH}`, {
      waitUntil: 'domcontentloaded',
    });

    // vxe-grid shows a loading indicator (.vxe-table--loading)
    const vxeLoading = base.page.locator('.vxe-loading');
    await expect(vxeLoading).toBeVisible({ timeout: 5000 });
  });
});

test.describe('UI State - Empty State (Req 22.2)', () => {
  test('should display empty state message when table has no data', async ({ base }) => {
    // Intercept the game list API to return empty data
    await base.page.route('**/api/admin/games**', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: { list: [], total: 0 },
            message: 'success',
          }),
        });
      } else {
        await route.continue();
      }
    });

    await base.navigateTo(GAME_LIST_PATH);

    // Wait for the table to render with empty state
    // vxe-grid shows empty placeholder when no data
    const emptyPlaceholder = base.page.locator('.vxe-table--empty-placeholder');
    await expect(emptyPlaceholder).toBeVisible({ timeout: 10000 });
  });
});

test.describe('UI State - Error Message (Req 22.3)', () => {
  test('should display error message when API request fails', async ({ base }) => {
    // First navigate to game detail page normally to set up the page
    const loginResp = await base.page.request.post(`${API_BASE_URL}/api/admin/auth/login`, {
      data: { username: 'admin', password: 'admin123' },
    });
    const loginBody = await loginResp.json();
    const token = loginBody.data.accessToken;

    const gamesResp = await base.page.request.get(`${API_BASE_URL}/api/admin/games?page=1&pageSize=1`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const gamesBody = await gamesResp.json();
    const gameCode = gamesBody.data.list[0]?.code;

    if (!gameCode) {
      test.skip();
      return;
    }

    // Navigate to game detail page first (let it load normally)
    await base.navigateTo(`/game/detail/${gameCode}`);
    await base.page.locator(Selectors.tabs).waitFor({ state: 'visible', timeout: 15000 });

    // Now intercept the feishu test API to simulate failure
    await base.page.route(`**/api/admin/games/${gameCode}/feishu/test`, async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          code: -1,
          data: null,
          message: '连接失败',
        }),
      });
    });

    // Click the feishu tab
    const tabNav = base.page.locator(Selectors.tabNav);
    const feishuTab = tabNav.locator('.ant-tabs-tab').filter({ hasText: '飞书' });
    await feishuTab.click();
    await base.page.waitForTimeout(500);

    // Click the "测试连接" button to trigger the error
    const activePane = base.page.locator('.ant-tabs-tabpane-active');
    const testBtn = activePane.locator('button').filter({ hasText: /测试连接/ });
    await testBtn.click();

    // Verify error message appears
    const errorMessage = base.page.locator(Selectors.messageError);
    await expect(errorMessage).toBeVisible({ timeout: 10000 });
  });
});

test.describe('UI State - Success Message (Req 22.4)', () => {
  test('should display success message when API request succeeds', async ({ base }) => {
    const loginResp = await base.page.request.post(`${API_BASE_URL}/api/admin/auth/login`, {
      data: { username: 'admin', password: 'admin123' },
    });
    const loginBody = await loginResp.json();
    const token = loginBody.data.accessToken;

    const gamesResp = await base.page.request.get(`${API_BASE_URL}/api/admin/games?page=1&pageSize=1`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const gamesBody = await gamesResp.json();
    const gameCode = gamesBody.data.list[0]?.code;

    if (!gameCode) {
      test.skip();
      return;
    }

    // Navigate to game detail page
    await base.navigateTo(`/game/detail/${gameCode}`);
    await base.page.locator(Selectors.tabs).waitFor({ state: 'visible', timeout: 15000 });

    // Intercept the save API to return success
    await base.page.route(`**/api/admin/games/${gameCode}`, async (route) => {
      if (route.request().method() === 'PUT') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: null,
            message: 'success',
          }),
        });
      } else {
        await route.continue();
      }
    });

    // Enter edit mode on basic tab
    const activePane = base.page.locator('.ant-tabs-tabpane-active');
    const editButton = activePane.locator('button').filter({ hasText: /编\s*辑/ });
    await editButton.click();

    // Wait for form to appear
    await activePane.locator('.ant-input').first().waitFor({ state: 'visible' });

    // Click save
    const saveButton = activePane.locator('button').filter({ hasText: /保\s*存/ });
    await saveButton.click();

    // Verify success message appears
    const successMessage = base.page.locator(Selectors.messageSuccess);
    await expect(successMessage).toBeVisible({ timeout: 10000 });
  });
});

test.describe('UI State - Game Detail Load Failure (Req 22.5)', () => {
  test('should display "游戏数据加载失败，请返回列表重试" when game detail API fails', async ({ base }) => {
    // Intercept the game detail API to simulate failure
    await base.page.route('**/api/admin/games/NONEXISTENT_CODE', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 404,
          contentType: 'application/json',
          body: JSON.stringify({
            code: -1,
            data: null,
            message: '游戏不存在',
          }),
        });
      } else {
        await route.continue();
      }
    });

    // Also intercept the status endpoint
    await base.page.route('**/api/admin/games/NONEXISTENT_CODE/status', async (route) => {
      await route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({
          code: -1,
          data: null,
          message: '游戏不存在',
        }),
      });
    });

    // Navigate to a non-existent game detail page
    await base.page.goto('http://localhost:5666/game/detail/NONEXISTENT_CODE', {
      waitUntil: 'networkidle',
    });

    // Wait for loading to finish and error state to appear
    // The component shows "游戏数据加载失败，请返回列表重试" when game is null and not loading
    const errorText = base.page.getByText('游戏数据加载失败，请返回列表重试');
    await expect(errorText).toBeVisible({ timeout: 15000 });
  });
});

test.describe('UI State - Save Button Loading (Req 22.6)', () => {
  test('should show loading state on save button during save operation', async ({ base }) => {
    const loginResp = await base.page.request.post(`${API_BASE_URL}/api/admin/auth/login`, {
      data: { username: 'admin', password: 'admin123' },
    });
    const loginBody = await loginResp.json();
    const token = loginBody.data.accessToken;

    const gamesResp = await base.page.request.get(`${API_BASE_URL}/api/admin/games?page=1&pageSize=1`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const gamesBody = await gamesResp.json();
    const gameCode = gamesBody.data.list[0]?.code;

    if (!gameCode) {
      test.skip();
      return;
    }

    // Navigate to game detail page
    await base.navigateTo(`/game/detail/${gameCode}`);
    await base.page.locator(Selectors.tabs).waitFor({ state: 'visible', timeout: 15000 });

    // Intercept the save API with a delay to observe loading state
    await base.page.route(`**/api/admin/games/${gameCode}`, async (route) => {
      if (route.request().method() === 'PUT') {
        // Add delay so we can observe the loading state
        await new Promise((resolve) => setTimeout(resolve, 2000));
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: null,
            message: 'success',
          }),
        });
      } else {
        await route.continue();
      }
    });

    // Enter edit mode on basic tab
    const activePane = base.page.locator('.ant-tabs-tabpane-active');
    const editButton = activePane.locator('button').filter({ hasText: /编\s*辑/ });
    await editButton.click();

    // Wait for form to appear
    await activePane.locator('.ant-input').first().waitFor({ state: 'visible' });

    // Click save - the button should enter loading state
    const saveButton = activePane.locator('button').filter({ hasText: /保\s*存/ });
    await saveButton.click();

    // Verify the save button has loading state (ant-btn-loading class)
    await expect(saveButton).toHaveClass(/ant-btn-loading/, { timeout: 3000 });
  });
});

test.describe('UI State - Feishu Test Button Loading (Req 22.7)', () => {
  test('should show loading state on feishu test button during connection test', async ({ base }) => {
    const loginResp = await base.page.request.post(`${API_BASE_URL}/api/admin/auth/login`, {
      data: { username: 'admin', password: 'admin123' },
    });
    const loginBody = await loginResp.json();
    const token = loginBody.data.accessToken;

    const gamesResp = await base.page.request.get(`${API_BASE_URL}/api/admin/games?page=1&pageSize=1`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const gamesBody = await gamesResp.json();
    const gameCode = gamesBody.data.list[0]?.code;

    if (!gameCode) {
      test.skip();
      return;
    }

    // Navigate to game detail page
    await base.navigateTo(`/game/detail/${gameCode}`);
    await base.page.locator(Selectors.tabs).waitFor({ state: 'visible', timeout: 15000 });

    // Intercept the feishu test API with a delay to observe loading state
    await base.page.route(`**/api/admin/games/${gameCode}/feishu/test`, async (route) => {
      // Add delay so we can observe the loading state
      await new Promise((resolve) => setTimeout(resolve, 2000));
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: null,
          message: 'success',
        }),
      });
    });

    // Click the feishu tab
    const tabNav = base.page.locator(Selectors.tabNav);
    const feishuTab = tabNav.locator('.ant-tabs-tab').filter({ hasText: '飞书' });
    await feishuTab.click();
    await base.page.waitForTimeout(500);

    // Click the "测试连接" button
    const activePane = base.page.locator('.ant-tabs-tabpane-active');
    const testBtn = activePane.locator('button').filter({ hasText: /测试连接/ });
    await testBtn.click();

    // Verify the test button has loading state (ant-btn-loading class)
    await expect(testBtn).toHaveClass(/ant-btn-loading/, { timeout: 3000 });
  });
});
