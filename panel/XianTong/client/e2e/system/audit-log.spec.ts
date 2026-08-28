import { test, expect } from '../fixtures/playwright-base';

/**
 * Audit Log E2E Tests
 *
 * Validates: Requirements 14.1-14.10
 *
 * Tests cover:
 * - Table columns display (行号, 操作人, 操作类型, 资源, 目标ID, 响应码, IP, 时间, 操作)
 * - Operation type labels: red for "delete", green for "create", blue for others
 * - Filter by username, operation type, target resource
 * - Detail modal: click "详情", verify requestParams and userAgent displayed
 * - Pagination
 */

const AUDIT_LOG_PATH = '/system/audit';

// Expected column headers per Requirement 14.1
const EXPECTED_COLUMNS = [
  '#',
  '操作人',
  '操作类型',
  '资源',
  '目标 ID',
  '响应码',
  'IP',
  '时间',
  '操作',
];

test.describe('Audit Log Page - Table Display', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(AUDIT_LOG_PATH);
    // Wait for the vxe-table to load
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });
  });

  test('should display all expected table columns (Req 14.1)', async ({ base }) => {
    // vxe-table renders column headers in .vxe-header--column elements
    const headerCells = base.page.locator('.vxe-table--header .vxe-header--column .vxe-cell');
    const count = await headerCells.count();
    const columnTexts: string[] = [];
    for (let i = 0; i < count; i++) {
      const text = (await headerCells.nth(i).textContent())?.trim() ?? '';
      if (text) columnTexts.push(text);
    }

    for (const expected of EXPECTED_COLUMNS) {
      expect(columnTexts).toContain(expected);
    }
  });

  test('should display red tag for "delete" operation type (Req 14.2)', async ({ base }) => {
    // Intercept the API to inject a row with operationType = 'delete'
    await base.page.route('**/api/admin/audit/logs**', async (route) => {
      const response = await route.fetch();
      const json = await response.json();
      if (json.data?.list?.length > 0) {
        json.data.list[0].operationType = 'delete';
      } else {
        json.data = {
          list: [{ id: 1, username: 'admin', operationType: 'delete', targetResource: 'game', targetId: '1', responseCode: 200, ipAddress: '127.0.0.1', createdAt: '2024-01-01 12:00:00' }],
          total: 1,
        };
      }
      await route.fulfill({ json });
    });
    await base.page.reload({ waitUntil: 'networkidle' });
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });

    const firstRow = base.page.locator('.vxe-table--body .vxe-body--row').first();
    // operationType is 3rd column (index 2)
    const opTypeCell = firstRow.locator('.vxe-body--column').nth(2);
    const tag = opTypeCell.locator('.ant-tag');
    await expect(tag).toBeVisible();
    await expect(tag).toContainText('delete');
    // Verify red/error color
    await expect(tag).toHaveClass(/ant-tag-error/);
  });

  test('should display green tag for "create" operation type (Req 14.3)', async ({ base }) => {
    await base.page.route('**/api/admin/audit/logs**', async (route) => {
      const response = await route.fetch();
      const json = await response.json();
      if (json.data?.list?.length > 0) {
        json.data.list[0].operationType = 'create';
      } else {
        json.data = {
          list: [{ id: 1, username: 'admin', operationType: 'create', targetResource: 'user', targetId: '2', responseCode: 200, ipAddress: '127.0.0.1', createdAt: '2024-01-01 12:00:00' }],
          total: 1,
        };
      }
      await route.fulfill({ json });
    });
    await base.page.reload({ waitUntil: 'networkidle' });
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });

    const firstRow = base.page.locator('.vxe-table--body .vxe-body--row').first();
    const opTypeCell = firstRow.locator('.vxe-body--column').nth(2);
    const tag = opTypeCell.locator('.ant-tag');
    await expect(tag).toBeVisible();
    await expect(tag).toContainText('create');
    // Verify green/success color
    await expect(tag).toHaveClass(/ant-tag-success/);
  });

  test('should display blue tag for other operation types (Req 14.4)', async ({ base }) => {
    await base.page.route('**/api/admin/audit/logs**', async (route) => {
      const response = await route.fetch();
      const json = await response.json();
      if (json.data?.list?.length > 0) {
        json.data.list[0].operationType = 'update';
      } else {
        json.data = {
          list: [{ id: 1, username: 'admin', operationType: 'update', targetResource: 'config', targetId: '3', responseCode: 200, ipAddress: '127.0.0.1', createdAt: '2024-01-01 12:00:00' }],
          total: 1,
        };
      }
      await route.fulfill({ json });
    });
    await base.page.reload({ waitUntil: 'networkidle' });
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });

    const firstRow = base.page.locator('.vxe-table--body .vxe-body--row').first();
    const opTypeCell = firstRow.locator('.vxe-body--column').nth(2);
    const tag = opTypeCell.locator('.ant-tag');
    await expect(tag).toBeVisible();
    await expect(tag).toContainText('update');
    // Verify blue color
    await expect(tag).toHaveClass(/ant-tag-blue/);
  });
});

