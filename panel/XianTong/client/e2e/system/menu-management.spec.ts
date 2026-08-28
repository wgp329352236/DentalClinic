import { test, expect, Selectors } from '../fixtures/playwright-base';

/**
 * Menu Management E2E Tests
 *
 * Validates: Requirements 12.1-12.10
 *
 * Tests cover:
 * - Tree table display with correct columns
 * - Menu type labels (目录/菜单/按钮) with correct colors
 * - Create menu modal with all form fields
 * - Edit menu modal with pre-filled data
 * - Delete menu with child menus warning
 */

const MENU_PATH = '/system/menu';
const API_BASE_URL = 'http://localhost:8080';

// Expected column headers per Requirement 12.1
const EXPECTED_COLUMNS = [
  '菜单名称',
  '类型',
  '路由',
  '权限',
  '图标',
  '排序',
  '操作',
];

/**
 * Authenticate and navigate to the menu management page.
 * Uses the correct Pinia persisted state key format for Vben Admin.
 */
async function authenticateAndNavigate(base: any, path: string) {
  // Login via API
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
  // Wait for the vxe-table tree to load (use .first() as vxe-table may render multiple body elements)
  await base.page.locator('.vxe-table--body').first().waitFor({ state: 'visible', timeout: 15000 });
}

test.describe('Menu Management - Tree Table Display', () => {
  test.beforeEach(async ({ base }) => {
    await authenticateAndNavigate(base, MENU_PATH);
  });

  test('should display all expected table columns (Req 12.1)', async ({ base }) => {
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

  test('should display tree structure with expandable rows', async ({ base }) => {
    // vxe-table tree nodes have tree-node toggle icons
    const treeNodes = base.page.locator('.vxe-table--body .vxe-tree--btn-wrapper');
    const treeNodeCount = await treeNodes.count();
    // There should be at least one expandable tree node (directory menus have children)
    expect(treeNodeCount).toBeGreaterThan(0);
  });
});

test.describe('Menu Management - Menu Type Labels', () => {
  test.beforeEach(async ({ base }) => {
    await authenticateAndNavigate(base, MENU_PATH);
  });

  test('should display blue "目录" label for directory type (Req 12.2)', async ({ base }) => {
    // Find a tag with "目录" text - real data should have directory type menus
    const directoryTag = base.page.locator('.vxe-table--body .ant-tag').filter({ hasText: '目录' }).first();
    await expect(directoryTag).toBeVisible();
    // Verify blue color - Ant Design uses ant-tag-blue class
    await expect(directoryTag).toHaveCSS('color', /./); // Tag exists and is styled
    const tagClasses = await directoryTag.getAttribute('class') ?? '';
    // The color prop "blue" renders as either class or inline style
    const style = await directoryTag.getAttribute('style') ?? '';
    const isBlue = tagClasses.includes('blue') || style.includes('blue') || tagClasses.includes('ant-tag-blue');
    expect(isBlue).toBe(true);
  });

  test('should display green "菜单" label for menu type (Req 12.3)', async ({ base }) => {
    const menuTag = base.page.locator('.vxe-table--body .ant-tag').filter({ hasText: '菜单' }).first();
    await expect(menuTag).toBeVisible();
    const tagClasses = await menuTag.getAttribute('class') ?? '';
    const style = await menuTag.getAttribute('style') ?? '';
    const isGreen = tagClasses.includes('green') || style.includes('green') || tagClasses.includes('ant-tag-green');
    expect(isGreen).toBe(true);
  });

  test('should display orange "按钮" label for button type (Req 12.4)', async ({ base }) => {
    // Button type menus may not be visible by default (they are children)
    // Expand tree nodes first to reveal button-type items
    const treeToggles = base.page.locator('.vxe-table--body .vxe-tree--btn-wrapper');
    const toggleCount = await treeToggles.count();

    // Expand all collapsed tree nodes to reveal button-type children
    for (let i = 0; i < toggleCount; i++) {
      const toggle = treeToggles.nth(i);
      const isCollapsed = await toggle.locator('.vxe-tree--node-btn.is--active').count() === 0;
      if (isCollapsed) {
        await toggle.click();
        await base.page.waitForTimeout(300);
      }
    }

    // Now check for button type tag
    const buttonTag = base.page.locator('.vxe-table--body .ant-tag').filter({ hasText: '按钮' }).first();
    // If no button type exists in real data, use route interception
    const buttonVisible = await buttonTag.isVisible().catch(() => false);
    if (buttonVisible) {
      const tagClasses = await buttonTag.getAttribute('class') ?? '';
      const style = await buttonTag.getAttribute('style') ?? '';
      const isOrange = tagClasses.includes('orange') || style.includes('orange') || tagClasses.includes('ant-tag-orange');
      expect(isOrange).toBe(true);
    } else {
      // Verify via route interception with mock data containing button type
      await base.page.route('**/api/admin/menus**', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: [
              {
                id: 1, menuName: '系统管理', menuType: 'directory', parentId: null,
                routePath: '/system', permissionCode: null, icon: 'lucide:settings',
                sortOrder: 1, isVisible: 1, isCache: 0,
                children: [
                  {
                    id: 2, menuName: '测试按钮', menuType: 'button', parentId: 1,
                    routePath: '', permissionCode: 'test:btn', icon: '',
                    sortOrder: 1, isVisible: 1, isCache: 0, children: [],
                  },
                ],
              },
            ],
            message: 'success',
          }),
        });
      });
      await base.page.reload({ waitUntil: 'networkidle' });
      await base.page.locator('.vxe-table--body').waitFor({ state: 'visible', timeout: 15000 });

      const mockedButtonTag = base.page.locator('.vxe-table--body .ant-tag').filter({ hasText: '按钮' }).first();
      await expect(mockedButtonTag).toBeVisible();
      const tagClasses = await mockedButtonTag.getAttribute('class') ?? '';
      const style = await mockedButtonTag.getAttribute('style') ?? '';
      const isOrange = tagClasses.includes('orange') || style.includes('orange') || tagClasses.includes('ant-tag-orange');
      expect(isOrange).toBe(true);
    }
  });

  test('should only display valid type labels (目录/菜单/按钮)', async ({ base }) => {
    // Verify that all type tags in the table are one of the valid labels
    const allTags = base.page.locator('.vxe-table--body .ant-tag');
    const tagCount = await allTags.count();
    expect(tagCount).toBeGreaterThan(0);

    const validLabels = ['目录', '菜单', '按钮'];
    for (let i = 0; i < tagCount; i++) {
      const text = (await allTags.nth(i).textContent())?.trim() ?? '';
      expect(validLabels).toContain(text);
    }
  });
});

