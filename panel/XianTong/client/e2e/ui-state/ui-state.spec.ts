import { BASE_URL, expect, test } from '../fixtures/playwright-base';

/**
 * UI State E2E Tests
 *
 * Validates: Requirements 22.1-22.3
 *
 * Tests cover:
 * - Loading skeleton while data is in flight (22.1)
 * - Explicit empty state when a section has no data (22.2)
 * - Error message on API failure, without blanking the rest of the page (22.3)
 *
 * The dashboard is used as the subject page because it exercises all three
 * states in one place: four loading-gated statistic cards, four sections that
 * fall back to <Empty>, and seven independent endpoints loaded through
 * Promise.allSettled so a single failure must not blank the page.
 *
 * Coverage notes on the original 22.x set:
 * - 22.4 (success message) is covered by system/config.spec.ts (Req 15.6).
 * - 22.5-22.7 were dropped together with the game module: they asserted the
 *   game detail load-failure banner, the game save button loading state and
 *   the Feishu test-connection button, none of which exist any more.
 *
 * Route: /dashboard/analytics
 */

const DASHBOARD_PATH = '/dashboard/analytics';

/** The four realtime cards are the ones gated by the view's `loading` flag. */
const REALTIME_CARD_COUNT = 4;

/** Empty-state copy rendered by the dashboard sections. */
const EMPTY_DESCRIPTIONS = [
  '暂无门店数据',
  '暂无客户数据',
  '暂无等级数据',
  '暂无推荐记录',
] as const;

test.describe('UI State - Loading Skeleton (Req 22.1)', () => {
  test('should show skeletons while data is in flight and clear them after', async ({
    base,
  }) => {
    // Hold every dashboard response so the loading window is observable.
    await base.page.route('**/api/admin/dashboard/**', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 2000));
      await route.continue();
    });

    // domcontentloaded on purpose: networkidle would only settle after the
    // delayed responses land, by which time the skeletons are already gone.
    await base.page.goto(`${BASE_URL}${DASHBOARD_PATH}`, {
      waitUntil: 'domcontentloaded',
    });

    await expect(
      base.page.locator('.ant-card-loading'),
      '数据在途时实时指标卡片应显示骨架屏',
    ).toHaveCount(REALTIME_CARD_COUNT, { timeout: 10_000 });

    // The skeletons must clear once the data arrives, not hang forever.
    await expect(
      base.page.locator('.ant-card-loading'),
      '数据到达后骨架屏没有撤掉',
    ).toHaveCount(0, { timeout: 10_000 });
  });
});

test.describe('UI State - Empty State (Req 22.2)', () => {
  test('should fall back to explicit empty states when sections have no data', async ({
    base,
  }) => {
    // Starve the four sections that own an <Empty> branch while letting the
    // rest of the page load normally.
    await base.page.route('**/api/admin/dashboard/**', async (route) => {
      const url = route.request().url();

      if (
        url.includes('/clinic-comparison') ||
        url.includes('/customer-levels') ||
        url.includes('/customer-sources')
      ) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 0, data: [], message: 'success' }),
        });
        return;
      }

      if (url.includes('/referral')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: {
              conversionRate: 0,
              monthlyNewCustomers: 0,
              monthlyRevenue: 0,
              topReferrers: [],
            },
            message: 'success',
          }),
        });
        return;
      }

      await route.continue();
    });

    await base.navigateTo(DASHBOARD_PATH);

    for (const description of EMPTY_DESCRIPTIONS) {
      await expect(
        base.page.getByText(description),
        `空数据时「${description}」空态没有渲染`,
      ).toBeVisible({ timeout: 10_000 });
    }

    // The empty sections must not leave a half-rendered chart behind, and the
    // page must still finish loading rather than hanging on the empty charts.
    await expect(base.page.locator('.ant-card-loading')).toHaveCount(0, {
      timeout: 10_000,
    });
  });
});

test.describe('UI State - Error Message (Req 22.3)', () => {
  test('should surface an error message and keep the rest of the page alive', async ({
    base,
  }) => {
    const failureMessage = '留存统计暂不可用';

    // Fail exactly one of the seven endpoints.
    await base.page.route('**/api/admin/dashboard/retention', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ code: -1, data: null, message: failureMessage }),
      });
    });

    await base.navigateTo(DASHBOARD_PATH);

    // requestClient's errorMessageResponseInterceptor pops an antd message.
    await expect(base.page.locator('.ant-message-error')).toContainText(
      failureMessage,
      { timeout: 10_000 },
    );

    // Promise.allSettled means the other six endpoints still render: a single
    // failure must degrade one section, never blank the whole dashboard.
    await expect(base.page.getByText('今日营收')).toBeVisible({
      timeout: 10_000,
    });
    await expect(base.page.locator('.ant-card-loading')).toHaveCount(0, {
      timeout: 10_000,
    });
  });
});
