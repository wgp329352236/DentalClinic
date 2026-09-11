# 变更日志

本文件按日期倒序记录 XianTong 项目的代码改动，每条包含：改了什么、为什么改、影响范围。

## 2026-09-11

### F. 牙科业务模块整体入库，同时下线 game / queue / task 三个旧模块

本节 112 个文件的变更（47 修改 / 21 删除 / 44 新增）**并非本次会话实现**，而是工作区中长期存在但从未提交的既有成果。本次只做两件事：入库前把六道验证门全部跑通，以及如实记录变更构成。各模块的设计意图未在会话中留痕，因此下文按文件与模块客观归类，不揣测动机。

#### F-1. 新增：牙科业务前端 10 个模块（36 个文件）

- **改了什么**：新增 10 个 API 层文件 `apps/web-antd/src/api/{appointment,clinic,consumption,coupon,customer,department,doctor,points,referral,schedule}.ts`，以及对应的 10 个视图目录（共 26 个 `.vue`）。按页面 `table-title` 取证的模块名与文件数：门诊管理 clinic 2、科室管理 department 2、医生管理 doctor 2、医生排班 schedule 2、预约管理 appointment 2、客户管理 customer 3、优惠券模板与发放记录 coupon 3、积分记录与积分规则 points 3、推荐关系与推荐奖励记录 referral 3、消费流水 consumption 4。
- **原因**：既有成果入库。模块间存在交叉引用，例如 `views/consumption/create-modal.vue` 与 `views/coupon/{index,record,form-modal}.vue` 都依赖 `#/api/coupon`，因此这 36 个文件必须整体入库，缺一即构建失败——这正是上一轮不肯单独提交 `coupon/form-modal.vue` 的原因。
- **影响范围**：`views/coupon/form-modal.vue` 中上一轮补的 `validate()` 修复随本节一并入库，E-2 记录的「留在工作区」状态至此消除，四处 `validate()` 修复全部入库。

#### F-2. 新增：后端 7 个 Kotlin 文件与 `server/.gitignore`

- **改了什么**：新增 `admin/route/ConsumptionRoutes.kt`、`admin/route/ReferralRoutes.kt`、`admin/service/AdminReferralService.kt`、`admin/service/ConsumptionAdminService.kt`、`common/ReferralRewardService.kt`、`common/config/SchemaMigration.kt`、`user/route/UserApiErrors.kt`，以及 `server/.gitignore`。
- **原因**：既有成果入库。
- **影响范围**：admin 与 user 两侧路由；`SchemaMigration.kt` 与 `ReferralRewardService.kt` 属 common 层、被多模块共享，`UserApiErrors.kt` 统一 C 端错误响应。

#### F-3. 修改：后端 28 个 Kotlin 文件 + 配置与种子数据

- **改了什么**：`admin/` 下 13 个（`AdminModule.kt`、`auth/RbacPlugin.kt`、`dto/response/BusinessResponse.kt`、`entity/AdminTables.kt`、`route/{Business,Coupon,Dashboard}Routes.kt`、`service/` 下 7 个）、`user/` 下 14 个（`UserModule.kt`、`route/` 下 8 个、`service/` 下 6 个）、`common/BaseService.kt`，另有 `src/main/resources/application.yaml` 与 `test-data.sql`。已跟踪文件合计 +2072 / −3344。
- **原因**：既有成果入库。其中 `ConfigRoutes.kt` 的 `.toList()` 修复已随上一节单独提交，不在本节范围。
- **影响范围**：整个后端。`./gradlew :server:cleanCompileKotlin :server:compileKotlin` 强制重编 BUILD SUCCESSFUL，`:server:test` 同样 BUILD SUCCESSFUL。仅剩一条既有 deprecation 告警：`user/service/FileUploadService.kt:41` 使用了已废弃的 `PartData.FileItem.streamProvider`（阻塞式 InputStream），官方建议改用 `provider()`。属技术债，本次未动。

#### F-4. 删除：game / queue / task 三个旧模块、notification 旧位置与静态路由模块

