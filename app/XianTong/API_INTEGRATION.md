# API 对接完成清单

## 已完成

### 1. 基础架构
- ✅ `utils/request.ts` - 更新为真实 API 请求封装，移除模拟数据
- ✅ `utils/api.ts` - 创建统一的 API 服务层，封装所有后端接口
- ✅ `pages/index/index.ts` - 微信登录对接 `/api/v1/auth/wechat-login`
- ✅ `pages/appointment/appointment.ts` - 预约流程对接真实 API

### 2. API 服务层 (`utils/api.ts`) 已封装的接口

| 模块 | 函数 | 对应后端接口 |
|---|---|---|
| **认证** | `wechatLogin()` | POST `/api/v1/auth/wechat-login` |
| | `refreshToken()` | POST `/api/v1/auth/refresh` |
| **用户资料** | `getUserProfile()` | GET `/api/v1/profile/me` |
| | `updateUserProfile()` | PUT `/api/v1/profile/me` |
| **门诊/医生** | `getClinics()` | GET `/api/v1/clinics` |
| | `getDepartments()` | GET `/api/v1/departments` |
| | `getDoctors()` | GET `/api/v1/doctors` |
| | `getDoctorSlots()` | GET `/api/v1/appointments/slots` |
| **预约** | `createAppointment()` | POST `/api/v1/appointments` |
| | `getMyAppointments()` | GET `/api/v1/appointments/my` |
| | `cancelAppointment()` | PUT `/api/v1/appointments/{id}/cancel` |
| | `rescheduleAppointment()` | PUT `/api/v1/appointments/{id}/reschedule` |
| **钱包** | `getWalletDetails()` | GET `/api/v1/wallet/details` |
| | `getRechargeTiers()` | GET `/api/v1/recharge/tiers` |
| | `createRecharge()` | POST `/api/v1/recharge/create` |
| **积分** | `getPointsBalance()` | GET `/api/v1/points/balance` |
| | `getPointsRecords()` | GET `/api/v1/points/records` |
| | `checkin()` | POST `/api/v1/points/checkin` |
| | `claimProfileCompleteReward()` | POST `/api/v1/points/profile-complete` |
| **推荐** | `getReferralCode()` | GET `/api/v1/referral/code` |
| | `getReferralRecords()` | GET `/api/v1/referral/records` |
| | `registerWithReferral()` | POST `/api/v1/referral/register` |
| **优惠券** | `getCoupons()` | GET `/api/v1/coupons` |
| | `redeemCoupon()` | POST `/api/v1/coupons/redeem` |
| **消息** | `getMessages()` | GET `/api/v1/messages` |
| | `getUnreadCount()` | GET `/api/v1/messages/unread-count` |
| | `markMessageRead()` | PUT `/api/v1/messages/{id}/read` |
| | `markAllRead()` | PUT `/api/v1/messages/read-all` |

## 待更新的页面

以下页面需要更新以使用 `utils/api.ts` 中的 API 函数：

### 3. 预约记录页面 (`pages/appointments/appointments.ts`)
```typescript
// 替换前
import { get, post } from '../../utils/request'
const result = await get('/appointments')

// 替换后
import { getMyAppointments, cancelAppointment } from '../../utils/api'
const result = await getMyAppointments(status)
await cancelAppointment(id)
```

### 4. 充值页面 (`pages/recharge/recharge.ts`)
```typescript
import { getRechargeTiers, createRecharge } from '../../utils/api'

// 加载充值档位
const result = await getRechargeTiers()

// 创建充值订单
const result = await createRecharge({ amount, clinicId })
```

### 5. 钱包页面 (`pages/wallet/wallet.ts`)
```typescript
import { getWalletDetails } from '../../utils/api'

const result = await getWalletDetails()
// result.data: { balance, bonusBalance, totalBalance, rechargeHistory }
```

### 6. 积分页面 (`pages/points/points.ts`)
```typescript
import { getPointsBalance, getPointsRecords, checkin } from '../../utils/api'

// 获取积分余额
const result = await getPointsBalance()

// 签到
const result = await checkin()
```

### 7. 推荐页面 (`pages/referral/referral.ts`)
```typescript
import { getReferralCode, getReferralRecords } from '../../utils/api'

const result = await getReferralCode()
// result.data: { referralCode, shareLink }
```

### 8. 个人中心页面 (`pages/profile/profile.ts`)
```typescript
import { getUserProfile, getCoupons, getMessages, getUnreadCount } from '../../utils/api'

// 获取用户资料（已在 index.ts 中实现）
const result = await getUserProfile()

// 获取优惠券
const result = await getCoupons()

// 获取未读消息数
const result = await getUnreadCount()
```

## 使用说明

1. **所有页面** 导入 `utils/api.ts` 而不是直接使用 `utils/request.ts`
2. **API 函数** 已经处理了认证 token、错误处理等逻辑
3. **不需要认证** 的接口（如获取门诊列表）在 api.ts 中已标记 `needAuth = false`
4. **后端地址** 在 `utils/request.ts` 中配置，开发环境默认 `http://localhost:8080`

## 注意事项

1. **CORS**: 确保后端已启用 CORS，允许小程序域名访问
2. **HTTPS**: 生产环境必须使用 HTTPS
3. **Token 刷新**: 当前未实现自动刷新 token，401 时会跳转回首页重新登录
4. **错误处理**: 所有 API 调用建议用 try-catch 包裹

## 测试步骤

1. 启动后端服务（端口 8080）
2. 微信开发者工具导入项目
3. 点击"编译"
4. 测试登录功能
5. 依次测试各页面功能