test.describe('Audit Log Page - Filters', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(AUDIT_LOG_PATH);
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });
  });

  test('should filter by username (Req 14.5)', async ({ base }) => {
    // Find the username filter input
    const usernameInput = base.page.locator('input[placeholder="用户名"]');
    await expect(usernameInput).toBeVisible();

    // Listen for the API request to verify the username filter is sent
    const requestPromise = base.page.waitForRequest((req) =>
      req.url().includes('/api/admin/audit/logs') && req.url().includes('username'),
    );

    await usernameInput.fill('admin');

    // The form has submitOnChange: true, so it should auto-submit
    const request = await requestPromise;
    expect(request.url()).toContain('username=admin');

    // Wait for table to update
    await base.page.waitForResponse((res) =>
      res.url().includes('/api/admin/audit/logs') && res.status() === 200,
    );
  });

  test('should filter by operation type (Req 14.6)', async ({ base }) => {
    // Find the operation type select
    const selectTrigger = base.page.locator('.ant-select').filter({ hasText: '全部' }).first();

    // If not found by text, try by the form field
    const operationTypeSelect = selectTrigger.isVisible()
      ? selectTrigger
      : base.page.locator('.ant-form-item').filter({ hasText: '操作类型' }).locator('.ant-select');

    await operationTypeSelect.click();

    // Wait for dropdown to appear and select "创建"
    const dropdown = base.page.locator('.ant-select-dropdown').last();
    await dropdown.waitFor({ state: 'visible', timeout: 5000 });

    // Listen for the API request with operationType filter
    const requestPromise = base.page.waitForRequest((req) =>
      req.url().includes('/api/admin/audit/logs') && req.url().includes('operationType'),
    );

    await dropdown.locator('.ant-select-item').filter({ hasText: '创建' }).click();

    const request = await requestPromise;
    expect(request.url()).toContain('operationType=create');
  });

  test('should filter by target resource (Req 14.7)', async ({ base }) => {
    // Find the target resource filter input
    const resourceInput = base.page.locator('input[placeholder="目标资源"]');
    await expect(resourceInput).toBeVisible();

    // Listen for the API request to verify the targetResource filter is sent
    const requestPromise = base.page.waitForRequest((req) =>
      req.url().includes('/api/admin/audit/logs') && req.url().includes('targetResource'),
    );

    await resourceInput.fill('game');

    // The form has submitOnChange: true, so it should auto-submit
    const request = await requestPromise;
    expect(request.url()).toContain('targetResource=game');

    // Wait for table to update
    await base.page.waitForResponse((res) =>
      res.url().includes('/api/admin/audit/logs') && res.status() === 200,
    );
  });
});

test.describe('Audit Log Page - Detail Modal', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(AUDIT_LOG_PATH);
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });
  });

  test('should open detail modal with requestParams and userAgent when clicking "详情" (Req 14.8)', async ({ base }) => {
    // Intercept the detail API to ensure we have known data
    await base.page.route('**/api/admin/audit/logs/*', async (route) => {
      // Only intercept GET requests for detail (not the list endpoint)
      const url = route.request().url();
      if (route.request().method() === 'GET' && /\/audit\/logs\/\d+$/.test(url)) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: {
              id: 1,
              username: 'admin',
              operationType: 'create',
              targetResource: 'game',
              targetId: '100',
              responseCode: 200,
              ipAddress: '192.168.1.1',
              userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)',
              requestParams: '{"code":"TEST_GAME","name":"Test Game"}',
              createdAt: '2024-06-15 10:30:00',
            },
            message: 'success',
          }),
        });
      } else {
        await route.continue();
      }
    });

    // Click the "详情" button on the first row
    const firstRow = base.page.locator('.vxe-table--body .vxe-body--row').first();
    const detailBtn = firstRow.getByRole('button', { name: '详情' });
    await expect(detailBtn).toBeVisible();
    await detailBtn.click();

    // Wait for the modal to appear
    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10000 });

    // Verify modal title
    await expect(modal).toContainText('审计日志详情');

    // Verify requestParams is displayed
    await expect(modal).toContainText('请求参数');
    await expect(modal).toContainText('TEST_GAME');

    // Verify userAgent is displayed
    await expect(modal).toContainText('User-Agent');
    await expect(modal).toContainText('Mozilla/5.0');
  });
});

test.describe('Audit Log Page - Pagination', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(AUDIT_LOG_PATH);
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });
  });

  test('should update data when clicking pagination controls (Req 14.9)', async ({ base }) => {
    // Check if pagination exists (requires more than one page of data)
    const pager = base.page.locator('.vxe-pager');
    const pagerVisible = await pager.isVisible().catch(() => false);

    if (pagerVisible) {
      // Listen for API request with page parameter
      const responsePromise = base.page.waitForResponse((res) =>
        res.url().includes('/api/admin/audit/logs') && res.status() === 200,
      );

      // Click next page button
      const nextBtn = pager.locator('.vxe-pager--next-btn');
      if (await nextBtn.isEnabled()) {
        await nextBtn.click();
        const response = await responsePromise;
        const json = await response.json();
        // Verify the response contains list data
        expect(json.data?.list).toBeDefined();
      }
    } else {
      // If no pager visible, data fits in one page - test passes
      test.skip();
    }
  });

  test('should display paginated audit logs in descending time order (Req 14.9, 14.10)', async ({ base }) => {
    // Intercept the API to verify the response structure
    const responsePromise = base.page.waitForResponse((res) =>
      res.url().includes('/api/admin/audit/logs') && res.status() === 200,
    );

    // Trigger a fresh data load
    await base.page.reload({ waitUntil: 'networkidle' });
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });

    const response = await responsePromise;
    const json = await response.json();

    // Verify response structure has list and total
    expect(json.data).toBeDefined();
    expect(json.data.list).toBeDefined();
    expect(Array.isArray(json.data.list)).toBe(true);
    expect(typeof json.data.total).toBe('number');

    // If there are multiple records, verify they are in descending time order
    const list = json.data.list;
    if (list.length >= 2) {
      for (let i = 0; i < list.length - 1; i++) {
        const currentTime = new Date(list[i].createdAt).getTime();
        const nextTime = new Date(list[i + 1].createdAt).getTime();
        expect(currentTime).toBeGreaterThanOrEqual(nextTime);
      }
    }
  });
});
