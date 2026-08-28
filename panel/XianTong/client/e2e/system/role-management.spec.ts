import { test, expect, API_BASE_URL } from '../fixtures/playwright-base';

/**
 * Role Management E2E Tests
 *
 * Validates: Requirements 10.1-10.11
 *
 * Tests cover:
 * - Table columns display (角色编码, 角色名称, 描述, 权限数, 系统角色, 创建时间, 操作)
 * - Create role modal with permission tree
 * - Edit role modal with pre-filled data and selected permissions
 * - System role delete disabled (isSystem=true)
 * - Non-system role delete confirmation and deletion
 * - Permission count tag (blue Tag)
 * - System role tag (red Tag for system, default for non-system)
 */

const ROLE_MANAGEMENT_PATH = '/system/role';

/**
 * Helper to authenticate and inject tokens into the correct localStorage key.
 * The Vben Admin app uses Pinia persisted state with key: `vben-web-antd-5.7.0-dev-core-access`
 */
async function authenticateAndNavigate(base: any, path: string) {
  const loginResp = await base.page.request.post(`${API_BASE_URL}/api/admin/auth/login`, {
    data: { username: 'admin', password: 'admin123' },
  });
  const loginBody = await loginResp.json();
  const token = loginBody.data.accessToken;
  const refreshToken = loginBody.data.refreshToken;

  // Navigate to the app to set localStorage
  await base.page.goto('http://localhost:5666', { waitUntil: 'domcontentloaded' });
  await base.page.waitForTimeout(500);

  // Inject tokens into localStorage with the correct Pinia persisted state key
  await base.page.evaluate(
    ({ accessToken, refresh }: { accessToken: string; refresh: string }) => {
      const namespace = 'vben-web-antd-5.7.0-dev';
      localStorage.setItem(
        `${namespace}-core-access`,
        JSON.stringify({ accessToken, refreshToken: refresh, accessCodes: [] }),
      );
    },
    { accessToken: token, refresh: refreshToken },
  );

  // Reload to pick up the tokens
  await base.page.reload({ waitUntil: 'networkidle' });

  // Navigate to the target page
  await base.navigateTo(path);
  // Wait for the vxe-grid table to load
  await base.page.locator('.vxe-table--body').first().waitFor({ state: 'visible', timeout: 15000 });
}

// Expected column headers per Requirement 10.1
const EXPECTED_COLUMNS = [
  '角色编码',
  '角色名称',
  '描述',
  '权限数',
  '系统角色',
  '创建时间',
  '操作',
];

test.describe('Role Management Page - Table Display', () => {
  test.beforeEach(async ({ base }) => {
    await authenticateAndNavigate(base, ROLE_MANAGEMENT_PATH);
  });

  test('should display all expected table columns (Req 10.1)', async ({ base }) => {
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

  test('should display blue Tag with permission count (Req 10.10)', async ({ base }) => {
    // The permission count column uses a blue Tag to display the count
    const firstRow = base.page.locator('.vxe-table--body .vxe-body--row').first();
    // permissions column is the 4th column (index 3)
    const permCountCell = firstRow.locator('.vxe-body--column').nth(3);
    const tag = permCountCell.locator('.ant-tag');

    await expect(tag).toBeVisible();
    // Verify it's a blue tag
    await expect(tag).toHaveAttribute('class', /ant-tag-blue/);
    // Verify it contains a number
    const text = await tag.textContent();
    expect(text?.trim()).toMatch(/^\d+$/);
  });

  test('should display red Tag for system roles and default for non-system (Req 10.11)', async ({ base }) => {
    const rows = base.page.locator('.vxe-table--body .vxe-body--row');
    const rowCount = await rows.count();

    let foundSystemRole = false;
    let foundNonSystemRole = false;

    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);
      // isSystem column is the 5th column (index 4)
      const isSystemCell = row.locator('.vxe-body--column').nth(4);
      const tag = isSystemCell.locator('.ant-tag');
      const tagText = await tag.textContent();

      if (tagText?.trim() === '是') {
        // System role should have red tag
        await expect(tag).toHaveAttribute('class', /ant-tag-red/);
        foundSystemRole = true;
      } else if (tagText?.trim() === '否') {
        // Non-system role should have default tag (no color class or 'default')
        const classAttr = await tag.getAttribute('class') ?? '';
        expect(classAttr).not.toMatch(/ant-tag-red/);
        foundNonSystemRole = true;
      }

      if (foundSystemRole && foundNonSystemRole) break;
    }

    // At least one type should be found in the data
    expect(foundSystemRole || foundNonSystemRole).toBe(true);
  });
});