- **改了什么**：删除 21 个文件——game 模块 9 个（`api/game.ts`、`views/game/detail/{index,sheet-mapping-modal}.vue`、`views/game/list/{clone,create,delete}-modal.vue`、`views/game/list/index.vue`、`e2e/game/{game-detail,game-list}.spec.ts`）、queue 模块 3 个（`api/queue.ts`、`views/queue/{dlq,status}/index.vue`）、task 模块 5 个（`api/task.ts`、`views/task/history/{detail-modal,index}.vue`、`views/task/{schedule,trigger}/index.vue`）、notification 旧位置 3 个（`views/system/notification/{history,rules,rule-form-modal}.vue`）、以及 `router/routes/modules/dashboard.ts`。
- **原因**：game / queue / task 属项目模板遗留，与牙科门诊业务无关；notification 三个文件是迁往 `views/notification/{history,rules}/` 后留下的旧位置副本，新位置的文件本就在版本库中并已修改。删除 `router/routes/modules/dashboard.ts` 后该目录为空，与 `accessMode: 'backend'`（菜单全部由后端 `/menu/all` 下发）一致；`locales/langs/{zh-CN,en-US}/page.json` 同步删掉 dashboard 文案段，同理。
- **影响范围**：`e2e/game/` 两个规格随模块下线一并删除，这是 E 节里 e2e 用例总数保持 94 不变的原因。typecheck 通过说明前端无残留引用。

#### F-5. 修改：前端 17 个文件

- **改了什么**：`api/core/auth.ts`、`api/dashboard.ts`、`api/request.ts`、`layouts/basic.vue`、`locales/langs/{en-US,zh-CN}/page.json`、`preferences.ts`、`views/notification/history/index.vue`、`views/notification/rules/{index,rule-form-modal}.vue`、`views/system/audit/{detail-modal,index}.vue`、`views/system/{config,menu,role,user}/index.vue`，以及 `packages/@core/preferences/__tests__/__snapshots__/config.test.ts.snap`。
- **原因**：既有成果入库。其中两处可以确证：`preferences.ts` 的改动**只新增 3 行注释**，说明 `defaultHomePath: '/dashboard'` 需与后端下发的 `userInfo.homePath` 对齐，值本身未变；快照文件的改动是**修正陈旧快照**——`packages/@core/preferences/src/config.ts` 的实际默认值早已是 `/dashboard`、`companyName: ''`、`companySiteLink: ''`、`date: '2025'`、`settingShow: false`，且该源文件无未提交改动，而快照却仍停留在框架原始的 `/analytics`、`Vben`、`2024`、`true`。
- **影响范围**：快照修正后 `pnpm run test:unit` 的 35 个测试文件 / 310 个测试全通过，其中 `config.test.ts` 通过即为源码与快照一致的确证。

#### F-6. 入库前的六道验证门

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 代码风格 | `pnpm run lint` | EXIT=0（oxfmt 1047 文件、oxlint 652 文件 / 217 规则、eslint 均 0 warning 0 error） |
| 依赖与类型 | `pnpm run check` | EXIT=0（typecheck 通过、cspell 475 文件 0 issues；circular 2 处与 dep 5 条为框架既有告警，不阻断） |
| 前端单元测试 | `pnpm run test:unit` | 35 个测试文件 / 310 个测试全通过 |
| 端到端 | `pnpm exec playwright test` | 94 passed / 0 failed / 0 skipped |
| 后端编译 | `./gradlew :server:cleanCompileKotlin :server:compileKotlin` | BUILD SUCCESSFUL（强制重编），1 条既有 deprecation 告警 |
| 后端测试 | `./gradlew :server:test` | BUILD SUCCESSFUL（`compileTestKotlin` 与 `test` 均真实执行） |

