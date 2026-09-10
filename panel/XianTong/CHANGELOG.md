# 变更日志

本文件按日期倒序记录 XianTong 项目的代码改动，每条包含：改了什么、为什么改、影响范围。

## 2026-09-10

本轮集中修复「工程链路可运行性」与「测试有效性」，共四项（B/D/C/A），并顺带修复两个真实缺陷。

### B. 恢复 monorepo 脚本入口（`pnpm lint` / `format` / `check:*` 全线不可用）

- **改了什么**：新建 `client/scripts/vsh/bin/vsh.mjs` 与 `client/scripts/turbo-run/bin/turbo-run.mjs`，各为两行 shim（`#!/usr/bin/env node` + `import '../dist/index.mjs';`），并赋予可执行权限。
- **原因**：两个包的 `package.json` 都声明了 `bin` 字段，但对应文件在仓库中缺失且从未提交，导致 `node_modules/.bin/vsh`、`.bin/turbo-run` 无法生成。`package.json` 中 `lint`、`format`、`check`、`check:circular`、`check:dep` 等脚本全部以 `vsh` 为入口，因此整条质量链在干净检出后直接崩溃。
- **影响范围**：仅新增文件，不改动任何现有逻辑。恢复后 `pnpm run check` 端到端 EXIT=0（循环依赖检查、依赖检查、38 个包类型检查、cspell 473 个文件）。

### D. 看板真实浏览器实测 + 新增 e2e 规格

- **改了什么**：新建 `client/e2e/dashboard/analytics.spec.ts`（10 个用例），覆盖看板 7 个接口全部返回 200、无错误提示、4 张实时指标卡渲染为数字、留存/推荐统计、4 张 ECharts 画布尺寸非零且非空态、推荐 TOP10、趋势初始 `days=7` 且切换 30 天后重新取数、根路径落到 `/dashboard`、整页截图。
- **原因**：看板此前只有静态代码，从未在真实浏览器中验证过图表是否真的画出来。
- **影响范围**：新增测试文件；实测结果 10/10 全绿，截图确认菜单、营收 ¥190.00、趋势三线双轴、8 家门店柱状图、来源环形图、等级分布、推荐 ¥47,590.00、TOP10 均真实渲染。

### 顺带修复缺陷一：e2e 认证层持久化键名错误（导致整套 e2e 从未可运行）

- **改了什么**：新建 `client/e2e/fixtures/app-storage.ts`，从 `apps/web-antd/.env` 的 `VITE_APP_NAMESPACE` 与 `package.json` 的 `version` 动态推导持久化键；修正 `client/e2e/global-setup.ts` 与 `client/e2e/fixtures/auth.fixture.ts` 改用该 helper。
- **原因**：两处旧代码把 token 写在 `vben-web-antd-access-store`，而 Vben 实际键名规则是 `${namespace}-${version}-${env}-${storeId}`，即 `vben-web-antd-5.7.0-dev-core-access`。键名不匹配使注入的登录态完全失效，所有测试都被重定向到 `/auth/login`。
- **影响范围**：e2e 基础设施。修复前全套 0 通过；修复后全量套件 62 通过 / 29 失败 / 3 跳过。采用动态推导而非硬编码版本号，避免版本升级后再次失效。

### 顺带修复缺陷二：`useEcharts` 空数据时每 30ms 无限重试

- **改了什么**：`client/apps/web-antd/src/views/dashboard/analytics/index.vue` 的 `renderCharts` 增加判空守卫，门店/来源/等级三张图在无数据时直接跳过渲染。
- **原因**：无数据时模板中的 `<Empty>` 会顶掉图表容器，容器 ref 变为 `undefined`；`useEcharts` 内部 `chartRef.value?.offsetHeight === 0` 对 `undefined` 求值得 `false`，随后 `isElHidden(null)` 为 `true`，于是进入每 30ms 一次且永不 resolve 的重试循环，使 `Promise.all` 挂死并遗留常驻定时器。
- **影响范围**：看板页面。该文件同时承载上一轮未提交的看板重构改动，文件粒度无法与本次守卫拆分。

### C. 清理两个失效 e2e 文件