test.describe('Menu Management - Create Menu Modal', () => {
  test.beforeEach(async ({ base }) => {
    await authenticateAndNavigate(base, MENU_PATH);
  });

  test('should open create modal with all form fields when clicking "新建菜单" (Req 12.5, 12.6)', async ({ base }) => {
    // Click the "新建菜单" button
    const createBtn = base.page.getByRole('button', { name: '新建菜单' });
    await expect(createBtn).toBeVisible();
    await createBtn.click();

    // Wait for modal to appear (Vben modal uses [role="dialog"])
    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10000 });

    // Verify modal title
    await expect(modal).toContainText('新建菜单');

    // Verify all form fields exist per Requirement 12.6
    await expect(modal.getByText('菜单名称')).toBeVisible();
    await expect(modal.getByText('类型')).toBeVisible();
    await expect(modal.getByText('父菜单 ID')).toBeVisible();
    await expect(modal.getByText('路由路径')).toBeVisible();
    await expect(modal.getByText('组件路径')).toBeVisible();
    await expect(modal.getByText('权限编码')).toBeVisible();
    await expect(modal.getByText('图标')).toBeVisible();
    await expect(modal.getByText('排序')).toBeVisible();
  });

  test('should have menu type select with correct options (Req 12.6)', async ({ base }) => {
    const createBtn = base.page.getByRole('button', { name: '新建菜单' });
    await createBtn.click();

    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10000 });

    // Click the type select to open dropdown
    const typeSelect = modal.locator('.ant-select').first();
    await typeSelect.click();

    // Verify options in the dropdown
    const dropdown = base.page.locator('.ant-select-dropdown').last();
    await dropdown.waitFor({ state: 'visible', timeout: 5000 });

    await expect(dropdown.getByText('目录')).toBeVisible();
    await expect(dropdown.getByText('菜单')).toBeVisible();
    await expect(dropdown.getByText('按钮')).toBeVisible();
  });

  test('should submit create form with valid data (Req 12.6)', async ({ base }) => {
    // Intercept create API to simulate success
    let createRequestBody: any = null;
    await base.page.route('**/api/admin/menus', async (route) => {
      if (route.request().method() === 'POST') {
        createRequestBody = route.request().postDataJSON();
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 0, data: null, message: 'success' }),
        });
      } else {
        await route.continue();
      }
    });

    const createBtn = base.page.getByRole('button', { name: '新建菜单' });
    await createBtn.click();

    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10000 });

    // Fill form fields
    // Menu name - find the input in the form item labeled "菜单名称"
    const menuNameInput = modal.locator('.ant-input').first();
    await menuNameInput.fill('TEST_E2E_MENU');

    // Select menu type - click the select then choose an option
    const typeSelect = modal.locator('.ant-select').first();
    await typeSelect.click();
    const dropdown = base.page.locator('.ant-select-dropdown').last();
    await dropdown.waitFor({ state: 'visible', timeout: 5000 });
    await dropdown.getByText('目录').click();

    // Route path
    const routePathInput = modal.locator('input[placeholder="/path"]');
    if (await routePathInput.isVisible()) {
      await routePathInput.fill('/test/e2e');
    }

    // Permission code
    const permCodeInput = modal.locator('input[placeholder="game:list"]');
    if (await permCodeInput.isVisible()) {
      await permCodeInput.fill('test:e2e');
    }

    // Submit the form via the modal confirm button
    const confirmBtn = modal.getByRole('button', { name: /确定|确认/ });
    if (await confirmBtn.isVisible()) {
      await confirmBtn.click();
    }

    // Wait for the request to be made
    await base.page.waitForTimeout(1000);

    // Verify the create API was called with correct data
    expect(createRequestBody).not.toBeNull();
    expect(createRequestBody.menuName).toBe('TEST_E2E_MENU');
    expect(createRequestBody.menuType).toBe('directory');
  });
});

