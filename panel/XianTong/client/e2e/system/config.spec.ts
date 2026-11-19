import { expectTableColumns } from '../fixtures/grid';
import { expect, test } from '../fixtures/playwright-base';

/**
 * System Config E2E Tests
 *
 * Validates: Requirements 15.1-15.9
 *
 * Tests cover:
 * - Table columns display (分类, 配置项, 描述, 值, 类型, 热更新, 操作)
 * - Sensitive config masking in view mode
 * - Hot reload label display (green "热更新" vs default "需重启")
 * - Edit controls by type (Input for string, InputNumber for number, Switch for boolean)
 * - Save config value
 * - Cancel edit
 *
 * The system config page uses Ant Design Vue Table with inline editing.
 * Route: /system/config
 */

const CONFIG_PATH = '/system/config';

// Expected column headers per Requirement 15.1
const EXPECTED_COLUMNS = [
  '分类',
  '配置项',
  '描述',
  '值',
  '类型',
  '热更新',
  '操作',
];

// Mock config data for controlled testing
const MOCK_CONFIG_RESPONSE = {
  code: 0,
  data: [
    {
      category: '基础配置',
      items: [
        {
          id: 1,
          configKey: 'app.name',
          configValue: 'Nova BI',
          configType: 'string',
          category: '基础配置',
          description: '应用名称',
          isSensitive: 0,
          reloadType: 'hot',
          minValue: null,
          maxValue: null,
        },
        {
          id: 2,
          configKey: 'app.secret',
          configValue: 'mysecretkey1234',
          configType: 'string',
          category: '基础配置',
          description: 'API密钥',
          isSensitive: 1,
          reloadType: 'restart',
          minValue: null,
          maxValue: null,
        },
        {
          id: 3,
          configKey: 'app.maxRetries',
          configValue: '5',
          configType: 'int',
          category: '基础配置',
          description: '最大重试次数',
          isSensitive: 0,
          reloadType: 'hot',
          minValue: 1,
          maxValue: 10,
        },
        {
          id: 4,
          configKey: 'app.debug',
          configValue: 'true',
          configType: 'bool',
          category: '基础配置',
          description: '调试模式',
          isSensitive: 0,
          reloadType: 'restart',
          minValue: null,
          maxValue: null,
        },
      ],
    },
  ],
  message: 'success',
};

/**
 * Helper to find a table row by config key text.
 * The config key is in the 2nd column (index 1).
 */
function getRowByKey(page: any, key: string) {
  return page.locator('.ant-table-tbody .ant-table-row').filter({
    has: page.locator('td:nth-child(2)', { hasText: key }),
  });
}

test.describe('System Config Page - Table Display', () => {
  test.beforeEach(async ({ base }) => {
    // Intercept config API to provide controlled test data
    await base.page.route('**/api/admin/config', async (route) => {
      await (route.request().method() === 'GET'
        ? route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(MOCK_CONFIG_RESPONSE),
          })
        : route.continue());
    });
    await base.navigateTo(CONFIG_PATH);
    await base.page
      .locator('.ant-table-tbody')
      .waitFor({ state: 'visible', timeout: 15_000 });
  });

  test('should display all expected table columns (Req 15.1)', async ({
    base,
  }) => {
    await expectTableColumns(base.page, EXPECTED_COLUMNS);
  });

  test('should mask sensitive config values in view mode (Req 15.2)', async ({
    base,
  }) => {
    // The sensitive config "app.secret" with value "mysecretkey1234" should be masked
    // maskSensitive shows "••••••••" + last 4 chars
    const row = getRowByKey(base.page, 'app.secret');
    await expect(row).toBeVisible();

    // Value is in the 4th column (index 3)
    const valueCell = row.locator('td').nth(3);
    const valueText = await valueCell.textContent();

    // Should show masked value containing dots and last 4 chars "1234"
    expect(valueText?.trim()).toContain('••••');
    expect(valueText?.trim()).toContain('1234');
    // Should NOT show the full plaintext value
    expect(valueText?.trim()).not.toBe('mysecretkey1234');
  });

  test('should display green "热更新" tag for reloadType=hot (Req 15.3)', async ({
    base,
  }) => {
    // app.name has reloadType=hot
    const row = getRowByKey(base.page, 'app.name');
    await expect(row).toBeVisible();

    const tag = row.locator('.ant-tag');
    await expect(tag).toBeVisible();
    await expect(tag).toContainText('热更新');
    // Verify green color class
    await expect(tag).toHaveClass(/green/);
  });

  test('should display default "需重启" tag for reloadType != hot (Req 15.4)', async ({
    base,
  }) => {
    // app.secret has reloadType=restart
    const row = getRowByKey(base.page, 'app.secret');
    await expect(row).toBeVisible();

    const tag = row.locator('.ant-tag');
    await expect(tag).toBeVisible();
    await expect(tag).toContainText('需重启');
    // Should NOT have green color class
    const tagClass = await tag.getAttribute('class');
    expect(tagClass).not.toContain('green');
  });
});

