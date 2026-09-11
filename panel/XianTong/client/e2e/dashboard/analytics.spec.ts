import type { Page } from '@playwright/test';

import { expect, test } from '../fixtures/playwright-base';

/**
 * Dashboard Analytics E2E Tests
 *
 * Validates: Requirement 4.10 (数据看板)
 *
 * Tests cover:
 * - All seven dashboard endpoints respond 200 for a real admin session
 * - The four realtime statistic cards render numeric values
 * - Each of the four charts mounts a real ECharts canvas with non-zero size
 * - Retention / referral statistics render
 * - Referrer TOP10 renders either data rows or an explicit empty state
 * - Switching the trend range refetches with days=7
 * - The dashboard is reachable as the default home path
 *
 * Unlike the other specs in this suite these tests deliberately do NOT mock the
 * dashboard APIs. The whole point is to prove the page renders real backend
 * data, so a mock would only prove that the mock works.
 *
 * Route: /dashboard/analytics
 */

const DASHBOARD_PATH = '/dashboard/analytics';

/**
 * The seven read-only endpoints the dashboard is composed of.
 * Kept in sync with server/admin/route/DashboardRoutes.kt and
 * apps/web-antd/src/api/dashboard.ts.
 */
const DASHBOARD_APIS = [
  'summary',
  'trends',
  'clinic-comparison',
  'customer-levels',
  'customer-sources',
  'referral',
  'retention',
] as const;

/** Titles of the four cards that host an ECharts instance. */
const CHART_CARDS = [
  '经营趋势',
  '门店对比（本月）',
  '客户来源占比',
  '客户等级分布',
] as const;

/**
 * Locate an Ant Design Card by its header title.
 * The realtime statistic cards have no header, so they never match here.
 */
function cardByTitle(page: Page, title: string) {
  return page
    .locator('.ant-card')
    .filter({ has: page.locator('.ant-card-head-title', { hasText: title }) });
}

/**
 * Read the rendered value of an Ant Design Statistic by its title.
 * Returns the numeric part only, without prefix (¥) or suffix (人/%).
 */
async function statisticValue(page: Page, title: string): Promise<string> {
  const statistic = page
    .locator('.ant-statistic')
    .filter({ has: page.locator('.ant-statistic-title', { hasText: title }) });

  await expect(statistic, `统计项「${title}」未渲染`).toBeVisible();

  const raw = await statistic
    .locator('.ant-statistic-content-value')
    .first()
    .textContent();
  return raw?.trim() ?? '';
}

/**
 * Wait until the dashboard has finished its initial load.
 *
 * Only the four realtime cards are gated by `loading`; the 客户留存 and 推荐概览
 * statistics render immediately with their zeroed defaults. Waiting on
 * `.ant-statistic` therefore resolves before any response arrives and proves
 * nothing. The realtime cards render an antd skeleton while `loading` is true,
 * so the skeletons disappearing is the real "data is on screen" signal.
 */
async function waitForDashboardReady(page: Page): Promise<void> {
  await page
    .locator('.ant-statistic')
    .first()
    .waitFor({ state: 'visible', timeout: 20_000 });
  await expect(
    page.locator('.ant-card-loading'),
    '实时指标卡片仍在骨架屏状态',
  ).toHaveCount(0, { timeout: 20_000 });
}

