import {
  collapseSelect,
  dialog,
  dialogConfirmButton,
} from '../fixtures/dialog';
import {
  expectTableColumns,
  gridRows,
  waitGridReady,
  waitGridRows,
} from '../fixtures/grid';
import { expect, test } from '../fixtures/playwright-base';

/**
 * User Management E2E Tests
 *
 * Validates: Requirements 7.1-7.14
 *
 * Tests cover:
 * - Table columns display (用户名, 显示名, 角色, 状态, 最后登录, 创建时间, 操作)
 * - Create user modal (username, password, displayName, email, roles)
 * - Edit user modal (pre-filled data, modify and submit)
 * - Status switch (confirmation dialog, status change)
 * - Password reset (confirmation with "123456" hint)
 * - Delete user (irreversible warning dialog)
 * - Search filtering by username or displayName
 * - Role tags display (blue Tag for each role)
 */

const USER_MANAGEMENT_PATH = '/system/user';

// Expected column headers per Requirement 7.1
const EXPECTED_COLUMNS = [
  '用户名',
  '显示名',
  '角色',
  '状态',
  '最后登录',
  '创建时间',
  '操作',
];

test.describe('User Management Page - Table Display', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(USER_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should display all expected table columns (Req 7.1)', async ({
    base,
  }) => {
    await expectTableColumns(base.page, EXPECTED_COLUMNS);
  });

  test('should display role tags as blue Tags for each role (Req 7.14)', async ({
    base,
  }) => {
    // Find the first row that has roles
    // 行定位一律用 `gridRows()`：操作列是 `fixed: 'right'`，vxe 会额外渲染一张
    // 只含操作列的冻结表（实测主表 6 列、冻结表 1 列），`.vxe-table--body
    // .vxe-body--row` 会把两张表的行都算上，行数翻倍。
    const rows = gridRows(base.page);
    // `count()` 不会自动等待，而 waitGridReady 只保证表头已渲染
    await waitGridRows(base.page);
    const rowCount = await rows.count();

    let foundRoleTags = false;
    for (let i = 0; i < Math.min(rowCount, 5); i++) {
      const row = rows.nth(i);
      const roleTags = row.locator('.ant-tag');
      const tagCount = await roleTags.count();
      if (tagCount > 0) {
        foundRoleTags = true;
        // Verify each tag has blue color class
        for (let j = 0; j < tagCount; j++) {
          const tag = roleTags.nth(j);
          await expect(tag).toHaveClass(/ant-tag-blue/);
          // Verify tag has text content (role name)
          const tagText = await tag.textContent();
          expect(tagText?.trim()).toBeTruthy();
        }
        break;
      }
    }

    // At least one user should have roles assigned
    expect(foundRoleTags).toBe(true);
  });

  test('should display status as Switch component (Req 7.7)', async ({
    base,
  }) => {
    const firstRow = gridRows(base.page).first();
    const switchEl = firstRow.locator('.ant-switch');
    await expect(switchEl).toBeVisible();
  });
});