test.describe('System Config Page - Edit Controls by Type', () => {
  test.beforeEach(async ({ base }) => {
    await base.page.route('**/api/admin/config', async (route) => {
      await (route.request().method() === 'GET'
        ? route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(MOCK_CONFIG_RESPONSE),
          })
        : route.continue());
    });
    await base.navigateTo(CONFIG_PATH);
    await base.page
      .locator('.ant-table-tbody')
      .waitFor({ state: 'visible', timeout: 15_000 });
  });

  test('should show Input control for string type config (Req 15.5)', async ({
    base,
  }) => {
    // app.name is string type
    const row = getRowByKey(base.page, 'app.name');
    await expect(row).toBeVisible();

    // Click the edit button in the action column
    const editBtn = row.getByRole('button', { name: '编辑' });
    await editBtn.click();

    // Verify Input control appears in the value cell (4th column)
    const valueCell = row.locator('td').nth(3);
    const input = valueCell.locator('.ant-input');
    await expect(input).toBeVisible();
  });

  test('should show InputNumber control for number type config (Req 15.5)', async ({
    base,
  }) => {
    // app.maxRetries is int type → number
    const row = getRowByKey(base.page, 'app.maxRetries');
    await expect(row).toBeVisible();

    // Click the edit button
    const editBtn = row.getByRole('button', { name: '编辑' });
    await editBtn.click();

    // Verify InputNumber control appears in the value cell
    const valueCell = row.locator('td').nth(3);
    const inputNumber = valueCell.locator('.ant-input-number');
    await expect(inputNumber).toBeVisible();
  });

  test('should show Switch control for boolean type config (Req 15.5)', async ({
    base,
  }) => {
    // app.debug is bool type → boolean
    const row = getRowByKey(base.page, 'app.debug');
    await expect(row).toBeVisible();

    // Click the edit button
    const editBtn = row.getByRole('button', { name: '编辑' });
    await editBtn.click();

    // Verify Switch control appears in the value cell
    const valueCell = row.locator('td').nth(3);
    const switchCtrl = valueCell.locator('.ant-switch');
    await expect(switchCtrl).toBeVisible();
  });
});

test.describe('System Config Page - Save and Cancel', () => {
  test.beforeEach(async ({ base }) => {
    await base.page.route('**/api/admin/config', async (route) => {
      await (route.request().method() === 'GET'
        ? route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(MOCK_CONFIG_RESPONSE),
          })
        : route.continue());
    });
    await base.navigateTo(CONFIG_PATH);
    await base.page
      .locator('.ant-table-tbody')
      .waitFor({ state: 'visible', timeout: 15_000 });
  });

  test('should save config value and show success message (Req 15.6)', async ({
    base,
  }) => {
    // Intercept the PUT request for config update
    let capturedBody: any = null;
    await base.page.route('**/api/admin/config/*', async (route) => {
      if (route.request().method() === 'PUT') {
        capturedBody = route.request().postDataJSON();
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 0, data: null, message: 'success' }),
        });
      } else {
        await route.continue();
      }
    });

    // Find the "app.name" row and click edit
    const row = getRowByKey(base.page, 'app.name');
    await expect(row).toBeVisible();

    const editBtn = row.getByRole('button', { name: '编辑' });
    await editBtn.click();

    // Modify the value
    const valueCell = row.locator('td').nth(3);
    const input = valueCell.locator('.ant-input');
    await input.clear();
    await input.fill('Nova BI Updated');

    // Click save
    const saveBtn = row.getByRole('button', { name: '保存' });
    await saveBtn.click();

    // Verify success message appears
    await base.page
      .locator('.ant-message-success')
      .waitFor({ state: 'visible', timeout: 5000 });

    // Verify the PUT request was sent with the correct value
    expect(capturedBody).toHaveProperty('value', 'Nova BI Updated');
  });

  test('should cancel edit without saving changes (Req 15.9)', async ({
    base,
  }) => {
    // Find the "app.name" row and click edit
    const row = getRowByKey(base.page, 'app.name');
    await expect(row).toBeVisible();

    const editBtn = row.getByRole('button', { name: '编辑' });
    await editBtn.click();

    // Modify the value
    const valueCell = row.locator('td').nth(3);
    const input = valueCell.locator('.ant-input');
    await input.clear();
    await input.fill('Should Not Be Saved');

    // Click cancel
    const cancelBtn = row.getByRole('button', { name: '取消' });
    await cancelBtn.click();

    // Verify edit mode is exited (no input visible)
    await expect(valueCell.locator('.ant-input')).toHaveCount(0);

    // Verify the displayed value is the original (not the modified one)
    const displayedValue = await valueCell.textContent();
    expect(displayedValue?.trim()).toBe('Nova BI');
    expect(displayedValue?.trim()).not.toBe('Should Not Be Saved');
  });
});
