import {
  collapseSelect,
  dialog,
  dialogCloseButton,
  dialogConfirmButton,
  dialogOverlay,
  invalidFormItems,
  waitDialogSettled,
} from '../fixtures/dialog';
import { gridActionButton, waitGridReady } from '../fixtures/grid';
import { expect, test } from '../fixtures/playwright-base';

/**
 * Modal Interaction E2E Tests
 *
 * Validates: Requirements 20.1-20.7
 *
 * Tests cover:
 * - Modal overlay and centering (20.1)
 * - Close via close button and overlay click (20.2)
 * - destroyOnClose behavior (20.3)
 * - Form validation: submit with empty required fields (20.4)
 * - Success callback: modal closes and parent refreshes (20.5)
 * - Delete confirmation styling: warning text and danger button (20.6)
 */

const USER_MANAGEMENT_PATH = '/system/user';

test.describe('Modal Interaction - Overlay and Centering (Req 20.1)', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(USER_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should display modal with overlay mask and centered dialog when opened', async ({
    base,
  }) => {
    // Open the create user modal
    const createBtn = base.page.getByRole('button', { name: '新建用户' });
    await createBtn.click();

    // Wait for the modal to appear
    const modal = dialog(base.page);
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

    // 遮罩由 DialogOverlay.vue 渲染成弹窗的**兄弟**节点，带源码写死的
    // `data-dismissable-modal` 属性。旧写法往弹窗祖先里找 `.ant-modal-wrap` 或
    // `[class*="vben-modal"]`——Vben Modal 底层是 reka-ui Dialog，这两种节点
    // 在本应用里根本不存在，xpath 直接报 element(s) not found。
    const overlay = dialogOverlay(base.page);
    await expect(overlay).toBeVisible();

    // 弹窗本体是 BODY 直属的 z-popup，同样不是 antd 的 .ant-modal-content
    await expect(modal).toHaveClass(/z-popup/);

    const viewport = base.page.viewportSize();
    expect(viewport).not.toBeNull();
    if (!viewport) return;

    // 遮罩是 fixed inset-0，必须铺满整个视口，否则点不到弹窗外的区域
    const overlayBox = await overlay.boundingBox();
    expect(overlayBox).not.toBeNull();
    if (overlayBox) {
      expect(overlayBox.width).toBeGreaterThanOrEqual(viewport.width);
      expect(overlayBox.height).toBeGreaterThanOrEqual(viewport.height);
    }

    // 弹窗 class 是 `inset-x-0 top-[10vh] mx-auto`：水平严格居中、垂直靠上。
    // 旧写法给了 30% 视口宽度的容差，那样即便弹窗贴到左边也能过，等于没断言。
    //
    // 量之前必须等进场动画跑完：`slide-in-from-top-[48%]` + `zoom-in-95` 持续
    // 300ms，中间态量出来的 y 会偏高十几像素。
    await waitDialogSettled(modal);
    const box = await modal.boundingBox();
    expect(box).not.toBeNull();
    if (box) {
      const centerX = box.x + box.width / 2;
      expect(Math.abs(centerX - viewport.width / 2)).toBeLessThan(2);
      expect(Math.abs(box.y - viewport.height * 0.1)).toBeLessThan(5);
      expect(box.y + box.height).toBeLessThanOrEqual(viewport.height);
    }

    // Close the modal to clean up
    await base.page.keyboard.press('Escape');
  });
});