test.describe('User Management Page - Create User Modal', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(USER_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should open create user modal with form fields when clicking "新建用户" (Req 7.2)', async ({
    base,
  }) => {
    // Click the "新建用户" button
    const createBtn = base.page.getByRole('button', { name: '新建用户' });
    await expect(createBtn).toBeVisible();
    await createBtn.click();

    // Wait for modal to appear (Vben modal uses [role="dialog"])
    const modal = dialog(base.page);
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

    // Verify modal title
    await expect(modal).toContainText('新建用户');

    // Verify form fields exist (use label locators)
    await expect(
      modal.locator('label').filter({ hasText: '用户名' }),
    ).toBeVisible();
    await expect(
      modal.locator('label').filter({ hasText: '密码' }),
    ).toBeVisible();
    await expect(
      modal.locator('label').filter({ hasText: '显示名称' }),
    ).toBeVisible();
    await expect(
      modal.locator('label').filter({ hasText: '邮箱' }),
    ).toBeVisible();
    await expect(
      modal.locator('label').filter({ hasText: '角色' }),
    ).toBeVisible();
  });

  test('should submit create user form with valid data (Req 7.3)', async ({
    base,
  }) => {
    // Intercept create API to simulate success (avoid creating real users)
    await base.page.route('**/api/admin/users', async (route) => {
      await (route.request().method() === 'POST'
        ? route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
              code: 0,
              data: { id: 999 },
              message: 'success',
            }),
          })
        : route.continue());
    });

    // Open create modal
    await base.page.getByRole('button', { name: '新建用户' }).click();
    const modal = dialog(base.page);
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

    // Wait for the role list to load in the modal
    await base.page.waitForTimeout(1000);

    // Fill form fields
    const usernameInput = modal.locator('input[placeholder="请输入用户名"]');
    await usernameInput.fill('test_e2e_user');

    const passwordInput = modal.locator('input[placeholder="请输入密码"]');
    await passwordInput.fill('password123');

    const displayNameInput = modal.locator(
      'input[placeholder="请输入显示名称"]',
    );
    await displayNameInput.fill('E2E Test User');

    const emailInput = modal.locator('input[placeholder="请输入邮箱"]');
    await emailInput.fill('test@example.com');

    // Select role from the multi-select dropdown
    const roleSelect = modal.locator('.ant-select').first();
    await roleSelect.click();
    // Wait for dropdown options to appear and select the first one
    const option = base.page
      .locator('.ant-select-dropdown .ant-select-item')
      .first();
    await option.waitFor({ state: 'visible', timeout: 5000 });
    await option.click();
    // 角色是 `mode: 'multiple'`，选完下拉面板不会自己关。旧写法按 Escape 收
    // 面板，但 Vben Modal 底层的 reka-ui Dialog 同样监听 Escape，弹窗会被一起
    // 关掉（失败快照里连 `[role="dialog"]` 都不剩），后面永远等不到 POST。
    await collapseSelect(base.page, roleSelect);

    // Listen for the create API request BEFORE clicking submit
    const requestPromise = base.page.waitForRequest(
      (req) =>
        req.url().includes('/api/admin/users') && req.method() === 'POST',
      { timeout: 10_000 },
    );

    // 弹窗内按钮的渲染顺序是 [全屏图标, 取 消, 确 认, 关闭图标]，旧写法
    // `nth(buttonCount - 1)` 点的是关闭图标：弹窗被关掉、表单从未提交，
    // 上面的 waitForRequest 于是永远等不到 POST。
    await dialogConfirmButton(modal).click();

    // Verify the API was called with correct data
    const request = await requestPromise;
    const postData = request.postDataJSON();
    expect(postData.username).toBe('test_e2e_user');
    expect(postData.displayName).toBe('E2E Test User');
    expect(postData.email).toBe('test@example.com');
    expect(postData.password).toBe('password123');
  });
});

test.describe('User Management Page - Edit User Modal', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(USER_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should open edit modal with pre-filled data when clicking "编辑" (Req 7.5)', async ({
    base,
  }) => {
    // Click the edit button in the first row's action column
    const editBtn = base.page
      .locator('.vxe-grid')
      .getByRole('button', { name: '编辑' })
      .first();
    await editBtn.click();

    // Wait for modal to appear
    const modal = dialog(base.page);
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

    // Wait for the form to be populated
    await base.page.waitForTimeout(1000);

    // Verify modal title
    await expect(modal).toContainText('编辑用户');

    // Verify form fields are pre-filled (username should have a value)
    const usernameInput = modal.locator('input[placeholder="请输入用户名"]');
    const usernameValue = await usernameInput.inputValue();
    expect(usernameValue).toBeTruthy();

    // Verify displayName is pre-filled
    const displayNameInput = modal.locator(
      'input[placeholder="请输入显示名称"]',
    );
    const displayNameValue = await displayNameInput.inputValue();
    expect(displayNameValue).toBeTruthy();
  });

  test('should submit edit form with modified data (Req 7.6)', async ({
    base,
  }) => {
    // Intercept update API to simulate success
    await base.page.route('**/api/admin/users/*', async (route) => {
      await (route.request().method() === 'PUT' &&
      !route.request().url().includes('status') &&
      !route.request().url().includes('password')
        ? route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ code: 0, data: null, message: 'success' }),
          })
        : route.continue());
    });

    // Click edit button in the grid (fixed-right column)
    const editBtn = base.page
      .locator('.vxe-grid')
      .getByRole('button', { name: '编辑' })
      .first();
    await editBtn.click();

    const modal = dialog(base.page);
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

    // Wait for the form to be populated
    await base.page.waitForTimeout(1000);

    // Modify displayName
    const displayNameInput = modal.locator(
      'input[placeholder="请输入显示名称"]',
    );
    await displayNameInput.clear();
    await displayNameInput.fill('Modified Display Name');

    // Listen for the update API request
    const requestPromise = base.page.waitForRequest(
      (req) =>
        req.url().match(/\/api\/admin\/users\/\d+$/) !== null &&
        req.method() === 'PUT',
      { timeout: 10_000 },
    );

    // 同 Req 7.3：`.last()` 命中的是关闭图标，不是确认键
    await dialogConfirmButton(modal).click();

    // Verify the API was called with modified data
    const request = await requestPromise;
    const putData = request.postDataJSON();
    expect(putData.displayName).toBe('Modified Display Name');
  });
});

