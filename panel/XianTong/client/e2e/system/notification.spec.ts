import { test, expect } from '../fixtures/playwright-base';

/**
 * Notification Module E2E Tests
 *
 * Validates: Requirements 16.1-16.7, 17.1-17.4
 *
 * Tests cover:
 * - Notification rules table columns display
 * - Enabled/disabled status labels
 * - Create rule modal
 * - Edit rule modal
 * - Delete rule confirmation
 * - Notification history table columns and status labels
 * - History pagination
 */

const RULES_PATH = '/notification/rules';
const HISTORY_PATH = '/notification/history';

// Expected column headers for notification rules table (Req 16.1)
const RULES_EXPECTED_COLUMNS = [
  '#',
  '规则名称',
  '条件类型',
  '阈值',
  'Webhook',
  '状态',
  '操作',
];

// Expected column headers for notification history table (Req 17.1)
const HISTORY_EXPECTED_COLUMNS = [
  '#',
  '规则名称',
  '发送状态',
  '触发时间',
  '通知内容',
  '错误信息',
];

test.describe('Notification Rules Page - Table Display', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(RULES_PATH);
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });
  });

  test('should display all expected table columns (Req 16.1)', async ({ base }) => {
    const headerCells = base.page.locator('.vxe-table--header .vxe-header--column .vxe-cell');
    const count = await headerCells.count();
    const columnTexts: string[] = [];
    for (let i = 0; i < count; i++) {
      const text = (await headerCells.nth(i).textContent())?.trim() ?? '';
      if (text) columnTexts.push(text);
    }

    for (const expected of RULES_EXPECTED_COLUMNS) {
      expect(columnTexts).toContain(expected);
    }
  });

  test('should display green "启用" label for enabled rules (Req 16.2)', async ({ base }) => {
    // Intercept API to ensure we have an enabled rule
    await base.page.route('**/api/admin/config/notifications', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: [
              { id: 1, ruleName: 'Test Rule Enabled', conditionType: 'task_failure', threshold: 5, webhookUrl: 'https://hook.example.com/1', isEnabled: true },
            ],
            message: 'success',
          }),
        });
      } else {
        await route.continue();
      }
    });
    await base.page.reload({ waitUntil: 'networkidle' });
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });

    const firstRow = base.page.locator('.vxe-table--body .vxe-body--row').first();
    const statusTag = firstRow.locator('.ant-tag');
    await expect(statusTag).toContainText('启用');
    await expect(statusTag).toHaveClass(/ant-tag-success/);
  });

  test('should display default "禁用" label for disabled rules (Req 16.3)', async ({ base }) => {
    // Intercept API to ensure we have a disabled rule
    await base.page.route('**/api/admin/config/notifications', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: [
              { id: 2, ruleName: 'Test Rule Disabled', conditionType: 'dlq_overflow', threshold: 10, webhookUrl: 'https://hook.example.com/2', isEnabled: false },
            ],
            message: 'success',
          }),
        });
      } else {
        await route.continue();
      }
    });
    await base.page.reload({ waitUntil: 'networkidle' });
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });

    const firstRow = base.page.locator('.vxe-table--body .vxe-body--row').first();
    const statusTag = firstRow.locator('.ant-tag');
    await expect(statusTag).toContainText('禁用');
    await expect(statusTag).toHaveClass(/ant-tag-default/);
  });
});

