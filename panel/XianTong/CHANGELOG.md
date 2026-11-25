# 变更日志

本文件按日期倒序记录 XianTong 项目的代码改动，每条包含：改了什么、为什么改、影响范围。

## 2026-11-25

### 后端请求日志插件与关键链路日志补全（新增）

- **改了什么**：
  1. 引入 `ktor-server-call-logging`，新增 `common/logging/RequestLogging.kt`：每个请求分配或沿用 `X-Request-Id`（格式合法才复用，否则生成 UUID），写入响应头并注入 MDC；完成后输出「方法 路径 状态码 耗时 客户端 IP 操作者」；对 token/code/phone 等敏感 query 参数打码，跳过静态资源。
  2. `configureRouting()` 安装该插件；`UserJwtAuthPlugin` 新增 `userIdOrNull()`，供日志读取操作者而不抛异常。
  3. `logback.xml` 改为控制台 + 滚动文件：`logs/server.log`（单文件 50MB、按天滚动、保留 14 天、上限 500MB）、`logs/error.log`（WARN 及以上、保留 30 天），文件写入走异步 appender；日志格式加入 `%X{requestId}`；`LOG_DIR` / `LOG_LEVEL` 可用环境变量覆盖。
  4. 管理端 StatusPages 兜底异常日志补充请求方法、路径与 requestId。
  5. 补齐关键链路日志：管理端登录四类失败原因、微信登录结果（是否新用户/是否授权手机号）、补绑手机号、预约创建与取消失败/成功、签到成功与门店码无效、消费支付发起与模拟结算、充值下单。
- **原因**：此前没有请求级日志与追踪 ID，客户端报错时无法与日志对应；登录、预约、支付等关键链路缺少入口/出口日志，排查只能依赖堆栈猜测。
- **影响范围**：仅日志输出，接口与业务行为不变。日志默认写入进程工作目录下的 `logs/`（`gradlew :server:run` 为 `server/logs/`），已在根 `.gitignore` 忽略；调整路径或级别请设置 `LOG_DIR` / `LOG_LEVEL`。注意：query 仅对已知敏感键打码，新增接口不要通过 query 传凭证。

## 2026-11-23

### 删除 user.primary_clinic_id 列（修改）

- **改了什么**：`AdminTables.Users` 移除 `primaryClinicId` 列定义；`SchemaMigration` 新增 `migrateDropPrimaryClinicId()`，启动时幂等执行 `ALTER TABLE "user" DROP COLUMN IF EXISTS primary_clinic_id`；移除历史回填中依赖该列的 `legacy_primary` 兜底 SQL；`demo-data.sql` 去掉该列并改为写入 `customer_clinic_relation`。
- **原因**：阶段 4 已完成读口径切换，客户归属单一门店的字段不再有读写方，保留会成为死数据并误导后续维护。
- **影响范围**：升级后首次启动会永久删除该列，原归属信息不可恢复（客户-门店的首访/最近到访事实已在 `customer_clinic_relation` 中保留）；升级前请确认数据库已备份。

### 移除客户「归属门店」，改为客户-门店业务关系（修改）

- **改了什么**：
  1. 新增 `customer_clinic_relation` 表（`user_id + clinic_id` 唯一），记录首次/最近到店、预约与消费次数、消费金额、来源；由 `AdminTables.kt` 定义、`AdminModule.kt` 建表，`SchemaMigration.migrateCustomerClinicRelations()` 首次启动时按预约/消费/充值回填，并以 `user.primary_clinic_id` 兜底历史客户。
  2. 新增 `CustomerClinicRelationService.touch()`，在用户端预约创建、管理端代客预约、消费结算、充值成功后与业务数据同事务写入关系。
  3. 读口径切换：`ClinicScopeGuard` 客户范围、客户列表门店筛选与详情关联门店、看板新客/客户来源/等级/留存/推荐、积分兑换核销与待核销列表全部改读关系表；积分兑换改为全局口径，不校验门店。
  4. 删除 `PUT /api/admin/customers/{id}/clinic` 及「调整归属门店」弹窗/API；资料接口不再读写 `primaryClinicId`，完善资料积分不再要求门店。
  5. 小程序资料页删除门店 picker、`primaryClinicId` 与全局 `clinicId` 字段；充值不再传门店，由后端按最近到店/默认营业门店解析。
- **原因**：业务上整个医院是一个整体，门店只是就诊地点，客户不归属单一门店；原 `user.primary_clinic_id` 既承担用户自选又承担管理端数据隔离，无法表达跨店就诊。
- **影响范围**：客户数、新客数改为「首次到店」口径，同一客户可计入多家门店，看板数字会跳变；从未到店客户不再出现在单店客户列表；`user.primary_clinic_id` 暂保留，阶段 5 清理。旧版小程序仍传 `primaryClinicId` 不会报错（全局 `ignoreUnknownKeys`）。

### 微信一键登录错误码细分（修改）

- **改了什么**：
  1. `common/wx/WechatMiniClient.kt`：`WechatApiException` 新增 `errcode` 字段，`code2session`、`getuserphonenumber`、`access_token` 三类微信调用失败时把微信原始错误码一并抛出，网络层失败保持为 null。
  2. `user/route/UserApiErrors.kt`：新增 `translateWechatLoginError`，把微信错误码翻译成 C 端可理解的业务码与文案——40029/40163（code 无效或已使用）回 400「登录凭证已失效，请重新点击登录」；45011（频率限制）回 429「操作过于频繁，请稍后再试」；40013/40125/40164（appId/appSecret 配置错误、IP 未白名单）回 500「微信登录服务配置异常，请联系客服」；其余仍走原通用兜底。
  3. `user/route/UserAuthRoutes.kt`：`/auth/wechat-login` 的异常兜底改用 `translateWechatLoginError`，保留原唯一约束冲突、请求体非法的翻译逻辑。
- **原因**：本地 `WECHAT_APP_SECRET` 失效（微信返回 `40125 invalid appsecret`）时，接口统一回 500「登录失败，请稍后重试」，用户与前端都无法判断是凭证过期、重复点击还是服务配置错误，只能盲目重试。
- **影响范围**：仅 `POST /api/v1/auth/wechat-login` 的错误响应码与文案；成功路径、`/auth/refresh`、`/auth/bind-phone` 不变。注意：若配置的 AppSecret 仍然错误，登录依旧失败，需在微信公众平台重置后更新 `server/.env.local`。

### 新增本地一键启动脚本（新增）

- **改了什么**：
  1. 新增 `panel/XianTong/start-local.sh`：检测 8080/5666 端口，已监听的服务直接复用；后端复用 `server/run-local.sh`（自动加载 `server/.env.local`），前端执行 `pnpm -F @vben/web-antd run dev`；日志写入 `panel/XianTong/logs/{backend,frontend}.log`，监听进程 PID 写入同目录 `*.pid`；启动后按端口探活并打印管理端地址与当前局域网地址。
  2. 参数：`--backend-only` / `--frontend-only` 单端操作，`--restart` 先停后启，`--help` 打印用法。
  3. `--restart` 只停止 PID 文件记录且进程树匹配的服务；端口被非本项目进程占用时提示并跳过，不误杀。
  4. 新增 `doc/本地启动脚本使用说明.md`，覆盖前置依赖、首次准备、参数、常用场景、日志与 PID、排障。
  5. 根 `.gitignore` 增加 `panel/XianTong/logs/`，避免 PID 文件入库。
- **原因**：本地联调需要分别手动启动前后端，容易漏加载 `.env.local`（导致能力误降级为 mock）或撞端口；改代码后缺少便捷的重启方式；内网 IP 变化后难以确认小程序该指向哪个地址。
- **影响范围**：仅新增脚本与文档，无运行时行为变化。

### 小程序接口地址配置补注释与调试日志（修改）

- **改了什么**：`app/XianTong/miniprogram/utils/request.ts` 中 `API_ORIGIN` 保持 `http://localhost:8080`（模拟器可用），注释补充「真机调试改为电脑局域网 IP」；新增 `console.log('[Config] API_ORIGIN:', API_ORIGIN)` 便于真机确认实际请求地址。
- **原因**：真机预览时 localhost 指向手机自身，排查联调问题时需要快速确认小程序当前使用的后端地址。
- **影响范围**：仅开发期配置常量和日志，`USE_MOCK` 仍为 `false`；上线前需切换为生产 HTTPS 域名。

### 小程序首页门店加载与距离计算重构（修改）

- **改了什么**：`app/XianTong/miniprogram/pages/index/index.ts` 的 `loadClinics` 改为先渲染服务端门店数据、再异步补充定位距离，避免定位授权阻塞列表首屏；抽出 `mapClinics` 统一映射门店字段并计算距离，坐标无效时置空、距离收敛为非负有限数，缺失距离时回退服务端值；排序在距离相同时保持服务端原顺序。
- **原因**：原先 `getClinics` 与 `getCurrentLocation` 用 `Promise.all` 并发，定位授权弹窗会拖慢门店展示；`hasCoordinates` 布尔值配合非空断言易在坐标缺失时产生无效距离。
- **影响范围**：仅小程序首页「附近门诊」列表的渲染时机与距离计算；接口不变。

## 2026-11-22

### 管理端优惠券定向发放、扫码核销与统计（新增）

- **改了什么**：
  1. 后端 `AdminCouponService` 新增 `grantCouponsToUsers`（目标用户去重、校验模板状态/有效期/库存，发放后累加 `issuedQuantity`，支持部分失败返回失败用户）与扫码核销逻辑（条件更新 `user_coupons.status=used` 防止并发重复核销，原子累加 `usedQuantity`）。
  2. 后端 `CouponRoutes` 新增 `POST /api/admin/coupons/grant`、`POST /api/admin/coupons/scan-redeem`、`GET /api/admin/coupons/statistics`，统一受 `coupon:manage` 权限约束。
  3. 前端新增 `coupon/grant-modal.vue`（选模板 + 多选客户，至少一名客户）、`coupon/redeem-modal.vue`（`qrData` 必填，成功展示券名与优惠金额）、`coupon/statistics.vue` 统计页；`coupon/index.vue` 增加「定向发券 / 扫码核销 / 统计」入口及行内「发券」；`coupon/record.vue` 增加扫码核销入口；`api/coupon.ts` 补充对应方法。
  4. 菜单种子 `DataSeedService` 新增「优惠券统计」菜单（`/coupon/statistics`，`coupon:view`）并加入 super_admin 路由白名单。
- **原因**：管理端此前只能创建模板、查看记录，缺少补发券、线下扫码核销和数据统计入口。
- **影响范围**：新增接口与页面，不影响原有模板 CRUD 与推广码逻辑；统计菜单需重新登录或等待菜单同步后可见。

### 客户归属门店调整（新增）

- **改了什么**：
  1. 后端新增 `PUT /api/admin/customers/{id}/clinic`（`customer:update`），请求体 `CustomerClinicChangeRequest(clinicId, reason)`；`CustomerService.changePrimaryClinic` 校验 ClinicScope 后更新归属门店，写入审计日志（操作人、IP、UA）并通知客户。
  2. 前端新增 `customer/clinic-modal.vue`：目标门店下拉禁用当前门店、仅显示启用门店，调整原因必填且最多 500 字；`customer/index.vue` 增加「调整门店」入口；`api/customer.ts` 新增 `changeCustomerClinic`。
- **原因**：客户跨店流转此前只能改库，缺少带原因与审计痕迹的管理入口。
- **影响范围**：仅管理端新增操作；客户原有预约/消费记录不迁移，后续新数据按新门店权限展示。