test.describe('User Management Page - Status Switch', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(USER_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should show confirmation dialog when clicking status switch (Req 7.7)', async ({
    base,
  }) => {
    // Find a row with a switch (not the admin user to avoid self-disable)
    const rows = gridRows(base.page);
    await waitGridRows(base.page);
    const rowCount = await rows.count();

    // Use the second row if available (avoid toggling admin user)
    const targetRow = rowCount > 1 ? rows.nth(1) : rows.first();
    const switchEl = targetRow.locator('.ant-switch');

    // 旧写法用 `isVisible().catch(() => false)` + `test.skip()` 兜底：行定位数到了
    // 冻结表的副本行（里面只有操作列、没有 switch）就静默跳过，用例看着没红，
    // 其实一次都没跑过。状态列就是 Switch，Req 7.7 已断言过，这里直接硬断言。
    await expect(switchEl).toBeVisible();

    await switchEl.click();

    // Wait for the confirmation modal to appear (Modal.confirm renders .ant-modal-confirm)
    const confirmModal = base.page.locator('.ant-modal-confirm');
    await confirmModal.waitFor({ state: 'visible', timeout: 5000 });

    // Verify the dialog contains enable/disable text
    const modalContent = await confirmModal.textContent();
    expect(modalContent).toMatch(/确定(禁用|启用)用户/);

    // Cancel to avoid actual status change
    const cancelBtn = confirmModal
      .locator('.ant-btn:not(.ant-btn-primary)')
      .first();
    await cancelBtn.click();
  });

  test('should update status after confirming (Req 7.8)', async ({ base }) => {
    // Intercept status update API
    await base.page.route('**/api/admin/users/*/status', async (route) => {
      await (route.request().method() === 'PUT'
        ? route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ code: 0, data: null, message: 'success' }),
          })
        : route.continue());
    });

    const rows = gridRows(base.page);
    await waitGridRows(base.page);
    const rowCount = await rows.count();
    const targetRow = rowCount > 1 ? rows.nth(1) : rows.first();
    const switchEl = targetRow.locator('.ant-switch');

    // 同 Req 7.7：不把「行没渲染出来」静默变成跳过
    await expect(switchEl).toBeVisible();

    await switchEl.click();

    // Wait for confirmation modal
    const confirmModal = base.page.locator('.ant-modal-confirm');
    await confirmModal.waitFor({ state: 'visible', timeout: 5000 });

    // Listen for the status update API request
    const requestPromise = base.page.waitForRequest(
      (req) => req.url().includes('/status') && req.method() === 'PUT',
    );

    // Click confirm
    const okBtn = confirmModal.locator('.ant-btn-primary');
    await okBtn.click();

    // Verify the API was called
    const request = await requestPromise;
    const putData = request.postDataJSON();
    expect(putData.status).toBeDefined();
    expect([0, 1]).toContain(putData.status);
  });
});

test.describe('User Management Page - Password Reset', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(USER_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should show confirmation dialog with "123456" hint when clicking "重置密码" (Req 7.9)', async ({
    base,
  }) => {
    // Action buttons are in the fixed-right column
    const resetBtn = base.page
      .locator('.vxe-grid')
      .getByRole('button', { name: '重置密码' })
      .first();
    await expect(resetBtn).toBeVisible();
    await resetBtn.click();

    // Wait for the confirmation modal to appear
    const confirmModal = base.page.locator('.ant-modal-confirm');
    await confirmModal.waitFor({ state: 'visible', timeout: 5000 });

    // Verify the dialog mentions "123456"
    const modalContent = await confirmModal.textContent();
    expect(modalContent).toContain('123456');
    expect(modalContent).toContain('重置');

    // Cancel to avoid actual password reset
    const cancelBtn = confirmModal
      .locator('.ant-btn:not(.ant-btn-primary)')
      .first();
    await cancelBtn.click();
  });

  test('should call password reset API after confirming (Req 7.10)', async ({
    base,
  }) => {
    // Intercept password reset API
    await base.page.route('**/api/admin/users/*/password', async (route) => {
      await (route.request().method() === 'PUT'
        ? route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ code: 0, data: null, message: 'success' }),
          })
        : route.continue());
    });

    const resetBtn = base.page
      .locator('.vxe-grid')
      .getByRole('button', { name: '重置密码' })
      .first();
    await resetBtn.click();

    // Wait for confirmation modal
    const confirmModal = base.page.locator('.ant-modal-confirm');
    await confirmModal.waitFor({ state: 'visible', timeout: 5000 });

    // Listen for the password reset API request
    const requestPromise = base.page.waitForRequest(
      (req) => req.url().includes('/password') && req.method() === 'PUT',
    );

    // Click confirm
    const okBtn = confirmModal.locator('.ant-btn-primary');
    await okBtn.click();

    // Verify the API was called with "123456"
    const request = await requestPromise;
    const putData = request.postDataJSON();
    expect(putData.newPassword).toBe('123456');
  });
});