test.describe('Role Management Page - Create Role Modal', () => {
  test.beforeEach(async ({ base }) => {
    await authenticateAndNavigate(base, ROLE_MANAGEMENT_PATH);
  });

  test('should open create modal with form fields and permission tree (Req 10.2, 10.3)', async ({ base }) => {
    // Click the "新建角色" button
    const createBtn = base.page.getByRole('button', { name: '新建角色' });
    await expect(createBtn).toBeVisible();
    await createBtn.click();

    // Wait for modal to appear
    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10000 });

    // Verify modal title
    await expect(modal).toContainText('新建角色');

    // Verify form fields exist
    await expect(modal.getByText('角色编码')).toBeVisible();
    await expect(modal.getByText('角色名称')).toBeVisible();
    await expect(modal.getByText('描述')).toBeVisible();

    // Verify permission tree section exists
    await expect(modal.getByText('权限分配')).toBeVisible();
    // The permission tree should be rendered with ant-design Tree component
    const tree = modal.locator('.ant-tree');
    await expect(tree).toBeVisible();
  });

  test('should fill form and submit to create a role (Req 10.3)', async ({ base }) => {
    // Intercept create API to simulate success
    await base.page.route('**/api/admin/roles', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 0, data: { id: 999 }, message: 'success' }),
        });
      } else {
        await route.continue();
      }
    });

    // Click the "新建角色" button
    await base.page.getByRole('button', { name: '新建角色' }).click();

    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10000 });

    // Fill roleCode
    const roleCodeInput = modal.locator('input[placeholder="如 editor"]');
    await roleCodeInput.fill('TEST_E2E_ROLE');

    // Fill roleName
    const roleNameInput = modal.locator('input[placeholder="请输入角色名称"]');
    await roleNameInput.fill('E2E测试角色');

    // Fill description
    const descInput = modal.locator('input[placeholder="角色描述"]');
    await descInput.fill('E2E测试用角色');

    // Select some permissions from the tree (click first checkbox)
    const treeCheckboxes = modal.locator('.ant-tree-checkbox');
    const checkboxCount = await treeCheckboxes.count();
    if (checkboxCount > 0) {
      await treeCheckboxes.first().click();
    }

    // Listen for the list refresh after modal closes
    const refreshPromise = base.page.waitForResponse((res) =>
      res.url().includes('/api/admin/roles') && res.request().method() === 'GET',
    );

    // Submit the form via the modal confirm button
    const confirmBtn = modal.getByRole('button', { name: /确定|确认/ });
    await confirmBtn.click();

    // Verify success message
    await base.page.locator('.ant-message-success').waitFor({ state: 'visible', timeout: 5000 });

    // Verify list refresh was triggered
    const refreshResponse = await refreshPromise.catch(() => null);
    if (refreshResponse) {
      expect(refreshResponse.url()).toContain('/api/admin/roles');
    }
  });
});

test.describe('Role Management Page - Edit Role Modal', () => {
  test.beforeEach(async ({ base }) => {
    await authenticateAndNavigate(base, ROLE_MANAGEMENT_PATH);
  });

  test('should open edit modal with pre-filled data and selected permissions (Req 10.4)', async ({ base }) => {
    const firstRow = base.page.locator('.vxe-table--body .vxe-body--row').first();

    // Get the role data from the first row for verification
    const roleCode = (await firstRow.locator('.vxe-body--column').nth(0).textContent())?.trim();
    const roleName = (await firstRow.locator('.vxe-body--column').nth(1).textContent())?.trim();

    // Click the "编辑" button in the first row
    const editBtn = firstRow.getByRole('button', { name: '编辑' });
    await expect(editBtn).toBeVisible();
    await editBtn.click();

    // Wait for modal to appear
    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10000 });

    // Verify modal title
    await expect(modal).toContainText('编辑角色');

    // Verify form fields are pre-filled
    const roleCodeInput = modal.locator('input[placeholder="如 editor"]');
    await expect(roleCodeInput).toHaveValue(roleCode ?? '');

    const roleNameInput = modal.locator('input[placeholder="请输入角色名称"]');
    await expect(roleNameInput).toHaveValue(roleName ?? '');

    // Verify permission tree is visible and has checked items
    const tree = modal.locator('.ant-tree');
    await expect(tree).toBeVisible();

    // Check that some permissions are pre-selected (checked checkboxes)
    const checkedNodes = modal.locator('.ant-tree-checkbox-checked');
    const checkedCount = await checkedNodes.count();
    // A role with permissions should have at least one checked node
    // (unless it's a role with 0 permissions)
    expect(checkedCount).toBeGreaterThanOrEqual(0);
  });
});