两点需要说明。其一，lint / check / test:unit / e2e 四道门扫描的都是磁盘文件而非 git 暂存区，因此它们在本节这批未跟踪文件在场的情况下就已经通过；后端两道门则是本次专门为入库补跑的，第一次执行 `compileKotlin` 时返回 `UP-TO-DATE`（复用缓存产物、等于没验证），改用 `cleanCompileKotlin` 强制重编后才算数。其二，新增的 10 个业务模块目前**没有 e2e 覆盖**（现有 94 个用例集中在 dashboard / system / modal / routing / ui-state），其正确性只有 typecheck、lint 与后端编译/测试保证，功能层面未经浏览器实测。

### G. 清掉 F-6 记下的两项遗留

#### G-1. `FileUploadService.saveFile` 改用非阻塞的 `provider()`

- **改了什么**：`server/src/main/kotlin/user/service/FileUploadService.kt` 中读上传字节的两行（原为 `val inputStream = part.streamProvider()` 加 `val bytes = inputStream.readBytes()`）改为单行 `val bytes = part.provider().toByteArray()`，并新增 `import io.ktor.utils.io.toByteArray`。
- **原因**：`streamProvider` 在 Ktor 3.5.0 已废弃，告警原文是「This API uses blocking InputStream. Please use provider() directly」。它把上传流包成阻塞式 `InputStream`，读的时候线程会一直卡在 socket 上；`provider()` 返回 `ByteReadChannel`，`toByteArray()` 是它的 suspend 扩展，等数据时能让出线程。API 选型是从 Gradle 缓存里的 `ktor-io-jvm-3.5.0.jar` 反编译确认的：`readRemaining()` 在 3.5.0 返回的是 `kotlinx.io.Source`（不是 okio，Ktor 3.x 已换 IO 库），凭印象写 `.buffer().readByteArray()` 会编译失败；而 `ByteReadChannelOperationsKt` 里本来就有 `toByteArray(ByteReadChannel, Continuation)`，直接用最省事。
- **影响范围**：仅 C 端上传端点 `POST /api/v1/files/upload`。`withContext(Dispatchers.IO)` 保留未动——读取虽已非阻塞，但同函数内的 `Files.write(filePath, bytes)` 仍是阻塞文件 IO，去掉会让它跑到默认调度器上。`FileUploadRoutes.kt` 里 `part.release()` 在 `saveFile` 之后调用，读取时机安全，无需改动。

#### G-2. 看板首页用例改等 URL 而非网络静默

- **改了什么**：`client/e2e/dashboard/analytics.spec.ts` 的 `should land on the dashboard when opening the app root`，把 `await base.page.waitForLoadState('networkidle')` 换成 `await base.page.waitForURL(/\/dashboard/, { timeout: 15_000 })`。
- **原因**：这条用例要等的信号本来就是「路由守卫把根路径重定向到看板」，`networkidle` 只是间接等到了它，一旦页面有轮询或长连接就会一直等到超时——E-4 里 notification 16.3 与 route-guard 19.3 正是这么被打红的。取证发现 `playwright-base.ts` 的 `navigateTo` 用的是 `waitUntil: 'domcontentloaded'`，返回时 SPA 守卫还没跑完、URL 仍是 `/`，所以这里的等待是必需的，不能直接删。
- **影响范围**：单个用例。改完该用例耗时从 3.9s 降到 2.1s，因为 `networkidle` 必须额外等满 500ms 的网络静默窗口，而 URL 一变即可返回。后面那句 `expect(page.url()).toContain('/dashboard')` 保留：逻辑上已被 `waitForURL` 蕴含，但它承载的是业务断言与中文失败消息，`waitForURL` 只表达等待条件。

#### G-3. 验证结果与一个验证边界

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 后端编译 | `./gradlew :server:cleanCompileKotlin :server:compileKotlin` | BUILD SUCCESSFUL，**F-3 记的那条 deprecation 告警已消失**（日志无任何 `w:` 行） |
| 上传端到端 | curl 走 mock 微信登录取 token 后真实上传 | 上传返回 `size=70` 与原文件一致（字节无截断）；GET 回来的 md5 与原文件逐字节相同；6MB 文件被正确挡下（`文件大小不能超过5MB`）；随后 DELETE 清理成功 |
| 代码风格 | `pnpm run lint` | EXIT=0 |
| 依赖与类型 | `pnpm run check` | EXIT=0（cspell 475 文件 0 issues） |
| 端到端 | `pnpm exec playwright test` | 94 passed / 0 failed / 0 skipped，用例分布与 F 节基线完全一致 |