test.describe('User Management Page - Delete User', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(USER_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should show irreversible warning dialog when clicking "删除" (Req 7.11)', async ({
    base,
  }) => {
    // Action buttons are in the fixed-right column
    const deleteBtn = base.page
      .locator('.vxe-grid')
      .getByRole('button', { name: '删除' })
      .first();
    await expect(deleteBtn).toBeVisible();
    await deleteBtn.click();

    // Wait for the confirmation modal to appear
    const confirmModal = base.page.locator('.ant-modal-confirm');
    await confirmModal.waitFor({ state: 'visible', timeout: 5000 });

    // Verify the dialog contains irreversible warning
    const modalContent = await confirmModal.textContent();
    expect(modalContent).toContain('不可恢复');
    expect(modalContent).toContain('删除');

    // Verify the confirm button has danger styling (okType: 'danger')
    const okBtn = confirmModal.locator(
      '.ant-btn-dangerous, .ant-btn-primary.ant-btn-dangerous',
    );
    await expect(okBtn.first()).toBeVisible();

    // Cancel to avoid actual deletion
    const cancelBtn = confirmModal
      .locator('.ant-btn:not(.ant-btn-dangerous)')
      .first();
    await cancelBtn.click();
  });

  test('should call delete API after confirming (Req 7.12)', async ({
    base,
  }) => {
    // Intercept delete API
    await base.page.route('**/api/admin/users/*', async (route) => {
      await (route.request().method() === 'DELETE'
        ? route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ code: 0, data: null, message: 'success' }),
          })
        : route.continue());
    });

    const deleteBtn = base.page
      .locator('.vxe-grid')
      .getByRole('button', { name: '删除' })
      .first();
    await deleteBtn.click();

    // Wait for confirmation modal
    const confirmModal = base.page.locator('.ant-modal-confirm');
    await confirmModal.waitFor({ state: 'visible', timeout: 5000 });

    // Listen for the delete API request
    const requestPromise = base.page.waitForRequest(
      (req) =>
        req.url().match(/\/api\/admin\/users\/\d+$/) !== null &&
        req.method() === 'DELETE',
    );

    // Click confirm (the OK button with okType: 'danger')
    const okBtn = confirmModal
      .locator('.ant-btn-primary, .ant-btn-dangerous')
      .first();
    await okBtn.click();

    // Verify the API was called
    const request = await requestPromise;
    expect(request.method()).toBe('DELETE');
    expect(request.url()).toMatch(/\/api\/admin\/users\/\d+$/);
  });
});

test.describe('User Management Page - Search Filtering', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(USER_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should filter results when searching by keyword (Req 7.13)', async ({
    base,
  }) => {
    // Get the search input (the form has submitOnChange: true)
    const searchInput = base.page.locator(
      '.ant-input[placeholder="搜索用户名或显示名"]',
    );
    await expect(searchInput).toBeVisible();

    // Listen for the API request to verify the keyword is sent
    const requestPromise = base.page.waitForRequest(
      (req) =>
        req.url().includes('/api/admin/users') && req.url().includes('keyword'),
    );

    // Type a search keyword
    await searchInput.fill('admin');

    // The form has submitOnChange: true, so it should auto-submit
    const request = await requestPromise;
    expect(request.url()).toContain('keyword=admin');

    // Wait for table to update
    await base.page.waitForResponse(
      (res) => res.url().includes('/api/admin/users') && res.status() === 200,
    );
  });
});