test.describe('Modal Interaction - Close Mechanisms (Req 20.2)', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(USER_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should close modal when clicking the close button', async ({
    base,
  }) => {
    // Open the create user modal
    const createBtn = base.page.getByRole('button', { name: '新建用户' });
    await createBtn.click();

    const modal = dialog(base.page);
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

    // 关闭按钮是 reka-ui 的 DialogClose，class 由 DialogContent.vue 写死为
    // `absolute top-3 right-3 h-6 w-6 rounded-full ...`（同排的全屏按钮是
    // right-10）。旧写法 `.ant-modal-close, [class*="close"]` 命中的是带
    // `data-[state=closed]:` 的 Tailwind 类名——实测 5 个 DIV，点了毫无反应。
    const closeBtn = dialogCloseButton(modal);
    await expect(closeBtn).toBeVisible();
    await closeBtn.click();

    // Verify the modal is closed
    await modal.waitFor({ state: 'hidden', timeout: 5000 });
  });

  test('should close modal when clicking the overlay/mask area', async ({
    base,
  }) => {
    // Open the create user modal
    const createBtn = base.page.getByRole('button', { name: '新建用户' });
    await createBtn.click();

    const modal = dialog(base.page);
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

    // 点遮罩应触发 DialogOverlay 的 @click → close。弹窗是 top-[10vh] mx-auto，
    // 所以遮罩的左上角必然落在弹窗之外。
    //
    // 旧写法用 `.ant-modal-wrap, .vben-modal-mask` 定位，两者都不存在，于是
    // 每次都走 else 分支按 Escape——用例看着是绿的，实际测的是键盘而不是遮罩。
    const overlay = dialogOverlay(base.page);
    await expect(overlay).toBeVisible();
    const overlayBox = await overlay.boundingBox();
    expect(overlayBox).not.toBeNull();
    if (!overlayBox) return;
    await base.page.mouse.click(overlayBox.x + 5, overlayBox.y + 5);

    // 关闭后 reka-ui 会把弹窗从 DOM 里移除，不只是隐藏
    await modal.waitFor({ state: 'hidden', timeout: 5000 });
    await expect(base.page.locator('[role="dialog"]')).toHaveCount(0);
  });
});

test.describe('Modal Interaction - destroyOnClose Behavior (Req 20.3)', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(USER_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should destroy modal content on close and recreate on reopen', async ({
    base,
  }) => {
    // Open the create user modal
    const createBtn = base.page.getByRole('button', { name: '新建用户' });
    await createBtn.click();

    const modal = dialog(base.page);
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

    // Wait for form to load
    await base.page.waitForTimeout(1000);

    // 先造出状态：填用户名。旧写法用 `if (await isVisible())` 包着，一旦
    // 选择器写错就静默跳过填充，用例照样「通过」，实际上什么都没测到。
    const usernameInput = modal.locator('input[placeholder="请输入用户名"]');
    await expect(usernameInput).toBeVisible();
    await usernameInput.fill('test_destroy_check');

    // Close the modal
    await dialogCloseButton(modal).click();
    await modal.waitFor({ state: 'hidden', timeout: 5000 });
    // destroyOnClose：关闭后整个弹窗从 DOM 移除，不只是隐藏
    await expect(base.page.locator('[role="dialog"]')).toHaveCount(0);

    // Reopen the modal
    await createBtn.click();
    const newModal = dialog(base.page);
    await newModal.waitFor({ state: 'visible', timeout: 10_000 });

    // Wait for form to load
    await base.page.waitForTimeout(1000);

    // 重开的是全新实例，onOpenChange 里的 resetForm() 应把上次的输入清掉
    const newUsernameInput = newModal.locator(
      'input[placeholder="请输入用户名"]',
    );
    await expect(newUsernameInput).toHaveValue('');

    // Clean up
    await base.page.keyboard.press('Escape');
  });
});