两点说明。其一，微信登录在 `UserAuthService.wechatLogin` 里是 mock 的（`openid = "mock_openid_${code}"`），任意 code 都会自动建号发 token，因此上传链路可以真实跑通而不必依赖微信侧凭证。这也是本节改上传读取方式的底气：不止编译通过，字节正确性是实测过的。其二，`pnpm run check` 里的 typecheck 这次是 `FULL TURBO` 缓存命中（125ms），原因是 `@vben/web-antd:typecheck` 只扫描 `apps/web-antd/`，而 `client/e2e/` 不在该包内——**本次对 e2e 的改动并未被静态类型检查覆盖**，其正确性靠运行时（94 passed）与 IDE 语言服务验证。`waitForURL` 接 RegExp 加 options 是 Playwright 的合法重载，但这一层保证不如编译强，值得后续补一个覆盖 `e2e/` 的 typecheck 任务。

本节开始时后端与前端 dev server 均已停止（8080 无监听，进程列表里只剩 Gradle 与 Kotlin 编译守护进程），后端按 `./gradlew :server:run` 重新拉起；前端由 `playwright.config.ts` 的 `webServer`（`pnpm dev:antd`，端口 5666，`reuseExistingServer: true`）自动启动，无需手动干预。启动日志中另有一条既有告警：`[Referral]: 未配置 referral.shareBaseUrl，分享链接仍指向占位域名 https://example.com/register，上线前必须替换`，属 F-3 入库代码的配置项，本次未动。

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

### E. e2e 全量转绿：修完上述遗留失败与 3 个假跳过

上一节记录的基线是 62 通过 / 29 失败 / 3 跳过。本轮全部修完，最终基线为 **94 通过 / 0 失败 / 0 跳过（约 2.5 分钟）**，`pnpm run lint` 与 `pnpm run check` 均 EXIT=0（typecheck 通过、cspell 475 个文件 0 issues）。

#### E-1. 把反复打红测试的结构性坑收敛进两个 fixture

- **改了什么**：新增 `client/e2e/fixtures/dialog.ts`（9 条取证结论 + `dialog` / `dialogOverlay` / `dialogCloseButton` / `dialogConfirmButton` / `dialogCancelButton` / `waitDialogSettled` / `collapseSelect`）与 `client/e2e/fixtures/grid.ts`（5 条取证结论 + `waitGridReady` / `gridRows` / `waitGridRows` / `expectTableColumns` / `gridActionButton` / `expandAllTreeNodes`）；`playwright-base.ts` 精简为 55 行，删掉 `Selectors` 常量、`loginAs` 与 15 个零引用 helper。
- **原因**：定位器此前散落在各规格文件里各猜一套，同一批 DOM 事实被反复重新发现。其中数条是硬事实，写进注释后不必再猜：Vben Modal 底层是 reka-ui Dialog 而非 antd Modal（`.ant-modal-wrap` 计数恒为 0，遮罩是弹窗的**兄弟**节点 `[data-dismissable-modal]` 而不是祖先）；`[class*="close"]` 会误命中 Tailwind 的 `data-[state=closed]:` 类名；antd Button 在两个中文字符之间插空格，确认键可访问名是「确 认」/「确 定」，`/确定|确认/` 两个都匹配不到；弹窗内按钮实测顺序是 `[全屏, 取 消, 确 认, 关闭图标]`，`.last()` 命中的是关闭图标；`okType:'danger'` 只渲染 `ant-btn-dangerous` 而不带 `ant-btn-primary`。
- **影响范围**：全部 e2e 规格文件。

#### E-2. 两个真实的生产缺陷