test.describe('Menu Management - Edit Menu Modal', () => {
  test.beforeEach(async ({ base }) => {
    await authenticateAndNavigate(base, MENU_PATH);
  });

  test('should open edit modal with pre-filled data when clicking "编辑" (Req 12.7)', async ({ base }) => {
    // Get the first row's menu name for verification
    const firstRow = base.page.locator('.vxe-table--body .vxe-body--row').first();
    const menuNameCell = firstRow.locator('.vxe-body--column').first();
    const originalMenuName = (await menuNameCell.textContent())?.trim() ?? '';

    // Click the edit button on the first row
    const editBtn = firstRow.getByRole('button', { name: '编辑' });
    await expect(editBtn).toBeVisible();
    await editBtn.click();

    // Wait for modal to appear
    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10000 });

    // Verify modal title indicates edit mode
    await expect(modal).toContainText('编辑菜单');

    // Verify the menu name field is pre-filled with the original value
    const menuNameInput = modal.locator('.ant-input').first();
    const inputValue = await menuNameInput.inputValue();
    expect(inputValue).toBeTruthy();
    // The input should contain the menu name from the row
    expect(inputValue).toBe(originalMenuName);
  });

  test('should pre-fill menu type in edit modal (Req 12.7)', async ({ base }) => {
    const firstRow = base.page.locator('.vxe-table--body .vxe-body--row').first();

    // Get the type from the tag in the row
    const typeTag = firstRow.locator('.ant-tag').first();
    const typeText = (await typeTag.textContent())?.trim() ?? '';

    // Click edit
    const editBtn = firstRow.getByRole('button', { name: '编辑' });
    await editBtn.click();

    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10000 });

    // Verify the type select shows the correct pre-selected value
    const selectedText = await modal.locator('.ant-select-selection-item').first().textContent();
    // The selected text should match the type label from the row
    expect(selectedText?.trim()).toBe(typeText);
  });
});