### 排班支持编辑并回显时段时长（新增）

- **改了什么**：
  1. 后端新增 `PUT /api/admin/schedules/{id}`（`doctor:update`，受 ClinicScope 约束）；`ScheduleResponse` 增加 `slotDuration` 字段。
  2. 前端 `schedule/form-modal.vue` 改为一套表单复用新建/编辑，起止时间改用 `TimePicker`（`HH:mm`）并在编辑时回填 `slotDuration`；`schedule/index.vue` 增加「编辑」按钮与「时段时长」列；`api/schedule.ts` 新增 `updateSchedule`。
- **原因**：此前排班只能删除重建，且列表看不到时段时长。
- **影响范围**：排班列表接口返回新增字段（向后兼容）；新建流程不变。

### 门诊营业日区间展示修复（修复）

- **改了什么**：`views/clinic/index.vue` 营业日展示改用区间文案（如「周一至周五」），空数据返回「未设置」，星期编号越界回退为「星期N」，排序改用 `toSorted` 避免原地修改源数组。
- **原因**：原实现直接按下标取星期名，空数据与越界值会显示 `undefined`，原地 `sort` 还会副作用修改源数组。
- **影响范围**：仅门诊列表展示层，无接口变化。

### 会员年度复评逻辑补齐单测（修改）

- **改了什么**：`MemberLevelReviewService` 按上一年「成功且已支付」的消费汇总计算，低于当前等级门槛 50% 时触发降级，保留最低等级并做幂等处理，同时更新 `lastYearConsumption` 并发送通知；新增 `MemberLevelReviewServiceTest.kt` 覆盖支付状态过滤与降级判定。
- **原因**：年度复评涉及金额与等级变更，缺少单测保护，支付状态过滤一旦回归会误降级。
- **影响范围**：仅每年 1 月 1 日 00:35 的复评任务及其测试，无接口变化。

## 2026-11-20

### 小程序资料保存后回读校验（新增）

- **改了什么**：`app/XianTong/miniprogram/pages/profile-edit/profile-edit.ts` 新增 `verifyProfileSaved(submitted)`，在 `updateUserProfile` 成功后重新调 `GET /profile/me`，逐项比对本次提交的 `nickname` 与 `avatar` 是否已落库；一致打 `console.log`、不一致打 `console.error` 并输出「提交值 vs 服务端值」差异；回读异常只 `warn`，不反推保存失败。仅 `envVersion !== 'release'`（开发版/体验版/真机调试）执行，线上跳过。
- **原因**：头像昵称授权改成 `chooseAvatar` + `type="nickname"` 后，需要一条可观测的闭环证据确认「授权 → 上传 → 上报 → 落库」，否则只能靠肉眼判断页面是否回显。
- **影响范围**：仅编辑资料页保存流程；线上无行为变化。回归：小程序 `npm run typecheck` 通过。

### 小程序授权成功上报手机号昵称（后端：登录响应回带手机号 + 授权绑定手机号接口）

- **改了什么**：
  1. `server/src/main/kotlin/user/service/UserAuthService.kt`：`WechatLoginResponse` 增加 `phone` 字段，`wechatLogin` 把本次解密得到的手机号回带给前端；新增 `bindPhone(userId, phoneCode)`，复用 `resolvePhone` 换取手机号后加密落库（支持补绑与换绑）；`resolvePhone` 日志改为通用文案（登录与重新授权共用）。
  2. `server/src/main/kotlin/user/route/UserAuthRoutes.kt`：`WechatLoginResponseDto` 增加 `phone` 字段并在登录响应中回填；新增鉴权路由 `POST /api/v1/auth/bind-phone`（`userAuthenticated` 保护，传 `phoneCode`），换取失败返回 400，号码被占用由 `cuser_uk_phone_hash` 唯一约束兜底翻译成 409；新增 `BindPhoneRequest` DTO。
- **原因**：登录接口此前不回带手机号，前端 `globalData.wechatPhone` 登录后始终为空，编辑资料页只能再发一次 `/profile/me` 兜底；且登录时取消手机号授权后没有任何入口补绑或换绑手机号。
- **影响范围**：`POST /auth/wechat-login` 响应体新增可选 `phone` 字段（向后兼容）；新增 `POST /auth/bind-phone` 需登录态。回归：`:server:compileKotlin` 零错误。

### 小程序授权成功上报手机号昵称（前端：头像昵称填写能力 + 重新授权手机号入口）

- **改了什么**：
  1. `app/XianTong/miniprogram/utils/api.ts`：新增 `bindPhone(phoneCode)` 调 `POST /auth/bind-phone`（需登录态）。
  2. `app/XianTong/miniprogram/pages/profile-edit/profile-edit.ts`：头像选择改为 `<button open-type="chooseAvatar">` 回调上传（取 `e.detail.avatarUrl`），取代 `wx.getUserProfile`/`wx.chooseMedia` 的匿名兜底；新增 `onReauthorizePhone` 调 `bindPhone`，成功后同步全局态 `wechatPhone` 与本页展示。
  3. `app/XianTong/miniprogram/pages/profile-edit/profile-edit.wxml`：头像行接入 `chooseAvatar`、昵称输入加 `type="nickname"` 唤起微信昵称填写、手机号行新增「重新授权」按钮；底部提示文案同步更新。
  4. `app/XianTong/miniprogram/pages/profile-edit/profile-edit.scss`：新增 `.avatar-pick-btn`/`.phone-cell`/`.reauth-phone-btn` 样式，重置 `<button>` 默认外观使其融入表单行。
  5. `app/XianTong/miniprogram/pages/index/index.ts`：`syncWechatProfile` 跳过匿名占位昵称「微信用户」（新版微信 `getUserProfile` 不再返回真实昵称），避免把占位符写入库；新用户登录成功后引导跳转编辑资料页完善头像昵称。
  6. `app/XianTong/miniprogram/app.ts`、`typings/index.d.ts`、`pages/profile/profile.ts`、`pages/profile-edit/profile-edit.ts`：新增 `wechatNickname` 全局态及读取优先级（服务端 > 微信登录昵称 > 手机号后 4 位），「我的」页昵称展示优先取服务端 `nickname`。
- **原因**：新版微信 `wx.getUserProfile` 不再弹窗、返回匿名「微信用户」与默认灰头像，导致上报的昵称/头像无意义；登录取消手机号授权后无法补绑。
- **影响范围**：C 端登录、编辑资料、我的页；接口字段均为可选新增，向后兼容。回归：小程序 `npm run typecheck` 通过。

### 修复 C 端门店经纬度与营业日显示异常

- **改了什么**：
  1. `app/XianTong/miniprogram/utils/util.ts`：`formatBusinessDays` 将连续营业日合并为区间（`1,2,3,4,5,6` → 「周一至周六」、全周 → 「每天」）；新增 `isValidCoordinate` 过滤空值、非有限值、`(0,0)` 与境外坐标；`getCurrentLocation` 在定位坐标无效时按未定位返回 `null`。
  2. `app/XianTong/miniprogram/utils/api.ts`：`getDoctorSlots` 增加可选 `clinicId` 参数。
  3. `app/XianTong/miniprogram/pages/appointment/appointment.ts`：距离仅在定位与门店坐标均有效时计算，否则隐藏；时段查询透传 `clinicId`，由后端按门店营业日/营业时间过滤。
  4. `app/XianTong/miniprogram/pages/index/index.ts`：首页门店距离同样使用 `isValidCoordinate`，避免无效坐标参与计算与排序。
  5. `panel/XianTong/client/apps/web-antd/src/views/clinic/index.vue`：管理端门诊列表营业日文案同步为区间合并，与 C 端一致。
- **原因**：微信开发者工具默认定位为 `(0,0)`，与北京门店坐标算出的距离达上万公里；C 端营业日逐项罗列不易读；预约页取时段未带门店 ID，闭店日仍显示可约，与后端校验口径不一致。
- **影响范围**：仅 C 端展示与时段查询参数、管理端门诊列表文案，接口字段不变（`clinicId` 为可选）。回归：门店 2（周一至周五营业）周六时段返回空、不带 `clinicId` 仍返回时段；小程序 `npm run typecheck` 通过。

### 手机号加密存储与安全回归

- **改了什么**：
  1. 新增 `common/security/PhoneCipherService.kt`：手机号归一化、AES-256-GCM 加密、HMAC-SHA256 盲索引、解密与兼容读取；密文损坏时不回退明文。
  2. `common/config/SchemaMigration.kt` 为 `user` 表补 `phone_hash` 列与唯一索引（存在重复时跳过建索引并告警），启动时把历史明文手机号迁移为密文。
  3. `common/config/StartupPreflight.kt` 增加生产环境密钥预检：`security.phoneEncryptionKey` / `security.phoneBlindIndexKey` 缺失时阻断启动。
  4. `UserAuthService`、`UserProfileService` 的手机号读写统一走加密服务；`application.yaml` 与 `.env.local.example` 增加两项空配置及生成说明。
  5. 新增 `SecurityRegressionTest.kt`，覆盖手机号归一化/加解密/盲索引、损坏密文、门店范围守卫与支付回调单号识别。
- **原因**：`user.phone` 此前明文落库，不符合个人信息加密要求；同时缺少上线前密钥校验，配置遗漏会在生产静默使用开发默认值。
- **影响范围**：登录、资料读取、资料修改的手机号链路；历史数据在启动时一次性迁移，接口返回仍为明文手机号。`:server:test` 全部通过。

### 四组功能专项测试缺陷修复

- **改了什么**：
  1. `UserCouponRoutes.kt` 新增 `CouponClaimDto/CouponRedeemDto/PromoClaimDto`，领券、核销、推广领券成功分支改用具名 DTO。
  2. 新增 `common/CouponUsageSupport.kt`，抽离预约下单/改约的优惠券占用、释放与校验逻辑，`CouponService`、`AppointmentService` 复用。
  3. `AppointmentService.kt` 新增 `parseBusinessDays/parseClock/clinicClosedReason`，`getAvailableSlots` 增加 `clinicId`，闭店日返回空时段；创建预约、改约校验门店营业日与营业时间。
  4. 爽约限制改为仅在阈值 `> 0` 时生效，`0` 表示关闭；`DataSeedService` 配置说明补充该口径。
  5. `UserReferralRoutes.kt` 推荐记录接口直接返回 `ReferralRecordsResponse`（含 `list/total/monthlyCount/summary/tierRewards`）。
  6. 按商圈补齐 6 家门店经纬度（数据修正，无代码变更）；`BusinessResponse.kt` 门店 DTO 补充 `businessDays/openTime/closeTime/latitude/longitude`。
- **原因**：专项测试发现领券/核销与推荐记录因 `mapOf` 无法序列化返回 400；营业日只展示未参与校验，闭店日仍可下单；爽约阈值 0 会拦截全部用户；门店坐标为空导致 C 端距离功能无数据。
- **影响范围**：C 端领券、核销、推荐页、预约与改约；管理端代客预约保持放行。回归：核销幂等、过期券提示、推荐页数据、闭店日时段为空与下单拒绝、阈值 0 可下单均验证通过；测试数据已清理，预约 14、审计 67 基线不变。

### 管理端看板、客户画像与门诊表单

