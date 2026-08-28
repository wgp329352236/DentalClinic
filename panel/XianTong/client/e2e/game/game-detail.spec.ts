import { test, expect } from '../fixtures/playwright-base';
import { Selectors, API_BASE_URL } from '../fixtures/playwright-base';

/**
 * Game Detail Page E2E Tests
 * Validates: Requirements 4.1-4.12
 *
 * Tests the game detail page with 19 platform tabs, edit mode,
 * sensitive field masking, and feishu integration features.
 */

const ALL_TAB_LABELS = [
  '基础配置',
  '飞书',
  'Google Ads',
  'Meta',
  'TikTok',
  'Apple',
  'App Store',
  'Adjust',
  'Applovin',
  'Moloco',
  'Unity',
  'Mintegral',
  'VK',
  'Yandex',
  'Reddit',
  'Remerge',
  'IronSource',
  'Mistplay',
  'TikTok Mini',
];

/**
 * Helper to find a button by its text content.
 * Ant Design Vue buttons may render text with spaces between characters (e.g., "编 辑").
 * This helper handles both cases.
 */
function btnLocator(page: any, container: any, text: string) {
  // Match button span containing the text (with possible spaces between chars)
  const spaced = text.split('').join('\\s*');
  return container.locator(`button`).filter({ hasText: new RegExp(`^\\s*${spaced}\\s*$`) });
}