- **改了什么**：其一，`server/src/main/kotlin/admin/route/ConfigRoutes.kt` 的 notifications 查询补 `.toList()`（该文件本轮仅此一行改动）。其二，4 个表单弹窗的 `onConfirm` 补上 `const { valid } = await formApi.validate(); if (!valid) return;`，分别为 `views/system/user/form-modal.vue`、`views/system/role/form-modal.vue`、`views/system/menu/form-modal.vue`、`views/coupon/form-modal.vue`。
- **原因**：前者 R2DBC 的 Flow 未终止收集，接口返回空；后者表单声明了 `required` 规则却从不校验，点「确认」会把空表单直接提交到后端。
- **影响范围**：配置接口的通知列表；4 个新建/编辑弹窗。其中 `views/coupon/form-modal.vue` 位于整个未跟踪的 `views/coupon/` 目录内（配套的 `api/coupon.ts` 同样未跟踪），单独入库会让该目录在版本库中残缺、`import` 指向不存在的文件、干净检出后构建失败，因此这一处**改动保留在工作区、本轮未提交**，待 coupon 模块整体入库时一并带上。另 3 处已入库。

#### E-3. 三个「假绿 / 假跳过」：用例从未真正执行过

- **改了什么**：其一，`role-management.spec.ts` Req 10.8 原先以 `test.skip()` 兜底，而种子库里只有 `super_admin` 一个系统角色，导致该用例**永久跳过**；现加 `mockRoleList()` 造出一个非系统角色并删掉 skip。其二，`user-management.spec.ts` Req 7.7 / 7.8 用 `.vxe-table--body .vxe-body--row` 定位行，该选择器会把主表与固定列冻结表的行**一起**匹配（行数翻倍），`nth(1)` 落到只有操作列的冻结行、找不到状态开关，随后 `isVisible().catch(() => false)` + `test.skip()` 把失败变成静默跳过；现改用 `gridRows()` + `waitGridRows()` 并换成硬断言。其三，Req 10.11 的 `expect(foundSystemRole || foundNonSystemRole).toBe(true)` 是「两类角色命中一类就算过」的弱断言；现改为系统角色与非系统角色两类都必须验到。
- **原因**：上一节基线里那 3 个 skipped 正是这三个用例。它们看着不红，实际一次都没跑过，覆盖率是虚的。
- **影响范围**：3 个用例由「跳过」转为真实执行并通过。

#### E-4. 清除 `networkidle` 与固定 sleep

- **改了什么**：e2e 里 15 处 `waitUntil:'networkidle'` 换成 `domcontentloaded`（`notification.spec.ts` 5 处、`audit-log.spec.ts` 4 处、`route-guard.spec.ts` 6 处含 `goBack`）；`route-guard.spec.ts` Req 19.3 重写为 `waitForResponse('/menu/all')` + `expect.poll` 判 403，并因需冷启动两次应用而显式 `setTimeout(60_000)`；Req 19.5 / 19.6 的 `waitForTimeout(3000)` 与 `(2000)` 换成等待 `aside` 的 `attached` 状态。
- **原因**：Vite dev server 的 HMR 长连接使 `networkidle` 永远等不到，多 worker 并发时尤其明显——它已实际打红 notification Req 16.3 与 route-guard Req 19.3（30s 超时，且报错被算到 `finally` 的 `context.close()` 上，完全指不到真正原因）。固定 sleep 则是把不确定性藏在时间里。
- **影响范围**：Req 19.3 从 30s 超时降到约 6s 通过。剩 `dashboard/analytics.spec.ts` 一处 `waitForLoadState('networkidle')` 未动，该用例稳定通过（约 4s）。

#### E-5. 两个由 `count()` 即时快照引起的竞态