- **改了什么**：
  1. `views/appointment/board.vue` 本周看板按天展开预约，补充状态统计与排班展示；`appointment/index.vue`、`detail-modal.vue` 同步取消申请状态、改约入口与明细字段。
  2. `views/customer/detail-modal.vue` 与 `api/customer.ts` 补全客户画像（消费、积分、券、推荐、归属门店等）。
  3. `views/clinic/form-modal.vue` 经纬度成对校验与营业日选择；门诊列表营业日文案改为区间展示。
  4. `AdminDashboardService`、`ConsumptionAdminService`、`AdminReferralService`、`MembershipAdminService` 等补齐看板与画像所需字段。
- **原因**：四组专项测试中看板信息不足、客户画像字段缺失、门诊表单缺少坐标校验。
- **影响范围**：仅管理端页面展示与查询字段，接口路径不变。

### C 端预约、钱包、资料与推荐页联调

- **改了什么**：
  1. `pages/appointments` 展示取消申请状态（待审批/已通过/已驳回）与改约入口；`pages/appointment` 补充改约摘要、优惠券选择与门店营业状态。
  2. `pages/wallet` 接入尾款支付（余额不足时微信补差）、充值档位与消费记录；`UserConsumptionPaymentService`、`UserRechargeRoutes` 配合调整支付状态流转。
  3. `pages/profile-edit` 支持首次设置归属门店、手机号只读回显；`PointsService`、`UserProfileService` 完善资料积分发放与等级字段。
  4. `pages/referral` 展示邀请记录、档位奖励与月度统计；`pages/coupon` 区分可用/已使用/已过期。
  5. `app.json` 增加 `scope.userLocation` 权限说明与 `requiredPrivateInfos: ["getLocation"]`。
- **原因**：C 端多处页面只有静态结构，缺少与后端新字段的联调；定位能力需要显式声明才能在小程序中使用。
- **影响范围**：小程序上述页面；后端仅补字段与状态，未改动既有接口路径。小程序 `npm run typecheck` 通过。

## 2026-11-19

### 修复多门店隔离与尾款支付的三处缺陷

- **改了什么**：
  1. `admin/service/AdminUserService.kt` 的 `updateUser` 入口统一执行 `requireFullClinicScope`，不再只在改角色/门店授权时才校验。
  2. `user/route/UserConsumptionRoutes.kt` 的错误响应由 `ApiResponse<Any>` 改为 `ApiResponse<Unit>` 并显式设置 HTTP 状态码（无效 ID 400、记录不存在/已支付 404、内部错误 500）。
  3. `admin/service/AdminRoleService.kt` 的 `updateRole` 在 `roleName`/`description` 均为空时跳过 `AdminRoles.update`。
- **原因**：专项测试发现——① 受限门店管理员只要持有 `user:update`，仅传 `displayName`/`email` 就能修改超管等任意管理员的账号资料，与「账号/角色系统管理仅全店范围可写」口径不一致；② `ApiResponse<Any>` 无法生成序列化器，重复支付/无效 ID/记录不存在三类分支统一抛 `SerializationException`，被全局处理包装成 HTTP 400「请求体解析失败」，C 端无法按 404 区分已支付；③ 只改权限的 `PUT /roles/{id}` 会执行空 UPDATE，Exposed 抛 `Can't prepare UPDATE statement without fields to update` 返回 500。
- **影响范围**：受限管理员修改任何管理员账号资料将返回 403（需超管操作）；C 端尾款支付异常分支改回约定的 400/404/500。回归验证：受限账号改他人资料 403，无效 ID/重复支付 404，仅传 `permissionIds` 更新角色 200；`:server:test` 全通过。
- **新增文档**：`doc/管理端-三项专项测试报告.md`，记录多门店权限隔离、消费尾款支付闭环、手机号加密存储三项专项测试的用例、数据库取证、发现的 3 个问题与修复回归结果。

### 新增管理端测试文档

- **改了什么**：新增 `doc/管理端-测试文档.md`，覆盖环境启动与健康检查、测试分层命令（后端编译/测试、前端类型/单测/E2E）、8 个分组 30 个页面清单、10 个 Playwright spec 覆盖矩阵、约 80 条手工用例，以及 11 项已知问题与验证边界。
- **原因**：此前测试资料只有零散的 API 测试报告，管理端缺少统一测试入口与用例基线，回归只能依赖记忆。
- **影响范围**：仅文档，不涉及代码。文档记录 2026-11-19 全量 E2E 结果：94 条用例 93 通过 / 1 失败，失败为 `user-management.spec.ts` 的 Req 7.14 断言范围过宽（把 geekblue 门店 Tag 当作 blue 角色 Tag），页面实现正常，属用例缺陷。

## 2026-11-18

### demo-data.sql 增加母亲节活动与首页轮播

- **改了什么**：`server/demo-data.sql` 新增第 9 节，插入母亲节活动（封面 `/images/mothers-day.jpg`、简介、5 段详情、排序 0、启用、`CURRENT_DATE - 7 天` 至 `+ 1 年`）及绑定该活动的轮播图（`link_type=none`、排序 0、启用）；结果核对增加「活动」「轮播图」两项；头部说明同步改为「本脚本额外补一条母亲节活动」。
- **原因**：活动与轮播图此前只由 `DataSeedService` 在表为空时播种，母亲节那条是手工调用接口录入的，换库或清空演示数据后无法复现。
- **影响范围**：仍保持幂等（按活动标题、`activity_id` 唯一索引去重）。实测连续执行两次，活动/轮播图计数均为 4；事务内删除旧记录后执行可正确插入（活动 12 + 轮播图指向活动 12）并回滚恢复。封面使用小程序包内图片，管理端缩略图会显示裂图，与其余包内图片演示轮播一致。

### 小程序新增母亲节活动演示封面

- **改了什么**：新增 `app/XianTong/miniprogram/images/mothers-day.jpg`（750×422，69KB），作为母亲节活动的演示封面，供 `demo-data.sql` 与包内资源路径 `/images/mothers-day.jpg` 引用。
- **原因**：演示活动封面若指向后台上传文件（`/api/v1/files/...`），换了数据库或环境就 404；与其余演示轮播图一致改用包内图片，任何环境 `git clone` 后即可显示。
- **影响范围**：仅新增一张演示图片，压缩后 69KB，不影响小程序包体积上限。

### 修复管理端图片上传成功却返回 400

- **改了什么**：`admin/route/AdminFileRoutes.kt` 新增 `@Serializable` 的 `UploadedFileDto`（url/fileName/size），上传成功响应由 `call.ok(mapOf(...))` 改为返回该 DTO。
- **原因**：`mapOf("url" to String, "size" to Long)` 被推断为 `Map<String, Any>`，kotlinx.serialization 找不到 `Any` 的序列化器，响应阶段抛 `Serializer for class 'ApiResponse' is not found`，接口返回 400；文件与 `uploaded_file` 记录其实已写入，前端却提示上传失败。所有走该接口的轮播图、门店封面、医生头像上传都受影响。
- **影响范围**：仅响应体类型，字段与 HTTP 200 不变；实测上传 jpg 返回 `{"code":0,"url":"/api/v1/files/...","fileName":"...","size":297171}`，图片可公开访问。

### server 模块忽略运行时上传目录

- **改了什么**：`panel/XianTong/server/.gitignore` 增加 `uploads/`。
- **原因**：图片上传落在 `server/uploads/`，属于运行时数据，不应随代码入库；此前未忽略，每次本地上传都会出现在未跟踪列表，容易误提交。
- **影响范围**：仅本地版本控制，部署环境与接口行为不受影响。

### 小程序底部 tabBar 增加图标

- **改了什么**：`app/XianTong/miniprogram/app.json` 的 `tabBar.list` 四项补充 `iconPath` / `selectedIconPath`；新增 8 张 81×81 透明 PNG（首页、预约、钱包、我的，未选中为灰色 `#999999`、选中为蓝色 `#1890ff`）。
- **原因**：此前底部导航只有文字，缺少图形标识，视觉上与小程序整体风格不统一。
- **影响范围**：仅底部导航展示，页面路由与业务逻辑不变；单张图标 1.1～1.7KB，符合微信 tabBar 图标建议的 81×81、≤40KB 规格。

### 排班管理增加空态提示

- **改了什么**：`views/schedule/index.vue` 使用 vxe-grid 的 `#empty` 插槽自定义空态文案——未选择医生时提示「请先在上方选择医生，再查看或新建排班」，已选择医生但无数据时提示「该医生暂无排班，可点击右上角「新建排班」添加」。
- **原因**：后端 `/schedules` 必须带 `doctorId`（缺失返回 400「缺少医生ID」），页面未选医生时不发请求、表格只显示默认「暂无数据」，用户不知道要先选医生。
- **影响范围**：仅排班页空态展示，查询逻辑与接口不变。

### 修复客户「调整」弹窗的 Modal 命名冲突

- **改了什么**：`views/customer/adjust-modal.vue` 将 `ant-design-vue` 的 `Modal` 以 `AntModal` 别名导入，二次确认改为 `AntModal.confirm`。
- **原因**：文件内同时从 ant-design-vue 导入 `Modal` 又用 `const [Modal, modalApi] = useVbenModal()` 声明同名变量，`vue-tsc` 报 TS2440（导入与本地声明冲突）与 TS2339（`confirm` 不存在），类型检查不通过。
- **影响范围**：仅变量命名，确认弹窗行为与外观不变；`vue-tsc --noEmit` 已通过。

## 2026-11-17

### 轮播图活动下拉的占用标注文案精简

- **改了什么**：`content/banner/form-modal.vue` 中已绑定其他轮播图的活动后缀由「（已绑定其他轮播图）」改为「（已占用）」。
- **原因**：下拉面板宽度约 240px，原后缀被截断为「（已绑定其他轮播…」，语义不完整；置灰已能表达不可选，后缀只需点明原因。
- **影响范围**：仅管理端轮播图表单的选项文案，校验与接口不变。

### 需求文档补充「轮播图关联活动」需求

- **改了什么**：`doc/牙科门诊客户管理小程序_需求文档.docx` 新增 `3.8 首页与活动`（轮播区、轮播与活动一对一、展示规则、活动详情页及异常态）与 `4.12 内容管理（轮播图与活动）`（活动管理、轮播图管理、校验限制、与系统设置的关系）；4.11 中的「首页轮播图」改为指向 4.12；附录 B 页面清单增加「用户端 / 活动详情页」与「管理端 / 内容管理」。
- **原因**：原文档只有 4.11 一句「小程序页面配置（首页轮播图…）」，缺少轮播图与活动的对应关系、展示规则和异常处理，已实现的功能无需求依据。
- **影响范围**：仅需求文档，不影响代码与线上行为。

### 本地演示数据脚本 demo-data.sql + mock 登录固定演示账号

- **改了什么**：新增 `server/demo-data.sql`（单文件、幂等、可重复执行），按小程序 mockData 写入门店 4 家、科室 28 条（每店 7 个）、医生 8 位及门店关联、每周排班（周一至周日 × 上下午，30 分钟一档）、积分商品 5 个、公告 3 条；并以演示用户 `mock_openid_demo` 写入钱包（2500 + 450 赠送）、会员等级、积分汇总 209 分与 13 条积分流水、4 条充值（含 1 条待支付）、6 条钱包流水、14 条预约（4 条 completed 兼作就诊记录）、4 条消费记录、5 张优惠券、8 条站内消息、2 笔积分兑换订单、1 条推荐关系与推荐奖励。`UserAuthService` 未配置微信凭证时 openid 由 `mock_openid_$code` 改为固定 `mock_openid_demo`。
- **原因**：小程序切到真实接口（`USE_MOCK=false`）后各页面无数据可看；而 `wx.login` 的 code 每次都不同，拼进 openid 会导致每次登录都新建用户，演示数据无法挂在登录后的账号上，本地联调也一直在换账号。
- **影响范围**：脚本只针对本地开发库手动执行，生产不跑；公共数据按名称去重、用户维度数据「已有即整批跳过」，重复执行不产生重复行也不覆盖页面上的改动；固定 openid 仅影响未配置微信凭证的环境，多人共用同一后端会共享该演示账号。本地库执行后实测：登录返回 userId=79（openid=mock_openid_demo），积分 209、钱包 2500/450、优惠券 4 张未使用、消息 8 条、就诊记录 4 条、医生号源按已有预约扣减均正常。