test.describe('Modal Interaction - Form Validation (Req 20.4)', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(USER_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should show validation errors when submitting with empty required fields', async ({
    base,
  }) => {
    // Open the create user modal
    const createBtn = base.page.getByRole('button', { name: '新建用户' });
    await createBtn.click();

    const modal = dialog(base.page);
    await modal.waitFor({ state: 'visible', timeout: 10_000 });

    // Wait for form to fully render
    await base.page.waitForTimeout(1000);

    // 空表单直接提交。弹窗内按钮的渲染顺序是
    // [全屏图标, 取 消, 确 认, 关闭图标]，旧写法 `nth(count - 1)` 点的是
    // 关闭图标——弹窗被关掉、表单从未提交，下面的校验断言必然找不到元素。
    await dialogConfirmButton(modal).click();

    // Vben Form 的校验错误不是 antd 的 `.ant-form-item-explain-error`：
    // form-field.vue 给校验失败的 FormItem 加 `form-valid-error`，错误文本由
    // vee-validate 的 ErrorMessage 渲染成 `p.text-destructive`。
    const invalidItems = invalidFormItems(modal);
    await expect(invalidItems.first()).toBeVisible({ timeout: 5000 });

    // schema 里 username / password / displayName 是 required、roleIds 是
    // selectRequired，四个都该报错
    expect(await invalidItems.count()).toBeGreaterThanOrEqual(3);

    // Verify error messages contain meaningful text
    const firstErrorText = await invalidItems
      .first()
      .locator('p.text-destructive')
      .textContent();
    expect(firstErrorText?.trim()).toBeTruthy();

    // 校验没过就不能把弹窗关掉
    await expect(modal).toBeVisible();

    // Clean up
    await base.page.keyboard.press('Escape');
  });

  test('should clear validation errors after filling required fields', async ({
    base,
  }) => {
    // Open the create user modal
    const createBtn = base.page.getByRole('button', { name: '新建用户' });
    await createBtn.click();

    const modal = dialog(base.page);
    await modal.waitFor({ state: 'visible', timeout: 10_000 });
    await base.page.waitForTimeout(1000);

    // Submit empty form to trigger validation
    await dialogConfirmButton(modal).click();

    // Verify errors are shown
    const invalidItems = invalidFormItems(modal);
    await expect(invalidItems.first()).toBeVisible({ timeout: 5000 });

    // 用户名这一项此刻必须处于报错状态
    const usernameItem = invalidItems.filter({
      has: base.page.locator('input[placeholder="请输入用户名"]'),
    });
    await expect(usernameItem).toHaveCount(1);

    // Fill in the username field
    const usernameInput = modal.locator('input[placeholder="请输入用户名"]');
    await usernameInput.fill('test_validation_user');

    // 再提交一次让 validate() 重跑：已填好的字段错误应消失，其余必填项仍在。
    // 不依赖 vee-validate 的自动重校验时机，这样断言是确定的。
    await dialogConfirmButton(modal).click();

    await expect(usernameItem).toHaveCount(0);
    // 密码 / 显示名称 / 角色 还是空的，表单整体依然无效
    await expect(invalidItems.first()).toBeVisible();
    // 因此弹窗不该被关掉
    await expect(modal).toBeVisible();

    // Clean up
    await base.page.keyboard.press('Escape');
  });
});

test.describe('Modal Interaction - Success Callback (Req 20.5)', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(USER_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should close modal and refresh parent data after successful operation', async ({
    base,
  }) => {
    // Intercept create API to simulate success
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
    const createBtn = base.page.getByRole('button', { name: '新建用户' });
    await createBtn.click();

    const modal = dialog(base.page);
    await modal.waitFor({ state: 'visible', timeout: 10_000 });
    await base.page.waitForTimeout(1000);

    // Fill required form fields
    const usernameInput = modal.locator('input[placeholder="请输入用户名"]');
    await usernameInput.fill('test_success_callback');

    const passwordInput = modal.locator('input[placeholder="请输入密码"]');
    await passwordInput.fill('password123');

    const displayNameInput = modal.locator(
      'input[placeholder="请输入显示名称"]',
    );
    await displayNameInput.fill('Test Success');

    const emailInput = modal.locator('input[placeholder="请输入邮箱"]');
    await emailInput.fill('test@example.com');

    // Select a role
    const roleSelect = modal.locator('.ant-select').first();
    await roleSelect.click();
    const option = base.page
      .locator('.ant-select-dropdown .ant-select-item')
      .first();
    await option.waitFor({ state: 'visible', timeout: 5000 });
    await option.click();
    // 角色是 `mode: 'multiple'`，选完下拉面板不会自己关。旧写法按 Escape 收
    // 面板，但 reka-ui Dialog 同样监听 Escape，弹窗会被一起关掉——后面的
    // waitForRequest 就永远等不到刷新请求。
    await collapseSelect(base.page, roleSelect);

    // Listen for the list refresh request (GET /api/admin/users)
    const refreshPromise = base.page.waitForRequest(
      (req: any) =>
        req.url().includes('/api/admin/users') && req.method() === 'GET',
      { timeout: 10_000 },
    );

    // Submit the form（同 Req 20.4：`.last()` 点的是关闭图标，表单不会提交，
    // 这个 waitForRequest 也就永远等不到刷新请求）
    await dialogConfirmButton(modal).click();

    // Verify the modal closes after success
    await modal.waitFor({ state: 'hidden', timeout: 10_000 });

    // 父列表必须真的重新拉过数据。旧写法 `.catch(() => null)` 再加 `if` 把
    // 超时变成了静默通过，等于没测。
    const refreshRequest = await refreshPromise;
    expect(refreshRequest.url()).toContain('/api/admin/users');
  });
});