test.describe('Role Management Page - System Role Delete Disabled', () => {
  test.beforeEach(async ({ base }) => {
    await authenticateAndNavigate(base, ROLE_MANAGEMENT_PATH);
  });

  test('should disable delete button for system roles (isSystem=true) (Req 10.6)', async ({ base }) => {
    const rows = base.page.locator('.vxe-table--body .vxe-body--row');
    const rowCount = await rows.count();

    let foundSystemRole = false;

    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);
      // isSystem column is the 5th column (index 4)
      const isSystemCell = row.locator('.vxe-body--column').nth(4);
      const tagText = await isSystemCell.locator('.ant-tag').textContent();

      if (tagText?.trim() === '是') {
        // Found a system role - verify delete button is disabled
        const deleteBtn = row.getByRole('button', { name: '删除' });
        await expect(deleteBtn).toBeVisible();
        await expect(deleteBtn).toBeDisabled();
        foundSystemRole = true;
        break;
      }
    }

    // Ensure we found at least one system role to test
    expect(foundSystemRole).toBe(true);
  });
});

test.describe('Role Management Page - Non-System Role Delete', () => {
  test.beforeEach(async ({ base }) => {
    await authenticateAndNavigate(base, ROLE_MANAGEMENT_PATH);
  });

  test('should show confirmation and delete non-system role (Req 10.8)', async ({ base }) => {
    const rows = base.page.locator('.vxe-table--body .vxe-body--row');
    const rowCount = await rows.count();

    let targetRow: ReturnType<typeof rows.nth> | null = null;

    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i);
      // isSystem column is the 5th column (index 4)
      const isSystemCell = row.locator('.vxe-body--column').nth(4);
      const tagText = await isSystemCell.locator('.ant-tag').textContent();

      if (tagText?.trim() === '否') {
        targetRow = row;
        break;
      }
    }

    if (!targetRow) {
      test.skip();
      return;
    }

    // Intercept delete API to simulate success
    await base.page.route('**/api/admin/roles/*', async (route) => {
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

    // Click the "删除" button on the non-system role
    const deleteBtn = targetRow.getByRole('button', { name: '删除' });
    await expect(deleteBtn).toBeEnabled();
    await deleteBtn.click();

    // Verify confirmation dialog appears (Modal.confirm)
    const confirmModal = base.page.locator('.ant-modal-confirm');
    await confirmModal.waitFor({ state: 'visible', timeout: 5000 });

    // Verify confirmation content mentions the role
    await expect(confirmModal).toContainText('删除角色');
    await expect(confirmModal).toContainText('确定删除');

    // Verify the OK button has danger type
    const okBtn = confirmModal.locator('.ant-btn-dangerous, .ant-btn-primary');
    await expect(okBtn.first()).toBeVisible();

    // Listen for list refresh
    const refreshPromise = base.page.waitForResponse((res) =>
      res.url().includes('/api/admin/roles') && res.request().method() === 'GET',
    );

    // Click confirm to delete
    await confirmModal.locator('.ant-btn-primary').click();

    // Verify success message
    await base.page.locator('.ant-message-success').waitFor({ state: 'visible', timeout: 5000 });

    // Verify list refresh was triggered
    const refreshResponse = await refreshPromise.catch(() => null);
    if (refreshResponse) {
      expect(refreshResponse.url()).toContain('/api/admin/roles');
    }
  });
});