- **改了什么**：删除 `client/e2e/system/inspect-menu.spec.ts`；将 `client/e2e/ui-state/ui-state.spec.ts` 从 472 行的 game 模块绑定完全重写为约 160 行、承载于看板的 22.1–22.3 三个用例（骨架屏、空态、错误提示与优雅降级）。
- **原因**：`inspect-menu.spec.ts` 只打印菜单结构、无任何断言，其覆盖范围已被 `menu-management.spec.ts` 的 15 个真实测试完全包含；`ui-state.spec.ts` 测试的 game 模块已在上一轮重构中删除，整个文件不可能通过。
- **影响范围**：重写后 3/3 全绿。文件中已注明 22.4 由 `config.spec.ts` 的 Req 15.6 覆盖，22.5–22.7 随 game 模块删除而作废。

### A. 清理 `pnpm-workspace.yaml` 未使用的 catalog 条目

- **改了什么**：从 `client/pnpm-workspace.yaml` 删除 17 条未被任何 workspace 包引用的 catalog 条目：`@changesets/changelog-github`、`@changesets/cli`、`@faker-js/faker`、`@nolebase/vitepress-plugin-git-changelog`、`@tanstack/vue-query`、`@types/jsonwebtoken`、`@vite-pwa/vitepress`、`antdv-next`、`element-plus`、`h3`、`jsonwebtoken`、`medium-zoom`、`naive-ui`、`tdesign-vue-next`、`unplugin-element-plus`、`vitepress`、`vitepress-plugin-group-icons`。保留仍在使用的 `@changesets/git`。
- **原因**：eslint 的 `yaml-no-unused-catalog-item` 规则对此报告 17 项错误，使 `pnpm lint` 恒定失败。
- **影响范围**：`pnpm-lock.yaml` 无需重生成——未使用的 catalog 条目本就不会进入锁文件，`pnpm install --frozen-lockfile` EXIT=0 已证明二者一致。清理后 `pnpm run lint` **首次全绿**（oxfmt 1045 个文件、oxlint 650 个文件 / 217 条规则均 0 warning 0 error），`pnpm run check` EXIT=0。

### 遗留问题（本轮未修，均为既有缺陷）

全量 e2e 基线为 62 通过 / 29 失败 / 3 跳过。这 29 个失败分布在 6 个本轮未改动的规格文件中，此前被认证层缺陷完全掩盖，根因已定位：

1. **`e2e/system/notification.spec.ts`（10 个）**：通知规则与历史表实际返回 0 条记录，页面显示「暂无数据 / 共 0 条记录」，vxe-table 的 `.vxe-table--body` 因高度为 0 被 Playwright 判定 hidden，`beforeEach` 等待其 visible 直接超时。表头 7 列其实全部正确渲染（Req 16.1 本可通过）。此外 Req 16.2 等用例在 `beforeEach` 导航之后才安装 `page.route` mock，真实空响应已先加载完毕，mock 时序错误。
2. **`e2e/system/menu-management.spec.ts`（9 个）**：vxe-table 的固定列会额外渲染一张 `<table class="vxe-table--body">`，导致该选择器触发 strict mode violation；而 `.first()` 命中的是左侧冻结表，其中不含「编辑 / 删除」按钮，相关点击全部超时。
3. **`e2e/modal/modal-interaction.spec.ts`（6 个）**：定位器依赖 `[role="dialog"]` 的祖先带 `ant-modal-wrap` 或 `vben-modal` 类名，实际 DOM 不匹配。
4. **`e2e/system/user-management.spec.ts`（2 个）、`role-management.spec.ts`（1 个）**：以「对话框内最后一个按钮」或 `/确定|确认/` 定位提交按钮并等待写接口，点击超时。
5. **`e2e/routing/route-guard.spec.ts`（1 个）**：Req 19.4 用全新无登录态上下文直接访问 `/system/user`，10 秒内未跳转到 `/auth/login`。该用例自建 context，不受本轮认证层修复影响，属既有失败；同文件内「先清空 localStorage 再访问」的同类用例通过，两者差异值得进一步排查。

另有框架级告警（不阻断 `check`）：`check:circular` 报框架内 2 处循环依赖，`check:dep` 报 5 条未使用依赖，`publint` EXIT=1（46 条，未纳入 `check` 链）。