### 活动详情页：区分「活动已结束」与「加载失败」并支持重试

- **改了什么**：`utils/request.ts` 在业务错误（`code !== 0`）的 `Error` 上携带 `code` 字段；活动详情页按 `404/400` 展示「活动不存在或已结束」，其余异常展示「活动加载失败」并提供「重新加载」按钮（`onRetryTap` 复用 `loadDetail`）；首页轮播跳转让 `wx.navigateTo` 带 `fail` 提示，并把兜底跳转的注释改为「后端已过滤未绑定活动的轮播，此处仅兜缓存旧数据」。
- **原因**：此前请求层对所有业务错误与网络异常统一 reject，详情页 catch 只关 loading，导致断网、超时、500 全部渲染成「活动不存在或已结束」，用户会误以为活动下线，且没有重试入口；页面栈满时点击轮播也毫无反馈。
- **影响范围**：错误语义改动仅新增 `Error.code` 字段，其他调用方仍是 try-catch，行为不变；活动详情页新增错误态与重试按钮，样式沿用现有空态布局。小程序 `tsc --noEmit` 通过。

## 2026-10-29

### 小程序页面样式打磨 + 演示数据与静态图片补全

- **改了什么**：首页、预约、消息、个人中心四个页面调整卡片内边距与列表样式（`app.scss` 的 `.card` 上下内边距统一、首页新增门店图片位与轮播卡片样式、消息/个人中心配色与间距微调）；`mockData.ts` 补充门店/医生/商品/活动等演示数据；`resolveFileUrl` 支持小程序包内 `/images/...` 原样返回，避免拼后端域名变 404；新增 `images/` 下 18 张本地演示图片与说明。
- **原因**：离线演示时卡片排版偏挤、门店无图，演示数据不足以覆盖预约状态、积分商品、活动等页面；包内图片被误拼上接口域名后加载失败。
- **影响范围**：纯展示层与 mock 数据，`USE_MOCK` 保持 `false`（走真实接口），不影响线上链路；新增图片为演示资源，正式上线可替换为后台上传的图片地址。

### 存量轮播图自动补建活动 + 本地演示数据 + 迁移语法修复

- **改了什么**：`DataSeedService` 增加 `seedBannerActivities()`（对所有 `activity_id` 为空的历史轮播图，按标题/图片补建一条「待完善」活动并回填绑定，幂等）与 `seedDemoContent()`（仅非生产且活动/轮播图都为空时，写入 3 组演示活动 + 已绑定轮播图，图片走小程序包内 `/images/banner-*.jpg`）；修复 `SchemaMigration.addColumnIfMissing` 拼接标识符未加引号导致 `ALTER TABLE user ...` 语法报错、进而中断其后所有迁移的问题（这也是 `mp_banner.activity_id` 之前加不上的原因）。
- **原因**：新模型下未绑定活动的轮播图会在 C 端整体消失，直接上线等于首页轮播清空；本地库 `mp_banner` 为空，无法验证「轮播图 → 活动详情」链路，需要一份可删的演示数据。
- **影响范围**：生产环境升级时会自动生成与原轮播图同名的活动（`created_by=system-backfill`），运营需补详情；演示数据只在开发环境且表为空时写入。实测：本地库已生成 3 活动 + 3 轮播图，`/api/v1/content/home` 返回带 `activityId` 的轮播，`/api/v1/content/activities/{id}` 正常、不存在时返回 404。

### 首页轮播图改为「一张轮播图对应一个活动」

- **改了什么**：新增 `mp_activity` 表与「内容管理 → 活动管理」页面（名称、封面、简介、详情、起止时间、排序、上下线，`AdminContentService` 增加活动 CRUD 与 `ContentAdminRoutes /activities`）；`mp_banner` 增加 `activity_id` 列与唯一索引（`SchemaMigration.migrateMpBannerActivity`，存量行 NULL 可共存）；轮播图新建/编辑必须选择活动，后端校验活动存在且未被其他轮播图占用，`BannerItemDto` 回带 `activityId/activityTitle`；管理端轮播图表单移除「跳转类型/跳转目标」，改为活动下拉（已占用活动置灰）；C 端 `/api/v1/content/home` 只返回「轮播图与活动都启用且在排期内」的项，新增公开接口 `/api/v1/content/activities/{id}`；小程序新增 `pages/activity/activity` 活动详情页（封面、简介、时间、分段详情、立即预约），首页轮播点击改为跳活动详情，仅历史无活动数据才回落旧跳转。
- **原因**：原轮播图只是「图片 + 自由跳转」，运营无法复用活动信息，也无法保证图文一致；需求要求一张轮播图对应一个活动，因此把活动提升为独立可维护实体，轮播图退化为活动的展示位，点击固定进入活动详情。
- **影响范围**：存量未绑定活动的轮播图由启动时的自动补建兜底（见上一条），补建出的活动需运营补充详情；`SaveBannerRequest` 去掉 `linkType/linkTarget`，旧前端提交这两个字段会被忽略（后端固定写 none）；活动停用或过了结束时间会连带隐藏其轮播图；一个活动最多挂一张轮播图（数据库唯一索引兜底）。
- **验证**：后端 `:server:compileKotlin` 通过；管理端 `vue-tsc --noEmit` 通过；小程序 `tsc --noEmit` 通过；已在本地库启动后端实测建表/迁移/接口（见上一条），管理端页面与小程序界面未做人工点测。

### 短信验证码接入腾讯云真实发送 + 凭证外置到本地 .env

- **改了什么**：新增 `common/sms/SmsConfig.kt`（腾讯云短信配置解析，环境变量 > yaml > 空，单一事实源）与 `common/sms/TencentSmsClient.kt`（TC3-HMAC-SHA256 签名直调 SendSms，不引入服务商 SDK，与 `WechatPayClient` 自研签名的做法一致）；`SmsVerificationService.sendVerificationCode` 改为「凭证齐全走真实下发、缺失降级为日志 mock」，并把写验证码挪到发送成功之后（失败不落库）；`StartupPreflight` 增加 `checkSms`（prod 缺失阻断启动、非 prod 告警）；`application.yaml` / `application-prod.yaml` 增加 `sms.tencent` 配置段与变量清单；新增 `server/.env.local.example` 模板与 `server/run-local.sh` 启动脚本，`.env.local` 写入 `server/.gitignore`。
- **原因**：此前短信验证码只把码打到服务端日志（`// TODO: 接入真实 SMS 提供商`），线上用户根本收不到，改手机号流程不可用；微信支付代码本身已完备，只是凭证只能手抄环境变量，容易漏配且密钥易随配置文件入库。统一收敛到 `.env.local` + 环境变量注入后，本地只需填一个文件，生产仍走部署平台环境变量。
- **影响范围**：验证码仍存进程内存（多实例部署换实例校验会失败，需迁 Redis）；`APP_ENV=prod` 且短信凭证不全时服务启动会 fail-fast；本次未拿到真实凭证，`.env.local` 中各项为空占位，相关能力仍走 mock，填值后需重启生效。

### 代码风格：main.kt 改为 import 引入 EngineMain

- **改了什么**：`main.kt` 里的全限定名调用 `io.ktor.server.netty.EngineMain.main(args)` 改为顶部 `import io.ktor.server.netty.EngineMain` 后调用 `EngineMain.main(args)`。
- **原因**：IDE 自动整理导入，消除行内全限定名，行为不变。
- **影响范围**：仅入口文件写法，启动行为与模块加载顺序均无变化。

## 2026-09-22

### 安全修复：PostgreSQL R2DBC 驱动升级（CVE-2025-59432）

- **改了什么**：`gradle/libs.versions.toml` 中 `postgresql` 版本从 `1.0.7.RELEASE` 升级到 `1.1.3.RELEASE`（`org.postgresql:r2dbc-postgresql`）。
- **原因**：Mend.io 扫描发现旧版本传递依赖 `com.ongres.scram:common:2.1`，存在 CVE-2025-59432（CVSS 7.5）时序攻击漏洞——SCRAM 认证中使用 `Arrays.equals` 短路比较密钥/签名，攻击者可通过计时侧通道推断认证凭据。1.1.3.RELEASE 将 scram 依赖升级到 `com.ongres.scram:scram-common:3.2`，改用 `MessageDigest.isEqual` 常量时间比较，彻底修复此漏洞。
- **影响范围**：仅 `libs.versions.toml` 版本号变更，`build.gradle.kts` 无需改动（`libs.postgresql.r2dbc` 引用自动跟随）。R2DBC 驱动 API 向后兼容，无业务代码需调整。

## 2026-09-16

本轮是需求文档差距分析后的**连续三批交付**（2026-09-15 ~ 09-16）：P0 微信登录/支付真实接入、P1（到店扫码签到、积分商城兑换、四个业务定时任务、推荐关系链路修复）、P2 管理端配置完整性（充值档位/会员等级/客户标签/消息模板 CRUD、充值流水分页、代客预约、预约时间轴看板、账号门店数据授权），另含今日收尾的充值档位/会员等级弹窗前端校验。改动跨三端：后端 19 新增 + 约 30 修改，管理端 14 新增 + 10 修改，小程序 8 修改；新增项目级知识库 CODE_WIKI.md，删除根目录遗留的 API.md 与 lefthook.yml。

各批的验证边界见 V 节：微信真实链路与定时任务**未经真机/线上实测**（凭证缺失时全部自动降级 mock），前端 typecheck 与后端编译均通过。

### M. P0：微信小程序登录与微信支付 V3 真实接入

#### M-1. common/wx 三件套与 mock 降级机制

- **改了什么**：新增 `common/wx/WechatConfig.kt`（104 行，配置解析单一事实源）、`WechatMiniClient.kt`（249 行，code2session、getPhoneNumber、access_token 进程内缓存）、`WechatPayClient.kt`（449 行，JSAPI 统一下单、V3  Authorization 签名、平台证书/回调解密验签）。
- **原因**：此前微信登录与支付全部是硬编码 mock，无法上线。三端配置统一走 `WechatConfig`，取值优先级为**环境变量 > application.yaml > 空串**；appId/appSecret 任一缺失则登录回落 `mock_openid`，支付任一项缺失则充值下单直接模拟支付成功——保证无凭证的本地环境仍可全链路联调，缺失时启动日志打 WARN 明示。
- **影响范围**：`WechatMiniClient` 的 access_token 用进程内缓存（带过期时间），单实例部署够用；**多实例部署需迁 Redis**，否则各实例各自刷新会撞微信接口频控。`WechatPayClient` 私钥支持 PEM 文本或 `privateKeyPath` 文件两种注入方式。

#### M-2. 登录链路接真实 code2session / getPhoneNumber

