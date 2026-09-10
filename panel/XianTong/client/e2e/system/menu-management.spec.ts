import { dialogCancelButton, dialogConfirmButton } from '../fixtures/dialog';
import {
  expandAllTreeNodes,
  expectTableColumns,
  gridActionButton,
  gridRows,
  waitGridReady,
} from '../fixtures/grid';
import { expect, test } from '../fixtures/playwright-base';

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

test.describe('Menu Management - Tree Table Display', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(MENU_PATH);
    await waitGridReady(base.page);
  });

  test('should display all expected table columns (Req 12.1)', async ({
    base,
  }) => {
    await expectTableColumns(base.page, EXPECTED_COLUMNS);
  });

  test('should display tree structure with expandable rows', async ({
    base,
  }) => {
    // vxe-table 4.18 renders the toggle as `.vxe-cell--tree-btn`; the class this
    // used to look for, `.vxe-tree--btn-wrapper`, does not exist at all.
    const treeNodes = gridRows(base.page).locator('.vxe-cell--tree-btn');
    // `count()` 是即时快照、不会自动等待，而 waitGridReady 只保证表头渲染完
    // （数据要等 XHR）——实测就是在数据还没到的瞬间读到 0 而打红。先用会自动
    // 等待的断言把第一个树开关等出来，再数总数。
    await expect(treeNodes.first()).toBeVisible();
    // There should be at least one expandable tree node (directory menus have children)
    expect(await treeNodes.count()).toBeGreaterThan(0);
  });
});

test.describe('Menu Management - Menu Type Labels', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(MENU_PATH);
    await waitGridReady(base.page);
  });

  test('should display blue "目录" label for directory type (Req 12.2)', async ({
    base,
  }) => {
    // Find a tag with "目录" text - real data should have directory type menus
    const directoryTag = gridRows(base.page)
      .locator('.ant-tag')
      .filter({ hasText: '目录' })
      .first();
    await expect(directoryTag).toBeVisible();
    // Verify blue color - Ant Design uses ant-tag-blue class
    await expect(directoryTag).toHaveCSS('color', /./); // Tag exists and is styled
    const tagClasses = (await directoryTag.getAttribute('class')) ?? '';
    // The color prop "blue" renders as either class or inline style
    const style = (await directoryTag.getAttribute('style')) ?? '';
    const isBlue =
      tagClasses.includes('blue') ||
      style.includes('blue') ||
      tagClasses.includes('ant-tag-blue');
    expect(isBlue).toBe(true);
  });

  test('should display green "菜单" label for menu type (Req 12.3)', async ({
    base,
  }) => {
    // 「菜单」类型的节点是目录的子节点，而树默认是折叠的（真实数据下首屏
    // 只有 7 个根目录），不展开就永远找不到。
    await expandAllTreeNodes(base.page);

    const menuTag = gridRows(base.page)
      .locator('.ant-tag')
      .filter({ hasText: '菜单' })
      .first();
    await expect(menuTag).toBeVisible();
    const tagClasses = (await menuTag.getAttribute('class')) ?? '';
    const style = (await menuTag.getAttribute('style')) ?? '';
    const isGreen =
      tagClasses.includes('green') ||
      style.includes('green') ||
      tagClasses.includes('ant-tag-green');
    expect(isGreen).toBe(true);
  });

  test('should display orange "按钮" label for button type (Req 12.4)', async ({
    base,
  }) => {
    // 种子菜单树里根本没有 button 类型的行（实测：7 个 directory + 21 个 menu），
    // 所以只能走路由拦截。旧写法「先看真实数据、不行再 mock」的第一个分支永远
    // 不可达，只是白白让用例变得不确定。
    await base.page.route('**/api/admin/menus**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: [
            {
              id: 1,
              menuName: '系统管理',
              menuType: 'directory',
              parentId: null,
              routePath: '/system',
              permissionCode: null,
              icon: 'lucide:settings',
              sortOrder: 1,
              isVisible: 1,
              isCache: 0,
              children: [
                {
                  id: 2,
                  menuName: '测试按钮',
                  menuType: 'button',
                  parentId: 1,
                  routePath: '',
                  permissionCode: 'test:btn',
                  icon: '',
                  sortOrder: 1,
                  isVisible: 1,
                  isCache: 0,
                  children: [],
                },
              ],
            },
          ],
          message: 'success',
        }),
      });
    });

    // `networkidle` 在 Vite dev server + 并发下并不可靠（实测会让 reload 直接超时），
    // 改为只等 DOM 加载完成，再用网格自己的就绪信号作为等待条件。
    await base.page.reload({ waitUntil: 'domcontentloaded' });
    await waitGridReady(base.page);
    // mock 数据里的「按钮」同样是目录的子节点，需展开后才渲染
    await expandAllTreeNodes(base.page);

    const buttonTag = gridRows(base.page)
      .locator('.ant-tag')
      .filter({ hasText: '按钮' })
      .first();
    await expect(buttonTag).toBeVisible();
    const tagClasses = (await buttonTag.getAttribute('class')) ?? '';
    const style = (await buttonTag.getAttribute('style')) ?? '';
    const isOrange =
      tagClasses.includes('orange') ||
      style.includes('orange') ||
      tagClasses.includes('ant-tag-orange');
    expect(isOrange).toBe(true);
  });

  test('should only display valid type labels (目录/菜单/按钮)', async ({
    base,
  }) => {
    // Verify that all type tags in the table are one of the valid labels
    await expandAllTreeNodes(base.page);
    const allTags = gridRows(base.page).locator('.ant-tag');
    const tagCount = await allTags.count();
    expect(tagCount).toBeGreaterThan(0);

    const validLabels = ['目录', '菜单', '按钮'];
    for (let i = 0; i < tagCount; i++) {
      const textRaw = await allTags.nth(i).textContent();
      const text = textRaw?.trim() ?? '';
      expect(validLabels).toContain(text);
    }
  });
});

