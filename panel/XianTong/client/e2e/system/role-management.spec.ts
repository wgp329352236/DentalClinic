import type { Page } from '@playwright/test';

import { dialog, dialogConfirmButton } from '../fixtures/dialog';
import { expectTableColumns, gridRows, waitGridReady } from '../fixtures/grid';
import { expect, test } from '../fixtures/playwright-base';

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
 *
 * 登录统一交给 `base` fixture（经 authenticatedPage 已带登录态）。这里曾经自带
 * 一个 `authenticateAndNavigate`，手工跑一遍 login 再写 localStorage，存在三个
 * 问题：把 Pinia namespace `vben-web-antd-5.7.0-dev` 写死（版本号一变就默默
 * 失效）、把 dev server 地址写死、并用 `reload({ waitUntil: 'networkidle' })`
 * 收尾——Vite dev server 下网络永远不会空闲，实测就是它把 Req 10.1 / 10.11 /
 * 10.3 全部卡在 beforeEach 超时。
 */

const ROLE_MANAGEMENT_PATH = '/system/role';

/**
 * mock 出「一个系统角色 + 一个非系统角色」。
 *
 * 种子库里只有 super_admin 一个角色，而它是系统角色（`isSystem` 为真时删除键
 * 本就 disabled）。于是非系统角色相关的断言在真实数据下永远无法执行：Req 10.8
 * 长期挂在 `test.skip()` 上、一次都没真跑过，Req 10.11 的
 * `foundSystemRole || foundNonSystemRole` 也只验证了前一半。这里给两个用例铺上
 * 确定的数据，让它们真的跑起来。
 */
async function mockRoleList(page: Page): Promise<void> {
  await page.route('**/api/admin/roles', async (route) => {
    await (route.request().method() === 'GET'
      ? route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: [
              {
                id: 1,
                roleCode: 'super_admin',
                roleName: '超级管理员',
                description: '拥有所有权限',
                permissions: ['system:user:list', 'system:role:list'],
                isSystem: true,
                createdAt: '2026-08-28 18:45:48',
              },
              {
                id: 2,
                roleCode: 'editor',
                roleName: '内容编辑',
                description: 'E2E 用的非系统角色',
                permissions: ['system:user:list'],
                isSystem: false,
                createdAt: '2026-08-28 18:46:00',
              },
            ],
            message: 'success',
          }),
        })
      : route.continue());
  });
}

/** 系统角色列（index 4）渲染的 Tag，文案是「是」/「否」。 */
function rowsBySystemFlag(page: Page, flag: '否' | '是') {
  return gridRows(page).filter({
    has: page.locator('.ant-tag', { hasText: flag }),
  });
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
    await mockRoleList(base.page);
    await base.navigateTo(ROLE_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should display all expected table columns (Req 10.1)', async ({
    base,
  }) => {
    // 旧写法先 `headerCells.count()` 再逐个 `nth(i).textContent()` 收集文案：
    // mock 数据到达后 vxe 会把 `fixed:'right'` 的操作列拆进冻结表、多出一份
    // 表头副本，count 读到的瞬时数量因此可能大于最终数量，nth(i) 就永远等不到。
    await expectTableColumns(base.page, EXPECTED_COLUMNS);
  });

  test('should display blue Tag with permission count (Req 10.10)', async ({
    base,
  }) => {
    // The permission count column uses a blue Tag to display the count
    const firstRow = gridRows(base.page).first();
    await expect(firstRow).toBeVisible();
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

  test('should display red Tag for system roles and default for non-system (Req 10.11)', async ({
    base,
  }) => {
    // 旧写法手动遍历行，行定位又用了 `.vxe-table--body .vxe-body--row`、且
    // `count()` 不会自动等待；断言还是 `foundSystemRole || foundNonSystemRole`，
    // 意味着两类角色只要命中一类就算过。现在数据是确定的（mockRoleList），
    // 两类都必须验到。
    const systemTag = rowsBySystemFlag(base.page, '是')
      .first()
      .locator('.vxe-body--column')
      .nth(4)
      .locator('.ant-tag');
    await expect(systemTag).toBeVisible();
    await expect(systemTag).toHaveAttribute('class', /ant-tag-red/);

    const normalTag = rowsBySystemFlag(base.page, '否')
      .first()
      .locator('.vxe-body--column')
      .nth(4)
      .locator('.ant-tag');
    await expect(normalTag).toBeVisible();
    const classAttr = (await normalTag.getAttribute('class')) ?? '';
    expect(classAttr).not.toMatch(/ant-tag-red/);
  });
});