- **改了什么**：`UserAuthService.wechatLogin` 改为「真实调用成功用真实 openid，异常/未配置时回落 mock」；`UserAuthRoutes` 的登录请求体扩展为 `WechatLoginRequest(code, phoneCode?, userInfo?)`，支持把 `wx.getPhoneNumber` 回调的动态令牌换真实手机号，以及 `wx.getUserProfile` 的兜底资料（`WxProfile(nickname, avatarUrl, gender)`）。
- **原因**：真实接口超时或返回 errcode 时若不回落，本地与故障期间所有用户都无法登录；回落只发生在未配置或调用失败时，生产配齐凭证后走的是真实分支。
- **影响范围**：C 端登录端点签名兼容（code 必填，phoneCode/userInfo 可选）。手机号占用仍由 `cuser_uk_phone` 唯一索引兜底。

#### M-3. 充值支付链路与订单状态机

- **改了什么**：`RechargeService.kt`（+约 400 行）重写为订单状态管理：下单（pending）→ 支付回调成功幂等入账（success）→ 失败/退款（failed/refunded），微信回调验签解密后按商户订单号幂等处理，重复通知不会重复加钱。`UserRechargeRoutes` 提供 `POST /api/v1/recharge/create`（JSAPI 下单）、`POST /api/v1/recharge/notify`（微信回调，公网路径）、`GET /api/v1/recharge/{id}/status`（前端轮询）、`GET /api/v1/recharge/tiers`（档位展示）。`SchemaMigration` 新增 `migrateRechargeOrderColumns()` 给 `recharge_record` 补 out_trade_no 等订单列。
- **原因**：支付回调可能重复投递且乱序，入账必须幂等；订单号是对接微信与对账的唯一锚点，不能没有。
- **影响范围**：**`/notify` 必须是公网 HTTPS 地址**（`WECHAT_PAY_NOTIFY_URL`），本地无法接收真实回调，联调走 mock 分支。

#### M-4. 小程序充值页与 API 层

- **改了什么**：`pages/recharge/recharge.ts`（+约 100 行）改为拉取 `/recharge/tiers` 展示配置化档位、调起 `wx.requestPayment`、支付后轮询订单状态；`utils/api.ts` 新增充值段类型与函数（+124 行）；`typings/index.d.ts` 补 4 个字段。
- **原因**：档位此前前端硬编码，与 P2 的档位配置化（见 S-3）必须同源，否则运营改了后台小程序不生效。

### N. 启动预检（fail-fast）与密钥配置外置

- **改了什么**：新增 `Preflight.kt`（Ktor 模块，注册在 modules 列表**第一位**，先于所有业务模块）与 `common/config/StartupPreflight.kt`：启动时一次性校验管理端 JWT、用户端 JWT、签到码 HMAC 密钥、数据库连接、微信小程序/支付、推荐分享域名。`APP_ENV=prod` 时任一密钥缺失/残留开发默认值（`admin-default-secret-change-me` 等）→ 汇总后抛 `IllegalStateException` 阻止启动；非生产只打 WARN。新增 `src/main/resources/application-prod.yaml` 生产配置骨架（所有密钥留空，只走环境变量）。`JwtConfig`（管理端）与 `UserJwtConfig`（用户端）的密钥改为 `GlobalConfig.requireSecret(env, yaml, devDefault)` 解析，顺序为环境变量 `ADMIN_JWT_SECRET`/`USER_JWT_SECRET` > yaml > 开发默认值（仅非 prod）。
- **原因**：配置对象是 lazy 解析的，缺密钥只在首次登录/首次签到才暴露；上线时漏配一个密钥就是线上事故。fail-fast 让错误在部署瞬间暴露，而不是第一个用户触发时。
- **影响范围**：本地开发无任何配置即可启动（全部降级 + WARN）；**生产部署必须准备的环境变量清单**已写进 application-prod.yaml 文件头注释：DB_URL/DB_USERNAME/DB_PASSWORD、ADMIN_JWT_SECRET、USER_JWT_SECRET、CHECKIN_SIGNING_SECRET、WECHAT_APP_ID/SECRET、WECHAT_MCH_ID/API_V3_KEY/MERCHANT_SERIAL、WECHAT_PRIVATE_KEY_PATH、WECHAT_PAY_NOTIFY_URL。生成方式 `openssl rand -hex 32`，两端 JWT 密钥必须各自独立。

### O. P1：到店扫码签到（HMAC 签名码）

#### O-1. CheckinCodeService 安全模型

- **改了什么**：新增 `common/security/CheckinCodeService.kt`：码格式 `dc1.<clinicId>.<HMAC-SHA256签名>`，签名内容为 `dc1.<clinicId>`，base64url 无填充；`issue(clinicId)` 确定性签发（同店同密钥输出一致，可重复打印），`verify(code)` 校验结构/版本/clinicId 合法性并用 `MessageDigest.isEqual` 恒定时间比较防时序侧信道，任一不通过返回 null。密钥取环境变量 `CHECKIN_SIGNING_SECRET` > yaml，缺失时生成一次性随机密钥并 WARN（重启后已打印码全失效，仅本地用）。
- **原因**：此前管理端直接把 `{"clinicId":1}` 明文生成二维码，任何人都能自制任意门店的签到码远程签到。clinicId 在码中明文可见无妨——没有服务端密钥就无法伪造合法签名。设计为**不过期的门店静态码**：码要打印张贴在前台长期使用，过期只会迫使反复重贴；需要整体作废时轮换密钥即可。

#### O-2. 后端签到端点的五重校验

- **改了什么**：`UserAppointmentRoutes` 新增 `POST /api/v1/appointments/{id}/checkin`；`AppointmentService.checkin` 依次校验：① 码能验签解出可信 clinicId；② 门店真实存在；③ 门店处于营业状态；④ 扫码门店与预约门店一致（防在家扫自制码）；⑤ 预约归属本人、状态为 pending/confirmed、且为就诊当天。已签到/已完成/已取消/爽约分别给明确文案。
- **影响范围**：仅新增状态迁移 `pending/confirmed → arrived`，不改既有预约流。

#### O-3. 管理端二维码弹窗

- **改了什么**：新增 `views/clinic/qrcode-modal.vue`：打开时调 `GET /api/admin/clinics/{id}/checkin-code` 从后端取**已签名码原文**，再用浏览器侧 `qrcode.toDataURL` 渲染；门店列表页加「签到码」入口。新增依赖 `qrcode` 与 `@types/qrcode`（package.json + pnpm-lock）。
- **原因**：签名密钥只在服务端，管理端绝不能自行拼码；渲染放浏览器侧是为了后端不引入图片生成依赖。

#### O-4. 小程序扫码上报

- **改了什么**：`pages/appointments` 预约卡片在「pending/confirmed 且就诊日期为今天」时展示「扫码签到」按钮（前端仅做显隐预判，最终以后端校验为准）；点击 `wx.scanCode` 扫码后把码原文**原样上报**，不在小程序侧解析 clinicId；`checkingIn` 锁防重复调起；用户取消扫码不打扰，失败直接展示后端文案。同批次还接入了 `PUT /appointments/{id}/cancel` 取消预约。

### P. P1：积分商城兑换

- **改了什么**：新增 `user/service/PointsMallService.kt`（223 行）与 `user/route/UserPointsMallRoutes.kt`，端点 `GET /api/v1/points/mall/goods`（可兑换商品）、`POST /api/v1/points/mall/exchange`（扣积分 + 生成唯一核销码）、`GET /api/v1/points/mall/orders`（我的兑换记录）。新增 `points_goods`、`points_exchange_order` 两表，DataSeed 播种两个商品：500 积分「口腔检查（单次）」、2000 积分「电动牙刷」。
- **设计约定**：兑换是单向操作（扣积分 + 发核销码），用户到店由前台核销；**管理端核销台未做**，为已知遗留。
- **影响范围**：扣减走 `user_points_summary` 与 `points_records` 流水，余额不足直接报错，不允许为负。

### Q. P1：四个业务定时任务

- **改了什么**：新增 `common/scheduler/` 六个文件：`AppointmentReminderService`（每日 20:05 发次日晚间提醒、10:05 发当日晨间提醒，对应内置模板 appointment_remind_evening/morning）、`AutoNoShowService`（每小时扫描，超时未到诊的 pending/confirmed 自动置 no_show）、`PointsExpireService`（每日，积分过期清零 + 提前 30 天提醒 points_expire_remind）、`MemberLevelReviewService`（年度复评，按消费重算等级），以及 `SchedulerSupport`（守护线程池 + 运行日志）与 `ScheduledNotifier`（优先发微信订阅消息、降级站内信兜底）。新增 `system_job_log` 表做执行记账，任务在 `AdminModule` 启动时装载。
- **幂等设计**：`appointment` 表加「已提醒」标记位（`migrateAppointmentReminderFlags`），重复执行/重启不重复轰炸；年度复评无行级标记，用 `system_job_log` 里带年份的 jobKey（如 `member_level_review_2026`）占位，1 月 1 日当天反复重启也不会连降多级；积分过期列由 `migratePointsRecordExpiryColumns()` 补。
- **影响范围**：微信订阅消息依赖真实模板 ID，未配置时全部降级站内信（见 R 节的公开模板接口）；任务用 daemon 线程池，进程退出不阻塞。**多实例部署时定时任务会多实例重复触发**，当前靠幂等记账兜底，后续应加分布式锁。

### R. P1：推荐关系链路修复与订阅模板公开接口

- **改了什么**：① 小程序 `app.ts`（+77 行）在 onLaunch/onShow 捕获 `query.ref`（分享卡片）与 `scene`（小程序码，需 decodeURIComponent 解析 k=v），暂存 globalData + localStorage（登录前可能多次冷启动），首页登录成功后消费并清空。② 后端 `ReferralService` 修正数据模型：推荐码只存在于**自引用占位行**（referrer_id = referee_id = 本人），绑定推荐关系一律**新增真实关系行**而非 UPDATE 占位行——旧逻辑会抹掉被推荐人自己的推荐码导致其后续无法分享；`SchemaMigration` 新增 `migrateReferralRelationIndex()` 建部分唯一索引 `ref_rel_uk_referee_real ON (referee_id) WHERE referrer_id <> referee_id`，保证一人最多一条真实关系。③ `ContentService`/`UserContentRoutes` 新增公开接口 `GET /api/v1/content/subscribe-templates`：只返回启用且已填真实微信模板 ID 的模板；ID 为空的播种占位不返回，小程序拿不到 ID 就不调 `wx.requestSubscribeMessage`（传空 ID 会直接报错），运营在消息模板配置里填入真实 ID 后无需发版即恢复订阅弹窗。
- **影响范围**：推荐码查询从「按 referrerId 取 limit(1)」改为精确定位自引用行，修复「被推荐后查到的是推荐人的码」的错乱。

### S. P2：管理端配置完整性

#### S-1. 四张配置表 + 迁移 + 菜单权限 seed

- **改了什么**：`AdminTables.kt`（+182 行）新增 7 张表：`recharge_tier`（充值档位）、`customer_tag` + `user_tag_rel`（客户标签与关联）、`admin_user_clinic`（账号-门店授权，见 S-5）、`points_goods`/`points_exchange_order`（积分商城，见 P 节）、`system_job_log`（定时任务记账）；消息模板表 `message_template` 同批启用。`DataSeedService.kt`（+183 行）播种三条内置消息模板（appointment_remind_evening/morning、points_expire_remind，**禁删且 templateKey 锁定**）、两个积分商品，以及菜单：充值档位（/member/tier）、会员等级（/member/level）、充值流水（/recharge/records）、客户标签（/customer/tags）、消息模板（/notification/template）、预约看板（/appointment/board），并补齐对应权限码（recharge_tier:list、member_level:list、recharge:list、customer_tag:list、message_template:list、appointment:create、clinic:data:all 等）。
- **影响范围**：幂等机制沿用 ensureMenu/权限判重，已部署库重启即生效；新菜单只关联 super_admin，其他角色需在角色管理勾选。新页面路由由 DB 菜单 seed 的 component 路径经 import.meta.glob 自动映射。