test.describe('Menu Management - Create Menu Modal', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(MENU_PATH);
    await waitGridReady(base.page);
  });

  test('should open create modal with all form fields when clicking "新建菜单" (Req 12.5, 12.6)', async ({
    base,
  }) => {
    // Click the "新建菜单" button
    const createBtn = base.page.getByRole('button', { name: '新建菜单' });
    await expect(createBtn).toBeVisible();
    await createBtn.click();

    // Wait for modal to appear (Vben modal uses [role="dialog"])
    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

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

  test('should have menu type select with correct options (Req 12.6)', async ({
    base,
  }) => {
    const createBtn = base.page.getByRole('button', { name: '新建菜单' });
    await createBtn.click();

    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

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

  test('should submit create form with valid data (Req 12.6)', async ({
    base,
  }) => {
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
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

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
    await routePathInput.fill('/test/e2e');

    // Permission code
    const permCodeInput = modal.locator(
      'input[placeholder="system:user:list"]',
    );
    await permCodeInput.fill('test:e2e');

    // Submit the form via the modal confirm button. The previous
    // `if (await confirmBtn.isVisible())` guard turned a wrong selector into a
    // silently skipped click, so the test failed on the assertion below instead
    // of at the click — hiding the real cause.
    await dialogConfirmButton(modal).click();

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
    await base.navigateTo(MENU_PATH);
    await waitGridReady(base.page);
  });

  test('should open edit modal with pre-filled data when clicking "编辑" (Req 12.7)', async ({
    base,
  }) => {
    // Get the first row's menu name for verification
    const firstRow = gridRows(base.page).first();
    const menuNameCell = firstRow.locator('.vxe-body--column').first();
    const originalMenuNameRaw = await menuNameCell.textContent();
    const originalMenuName = originalMenuNameRaw?.trim() ?? '';

    // Click the edit button — the action column is `fixed: 'right'`, so the
    // button lives in the frozen table, not inside the main body row.
    const editBtn = gridActionButton(base.page, '编辑');
    await expect(editBtn).toBeVisible();
    await editBtn.click();

    // Wait for modal to appear
    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

    // Verify modal title indicates edit mode
    await expect(modal).toContainText('编辑菜单');

    // Verify the menu name field is pre-filled with the original value
    const menuNameInput = modal.locator('.ant-input').first();
    const inputValue = await menuNameInput.inputValue();
    expect(inputValue).toBeTruthy();
    // The input should contain the menu name from the row
    expect(inputValue).toBe(originalMenuName);
  });

  test('should pre-fill menu type in edit modal (Req 12.7)', async ({
    base,
  }) => {
    const firstRow = gridRows(base.page).first();

    // Get the type from the tag in the row
    const typeTag = firstRow.locator('.ant-tag').first();
    const typeTextRaw = await typeTag.textContent();
    const typeText = typeTextRaw?.trim() ?? '';

    // Click edit
    await gridActionButton(base.page, '编辑').click();

    const modal = base.page.locator('[role="dialog"]').last();
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

    // Verify the type select shows the correct pre-selected value
    const selectedText = await modal
      .locator('.ant-select-selection-item')
      .first()
      .textContent();
    // The selected text should match the type label from the row
    expect(selectedText?.trim()).toBe(typeText);
  });
});

test.describe('Menu Management - Delete Menu', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(MENU_PATH);
    await waitGridReady(base.page);
  });

  test('should show delete confirmation with child menus warning (Req 12.9)', async ({
    base,
  }) => {
    const firstRow = gridRows(base.page).first();

    // Get the menu name for verification
    const menuNameCell = firstRow.locator('.vxe-body--column').first();
    const menuNameRaw = await menuNameCell.textContent();
    const menuName = menuNameRaw?.trim() ?? '';

    // Click the delete button (also in the frozen `fixed: 'right'` table)
    const deleteBtn = gridActionButton(base.page, '删除');
    await expect(deleteBtn).toBeVisible();
    await deleteBtn.click();

    // Wait for the confirmation modal (Modal.confirm)
    const confirmModal = base.page.locator('.ant-modal-confirm').last();
    await confirmModal.waitFor({ state: 'visible', timeout: 10_000 });

    // Verify the title contains "删除菜单"
    await expect(confirmModal).toContainText('删除菜单');

    // Verify warning text mentions child menus deletion (Req 12.9)
    await expect(confirmModal).toContainText('所有子菜单');

    // Verify the menu name is mentioned in the confirmation
    if (menuName) {
      await expect(confirmModal).toContainText(menuName);
    }

    // Verify the confirm button has danger styling. `okType: 'danger'` renders
    // `ant-btn-dangerous` *without* `ant-btn-primary`, so assert on the former.
    const okBtn = dialogConfirmButton(confirmModal);
    await expect(okBtn).toBeVisible();
    await expect(okBtn).toHaveClass(/ant-btn-dangerous/);
  });

  test('should call delete API when confirmed (Req 12.10)', async ({
    base,
  }) => {
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

    const deleteBtn = gridActionButton(base.page, '删除');
    await deleteBtn.click();

    // Wait for confirmation modal
    const confirmModal = base.page.locator('.ant-modal-confirm').last();
    await confirmModal.waitFor({ state: 'visible', timeout: 10_000 });

    // Listen for list refresh after deletion
    const refreshPromise = base.page.waitForRequest(
      (req) => req.url().includes('/api/admin/menus') && req.method() === 'GET',
    );

    // Click the OK/confirm button
    await dialogConfirmButton(confirmModal).click();

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

    const deleteBtn = gridActionButton(base.page, '删除');
    await deleteBtn.click();

    // Wait for confirmation modal
    const confirmModal = base.page.locator('.ant-modal-confirm').last();
    await confirmModal.waitFor({ state: 'visible', timeout: 10_000 });

    // Click cancel button
    await dialogCancelButton(confirmModal).click();

    // Wait for modal to close
    await confirmModal.waitFor({ state: 'hidden', timeout: 5000 });

    // Verify delete API was NOT called
    expect(deleteApiCalled).toBe(false);
  });
});