test.describe('Dashboard Analytics - Data Loading', () => {
  test('should load all seven dashboard endpoints with HTTP 200', async ({
    base,
  }) => {
    const statusByApi = new Map<string, number>();

    // Attach before navigating so the initial burst of requests is captured.
    base.page.on('response', (response) => {
      const url = response.url();
      for (const api of DASHBOARD_APIS) {
        if (url.includes(`/api/admin/dashboard/${api}`)) {
          statusByApi.set(api, response.status());
        }
      }
    });

    await base.navigateTo(DASHBOARD_PATH);
    await waitForDashboardReady(base.page);

    // Poll rather than assert immediately: the seven requests race each other,
    // and reporting the stragglers by name makes a slow or missing endpoint
    // obvious instead of looking like a flaky wait.
    await expect
      .poll(
        () => DASHBOARD_APIS.filter((api) => !statusByApi.has(api)).join(', '),
        { message: '以下看板接口始终没有被请求', timeout: 20_000 },
      )
      .toBe('');

    for (const api of DASHBOARD_APIS) {
      expect(statusByApi.get(api), `/dashboard/${api} 返回非 200`).toBe(200);
    }
  });

  test('should not surface a request error message on load', async ({
    base,
  }) => {
    await base.navigateTo(DASHBOARD_PATH);
    await waitForDashboardReady(base.page);

    // requestClient pops an antd message on failure; a clean load shows none.
    await expect(base.page.locator('.ant-message-error')).toHaveCount(0);
  });
});

test.describe('Dashboard Analytics - Realtime Statistics', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(DASHBOARD_PATH);
    await waitForDashboardReady(base.page);
  });

  test('should render the four realtime statistic cards as numbers', async ({
    base,
  }) => {
    const titles = [
      '今日预约数',
      '今日到店数',
      '今日营收',
      '今日新增客户',
    ] as const;

    for (const title of titles) {
      const value = await statisticValue(base.page, title);
      // Ant Design Statistic renders 0 as "0" and 190.0 with precision as "190.00".
      // Anything that is not a plain number means the binding broke.
      expect(
        value,
        `统计项「${title}」的值不是数字，实际渲染为「${value}」`,
      ).toMatch(/^[\d,]+(?:\.\d+)?$/);
    }
  });

  test('should render retention and referral statistics', async ({ base }) => {
    const titles = [
      '复诊率',
      '流失率',
      '推荐注册转化率',
      '推荐带来的新客',
      '推荐带来的营收',
    ] as const;

    for (const title of titles) {
      const value = await statisticValue(base.page, title);
      expect(value, `统计项「${title}」渲染为空`).not.toBe('');
      expect(
        value,
        `统计项「${title}」的值不是数字，实际渲染为「${value}」`,
      ).toMatch(/^[\d,]+(?:\.\d+)?$/);
    }
  });
});

test.describe('Dashboard Analytics - Charts', () => {
  test.beforeEach(async ({ base }) => {
    await base.navigateTo(DASHBOARD_PATH);
    await waitForDashboardReady(base.page);
  });

  test('should mount a sized ECharts canvas in each chart card', async ({
    base,
  }) => {
    for (const title of CHART_CARDS) {
      const card = cardByTitle(base.page, title);
      await expect(card, `图表卡片「${title}」未渲染`).toBeVisible();

      // EchartsUI is a bare div; ECharts injects the canvas on init. A canvas
      // with a non-zero box proves the instance was created and actually laid
      // out, which a detached or zero-height container would not.
      const canvas = card.locator('canvas').first();
      await expect(canvas, `图表「${title}」没有渲染出 canvas`).toBeVisible();

      const box = await canvas.boundingBox();
      expect(
        box?.width ?? 0,
        `图表「${title}」canvas 宽度为 0`,
      ).toBeGreaterThan(0);
      expect(
        box?.height ?? 0,
        `图表「${title}」canvas 高度为 0`,
      ).toBeGreaterThan(0);
    }
  });

  test('should render data instead of an empty state when data exists', async ({
    base,
  }) => {
    // The dev database is seeded with clinics, customer levels and sources, so
    // these three cards must draw a chart rather than fall back to <Empty>.
    // If the seed data is ever removed this assertion will point straight at it.
    for (const title of ['门店对比（本月）', '客户来源占比', '客户等级分布']) {
      const card = cardByTitle(base.page, title);
      await expect(card.locator('canvas').first()).toBeVisible();
      await expect(
        card.locator('.ant-empty'),
        `图表「${title}」落到了空态，说明接口没返回数据`,
      ).toHaveCount(0);
    }
  });
});