#### S-2. ClinicScope 门店数据隔离

- **改了什么**：新增 `admin/auth/ClinicScope.kt`：从当前账号解析 `ClinicScope(all, clinicIds)`——拥有 `clinic:data:all` 权限或 super_admin 为全店；否则取 `admin_user_clinic_rel` 的授权列表；无授权为空范围。看板（DashboardRoutes/AdminDashboardService：预约/营收按业务行 clinicId、客户类指标按 primaryClinicId 过滤，空范围直接返回零值）、预约、客户、消费、充值流水等查询服务全部收窄为接受 scope；`VbenRoutes` 的 userInfo 下发 `clinicIds` 与全店标记供前端控制。
- **原因**：多门店账号此前能看到全部门店数据，属越权；隔离必须在后端查询层做，前端隐藏入口不算数。
- **影响范围**：**存量非 super_admin 账号需在用户管理页手工授权门店**，否则看不到任何业务数据（拥有 clinic:data:all 的角色豁免）。

#### S-3. 配置化 CRUD（档位/等级/标签/模板 + 流水分页 + 客户打标）

- **改了什么**：新增 `admin/service/MembershipAdminService.kt`、`admin/route/MembershipAdminRoutes.kt` 与 `dto/request/ManageRequests.kt`、`dto/response/ManageResponse.kt`，端点：`/recharge-tiers` GET/POST/PUT/DELETE、`/member-levels` GET/POST/PUT/DELETE（**有关联客户的等级删除返回 409**）、`/recharge-records` 分页 GET（keyword/clinicId/status/dateFrom/dateTo）、`/customer-tags` CRUD + `/customer-tags/users/{userId}` GET/PUT（客户打标）、`/message-templates` GET/POST/PUT/DELETE（内置模板禁删）。档位逻辑下沉 `common/RechargeTierService.kt` 供 C 端 /recharge/tiers 与管理端共享同一事实源。
- **管理端页面**：新增 API 文件 `api/{recharge,member,customerTag,messageTemplate}.ts`；页面 `views/member/{tier,level}.vue + 两个 modal`、`views/recharge/records.vue`、`views/customer/tags.vue + tag-modal.vue`、`views/notification/template/{index,form-modal}.vue`；客户列表接入标签列与筛选，客户详情弹窗内可管理标签。

#### S-4. 代客预约 + 日期范围筛选 + 时间轴看板

- **改了什么**：`BusinessServices.kt` 新增 `AppointmentAdminService.createAppointment`：校验日期格式、ClinicScope 门店范围、客户/医生状态、排班匹配、容量冲突；代客预约**默认 confirmed、跳过爽约黑名单检查**（前台替客户操作不应被黑名单拦截）。`GET /api/admin/appointments` 增加 `dateFrom/dateTo`（yyyy-MM-dd）筛选。新增 `views/appointment/board.vue`（时间轴看板：单日单店一次查询、前端按医生/时段分组，可切天切店）与 `create-modal.vue`（代约弹窗）；预约列表页加日期范围筛选；`api/appointment.ts` 同步。
- **实现注意**：Exposed r2dbc insert 块内列赋值必须用表名限定（`it[Appointments.userId]`），裸列名在多列同名场景编译报歧义。

#### S-5. 账号门店多选授权

- **改了什么**：`views/system/user/form-modal.vue` 增加「授权门店」多选（ApiSelect 绑定 getClinics，空选 = 无任何门店数据，help 文案说明 clinic:data:all 豁免）；新建/编辑均提交 clinicIds，编辑时全量覆盖（含清空）；`UserRequests`/`UserResponses` 增加 clinicIds 字段；用户列表展示授权情况。

### T. 充值档位 / 会员等级弹窗前端校验（今日收尾）

- **改了什么**：`views/member/tier-modal.vue` 与 `level-modal.vue` 在 Vben 表单只有 required/selectRequired 两条内置规则的限制下，采用两层校验：① `componentProps` 实时约束（maxLength+showCount、InputNumber 的 min/max/precision）；② 弹窗打开时拉取全量档位/等级缓存，`onConfirm` 中做类型转换与业务校验——档位：名称 1-30 字、门槛 (0, 999999]、赠送比例 0~1、排序 0-9999 整数、**门槛金额唯一**（以「分」四舍五入取整比较规避浮点误差，比对排除当前编辑项）；等级：编码 1-9999 整数且唯一、名称 1-20 字、升级门槛 0-9999999、折扣 0.01~1、权益说明 ≤500 字。文本提交前 trim，校验失败 `message.error` 并中断。
- **原因与边界**：唯一性是后端也兜的约束，前端先拦可以少一次请求；拉取列表失败时静默降级为空数组（不阻塞操作，最终以后端校验为准）。

### U. 文档与仓库清理

- **新增 CODE_WIKI.md**（1684 行）：项目级代码知识库，覆盖整体架构、Ktor 模块布局/路由清单/实体表/鉴权、Vben monorepo 结构、小程序页面清单与运行方式，最后更新 2026-09-15。
- **删除根目录 API.md 与 lefthook.yml**：前者为服务端初始化期的接口文档，已被 CODE_WIKI.md 与各路由实现取代；仓库实际未启用 lefthook 钩子，配置文件为死配置。
- **.gitignore 增加 `.trae/`**：IDE/代理的本地工作目录（含实施计划等过程文档），不入库。

### V. 验证结果与验证边界

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 后端编译 | `./gradlew :server:compileKotlin -q --console=plain` | 零输出（成功） |
| 管理端类型 | `pnpm -F @vben/web-antd run typecheck` | EXIT=0 |
| 小程序类型 | `npx tsc --noEmit -p tsconfig.json` | 前序批次已验证通过 |

验证边界（上线前必须补的实测）：

1. **微信真实链路未实测**：微信支付 `/notify` 回调需公网 HTTPS，本地无法接收；配齐凭证后必须做真机登录、真机支付、回调验签、退款对账测试。
2. **定时任务未实跑**：四个任务只做了编译验证与幂等设计，触发时刻（20:05/10:05/整点/每日/1/1）未真实等待验证；多实例部署需补分布式锁。
3. **e2e 未回归**：本轮未跑 Playwright，管理端新页面（看板/档位/等级/标签/模板/流水）与小程序签到/兑换仅有类型检查与编译保证。
4. IDE Kotlin 插件（2.1.0）与 stdlib 2.4.0 / Exposed 1.3.1 元数据不匹配，GetDiagnostics 的 Unresolved reference 全为噪音，后端只认 gradle 编译结果。

上线检查清单：① 配齐 application-prod.yaml 文件头所列全部环境变量；② 微信公众平台配置 request/uploadFile 合法域名与支付回调 HTTPS；③ 配置稳定的 CHECKIN_SIGNING_SECRET 后打印各门店签到码；④ 给非 super_admin 角色授权门店；⑤ 录入真实微信订阅消息模板 ID；⑥ 数据库迁移全部幂等（补列/索引/建表），重启自动执行。

## 2026-09-14

本轮实施需求文档差距分析的**第 1 步**：补齐「小程序个人中心」与「首页内容配置」两条链路，并先清掉四个会在实施过程中直接绊住人的前置阻塞点（B1~B4，见 H 节）。改动跨三端：后端 6 新增 + 8 修改 + 1 迁移，管理端 6 新增 + 1 修改，小程序 16 新增 + 14 修改。

按用户指示「第 1 步不验证」，**跳过正式验证门**——`pnpm exec playwright test`（e2e）与 `pnpm run test:unit` 未跑，content 模块也没有新增 e2e 规格；只跑了编译/类型/风格层面的轻量验证，结果与验证边界见 L 节。

### H. 四个前置阻塞点

#### H-1. 首页图片全是死链（B1）

- **改了什么**：`miniprogram/images/` 目录下只有一个 README.md，而首页 `data` 里硬编码的 3 条 banners（`/images/banner{1,2,3}.jpg`）与 4 条 quickActions（`/images/icon-*.png`）全部指向不存在的文件。本轮 banners 改为后端下发（见 I-3、K-4），quickActions 的 `icon` 字段改为 emoji（📅💰🎁👥），`.quick-action-icon` 字号从 24rpx 提到 44rpx。
- **原因**：死链在真机上表现为轮播区一片空白加四个快捷入口没有图标，是首页最显眼的破相；而补图需要真实设计素材，不是代码层面能解决的问题。emoji 按字号渲染，沿用 24rpx 会小到看不清。
- **影响范围**：仅首页。emoji 方案不引入任何资源文件，也就不存在再次死链的可能。收尾用 `grep -rn "/images/" pages/ utils/ app.ts app.json` 复查为空，全库无残留引用。

#### H-2. 改手机号撞唯一索引返回 500（B2）

- **改了什么**：`user/route/UserApiErrors.kt`（53→96 行）的 `translateApiError` 从两分支扩成三分支，新增 SQLState 23505 识别，命中回 409 并给出用户能据以修正的文案。
- **取证细节**：映射表的键取自 `AdminTables` 里 uniqueIndex 的名字（`cuser_uk_phone` → 「该手机号已被其他账号使用」、`cuser_uk_openid` → 「该微信账号已绑定其他用户」），因为 PostgreSQL 的报错原文形如 `duplicate key value violates unique constraint "cuser_uk_phone"`，约束名稳定且不随数据变化，比解析列名可靠。查找方式是 `generateSequence<Throwable>(e) { it.cause }.filterIsInstance<R2dbcException>().firstOrNull { it.sqlState == "23505" }`——必须沿 cause 链遍历，Exposed 的事务层与 R2DBC 驱动都可能把原始异常裹进别的异常再抛。未命中映射表时回通用文案「数据重复，请检查后重试」，不暴露是哪个约束。
- **原因**：改手机号撞上他人已用号码是业务常态而非故障。回 500 会让用户以为系统坏了并反复重试，而重试永远不会让重复的号码变得不重复。
- **影响范围**：所有走 `translateApiError` 的 C 端路由。冲突分支记 WARN 而非 ERROR，避免淹没真正的服务器故障形成告警噪音（与既有的请求体解析分支同理）。三个分支都不回传 `e.message`：底层异常的文本会把表名/列名/约束名乃至服务器绝对路径暴露给 C 端用户。

#### H-3. 管理端零上传能力（B3）

- **改了什么**：新增 `admin/route/AdminFileRoutes.kt`（103 行），提供 `POST /api/admin/files/upload` 与 `DELETE /api/admin/files/{fileName}`，两个端点均要求 `content:manage` 权限；在 `AdminModule.adminRouting()` 的 `/api/admin` route 下挂载。管理端配套新增 `api/upload.ts`（30 行）。
- **原因**：既有的 `POST /api/v1/files/upload` 位于 `userAuthenticated { }` 内，管理员 JWT 进不去。没有上传能力，轮播图配置页就只能让管理员手填外链，而外链图在小程序里还要额外配 downloadFile 合法域名。
- **取证细节**：前端用 `requestClient.upload(url, { file })` 走 FileUploader，FormData 的 Content-Type 由 axios 在浏览器环境自动设置（带 boundary），不能手写。默认 10s 超时对 5MB 图片偏紧，弱网下容易在传完前被中断，显式设为 60s。后端把 multipart 读取与业务校验拆成两段 try：前者失败包 500 且不回传 `e.message`，后者抛 ValidationException 冒泡到 StatusPages 映射 400。
- **影响范围**：管理端新增两个端点。**素材的读取端点仍在 C 端命名空间**（`GET /api/v1/files/{fileName}`，公开），因此管理端上传后拿到的相对地址在 dev 下需要额外的 vite 代理，见 J-2。

