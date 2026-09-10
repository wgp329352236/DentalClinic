import type { Locator, Page } from '@playwright/test';

import { expect } from '@playwright/test';

/**
 * Vben Modal / antd Modal.confirm 的稳定定位辅助。
 *
 * 下面每一条都经真实 DOM 取证确认，收敛在这里免得每个用例各猜一套选择器：
 *
 * 1. Vben Modal 不是 antd Modal，底层是 reka-ui Dialog。页面上
 *    `.ant-modal-wrap`、`.ant-modal-root`、`[class*="vben-modal"]` 的计数全为 0；
 *    弹窗本体是 BODY 直属的 `div[role="dialog"].z-popup`。
 *
 * 2. 遮罩由 DialogOverlay.vue 渲染成 dialog 的**兄弟**节点而不是祖先：
 *    `<div :data-dismissable-modal="id" class="z-popup bg-overlay inset-0">`。
 *    所以「往上找 dialog 的祖先当遮罩」必然落空，而 `data-dismissable-modal`
 *    是源码写死的稳定属性。
 *
 * 3. `[class*="close"]` 会误命中 Tailwind 的 `data-[state=closed]:...` 这类类名
 *    （实测返回 5 个 DIV，点了没有任何反应，弹窗永远关不掉）。关闭按钮是
 *    reka-ui DialogClose 渲染的 `button`，class 由 DialogContent.vue 写死为
 *    `absolute top-3 right-3 h-6 w-6 rounded-full ...`，同一排的全屏按钮则是
 *    `right-10`，因此 `button.right-3` 在弹窗内唯一命中关闭按钮。它没有
 *    aria-label，无法用可访问名定位。
 *
 * 4. antd Button 会在两个中文字符之间插入空格：Vben Modal 的确认按钮可访问名是
 *    「确 认」，antd `Modal.confirm` 的是「确 定」，`{ name: /确定|确认/ }`
 *    两个都匹配不到。
 *
 * 5. 弹窗内按钮的实测渲染顺序是 [全屏图标, 取 消, 确 认, 关闭图标]，
 *    所以 `locator('button').last()` 命中的是关闭图标，不是确认键。
 *
 * 6. `okType: 'danger'` 只渲染 `ant-btn-dangerous`，**不带** `ant-btn-primary`，
 *    靠 `.ant-btn-primary` 定位确认按钮会超时。
 *
 * 7. Vben Form 的校验错误也不是 antd 的 `.ant-form-item-explain-error`：
 *    form-field.vue 给 FormItem 加 `form-valid-error`，FormMessage.vue 用
 *    vee-validate 的 ErrorMessage 渲染成 `p.text-destructive`。
 *
 * 8. 弹窗带进场动画：DialogContent.vue 默认 `animationType: 'slide'`，modal.vue
 *    又给了 `duration-300`，于是 `slide-in-from-top-[48%]` + `zoom-in-95` 会持续
 *    300ms。这期间 `boundingBox()` 量到的是中间态——实测动画跑到约 93% 时 y 仍
 *    比最终的 `top-[10vh]` 高 18.37px。量几何尺寸前先 `waitDialogSettled()`。
 *
 * 9. 弹窗开着时不能按 Escape 收下拉：reka-ui Dialog 也监听 Escape（modal.vue 的
 *    `@escape-key-down`），一次按键会把弹窗一起关掉。
 */

/** 当前打开的弹窗本体。 */
export function dialog(page: Page): Locator {
  return page.locator('[role="dialog"]').last();
}

/** 弹窗遮罩，是弹窗本体的兄弟节点。 */
export function dialogOverlay(page: Page): Locator {
  return page.locator('[data-dismissable-modal]');
}

/** 弹窗右上角的关闭按钮（X 图标，无 aria-label）。 */
export function dialogCloseButton(scope: Locator | Page): Locator {
  return scope.locator('button.right-3');
}

/** 弹窗的确认按钮，容忍 antd 在中文字符间插入的空格。 */
export function dialogConfirmButton(scope: Locator | Page): Locator {
  return scope.getByRole('button', { name: /确\s*[认定]/ });
}

/** 弹窗的取消按钮，同上。 */
export function dialogCancelButton(scope: Locator | Page): Locator {
  return scope.getByRole('button', { name: /取\s*消/ });
}

/** 校验未通过的表单项，仅在 `formApi.validate()` 失败后出现。 */
export function invalidFormItems(scope: Locator | Page): Locator {
  return scope.locator('.form-valid-error');
}

/**
 * 等弹窗的进出场动画结束，之后才能安全地量 `boundingBox()`（见文件头第 8 条）。
 *
 * 动画可能还没被样式系统挂上，所以先空跑两帧再取一次，避免「查不到动画就以为
 * 已经结束」。被取消的动画其 `finished` 会 reject，这里吞掉——取消本身就意味着
 * 元素已经不在动画态了。
 */
export async function waitDialogSettled(target: Locator): Promise<void> {
  await target.evaluate(async (element) => {
    const settle = () =>
      Promise.all(
        element
          .getAnimations()
          .map((animation) => animation.finished.catch(() => undefined)),
      );
    if (element.getAnimations().length > 0) {
      return settle();
    }
    await new Promise((resolve) => {
      requestAnimationFrame(() => requestAnimationFrame(resolve));
    });
    return settle();
  });
}

/**
 * 收起 antd Select 的下拉面板：`mode: 'multiple'` 选完选项后面板不会自己关，
 * 而它会盖住弹窗底部的确认按钮。
 *
 * 别用 Escape（见文件头第 9 条）——那会把整个弹窗一起关掉，失败快照里连
 * `[role="dialog"]` 都不剩，随后点确认键只能干等到用例超时。rc-select 的
 * selector 是「点一次开、再点一次关」，用 select 自己收面板最稳：面板永远贴在
 * selector 的上方或下方，不会盖住它。
 */
export async function collapseSelect(
  page: Page,
  select: Locator,
): Promise<void> {
  await select.click();
  await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0);
}