test.describe('Modal Interaction - Delete Confirmation Styling (Req 20.6)', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(USER_MANAGEMENT_PATH);
    await waitGridReady(base.page);
  });

  test('should display delete confirmation with warning text and danger-styled button', async ({
    base,
  }) => {
    // Click the delete button on the first user row
    // 操作列是 fixed: 'right'，按钮只存在于冻结表，不在主表数据行内
    const deleteBtn = gridActionButton(base.page, '删除');
    await expect(deleteBtn).toBeVisible();
    await deleteBtn.click();

    // Wait for the confirmation modal to appear (Modal.confirm uses .ant-modal-confirm)
    const confirmModal = base.page.locator('.ant-modal-confirm');
    await confirmModal.waitFor({ state: 'visible', timeout: 5000 });

    // Verify warning text is present (mentions irreversible/不可恢复)
    const modalContent = await confirmModal.textContent();
    expect(modalContent).toContain('不可恢复');

    // Verify the confirm button has danger styling
    // Modal.confirm with okType: 'danger' renders .ant-btn-dangerous
    const dangerBtn = confirmModal.locator('.ant-btn-dangerous');
    await expect(dangerBtn.first()).toBeVisible();

    // Verify the danger button has distinct visual styling (red/danger color class)
    const dangerBtnClasses = await dangerBtn.first().getAttribute('class');
    expect(dangerBtnClasses).toMatch(/dangerous/);

    // Verify there's also a cancel button (non-danger)
    const cancelBtn = confirmModal
      .locator('.ant-btn:not(.ant-btn-dangerous)')
      .first();
    await expect(cancelBtn).toBeVisible();

    // Cancel to avoid actual deletion
    await cancelBtn.click();
    await confirmModal.waitFor({ state: 'hidden', timeout: 5000 });
  });

  test('should display warning icon in delete confirmation dialog', async ({
    base,
  }) => {
    // Click delete on first user
    // 操作列是 fixed: 'right'，按钮只存在于冻结表，不在主表数据行内
    const deleteBtn = gridActionButton(base.page, '删除');
    await deleteBtn.click();

    const confirmModal = base.page.locator('.ant-modal-confirm');
    await confirmModal.waitFor({ state: 'visible', timeout: 5000 });

    // Verify the warning icon is rendered inside the confirm body.
    // The concrete icon class (anticon-exclamation-circle / anticon-warning) varies
    // across antd versions, so only assert that an icon node exists.
    const warningIcon = confirmModal.locator(
      '.ant-modal-confirm-body .anticon',
    );
    await expect(warningIcon.first()).toBeAttached();

    // The confirm body itself must be rendered with the warning content
    const confirmBody = confirmModal.locator('.ant-modal-confirm-body');
    await expect(confirmBody).toBeVisible();

    // Cancel
    const cancelBtn = confirmModal
      .locator('.ant-btn:not(.ant-btn-dangerous)')
      .first();
    await cancelBtn.click();
  });
});