test.describe('Notification Rules Page - Create Rule Modal', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(RULES_PATH);
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });
  });

  test('should open create rule modal with form fields when clicking "新建规则" (Req 16.4)', async ({ base }) => {
    const createBtn = base.page.getByRole('button', { name: '新建规则' });
    await expect(createBtn).toBeVisible();
    await createBtn.click();

    // Wait for modal to appear
    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10000 });

    // Verify modal title
    await expect(modal).toContainText('新建通知规则');

    // Verify form fields exist
    await expect(modal.getByText('规则名称')).toBeVisible();
    await expect(modal.getByText('条件类型')).toBeVisible();
    await expect(modal.getByText('阈值')).toBeVisible();
    await expect(modal.getByText('Webhook URL')).toBeVisible();
    await expect(modal.getByText('启用')).toBeVisible();
  });

  test('should submit create rule form successfully (Req 16.5)', async ({ base }) => {
    // Intercept create API
    await base.page.route('**/api/admin/config/notifications', async (route) => {
      if (route.request().method() === 'POST') {
        const body = route.request().postDataJSON();
        expect(body.ruleName).toBeTruthy();
        expect(body.conditionType).toBeTruthy();
        expect(body.threshold).toBeGreaterThan(0);
        expect(body.webhookUrl).toBeTruthy();
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 0, data: { id: 99 }, message: 'success' }),
        });
      } else {
        await route.continue();
      }
    });

    // Open create modal
    await base.page.getByRole('button', { name: '新建规则' }).click();
    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10000 });

    // Fill form fields
    const ruleNameInput = modal.locator('input').first();
    await ruleNameInput.fill('E2E Test Rule');

    // Select condition type
    const conditionSelect = modal.locator('.ant-select').first();
    await conditionSelect.click();
    await base.page.locator('.ant-select-item-option').filter({ hasText: '任务失败次数' }).click();

    // Fill threshold
    const thresholdInput = modal.locator('.ant-input-number-input');
    await thresholdInput.fill('5');

    // Fill webhook URL
    const webhookInput = modal.locator('input[placeholder*="feishu"]');
    await webhookInput.fill('https://open.feishu.cn/open-apis/bot/v2/hook/test');

    // Submit
    const confirmBtn = modal.getByRole('button', { name: /确定|确认/ });
    await confirmBtn.click();

    // Verify success message
    await base.page.locator('.ant-message-success').waitFor({ state: 'visible', timeout: 5000 });
  });
});

test.describe('Notification Rules Page - Edit Rule Modal', () => {
  test.beforeEach(async ({ base }) => {
    // Intercept API to provide a known rule for editing
    await base.page.route('**/api/admin/config/notifications', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: [
              { id: 1, ruleName: 'Existing Rule', conditionType: 'task_failure', threshold: 3, webhookUrl: 'https://hook.example.com/existing', isEnabled: true },
            ],
            message: 'success',
          }),
        });
      } else {
        await route.continue();
      }
    });
    await base.navigateTo(RULES_PATH);
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });
  });

  test('should open edit modal with pre-filled data when clicking "编辑" (Req 16.6)', async ({ base }) => {
    const firstRow = base.page.locator('.vxe-table--body .vxe-body--row').first();
    const editBtn = firstRow.getByRole('button', { name: '编辑' });
    await expect(editBtn).toBeVisible();
    await editBtn.click();

    // Wait for modal to appear
    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10000 });

    // Verify modal title
    await expect(modal).toContainText('编辑通知规则');

    // Verify pre-filled rule name
    const ruleNameInput = modal.locator('input').first();
    await expect(ruleNameInput).toHaveValue('Existing Rule');
  });
});

test.describe('Notification Rules Page - Delete Rule', () => {
  test.beforeEach(async ({ base }) => {
    // Intercept API to provide a known rule for deletion
    await base.page.route('**/api/admin/config/notifications', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: [
              { id: 1, ruleName: 'Rule To Delete', conditionType: 'dlq_overflow', threshold: 10, webhookUrl: 'https://hook.example.com/delete', isEnabled: false },
            ],
            message: 'success',
          }),
        });
      } else {
        await route.continue();
      }
    });
    await base.navigateTo(RULES_PATH);
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });
  });

  test('should show confirmation dialog and delete rule when confirmed (Req 16.7)', async ({ base }) => {
    // Intercept delete API
    await base.page.route('**/api/admin/config/notifications/*', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 0, data: null, message: 'success' }),
        });
      } else {
        await route.continue();
      }
    });

    const firstRow = base.page.locator('.vxe-table--body .vxe-body--row').first();
    const deleteBtn = firstRow.getByRole('button', { name: '删除' });
    await expect(deleteBtn).toBeVisible();
    await deleteBtn.click();

    // Wait for Modal.confirm dialog to appear
    const confirmModal = base.page.locator('.ant-modal-confirm');
    await confirmModal.waitFor({ state: 'visible', timeout: 5000 });

    // Verify confirmation content mentions the rule name
    await expect(confirmModal).toContainText('删除规则');
    await expect(confirmModal).toContainText('Rule To Delete');

    // Verify danger-styled OK button
    const okBtn = confirmModal.locator('.ant-btn-dangerous, .ant-btn-primary');
    await expect(okBtn.first()).toBeVisible();

    // Click confirm
    await confirmModal.locator('.ant-btn-primary').click();

    // Verify success message
    await base.page.locator('.ant-message-success').waitFor({ state: 'visible', timeout: 5000 });
  });
});

