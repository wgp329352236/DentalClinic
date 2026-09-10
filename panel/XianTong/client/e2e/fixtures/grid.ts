import type { Locator, Page } from '@playwright/test';

import { expect } from '@playwright/test';

/**
 * vxe-table 的稳定定位辅助。弹窗相关的见 ./dialog.ts。
 *
 * 这里收敛的是两个反复把 E2E 打红的结构性坑，均已通过真实 DOM 取证确认：
 *
 * 1. 列定义里写了 `fixed: 'right'`（或 left）时，vxe-table 会**额外**渲染一张
 *    `table.vxe-table--body` 放在 `.vxe-table--fixed-right-wrapper` 内，与主表
 *    同名。直接用 `.vxe-table--body` 会触发 Playwright strict mode violation，
 *    而 `.first()` 命中的是主表——操作列按钮被 vxe 从主表移除了，只存在于冻结表，
 *    于是 `row.getByRole('button', { name: '编辑' })` 永远找不到元素。
 *
 * 2. 表格 0 条记录时 body 高度为 0，Playwright 判定其为 hidden
 *    （日志表现为 `33 × locator resolved to hidden <table class="vxe-table--body">`），
 *    `waitFor({ state: 'visible' })` 必然超时；而此时表头与空态提示其实都渲染正常。
 *    因此「网格就绪」必须以表头为判据，不能以表体为判据。
 *
 * 3. 也正因为有冻结表，`.vxe-table--body .vxe-body--row` 会把主表与冻结表的行
 *    **一起**匹配上，行数是真实行数的两倍：`nth(i)` 遍历到冻结表的副本行时，
 *    它只有被冻结的那几列，`.vxe-body--column` 的 `nth(4)` 之类的索引直接落空。
 *    vxe 源码里主表的 wrapper class 是 `vxe-table--body-wrapper body--wrapper`，
 *    冻结表是 `fixed-left--wrapper` / `fixed-right--wrapper`，所以 `gridRows()`
 *    用 `.body--wrapper` 精确锁定主表。
 *
 * 4. 表头是同步渲染的，数据却要等 XHR 回来。`waitGridReady()` 之后立刻调
 *    `locator.count()` 可能读到 0——`count()` 是即时快照、不会自动等待。需要
 *    行数据的用例请再接一个 `waitGridRows()`。
 *
 * 5. 表头同样会被固定列拆出副本，且副本是 **hidden** 的、在 DOM 里还排在可见的
 *    那份之前，`.first()` 会稳定命中隐藏元素。另一侧，「先 `count()` 再逐个
 *    `nth(i).textContent()` 收集文案」也不行：数据到达后 vxe 重排表头，count 读到
 *    的瞬时数量可能**大于**最终数量，`nth(i)` 就永远等不到，直接撞满 30s 超时，
 *    而报错只说一句 `waiting for locator(...).nth(7)`，完全指不到真正原因。实测
 *    角色列表这条单跑必挂、四个 worker 并发时侥幸通过。断言列请用
 *    `expectTableColumns()`。
 */

/** 等待网格渲染就绪。以表头为判据，空数据时同样成立。 */
export async function waitGridReady(
  page: Page,
  timeout = 15_000,
): Promise<void> {
  await page
    .locator('.vxe-table--header-wrapper')
    .first()
    .waitFor({ state: 'visible', timeout });
}

/** 主表的数据行，不含固定列渲染出的副本。 */
export function gridRows(page: Page): Locator {
  return page.locator('.vxe-table--body-wrapper.body--wrapper .vxe-body--row');
}

/**
 * 等到网格真的拿到数据行（见文件头第 4 条）。
 *
 * 只适用于种子数据或 mock 保证了一定有行的表。本来就可能是空表的用例别用，
 * 否则会白等一个超时；那种情况直接用会自动等待的 web-first 断言。
 */
export async function waitGridRows(
  page: Page,
  timeout = 15_000,
): Promise<void> {
  await gridRows(page).first().waitFor({ state: 'visible', timeout });
}

/**
 * 断言表头包含给定的列名，vxe 与 antd 两种表都适用（见文件头第 5 条）。
 *
 * 每一列都走会自动等待的 `toBeVisible()`，因此不依赖任何瞬时的元素数量快照：
 * 表头重排不影响结果，缺列时报错也直接点名是哪一列。
 */
export async function expectTableColumns(
  page: Page,
  expected: string[],
): Promise<void> {
  // `:visible` 不能省：省了就会命中固定列拆出的那份隐藏表头副本。
  const vxeHeader = page.locator(
    '.vxe-table--header .vxe-header--column .vxe-cell:visible',
  );
  const antdHeader = page.locator('.ant-table-thead th:visible');
  const headerCells = vxeHeader.or(antdHeader);

  await expect(headerCells.first()).toBeVisible();
  for (const column of expected) {
    await expect(
      headerCells.filter({ hasText: column }).first(),
      `表头里找不到「${column}」列`,
    ).toBeVisible();
  }
}

/**
 * 操作列按钮。按名称全局定位再取序号，比「先定位行、再在行内找按钮」更稳：
 * 固定列的按钮不在主表行内，而这种写法在该表有无 fixed 列时都成立。
 */
export function gridActionButton(
  page: Page,
  name: RegExp | string,
  index = 0,
): Locator {
  return page.getByRole('button', { name }).nth(index);
}

/**
 * 展开树形表格的全部节点。
 *
 * vxe-table 4.18 的树开关是 `.vxe-cell--tree-node` 内的 `.vxe-cell--tree-btn`，
 * 展开后父节点带 `is--active`；`.vxe-tree--btn-wrapper` 与 `.vxe-tree--node-btn`
 * 这两个类名在当前版本里并不存在。树默认是折叠的，所以只断言根节点看不到的
 * 子节点（如菜单/按钮类型）之前必须先展开。
 *
 * 展开会渲染出新的子行，其中可能还有折叠的，因此每轮都重新查询、只点当前仍
 * 折叠的第一个，直到没有为止——这比一次性点所有开关安全，因为点已展开的节点
 * 会把它折回去。
 */
export async function expandAllTreeNodes(
  page: Page,
  maxClicks = 100,
): Promise<void> {
  // 同第 4 条：数据没到时第一轮 `count()` 就是 0，函数会静默地什么都不做，
  // 调用方随后断言子节点必然失败，且报错信息完全指不到真正的原因。
  await waitGridRows(page);
  for (let i = 0; i < maxClicks; i++) {
    const collapsed = gridRows(page).locator(
      '.vxe-cell--tree-node:not(.is--active) .vxe-cell--tree-btn',
    );
    if ((await collapsed.count()) === 0) return;
    await collapsed.first().click();
    await page.waitForTimeout(150);
  }
  throw new Error(`树节点展开点击超过 ${maxClicks} 次仍未收敛`);
}
