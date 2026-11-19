import type { Page } from '@playwright/test';

import { API_BASE_URL, test as authTest } from './auth.fixture';

/**
 * Base URL for the frontend dev server.
 */
export const BASE_URL = 'http://localhost:5666';

/**
 * API URL for direct backend requests.
 */
export { API_BASE_URL };

/**
 * 各 spec 共用的页面操作入口。
 *
 * 这里刻意只留真正被用到的东西。它曾经还带着一整套基于 `.ant-table` /
 * `.ant-modal` 的 helper（waitForTableLoad、getTableRows、openModal、
 * closeModal、submitForm、confirmDialog、clickRowAction……）以及配套的
 * `Selectors` 常量表，但本应用的表格是 vxe-table、弹窗是 reka-ui Dialog，
 * 那些选择器一个都命中不了；它们在全站零引用，摆在 fixture 里只会误导后来
 * 写测试的人。网格与弹窗的稳定定位分别见 ./grid.ts 与 ./dialog.ts。
 */
export class PlaywrightBase {
  constructor(public readonly page: Page) {}

  /**
   * Navigate to a specific route path within the application.
   *
   * 只等 DOMContentLoaded，不等 networkidle：Vite dev server 下的按需编译与 HMR
   * websocket 会让网络一直有活动，实测 `networkidle` 会让 goto 直接超时（曾一次
   * 打红 14 个用例）。页面是否就绪交给各页自己的信号判断，列表页用
   * `waitGridReady()`。
   *
   * @param path - 路由路径（如 '/system/user'），也接受完整 URL
   */
  async navigateTo(path: string): Promise<void> {
    const url = path.startsWith('http') ? path : `${BASE_URL}${path}`;
    await this.page.goto(url, { waitUntil: 'domcontentloaded' });
  }
}

/**
 * Extended test fixture that provides both authentication and PlaywrightBase
 * helpers.
 */
export const test = authTest.extend<{ base: PlaywrightBase }>({
  base: async ({ authenticatedPage }, use) => {
    const helper = new PlaywrightBase(authenticatedPage);
    await use(helper);
  },
});

export { expect } from '@playwright/test';