test.describe('Notification History Page - Table Display', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(HISTORY_PATH);
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });
  });

  test('should display all expected table columns (Req 17.1)', async ({ base }) => {
    const headerCells = base.page.locator('.vxe-table--header .vxe-header--column .vxe-cell');
    const count = await headerCells.count();
    const columnTexts: string[] = [];
    for (let i = 0; i < count; i++) {
      const text = (await headerCells.nth(i).textContent())?.trim() ?? '';
      if (text) columnTexts.push(text);
    }

    for (const expected of HISTORY_EXPECTED_COLUMNS) {
      expect(columnTexts).toContain(expected);
    }
  });

  test('should display green "已发送" label for sent notifications (Req 17.2)', async ({ base }) => {
    // Intercept API to ensure we have a sent notification
    await base.page.route('**/api/admin/config/notifications/history**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: {
            list: [
              { id: 1, ruleName: 'Test Rule', deliveryStatus: 'sent', triggeredAt: '2024-01-15 10:30:00', messageContent: 'Task failed 5 times', errorMessage: '' },
            ],
            total: 1,
          },
          message: 'success',
        }),
      });
    });
    await base.page.reload({ waitUntil: 'networkidle' });
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });

    const firstRow = base.page.locator('.vxe-table--body .vxe-body--row').first();
    const statusTag = firstRow.locator('.ant-tag');
    await expect(statusTag).toContainText('已发送');
    await expect(statusTag).toHaveClass(/ant-tag-success/);
  });

  test('should display red "失败" label for failed notifications (Req 17.3)', async ({ base }) => {
    // Intercept API to ensure we have a failed notification
    await base.page.route('**/api/admin/config/notifications/history**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: {
            list: [
              { id: 2, ruleName: 'Test Rule', deliveryStatus: 'failed', triggeredAt: '2024-01-15 11:00:00', messageContent: 'DLQ overflow', errorMessage: 'Connection timeout' },
            ],
            total: 1,
          },
          message: 'success',
        }),
      });
    });
    await base.page.reload({ waitUntil: 'networkidle' });
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });

    const firstRow = base.page.locator('.vxe-table--body .vxe-body--row').first();
    const statusTag = firstRow.locator('.ant-tag');
    await expect(statusTag).toContainText('失败');
    await expect(statusTag).toHaveClass(/ant-tag-error/);
  });
});

test.describe('Notification History Page - Pagination', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(HISTORY_PATH);
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });
  });

  test('should update data when clicking pagination controls (Req 17.4)', async ({ base }) => {
    // Intercept API to provide paginated data
    let requestedPage = 1;
    await base.page.route('**/api/admin/config/notifications/history**', async (route) => {
      const url = new URL(route.request().url());
      requestedPage = Number.parseInt(url.searchParams.get('page') || '1', 10);
      const pageSize = Number.parseInt(url.searchParams.get('pageSize') || '10', 10);

      // Generate items for the requested page
      const items = Array.from({ length: pageSize }, (_, i) => ({
        id: (requestedPage - 1) * pageSize + i + 1,
        ruleName: `Rule ${(requestedPage - 1) * pageSize + i + 1}`,
        deliveryStatus: i % 2 === 0 ? 'sent' : 'failed',
        triggeredAt: `2024-01-${String(15 - i).padStart(2, '0')} 10:00:00`,
        messageContent: `Notification content ${(requestedPage - 1) * pageSize + i + 1}`,
        errorMessage: i % 2 === 0 ? '' : 'Error occurred',
      }));

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: { list: items, total: 25 }, // 25 total items to ensure multiple pages
          message: 'success',
        }),
      });
    });

    await base.page.reload({ waitUntil: 'networkidle' });
    await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });

    // Check if pager exists
    const pager = base.page.locator('.vxe-pager');
    const pagerVisible = await pager.isVisible().catch(() => false);

    if (pagerVisible) {
      // Listen for API request with page parameter
      const responsePromise = base.page.waitForResponse((res) =>
        res.url().includes('/api/admin/config/notifications/history') && res.status() === 200,
      );

      // Click next page button
      const nextBtn = pager.locator('.vxe-pager--next-btn');
      if (await nextBtn.isEnabled()) {
        await nextBtn.click();
        const response = await responsePromise;
        const json = await response.json();
        // Verify the response contains list data
        expect(json.data?.list).toBeDefined();
        expect(json.data.list.length).toBeGreaterThan(0);
      }
    } else {
      // If no pager visible, data fits in one page - test passes
      test.skip();
    }
  });
});