test.describe('Game Detail Page', () => {
  let gameCode: string;

  test.beforeEach(async ({ base }) => {
    // Authenticate via API and inject token into the correct localStorage key
    const loginResp = await base.page.request.post(`${API_BASE_URL}/api/admin/auth/login`, {
      data: { username: 'admin', password: 'admin123' },
    });
    const loginBody = await loginResp.json();
    const token = loginBody.data.accessToken;
    const refreshToken = loginBody.data.refreshToken;

    // Get a game code via API
    const gamesResp = await base.page.request.get(`${API_BASE_URL}/api/admin/games?page=1&pageSize=1`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const gamesBody = await gamesResp.json();
    gameCode = gamesBody.data.list[0].code;
    expect(gameCode).toBeTruthy();

    // Navigate to the app to set localStorage
    await base.page.goto('http://localhost:5666', { waitUntil: 'domcontentloaded' });
    await base.page.waitForTimeout(1000);

    // Inject tokens into localStorage with the correct Pinia persisted state key
    // Store ID is 'core-access', namespace is 'vben-web-antd-5.7.0-dev'
    await base.page.evaluate(
      ({ accessToken, refresh }) => {
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

    // Navigate to the game detail page
    await base.navigateTo(`/game/detail/${gameCode}`);
    // Wait for the tabs to be visible (indicates page loaded successfully)
    await base.page.locator(Selectors.tabs).waitFor({ state: 'visible', timeout: 15000 });
  });

  test('should display all 19 platform tabs (Requirement 4.1)', async ({ base }) => {
    const tabNav = base.page.locator(Selectors.tabNav);
    await tabNav.waitFor({ state: 'visible' });

    // Verify all tab labels are present
    for (const label of ALL_TAB_LABELS) {
      // Use a regex that matches the label followed by optional whitespace and indicator
      const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      const tabItem = tabNav.locator('.ant-tabs-tab').filter({ hasText: new RegExp(escaped) });
      const count = await tabItem.count();
      // For labels like "TikTok" that match multiple tabs, verify at least one exists
      expect(count).toBeGreaterThanOrEqual(1);
    }

    // Verify exactly 19 tabs total
    const tabCount = await tabNav.locator('.ant-tabs-tab').count();
    expect(tabCount).toBe(19);
  });

  test('should display base config tab with game info (Requirement 4.2)', async ({ base }) => {
    // The base config tab should be active by default
    const activePane = base.page.locator('.ant-tabs-tabpane-active');
    await activePane.waitFor({ state: 'visible' });

    // Verify the Descriptions component shows key fields
    const descriptions = activePane.locator('.ant-descriptions');
    await expect(descriptions).toBeVisible();

    // Check that key labels are present using span text (exact match)
    const expectedLabels = [
      '游戏代码',
      '游戏名称',
      '地区',
      '类型',
      '付费类型',
      '开服时间',
      '关服时间',
      '支持地区',
      '拉取渠道',
      '推送渠道',
    ];

    for (const label of expectedLabels) {
      // Use the span inside the label cell for exact matching
      const labelSpan = descriptions.locator('.ant-descriptions-item-label span').filter({ hasText: label });
      const count = await labelSpan.count();
      expect(count, `Expected label "${label}" to be present`).toBeGreaterThanOrEqual(1);
    }

    // Verify game code value matches - use the cell directly after the label
    const codeLabel = descriptions.locator('th.ant-descriptions-item-label span').filter({ hasText: /^游戏代码$/ });
    const codeCell = codeLabel.locator('xpath=ancestor::th/following-sibling::td[1]');
    await expect(codeCell).toContainText(gameCode);
  });

  test('should show platform configured indicator - green ✅ for configured, red ❌ for unconfigured (Requirements 4.3, 4.4)', async ({ base }) => {
    const tabNav = base.page.locator(Selectors.tabNav);

    // Check that platform tabs (not basic) have status indicators
    const configuredTags = tabNav.locator('.ant-tag-success');
    const unconfiguredTags = tabNav.locator('.ant-tag-error');

    // At least some indicators should be present (either configured or unconfigured)
    const configuredCount = await configuredTags.count();
    const unconfiguredCount = await unconfiguredTags.count();

    // There should be 18 platform tabs (all except basic) with indicators
    expect(configuredCount + unconfiguredCount).toBe(18);

    // Verify configured tabs show ✅
    if (configuredCount > 0) {
      await expect(configuredTags.first()).toContainText('✅');
    }

    // Verify unconfigured tabs show ❌
    if (unconfiguredCount > 0) {
      await expect(unconfiguredTags.first()).toContainText('❌');
    }
  });

  test('should toggle edit mode when clicking "编辑" button (Requirement 4.5)', async ({ base }) => {
    const activePane = base.page.locator('.ant-tabs-tabpane-active');

    // Initially should show Descriptions (view mode)
    await expect(activePane.locator('.ant-descriptions')).toBeVisible();

    // Click the "编辑" button (may render as "编 辑" with space)
    const editButton = btnLocator(base.page, activePane, '编辑');
    await expect(editButton).toBeVisible();
    await editButton.click();

    // After clicking edit, form fields should become editable (Input elements appear)
    const inputs = activePane.locator('.ant-input');
    await expect(inputs.first()).toBeVisible();

    // The "保存" and "取消" buttons should now be visible
    const saveButton = btnLocator(base.page, activePane, '保存');
    const cancelButton = btnLocator(base.page, activePane, '取消');
    await expect(saveButton).toBeVisible();
    await expect(cancelButton).toBeVisible();

    // The "编辑" button should no longer be visible
    await expect(editButton).toBeHidden();
  });

  test('should save config in edit mode (Requirement 4.6)', async ({ base }) => {
    const activePane = base.page.locator('.ant-tabs-tabpane-active');

    // Enter edit mode
    await btnLocator(base.page, activePane, '编辑').click();

    // Wait for form to appear
    const nameInput = activePane.locator('.ant-input').first();
    await nameInput.waitFor({ state: 'visible' });

    // Click save
    await btnLocator(base.page, activePane, '保存').click();

    // Wait for success message
    await base.page
      .locator(Selectors.messageSuccess)
      .waitFor({ state: 'visible', timeout: 10000 });

    // After save, should return to view mode (Descriptions visible again)
    await expect(activePane.locator('.ant-descriptions')).toBeVisible({ timeout: 10000 });
  });

  test('should cancel edit mode without saving changes (Requirement 4.7)', async ({ base }) => {
    const activePane = base.page.locator('.ant-tabs-tabpane-active');

    // Get original game name from view mode - target the cell after the "游戏名称" label
    const nameLabel = activePane.locator('.ant-descriptions th.ant-descriptions-item-label span').filter({ hasText: /^游戏名称$/ });
    const nameContent = nameLabel.locator('xpath=ancestor::th/following-sibling::td[1]');
    const originalName = await nameContent.textContent();

    // Enter edit mode
    await btnLocator(base.page, activePane, '编辑').click();

    // Modify the name field (first input is the game name)
    const nameInput = activePane.locator('.ant-input').first();
    await nameInput.waitFor({ state: 'visible' });
    await nameInput.fill('TEMP_MODIFIED_NAME_E2E');

    // Click cancel
    await btnLocator(base.page, activePane, '取消').click();

    // Should return to view mode
    await expect(activePane.locator('.ant-descriptions')).toBeVisible();

    // Verify the name is unchanged
    const nameAfterCancel = await nameContent.textContent();
    expect(nameAfterCancel).toBe(originalName);
  });

  test('should show unsaved changes dialog when switching tab while editing (Requirement 4.8)', async ({ base }) => {
    const activePane = base.page.locator('.ant-tabs-tabpane-active');

    // Enter edit mode on basic tab
    await btnLocator(base.page, activePane, '编辑').click();
    await activePane.locator('.ant-input').first().waitFor({ state: 'visible' });

    // Try to switch to another tab (e.g., 飞书)
    const tabNav = base.page.locator(Selectors.tabNav);
    const feishuTab = tabNav.locator('.ant-tabs-tab').filter({ hasText: '飞书' });
    await feishuTab.click();

    // A confirmation dialog should appear
    const confirmModal = base.page.locator('.ant-modal-confirm');
    await confirmModal.waitFor({ state: 'visible', timeout: 5000 });

    // Verify the dialog mentions unsaved changes
    await expect(confirmModal).toContainText('未保存的修改');

    // Cancel the dialog to stay on current tab
    const cancelBtn = confirmModal.locator('.ant-btn:not(.ant-btn-primary)').first();
    await cancelBtn.click();

    // Should still be in edit mode on basic tab
    await expect(activePane.locator('.ant-input').first()).toBeVisible();
  });

  test('should mask sensitive fields (token/secret/key/password) showing last 4 chars (Requirement 4.9)', async ({ base }) => {
    const tabNav = base.page.locator(Selectors.tabNav);

    // Find a configured platform tab (one with ✅)
    const configuredTab = tabNav.locator('.ant-tabs-tab').filter({ has: base.page.locator('.ant-tag-success') }).first();
    const hasConfigured = await configuredTab.count() > 0;

    if (hasConfigured) {
      await configuredTab.click();
      await base.page.waitForTimeout(1000);

      const activePane = base.page.locator('.ant-tabs-tabpane-active');
      const descriptions = activePane.locator('.ant-descriptions');

      if (await descriptions.isVisible()) {
        // Look for fields that contain sensitive keywords in their labels
        const sensitiveLabels = descriptions.locator('.ant-descriptions-item-label').filter({
          hasText: /token|secret|key|password/i,
        });

        const sensitiveCount = await sensitiveLabels.count();
        if (sensitiveCount > 0) {
          // For each sensitive field, verify the value is masked (contains ••••)
          for (let i = 0; i < sensitiveCount; i++) {
            const row = sensitiveLabels.nth(i).locator('xpath=ancestor::tr');
            const content = row.locator('.ant-descriptions-item-content');
            const text = await content.textContent();
            // Masked values should contain •••• pattern
            if (text && text.trim() !== '-') {
              expect(text).toMatch(/••••/);
            }
          }
        }
      }
    } else {
      // If no platform is configured, test passes (nothing to mask)
      test.skip();
    }
  });

  test('should use password input type for sensitive fields in edit mode (Requirement 4.10)', async ({ base }) => {
    // Navigate to feishu tab
    const tabNav = base.page.locator(Selectors.tabNav);
    const feishuTab = tabNav.locator('.ant-tabs-tab').filter({ hasText: /^飞书/ });
    await feishuTab.click();
    await base.page.waitForTimeout(1000);

    // Get the currently active tab pane (after switching)
    const activePane = base.page.locator('.ant-tabs-tabpane-active');
    await activePane.waitFor({ state: 'visible' });

    // Enter edit mode
    const editButton = btnLocator(base.page, activePane, '编辑');
    await editButton.waitFor({ state: 'visible', timeout: 5000 });
    await editButton.click();

    // Wait for form to appear
    await activePane.locator('.ant-input, .ant-input-password').first().waitFor({ state: 'visible' });

    // The "App Secret" field should use InputPassword (ant-input-password)
    const passwordInputs = activePane.locator('.ant-input-password');
    await expect(passwordInputs.first()).toBeVisible();

    // Verify the password input has type="password" on the inner input
    const innerInput = passwordInputs.first().locator('input');
    await expect(innerInput).toHaveAttribute('type', 'password');

    // Cancel to exit edit mode
    await btnLocator(base.page, activePane, '取消').click();
  });

  test('should have feishu test connection button (Requirement 4.11)', async ({ base }) => {
    // Navigate to feishu tab
    const tabNav = base.page.locator(Selectors.tabNav);
    const feishuTab = tabNav.locator('.ant-tabs-tab').filter({ hasText: /^飞书/ });
    await feishuTab.click();
    await base.page.waitForTimeout(1000);

    const activePane = base.page.locator('.ant-tabs-tabpane-active');

    // The "测试连接" button should be visible (may have spaces)
    const testConnectionBtn = btnLocator(base.page, activePane, '测试连接');
    await expect(testConnectionBtn).toBeVisible();

    // Click the button - it should trigger a request (may succeed or fail depending on config)
    await testConnectionBtn.click();

    // Either a success or error message should appear
    const messageEl = base.page.locator('.ant-message');
    await messageEl.waitFor({ state: 'visible', timeout: 15000 });
  });

  test('should show feishu sheet mapping modal (Requirement 4.12)', async ({ base }) => {
    // Navigate to feishu tab
    const tabNav = base.page.locator(Selectors.tabNav);
    const feishuTab = tabNav.locator('.ant-tabs-tab').filter({ hasText: /^飞书/ });
    await feishuTab.click();
    await base.page.waitForTimeout(1000);

    const activePane = base.page.locator('.ant-tabs-tabpane-active');

    // The "查看 Sheet 映射" button should be visible
    const sheetMappingBtn = activePane.locator('button').filter({ hasText: /Sheet\s*映射/ });
    await expect(sheetMappingBtn).toBeVisible();

    // Click the button
    await sheetMappingBtn.click();

    // A modal should appear with the sheet mapping title
    const modal = base.page.locator('.ant-modal').filter({ hasText: 'Sheet 映射' });
    await modal.waitFor({ state: 'visible', timeout: 10000 });

    // The modal should contain a table structure or "无映射数据" message
    const hasTable = await modal.locator('table').count() > 0;
    const hasEmptyMessage = await modal.getByText('无映射数据').count() > 0;
    expect(hasTable || hasEmptyMessage).toBeTruthy();

    // Close the modal
    await base.page.keyboard.press('Escape');
    await modal.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
  });

  test('should confirm tab switch dialog and actually switch tab (Requirement 4.8 - confirm path)', async ({ base }) => {
    const activePane = base.page.locator('.ant-tabs-tabpane-active');

    // Enter edit mode on basic tab
    await btnLocator(base.page, activePane, '编辑').click();
    await activePane.locator('.ant-input').first().waitFor({ state: 'visible' });

    // Try to switch to feishu tab
    const tabNav = base.page.locator(Selectors.tabNav);
    const feishuTab = tabNav.locator('.ant-tabs-tab').filter({ hasText: /^飞书/ });
    await feishuTab.click();

    // Confirmation dialog should appear
    const confirmModal = base.page.locator('.ant-modal-confirm');
    await confirmModal.waitFor({ state: 'visible', timeout: 5000 });

    // Click "确定切换" to confirm
    const okBtn = confirmModal.locator('.ant-btn-primary');
    await okBtn.click();

    // Should now be on the feishu tab
    await base.page.waitForTimeout(1000);
    const feishuTabActive = tabNav.locator('.ant-tabs-tab-active').filter({ hasText: /^飞书/ });
    await expect(feishuTabActive).toBeVisible();
  });
});