test.describe('Dashboard Analytics - Referrer TOP10', () => {
  test('should render rows or an explicit empty state', async ({ base }) => {
    await base.navigateTo(DASHBOARD_PATH);
    await waitForDashboardReady(base.page);

    const card = cardByTitle(base.page, '推荐人 TOP10');
    await expect(card, '推荐人 TOP10 卡片未渲染').toBeVisible();

    // The table only mounts once the referral response arrives, which
    // waitForDashboardReady has already guaranteed.
    const rows = card.locator('.ant-table-tbody .ant-table-row');
    const rowCount = await rows.count();
    if (rowCount === 0) {
      await expect(
        card.locator('.ant-empty'),
        '推荐人 TOP10 既没有数据行也没有空态，属于渲染缺失',
      ).toBeVisible();
    } else {
      await expect(card.locator('.ant-table-thead')).toBeVisible();
      expect(rowCount, '推荐人 TOP10 最多 10 行').toBeLessThanOrEqual(10);
    }
  });
});

test.describe('Dashboard Analytics - Trend Range Switch', () => {
  test('should load days=7 initially and refetch days=30 on switch', async ({
    base,
  }) => {
    const trendRequests: string[] = [];

    // Attach before navigating: the very first load already requests the
    // default range, and the view defaults trendDays to 7.
    base.page.on('request', (request) => {
      const url = request.url();
      if (url.includes('/api/admin/dashboard/trends')) {
        trendRequests.push(url);
      }
    });

    await base.navigateTo(DASHBOARD_PATH);
    await waitForDashboardReady(base.page);

    await expect
      .poll(
        () => trendRequests.filter((url) => url.includes('days=7')).length,
        {
          message: '初始加载没有按默认 trendDays=7 请求趋势数据',
          timeout: 10_000,
        },
      )
      .toBeGreaterThan(0);

    const thirtyDays = base.page
      .locator('.ant-radio-button-wrapper', { hasText: '近 30 天' })
      .first();
    await expect(thirtyDays, '趋势范围切换控件未渲染').toBeVisible();
    await thirtyDays.click();

    // 30 is a genuine value change. Clicking the already-selected "近 7 天"
    // would not fire antd's change event, so the refetch would never happen.
    await expect
      .poll(
        () => trendRequests.filter((url) => url.includes('days=30')).length,
        {
          message: '切到近 30 天后没有发出 days=30 的趋势请求',
          timeout: 10_000,
        },
      )
      .toBeGreaterThan(0);

    // The chart must survive the refetch rather than being torn down.
    const trendCard = cardByTitle(base.page, '经营趋势');
    await expect(trendCard.locator('canvas').first()).toBeVisible();
  });
});

test.describe('Dashboard Analytics - Home Path', () => {
  test('should land on the dashboard when opening the app root', async ({
    base,
  }) => {
    // preferences.ts sets defaultHomePath to /dashboard and the seeded menu
    // redirects that directory to its first child.
    await base.navigateTo('/');
    // navigateTo 只等 domcontentloaded，返回时 SPA 的路由守卫还没跑完，URL 仍是 "/"。
    // 这条用例要等的信号本来就是「守卫把根路径重定向到看板」，所以直接等 URL；
    // 原来的 networkidle 只是间接等到了它，一旦页面有轮询或长连接就会等到超时。
    await base.page.waitForURL(/\/dashboard/, { timeout: 15_000 });

    expect(
      base.page.url(),
      '打开根路径后没有落到看板，首页指向可能失效',
    ).toContain('/dashboard');
  });
});

test.describe('Dashboard Analytics - Visual Evidence', () => {
  test('should capture a full page screenshot', async ({ base }) => {
    await base.navigateTo(DASHBOARD_PATH);
    await waitForDashboardReady(base.page);

    // Give ECharts time to finish its enter animation before shooting.
    await base.page.waitForTimeout(1500);

    await base.page.screenshot({
      fullPage: true,
      path: 'test-results/dashboard-analytics-full.png',
    });
  });
});