#### H-4. FileUploadService 下沉到 common 避免包循环（B4）

- **改了什么**：`user/service/FileUploadService.kt` 移到 `common/FileUploadService.kt`（git 识别为 rename），`user/UserModule.kt`、`user/route/FileUploadRoutes.kt`、`admin/AdminModule.kt` 三处 import 改指 `com.dentalclinic.common.FileUploadService`。
- **原因**：H-3 让管理端也要上传，但 user 包已依赖 admin 包（54 处），admin 反向 import `user.service` 会形成包循环。与 `BaseService`、`ReferralRewardService` 下沉 common 的理由一致。
- **影响范围**：`saveFile`/`deleteFile`/`batchDeleteFiles` 三个方法新增 `ownerType` 参数（默认 `OWNER_USER`，签名向后兼容）；companion object 加 `OWNER_USER`/`OWNER_ADMIN`/`ADMIN_OWNER_ID` 三个常量。删除时的归属校验**同时比对 userId 与 ownerType**：管理端文件的 userId 统一写 0，只比 userId 会让任意两个管理员互相删掉对方上传的文件，也会让 id 恰为 0 的 C 端用户碰到管理端文件。`getFilePath` 的路径越界防护（空字节拦截 + canonicalFile + 带分隔符前缀比较）随迁移保留未动。

#### H-5. 收尾时清掉的两处残留

- **改了什么**：其一，`user/service/FileUploadService.kt` 在迁移后仍留在磁盘上（9616 字节，内容与 common 版仅差 package 行、一行 import 与一段说明注释），处于 git 未跟踪状态且无任何代码引用，已删除。其二，`server/build.gradle.kts` 有一处只删掉一个空行的改动，与本轮无关，已 `git checkout --` 还原。
- **原因**：两个同名类分居 `com.dentalclinic.user.service` 与 `com.dentalclinic.common` 两个包，编译不报错（FQN 不冲突，所有 import 都已指向 common 版），因此不会被任何一道验证门发现，但它是死代码：后来人改上传逻辑时很可能改到不生效的那一份。
- **影响范围**：删除后重跑 `./gradlew :server:cleanCompileKotlin :server:test`，BUILD SUCCESSFUL——L 节的后端两道门是删完之后跑的，不是复用删之前的结果。

### I. 后端：小程序内容配置与 C 端资料接口

#### I-1. mp_banner / mp_notice 两表与 uploaded_file.owner_type

- **改了什么**：`admin/entity/AdminTables.kt`（+81 行）新增 `MpBanners`（表 `mp_banner`）、`MpNotices`（表 `mp_notice`）与常量对象 `ContentLinkType`（`none`/`page`/`webview`，另有 `ALL` 集合供校验用）；`UploadedFiles` 加 `owner_type varchar(10) default 'user'`。`common/config/SchemaMigration.kt` 新增 `migrateUploadedFileOwnerType()` 并接入 `migrate()` 调用链。
- **表结构**：banner 为 title(100) / image_url(500) / link_type(20) / link_target(500, nullable) / sort_order / status / start_at / end_at / created_by / created_at / updated_at，另建索引 `mp_banner_idx_status_sort (status, sort_order)`；notice 同构，用一个 content 列替换 title + image_url。
- **原因**：不复用既有的 `admin_system_config`——它的 `config_value` 只有 varchar(500)，且管理端是单行 key-value 编辑器，装不下多条「图片 + 标题 + 跳转 + 排期」记录。owner_type 必须走显式迁移，因为 `SchemaUtils.create()` 只建缺失的表、不给已有表补列。
- **影响范围**：历史 `uploaded_file` 行全是 C 端上传，`DEFAULT 'user'` 即正确语义，无需回填。迁移幂等（`addColumnIfMissing`），已部署库重启一次即可。

#### I-2. 管理端 CRUD

- **改了什么**：新增 `admin/service/AdminContentService.kt`（286 行）与 `admin/route/ContentAdminRoutes.kt`（109 行）。`/api/admin/banners` 与 `/api/admin/notices` 各提供 GET 列表（支持 `?status=` 过滤）/ POST / PUT `{id}` / DELETE `{id}`；读操作要 `content:view`，写操作要 `content:manage`。DTO 为 `SaveBannerRequest`、`SaveNoticeRequest`、`BannerItemDto`、`NoticeItemDto`。
- **原因**：管理端列表看全量（含已下线与未到期的），排期过滤只发生在 C 端接口，否则运营没法提前录入待上线的素材。
- **影响范围**：校验集中在 `validateBanner`/`validateNotice`/`validateCommon`：linkType 必须在 `ContentLinkType.ALL` 内；非 none 必须有 linkTarget；webview 的 linkTarget 必须是 https；endAt 不早于 startAt。`parseDate(value, endOfDay)` 与 `AdminCouponService` 同口径——起始补 00:00:00、截止补 23:59:59，同时容忍 `yyyy-MM-dd HH:mm:ss` 全格式，避免运营从 Excel 粘时间进来就报格式错误。

#### I-3. C 端公开接口 GET /api/v1/content/home

- **改了什么**：新增 `user/service/ContentService.kt`（101 行）与 `user/route/UserContentRoutes.kt`（41 行），一次返回 `{ banners, notices }`；DTO 为 `BannerDto(id, title, imageUrl, linkType, linkTarget)`、`NoticeDto(id, content, linkType, linkTarget)`、`HomeContentDto`。路由挂在 `userAuthenticated` **之外**。
- **原因**：合成一个接口而不是 banner、notice 各一个，是为了少一轮 RTT——需求 5.1 要求首屏 <2s。放在鉴权外是因为首页在未登录状态也要展示轮播图与公告。
- **影响范围**：排期三条件为 `status = 1` 且 `(start_at is null 或 <= now)` 且 `(end_at is null 或 >= now)`，命中 I-1 建的复合索引；返回条数上限 `MAX_BANNERS = 10`、`MAX_NOTICES = 5`。异常走 `translateApiError`，与其他 C 端路由一致。

#### I-4. /profile/me 返回真实会员等级与累计消费

- **改了什么**：新增 `user/service/UserProfileService.kt`（198 行），`user/route/UserProfileRoutes.kt` 从 159 行的内联查询瘦身为 84 行的纯路由（查库与校验逻辑全部搬进 service）。`UserProfileDto` 新增 levelCode / levelName / discountRate / benefits / accumulatedConsumption / nextLevelCost 六个字段。
- **原因**：需求 3.7.1 的等级卡片此前在小程序侧是硬编码的「Lv1 / 普通会员 / 距升级 3000」，接口不下发这些字段，前端就只能造假。
- **实现细节**：`getProfile(userId)` 一次取全 `MemberLevels`（该表只有 4 行）在内存里算当前级与下一级，避免两次查询；用户关联的等级记录缺失时兜底到最低级而不是返回 null；已是最高级时 `nextLevelCost = null`（因此小程序侧该字段类型是 `number | null`）。`parseBenefits(raw)` 处理的是 `member_level.benefits` 这个 **text 列存 `{"benefits":[...]}` JSON 文本**的情况（不是 exposed-json 列），解析失败降级为空列表而不抛异常。
- **影响范围**：`updateProfile` 只做格式校验（手机号 `^1[3-9]\d{9}$`、gender ∈ {1,2}、age ∈ 0..150、姓名/昵称非空白），返回 sealed class `ProfileUpdateResult`（Success / NotFound / Error）。唯一约束冲突**不在 service 里捕获**，交路由层的 `translateApiError` 翻译成 409（见 H-2）——在 service 里 catch 会把它变成一条正常的返回值，丢掉 H-2 刚建立的语义。手机号只做格式校验、不做验证码校验，因为短信通道与真实微信 code2Session 尚未接入，号码占用由 `cuser_uk_phone` 唯一索引兜底。

#### I-5. 权限与菜单 seed

- **改了什么**：`admin/service/DataSeedService.kt`（+20/-1）新增权限 `content:view`（查看小程序内容）与 `content:manage`（管理小程序内容）；新增一级目录「内容管理」（routePath `/content`，icon `lucide:image`，sortOrder 75）与两个子菜单「轮播图管理」（`/content/banner` → `/content/banner/index`，icon `lucide:images`）、「公告管理」（`/content/notice` → `/content/notice/index`，icon `lucide:megaphone`）；`linkMenusToSuperAdmin` 的 menuPaths 补 `/content`、`/content/banner`、`/content/notice` 三条；seed 完成的日志文案同步加上 content。
- **原因**：项目是 `accessMode: 'backend'`，菜单全部由 `/menu/all` 下发，不 seed 就看不到入口。
- **影响范围**：幂等性沿用既有机制——`ensureMenu` 按 routePath 判重、权限按 permissionCode 判重、super_admin 每次启动补齐全部权限与菜单，因此已部署的库重启一次即可看到新菜单，不需要手工插数据。**有意不 seed 默认轮播图/公告**：没有真实图片素材，seed 假 URL 等于把 H-1 刚清掉的死链又装回去；小程序侧对空列表有 `wx:if` 兜底（整块不渲染），管理员录入后即生效。

### J. 管理端：内容管理两个配置页

#### J-1. api/content.ts 与 banner / notice 两个页面

- **改了什么**：新增 `api/content.ts`（72 行，`ContentLinkType` 联合类型 + `BannerItem`/`NoticeItem` 接口 + 8 个 CRUD 函数）与 `views/content/{banner,notice}/{index,form-modal}.vue`（4 文件共 631 行，其中 banner/form-modal 226 行、notice/form-modal 145 行）。
- **图片地址独立于 schema 管理**：banner 表单的 `imageUrl` 用 `ref<string>('')` 单独持有，配 antd `Upload` 的 `customRequest` 直传，`onConfirm` 时先校验 imageUrl 非空再 `formApi.validate()`，payload 手动合并。原因是 adapter 里的 Upload 组件绑的是 fileList 数组，而后端存的是单个 URL 字符串，两套模型对不上，硬塞进 schema 要在 onChange 里反复拆装。
- **linkTarget 用 schema 的 `dependencies`**：`if: values => values.linkType !== 'none'`（隐藏）、`rules: values => values.linkType === 'none' ? null : 'required'`（动态必填）、`triggerFields: ['linkType']`。同时保留一个手填 URL 的 Input，便于运营直接粘已有素材地址。
- **两个交互细节**：前端做 5MB 前置拦截（后端也拦，但等传完再报错体验差）；上传失败时只在 `!error?.response` 的情况下弹 toast，因为 requestClient 的响应拦截器已经弹过一次，不判就会双重提示。
- **图标选型**：用 `@vben/icons` 的 `IconifyIcon`，不用 `@ant-design/icons-vue`——后者不在 web-antd 的 dependencies 里（首次写完才发现）。loading 态用 `class="animate-spin"`，因为 @iconify/vue 的 Icon 没有 `spin` 属性。

#### J-2. vite dev server 补 /api/v1 代理