test.describe('Role Management Page - Create Role Modal', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(ROLE_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should open create modal with form fields and permission tree (Req 10.2, 10.3)', async ({
    base,
  }) => {
    // Click the "新建角色" button
    const createBtn = base.page.getByRole('button', { name: '新建角色' });
    await expect(createBtn).toBeVisible();
    await createBtn.click();

    // Wait for modal to appear
    const modal = dialog(base.page);
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

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

  test('should fill form and submit to create a role (Req 10.3)', async ({
    base,
  }) => {
    // Intercept create API to simulate success
    await base.page.route('**/api/admin/roles', async (route) => {
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

    // Click the "新建角色" button
    await base.page.getByRole('button', { name: '新建角色' }).click();

    const modal = dialog(base.page);
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

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
    // 旧写法用 `if (checkboxCount > 0)` 包着，权限树没渲染时就静默跳过，
    // 用例照样「通过」，实际一个权限都没勾。
    const treeCheckboxes = modal.locator('.ant-tree-checkbox');
    await expect(treeCheckboxes.first()).toBeVisible();
    await treeCheckboxes.first().click();

    // Listen for the list refresh after modal closes
    const refreshPromise = base.page.waitForResponse(
      (res) =>
        res.url().includes('/api/admin/roles') &&
        res.request().method() === 'GET',
      { timeout: 10_000 },
    );

    // Submit the form via the modal confirm button。antd Button 会在两个中文
    // 字符之间插空格，确认键的可访问名是「确 认」，`/确定|确认/` 匹配不到。
    await dialogConfirmButton(modal).click();

    // Verify success message
    await base.page
      .locator('.ant-message-success')
      .waitFor({ state: 'visible', timeout: 5000 });

    // 列表必须真的重新拉过数据。旧写法 `.catch(() => null)` 再加 `if` 把超时
    // 变成静默通过，等于没测。
    const refreshResponse = await refreshPromise;
    expect(refreshResponse.url()).toContain('/api/admin/roles');
  });
});

test.describe('Role Management Page - Edit Role Modal', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(ROLE_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should open edit modal with pre-filled data and selected permissions (Req 10.4)', async ({
    base,
  }) => {
    const firstRow = gridRows(base.page).first();
    await expect(firstRow).toBeVisible();

    // Get the role data from the first row for verification
    const roleCodeRaw = await firstRow
      .locator('.vxe-body--column')
      .nth(0)
      .textContent();
    const roleCode = roleCodeRaw?.trim();
    const roleNameRaw = await firstRow
      .locator('.vxe-body--column')
      .nth(1)
      .textContent();
    const roleName = roleNameRaw?.trim();

    // Click the "编辑" button in the first row
    const editBtn = firstRow.getByRole('button', { name: '编辑' });
    await expect(editBtn).toBeVisible();
    await editBtn.click();

    // Wait for modal to appear
    const modal = dialog(base.page);
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

    // Verify modal title
    await expect(modal).toContainText('编辑角色');

    // Verify form fields are pre-filled
    const roleCodeInput = modal.locator('input[placeholder="如 editor"]');
    await expect(roleCodeInput).toHaveValue(roleCode ?? '');

    const roleNameInput = modal.locator('input[placeholder="请输入角色名称"]');
    await expect(roleNameInput).toHaveValue(roleName ?? '');

    // Verify permission tree is visible and really rendered checkable nodes
    const tree = modal.locator('.ant-tree');
    await expect(tree).toBeVisible();
    const checkboxCount = await modal.locator('.ant-tree-checkbox').count();
    expect(checkboxCount).toBeGreaterThan(0);

    // 表格里「权限数」列写的就是这个角色分到的权限数量，大于 0 时弹窗里就
    // 应该有勾选节点。旧写法断言 `checkedCount >= 0`——任何非负数都成立，
    // 等于没断言。（不断言两者相等：勾满一个模块时 antd Tree 会额外给父节点
    // 加上 checked，而父节点的 key 是字符串、不计入权限数。）
    const permCountText = await firstRow
      .locator('.vxe-body--column')
      .nth(3)
      .textContent();
    const permCount = Number(permCountText?.trim() ?? '0');
    const checkedCount = await modal
      .locator('.ant-tree-checkbox-checked')
      .count();
    if (permCount > 0) {
      expect(checkedCount).toBeGreaterThan(0);
    }
  });
});