test.describe('Menu Management - Delete Menu', () => {
  test.beforeEach(async ({ base }) => {
    await authenticateAndNavigate(base, MENU_PATH);
  });

  test('should show delete confirmation with child menus warning (Req 12.9)', async ({ base }) => {
    const firstRow = base.page.locator('.vxe-table--body .vxe-body--row').first();

    // Get the menu name for verification
    const menuNameCell = firstRow.locator('.vxe-body--column').first();
    const menuName = (await menuNameCell.textContent())?.trim() ?? '';

    // Click the delete button
    const deleteBtn = firstRow.getByRole('button', { name: '删除' });
    await expect(deleteBtn).toBeVisible();
    await deleteBtn.click();

    // Wait for the confirmation modal (Modal.confirm)
    const confirmModal = base.page.locator('.ant-modal-confirm').last();
    await confirmModal.waitFor({ state: 'visible', timeout: 10000 });

    // Verify the title contains "删除菜单"
    await expect(confirmModal).toContainText('删除菜单');

    // Verify warning text mentions child menus deletion (Req 12.9)
    await expect(confirmModal).toContainText('所有子菜单');

    // Verify the menu name is mentioned in the confirmation
    if (menuName) {
      await expect(confirmModal).toContainText(menuName);
    }

    // Verify the confirm button has danger styling
    const okBtn = confirmModal.locator('.ant-btn-dangerous, .ant-btn-primary');
    await expect(okBtn.first()).toBeVisible();
  });

  test('should call delete API when confirmed (Req 12.10)', async ({ base }) => {
    // Intercept delete API to simulate success
    let deleteApiCalled = false;
    await base.page.route('**/api/admin/menus/*', async (route) => {
      if (route.request().method() === 'DELETE') {
        deleteApiCalled = true;
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
    await deleteBtn.click();

    // Wait for confirmation modal
    const confirmModal = base.page.locator('.ant-modal-confirm').last();
    await confirmModal.waitFor({ state: 'visible', timeout: 10000 });

    // Listen for list refresh after deletion
    const refreshPromise = base.page.waitForRequest((req) =>
      req.url().includes('/api/admin/menus') && req.method() === 'GET',
    );

    // Click the OK/confirm button
    const okBtn = confirmModal.locator('.ant-btn-primary').first();
    await okBtn.click();

    // Verify the delete API was called
    await refreshPromise.catch(() => null);
    expect(deleteApiCalled).toBe(true);
  });

  test('should not delete when cancel is clicked', async ({ base }) => {
    let deleteApiCalled = false;
    await base.page.route('**/api/admin/menus/*', async (route) => {
      if (route.request().method() === 'DELETE') {
        deleteApiCalled = true;
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
    await deleteBtn.click();

    // Wait for confirmation modal
    const confirmModal = base.page.locator('.ant-modal-confirm').last();
    await confirmModal.waitFor({ state: 'visible', timeout: 10000 });

    // Click cancel button
    const cancelBtn = confirmModal.locator('.ant-btn:not(.ant-btn-primary)').first();
    await cancelBtn.click();

    // Wait for modal to close
    await confirmModal.waitFor({ state: 'hidden', timeout: 5000 });

    // Verify delete API was NOT called
    expect(deleteApiCalled).toBe(false);
  });
});