- **改了什么**：`apps/web-antd/vite.config.ts` 新增 `/api/v1` → `http://localhost:8080` 的代理。
- **原因**：上传素材的读取端点在 C 端命名空间下（`GET /api/v1/files/{fileName}`），而 `requestClient` 的 baseURL 是 `/api/admin`，原有代理只覆盖 `/api/admin`。不代理的话，管理端上传成功后拿到的相对地址在 dev 下 404，轮播图预览、门店封面、医生头像全是裂图。
- **影响范围**：仅 dev server；生产由 Nginx 统一转发，不受影响。

### K. 小程序：个人中心四页与首页改造

#### K-1. 四个新页面（16 文件 878 行）

- **改了什么**：新增 `pages/profile-edit`（4 文件 268 行，编辑姓名/昵称/手机号/性别/年龄，头像走 `wx.chooseMedia` + `uploadFile`）、`pages/medical-records`（4 文件 163 行，就诊记录列表，分页 + 关键词搜索）、`pages/medical-record-detail`（4 文件 192 行，单次就诊详情含消费明细）、`pages/messages`（4 文件 255 行，消息中心，读/未读与标记已读）；`app.json` 注册四页。
- **原因**：需求 3.7 个人中心里的这三项（编辑资料、就诊记录、消息中心）此前是 `wx.showToast({ title: 'xx开发中' })` 占位，点了只弹 toast。对应后端接口（`PUT /profile/me`、`GET /medical-records`、`GET /messages`）早就存在，缺的只是页面。
- **一处逻辑修正**：`medical-record-detail` 最初写了个 `onCallClinic`，把 `record.clinicAddress` 当电话号码传给 `wx.makePhoneCall`——详情 DTO 里根本没有门诊电话字段，地址字符串喂给拨号是错的。已删掉该方法与对应按钮，只保留「再次预约」。
- **profile-edit 的错误呈现**：提交失败时直接展示 `error.message`，这样 H-2 的 409 文案（「该手机号已被其他账号使用」）能原样呈现给用户，而不是被吞成一句「保存失败」。

#### K-2. api.ts 与 types/index.ts 补齐

- **改了什么**：`utils/api.ts` 442→552 行——`UserProfileResponse` 扩展 6 个会员字段；新增「就诊记录」段（`MedicalRecord`/`ConsumptionDetail`/`MedicalRecordDetail`/`MedicalRecordsResponse` + `getMedicalRecords(page, pageSize, keyword?)` + `getMedicalRecordDetail(id)`）与「首页内容」段（`ContentLinkType`/`Banner`/`Notice`/`HomeContent` + `getHomeContent()`，`needAuth = false`）；`updateUserProfile` 的返回类型改为 `put<null>`。`types/index.ts` 167→226 行——`UserInfo` 加 balance/bonusBalance/nickname，`MemberLevel.nextLevelCost` 改 `number | null`，新增 `MedicalRecord`/`ConsumptionDetail`/`MedicalRecordDetail`/`ContentLinkType`/`Banner`/`Notice`。
- **原因**：`updateUserProfile` 原声明的返回类型带着 data，而后端 `PUT /profile/me` 只回 code/message；`nextLevelCost` 改成可空是因为已是最高等级时后端回 null（见 I-4），保持 `number` 会让前端把 null 当 0 算进度条。
- **影响范围**：仅类型与新增函数，未改动既有函数签名，现有页面无需跟改。

#### K-3. 相对文件地址的解析

- **改了什么**：`utils/request.ts` 拆出并导出 `API_ORIGIN`，`BASE_URL` 改为 `${API_ORIGIN}/api/v1` 模板串；`utils/util.ts` 新增 `resolveFileUrl(url)`。
- **原因**：后端返回的文件地址是相对路径（`FileUploadService` 的 `baseUrl` 为 `/api/v1/files`），而小程序的 `<image src>` 不认相对路径，直接绑上去就是裂图。
- **影响范围**：`resolveFileUrl` 对 `//`、`data:`、`wxfile://`、`http://tmp/`、`^https?://` 五种前缀原样返回（`wx.chooseMedia` 的临时路径与网络图都落在这几类），其余拼 `API_ORIGIN`。导出 `API_ORIGIN` 而不是让 util 再写一遍域名，避免两处配置漂移。

#### K-4. 首页轮播图与公告改后端下发

- **改了什么**：`pages/index/index.ts`（309→402 行）——`data.banners`/`data.notices` 改为空数组，新增 `loadHomeContent()` 在 `onShow` 里当两者都为空时补拉；`navigateByContent(linkType, linkTarget)` 统一处理跳转；新增 `onBannerImageError(e)`。`index.wxml` 用 `<image>` + `wx:if` 渲染轮播、公告改纵向 swiper；`index.scss` 新增 `.banner-item { position: relative }`、`.banner-image`、`.notice-bar`/`.notice-icon`/`.notice-swiper`/`.notice-item`。
- **跳转策略**：`none` 或无 target 直接返回；`webview` 明确 toast「该活动暂不支持在小程序内打开」而不是静默失败；`page` 类型先查 `TAB_BAR_PAGES` 决定用 `switchTab` 还是 `navigateTo`，`navigateTo` 带 fail 回调提示「页面不存在」。
- **原因**：webview 之所以只提示不跳转，是因为项目里没有 web-view 承载页。后端 `AdminContentService.validateCommon` 仍允许录入 https 的 webview 目标——管理端能力保持完整，C 端待补承载页，这样补的时候不用改数据也不用改管理端。
- **影响范围**：`onBannerImageError` 在图片加载失败时把该项的 image 置空（``this.setData({ [`banners[${index}].image`]: '' })``），配合 wxml 的 wx:if 让整项退化为纯色标题块，而不是留一个灰色破图占位。余额行改为读 `userInfo.balance`/`bonusBalance` 真值，此前是硬编码的假数字。

#### K-5. 顺带修掉一个既有的静默失败

- **改了什么**：`index.ts` 新增 `TAB_BAR_PAGES` 常量（四个 tab 路径），`onQuickAction` 与 `onClinicTap` 由 `wx.navigateTo` 改为按该常量选 `switchTab`；`app.ts` 与 `typings/index.d.ts` 新增 `globalData.pendingClinicId` 字段与 `setPendingClinicId(id)` 方法；`pages/appointment/appointment.ts` 新增 `onShow()`，读取 `pendingClinicId` 后**立即置空**再 setData + `loadDepartments()`。
- **原因**：首页 quickActions 的「预约挂号」（`/pages/appointment/appointment`）与门店卡片点击都在用 `navigateTo` 跳 tabBar 页面，而 `navigateTo` 不能跳 tabBar、会**静默失败**——点了完全没反应，控制台也不报错。这是本轮之前就存在的缺陷，改首页时顺手撞到。
- **影响范围**：门店 id 因为 `switchTab` 不支持带 query，改走 `globalData.pendingClinicId` 中转。`onShow` 里读完立刻置空是必要的：否则用户自己切到预约 tab 时会被上一次点击残留的门店 id 悄悄改掉选择。

#### K-6. profile 页对齐真实数据

- **改了什么**：`pages/profile/profile.ts`（141→171 行）——`loadUserInfo` 用后端真实字段填 `memberLevel`（levelCode/levelName/discountRate/benefits/nextLevelCost）与 `totalSpent`（accumulatedConsumption）、balance、bonusBalance、avatar（经 `resolveFileUrl`），不再硬编码 Lv1/普通会员/3000；`onLoad` 只做 `checkLogin()`，实际取数移到 `onShow`；新增 `formatDiscount(discount)`；`editProfile`/`goToVisitRecords`/`goToMessages` 三个 toast 占位改为 `wx.navigateTo`。`profile.wxml` 加余额行、权益标签、折扣行；`profile.scss` +24 行。
- **原因**：取数移到 `onShow` 是为了从 profile-edit 返回时资料能自动刷新（`onLoad` 在页面栈里只跑一次）。
- **两处显示细节**：折扣不在 wxml 里直接写 `discount * 10`，因为会出浮点尾差（0.57 × 10 = 5.699999999999999），改在 ts 侧用 `Number((discount * 10).toFixed(2))`，且 `discount >= 1` 时返回空串让 wxml 隐藏该行。升级进度条的显示条件从 `nextLevelCost > 0 && userInfo.totalSpent` 改为只判 `nextLevelCost > 0` 并加 `wx:else` 分支显示「已是当前最高等级」——原条件下累计消费为 0 的新用户看不到进度条，而新用户恰恰是最该看到升级激励的一批。

### L. 验证结果与验证边界

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 后端编译 | `./gradlew :server:cleanCompileKotlin` | BUILD SUCCESSFUL（强制重编、非 UP-TO-DATE），日志无 `w:` 告警 |
| 后端测试 | `./gradlew :server:test` | BUILD SUCCESSFUL in 43s（`compileTestKotlin` 与 `test` 均真实执行） |
| 后端打包 | `./gradlew :server:build -x test` | EXIT=0 |
| 管理端类型 | `pnpm typecheck` | EXIT=0（初次报 4 处 TS2345，见下） |
| 管理端风格 | `npx oxlint --config oxlint.config.ts` | Found 0 warnings and 0 errors（6 文件 / 217 规则） |
| 管理端格式 | `npx oxfmt` + `npx eslint` | 均无输出 |
| 小程序类型 | `npx tsc --noEmit -p tsconfig.json` | EXIT=0 |
| 死链复查 | `grep -rn "/images/" pages/ utils/ app.ts app.json` | 空 |

四点说明。

其一，typecheck 初次报 4 处 TS2345：`views/content/{banner,notice}/index.vue` 的 `onEdit(row: BannerItem)` / `onDelete(row: BannerItem)` 与 vxe grid slot 传进来的 `Record<string, any>` 不兼容。按项目既有约定（`views/clinic/index.vue` 用的就是 `row: any`）移除 `import type` 并把参数改成 `any`。这是 vxe slot 类型定义的既有限制，不是本轮引入的问题。

其二，小程序的 `npx tsc --noEmit` 首次执行无任何输出，为确认它不是空跑，先用 `--listFiles | grep -c miniprogram` 得到 20 个文件，再写入一个 `const x: number = "abc"` 的探针文件确认能报 TS2322，随后删除探针。

其三，后端两道门是在删掉 H-5 那个死代码副本之后重跑的，不是复用删之前的结果。

其四，`grep -rn` 在 zsh 下不能直接写 `--include=*.ts`（会报 `no matches found`），改用显式目录列表。

**验证边界**：按用户指示跳过了 e2e 与单元测试，因此新增的两个管理端配置页与四个小程序页面**未经浏览器/真机实测**，其正确性只有类型检查、lint 与后端编译/测试保证。内容配置的完整链路（管理端上传图 → 存 mp_banner → 小程序首页拉取渲染）需要在后端起来之后手工走一遍。

**运维提示**：

- 数据库无需手工迁移：`mp_banner`/`mp_notice` 由 `SchemaUtils.create` 建表，`uploaded_file.owner_type` 由 `SchemaMigration` 补列，两者都在启动时幂等执行。
- 新菜单只自动关联了 super_admin。其他角色要看到「内容管理」，需在「系统管理 → 角色管理」里手工勾选。
- **首页轮播图与公告初始为空**，管理员在「内容管理」录入后小程序首页才展示（有意为之，理由见 I-5）。
- 小程序 `utils/request.ts` 的 `API_ORIGIN` 仍是 `http://localhost:8080`：真机预览需改成电脑局域网 IP，上线需换成 HTTPS 域名并在微信公众平台配置 request 合法域名。

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