- **改了什么**：其一，`menu-management.spec.ts` 树结构用例改为先 `expect(treeNodes.first()).toBeVisible()` 再 `count()`，`expandAllTreeNodes()` 开头补 `waitGridRows()`。其二，新增 `expectTableColumns()`，替换掉 7 处「先 `headerCells.count()` 再逐个 `nth(i).textContent()` 收集文案」的重复写法（`user` / `role` / `menu` / `config` / `audit-log` 各 1 处，`notification` 2 处）。
- **原因**：`locator.count()` 是即时快照、不会自动等待，而 `waitGridReady()` 只保证表头（同步渲染）、数据要等 XHR。这有两个失败方向：数据未到时 `count()` 读到 0，函数静默地什么都不做，调用方随后断言子节点必然失败；数据到达后 vxe 重排表头（固定列拆进冻结表），`count()` 读到的瞬时数量反而**大于**最终数量，`nth(i)` 永远等不到、直接撞满 30s 超时。后者在角色列表 Req 10.1 上表现为**单跑必挂、四个 worker 并发时侥幸通过**。
- **影响范围**：`expectTableColumns()` 对 vxe 与 antd 两种表头都加了 `:visible` 过滤——固定列拆出的那份表头副本是 hidden 的且在 DOM 里排在可见副本之前，不过滤就会让 `.first()` 稳定命中隐藏元素（这一点在全量跑时实际打红了 user Req 7.1 与 menu Req 12.1，已修）。角色列表 Req 10.1 经 `--repeat-each=5` 复验 5/5 通过（含 4 次冷启动、6.7s；修复前同条件 30.1s 超时）。

#### E-6. 弹窗相关的两个既有测试缺陷

- **改了什么**：`modal-interaction.spec.ts` Req 20.1 在量 `boundingBox()` 前补 `waitDialogSettled()`；Req 20.5 与 `user-management.spec.ts` Req 7.3 把收下拉的 `keyboard.press('Escape')` 换成 `collapseSelect()`。
- **原因**：其一，`DialogContent.vue` 默认 `animationType:'slide'` 且 `modal.vue` 给了 `duration-300`，进场动画 `slide-in-from-top-[48%]` + `zoom-in-95` 持续 300ms，动画跑到约 93% 时量到的 y 仍比最终的 `top-[10vh]` 高 18.37px，即量到的是中间态而非布局事实。其二，reka-ui Dialog 同样监听 Escape（`modal.vue` 的 `@escape-key-down`），弹窗开着时按 Escape 收 antd 下拉会把弹窗一起关掉，失败快照里根本不存在 dialog，后续等确认键与刷新请求自然全部落空。这两处此前被「`.last()` 点到关闭图标」的缺陷掩盖着。
- **影响范围**：3 个用例。`collapseSelect()` 采用「再点一次 select 自身来 toggle 关闭」而不是点击弹窗标题，因为 rc-select 的面板永远贴在 selector 上方或下方、不会盖住它，而向上弹出的面板有可能遮住标题。

#### 本轮明确未改动的事项

- **`redirect` 双重编码**：`encodeURIComponent` 写入叠加 Vue Router 自动编码，URL 里出现 `%252F`；读取时自动解码再叠加 `decodeURIComponent`，还原结果正确。编解码对称抵消，**属冗余而非缺陷**，本轮不改。route-guard 里 15 处相关字样全是注释与 mock，无断言依赖编码形式。
- **工作区既有重构**：`git status` 中另有 100 余条与本轮无关的改动（game 模块整体下线 9 个删除，含 `api/game.ts`、6 个 `views/game/**/*.vue`、2 个 `e2e/game/*.spec.ts`；牙科业务后端 28 个 `.kt` 修改 + 7 个新增；notification 从 `views/system/` 迁至 `views/` 等），按约定完全未触碰，本轮提交只含上述路径。因此 e2e 用例总数由 94 保持不变，`e2e/game/` 的两个规格随模块下线一并删除、不在本轮范围内。
- **4 个纯格式化文件**：`e2e/system/config.spec.ts`、`e2e/tsconfig.json`、`playwright.config.ts`、`client/package.json` 的改动全部是 oxfmt / eslint 的自动产物、**零语义变化**（import 排序、printWidth 80 拆行、JSON key 重排、`15000`→`15_000`、删空行），随本轮一并入库，避免下次任何人跑 `format` 都重现同一份 diff。