test.describe('Role Management Page - System Role Delete Disabled', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(ROLE_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should disable delete button for system roles (isSystem=true) (Req 10.6)', async ({
    base,
  }) => {
    // 旧写法先 `rows.count()` 再手动遍历：`count()` 是即时快照、不会自动等待，
    // 而 waitGridReady 只保证表头渲染完（数据要等 XHR），读到 0 时循环一次都
    // 不进，最后 `expect(foundSystemRole).toBe(true)` 必然失败——实测就是这么红的。
    const systemRow = rowsBySystemFlag(base.page, '是').first();
    await expect(systemRow).toBeVisible();

    // index.vue 的删除键绑了 `:disabled="!!row.isSystem"`
    const deleteBtn = systemRow.getByRole('button', { name: '删除' });
    await expect(deleteBtn).toBeVisible();
    await expect(deleteBtn).toBeDisabled();
  });
});

test.describe('Role Management Page - Non-System Role Delete', () => {
  test.beforeEach(async ({ base }) => {
    await mockRoleList(base.page);
    await base.navigateTo(ROLE_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should show confirmation and delete non-system role (Req 10.8)', async ({
    base,
  }) => {
    // 同 Req 10.6：不再用 `count()` + 遍历 + 找不到就 `test.skip()`。那个兜底让
    // 这条用例在只有 super_admin 的种子数据下永久跳过，看着没红，其实从未执行。
    const targetRow = rowsBySystemFlag(base.page, '否').first();
    await expect(targetRow).toBeVisible();

    // Intercept delete API to simulate success
    await base.page.route('**/api/admin/roles/*', async (route) => {
      await (route.request().method() === 'DELETE'
        ? route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ code: 0, data: null, message: 'success' }),
          })
        : route.continue());
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

    // onDelete 用的是 okType: 'danger'，渲染出来是 `ant-btn ant-btn-default
    // ant-btn-dangerous`，**不带** ant-btn-primary。旧写法断言时用了
    // `.ant-btn-dangerous, .ant-btn-primary` 的或选择器（danger 丢了也能过），
    // 点击时却只找 `.ant-btn-primary`——这条用例只要没被 skip 就必然超时。
    const okBtn = dialogConfirmButton(confirmModal);
    await expect(okBtn).toBeVisible();
    await expect(okBtn).toHaveClass(/ant-btn-dangerous/);

    // Listen for list refresh
    const refreshPromise = base.page.waitForResponse(
      (res) =>
        res.url().includes('/api/admin/roles') &&
        res.request().method() === 'GET',
      { timeout: 10_000 },
    );

    // Click confirm to delete
    await okBtn.click();

    // Verify success message
    await base.page
      .locator('.ant-message-success')
      .waitFor({ state: 'visible', timeout: 5000 });

    // 同 Req 10.3：不把超时吞成静默通过
    const refreshResponse = await refreshPromise;
    expect(refreshResponse.url()).toContain('/api/admin/roles');
  });
});
