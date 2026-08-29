# 牙科门诊小程序 API 文档

**版本**: v1.1  
**更新日期**: 2026-08-29  
**基础URL**: `http://localhost:8080`

---

## 目录

- [用户端 API](#用户端-api)
  - [认证](#1-认证)
  - [个人中心](#2-个人中心)
  - [钱包](#3-钱包)
  - [充值](#4-充值)
  - [预约管理](#5-预约管理)
  - [积分体系](#6-积分体系)
  - [推荐裂变](#7-推荐裂变)
  - [优惠券](#8-优惠券)
  - [消息通知](#9-消息通知)
  - [文件上传](#10-文件上传)
- [管理端 API](#管理端-api)
  - [认证](#1-管理端认证)
  - [用户管理](#2-用户管理)
  - [角色管理](#3-角色管理)
  - [菜单管理](#4-菜单管理)
  - [数据看板](#5-数据看板)
  - [配置管理](#6-配置管理)
  - [审计日志](#7-审计日志)

---

## 通用说明

### 响应格式

所有接口返回统一的 JSON 格式：

```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | int | 状态码，0 表示成功，非 0 表示失败 |
| message | string | 响应消息 |
| data | object/null | 响应数据，失败时为 null |

### 常见错误码

| code | 说明 |
|---|---|
| 0 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证或令牌无效 |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### 认证方式

用户端接口需要在请求头中携带 JWT access token：

```
Authorization: Bearer <access_token>
```

---

## 用户端 API

### 1. 认证

#### 1.1 微信登录/注册

通过微信授权 code 进行登录或注册。新用户自动创建账户并初始化钱包。

**请求**

```
POST /api/v1/auth/wechat-login
Content-Type: application/json
```

**请求体**

```json
{
  "code": "wechat_js_code"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | string | 是 | 微信小程序 wx.login() 返回的 code |

**响应示例**

```json
{
  "code": 0,
  "message": "注册成功",
  "data": {
    "userId": 1,
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "isNewUser": true
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| userId | long | 用户ID |
| accessToken | string | 访问令牌（24小时有效） |
| refreshToken | string | 刷新令牌（30天有效） |
| isNewUser | boolean | 是否为新注册用户 |

---

#### 1.2 刷新 Access Token

使用 refresh token 获取新的 access token。

**请求**

```
POST /api/v1/auth/refresh
Content-Type: application/json
```

**请求体**

```json
{
  "refreshToken": "eyJhbGci..."
}
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGci..."
  }
}
```

**错误响应**

```json
{
  "code": 401,
  "message": "无效的刷新令牌",
  "data": null
}
```

---

### 2. 个人中心

#### 2.1 获取个人信息

获取当前登录用户的个人资料和钱包余额摘要。

**请求**

```
GET /api/v1/profile/me
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": 1,
    "openid": "mock_openid_test_code_001",
    "phone": null,
    "nickname": null,
    "realName": null,
    "gender": null,
    "age": null,
    "avatar": null,
    "primaryClinicId": null,
    "balance": 500.0,
    "bonusBalance": 50.0
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| userId | long | 用户ID |
| openid | string | 微信openid |
| phone | string/null | 手机号 |
| nickname | string/null | 昵称 |
| realName | string/null | 真实姓名 |
| gender | int/null | 性别（1=男，2=女） |
| age | int/null | 年龄 |
| avatar | string/null | 头像URL |
| primaryClinicId | long/null | 首选就诊门店ID |
| balance | double | 实际支付余额 |
| bonusBalance | double | 赠送金额 |

---

#### 2.2 更新个人信息

修改用户的个人资料（支持部分字段更新）。

**请求**

```
PUT /api/v1/profile/me
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**

```json
{
  "realName": "张三",
  "phone": "13800138000",
  "gender": 1,
  "age": 30,
  "avatar": "https://example.com/avatar.jpg",
  "nickname": "牙医小张"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| realName | string | 否 | 真实姓名 |
| phone | string | 否 | 手机号（需符合11位格式） |
| gender | int | 否 | 性别：1=男，2=女 |
| age | int | 否 | 年龄（0-150） |
| avatar | string | 否 | 头像URL |
| nickname | string | 否 | 昵称 |

所有字段均为可选，仅更新传入的字段。

**响应示例**

```json
{
  "code": 0,
  "message": "更新成功",
  "data": null
}
```

**错误响应**

```json
{
  "code": 400,
  "message": "手机号格式不正确",
  "data": null
}
```

```json
{
  "code": 400,
  "message": "性别值无效，应为1(男)或2(女)",
  "data": null
}
```

---

### 3. 钱包

#### 3.1 获取钱包详情

获取完整的钱包信息，包括余额、赠送金额和最近10条充值记录。

**请求**

```
GET /api/v1/wallet/details
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "balance": 6500.0,
    "bonusBalance": 1150.0,
    "totalBalance": 7650.0,
    "version": 3,
    "rechargeHistory": [
      {
        "id": 5,
        "amount": 5000.0,
        "bonusAmount": 1000.0,
        "status": "success",
        "paidAt": "2026-08-28 12:25:48",
        "createdAt": "2026-08-28 12:25:47"
      },
      {
        "id": 4,
        "amount": 1000.0,
        "bonusAmount": 100.0,
        "status": "success",
        "paidAt": "2026-08-28 12:25:35",
        "createdAt": "2026-08-28 12:25:34"
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| balance | double | 实际支付余额 |
| bonusBalance | double | 赠送金额 |
| totalBalance | double | 总可用余额（balance + bonusBalance） |
| version | int | 乐观锁版本号 |
| rechargeHistory | array | 最近10条充值记录（按时间倒序） |

**充值记录字段**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | long | 充值记录ID |
| amount | double | 充值金额 |
| bonusAmount | double | 赠送金额 |
| status | string | 状态（pending/success/failed/refunded） |
| paidAt | string/null | 支付时间 |
| createdAt | string | 创建时间 |

---

### 4. 充值

#### 4.1 获取充值档位

获取系统配置的充值档位及赠送规则。

**请求**

```
GET /api/v1/recharge/tiers
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    { "amount": 1000, "bonus": 100, "rate": "10%" },
    { "amount": 3000, "bonus": 450, "rate": "15%" },
    { "amount": 5000, "bonus": 1000, "rate": "20%" },
    { "amount": 10000, "bonus": 2500, "rate": "25%" }
  ]
}
```

---

#### 4.2 创建充值订单

创建充值订单并模拟支付成功（生产环境需对接微信支付）。

**请求**

```
POST /api/v1/recharge/create
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**

```json
{
  "amount": 1000,
  "clinicId": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| amount | double | 是 | 充值金额（1~5000元） |
| clinicId | long | 是 | 操作门店ID |

**响应示例**

```json
{
  "code": 0,
  "message": "充值成功",
  "data": {
    "recordId": 4,
    "amount": 1000.0,
    "bonusAmount": 100.0,
    "totalAdded": 1100.0,
    "paidAt": "2026-08-28T12:25:35.009003"
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| recordId | long | 充值记录ID |
| amount | double | 充值金额 |
| bonusAmount | double | 赠送金额 |
| totalAdded | double | 总共增加金额 |
| paidAt | string | 支付完成时间 |

**错误响应**

```json
{
  "code": 400,
  "message": "充值金额不能小于1元",
  "data": null
}
```

```json
{
  "code": 400,
  "message": "单笔充值不能超过5000元",
  "data": null
}
```

---

### 5. 预约管理

#### 5.1 查询门店列表

获取所有营业中的门店列表。

**请求**

```
GET /api/v1/clinics
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "阳光口腔门诊",
      "address": "北京市朝阳区建国路100号",
      "phone": "010-12345678",
      "openTime": "09:00",
      "closeTime": "18:00",
      "description": "专业口腔医疗服务",
      "coverImage": null
    }
  ]
}
```

---

#### 5.2 查询科室列表

获取指定门店的科室/项目列表。

**请求**

```
GET /api/v1/departments?clinicId=1
Authorization: Bearer <access_token>
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| clinicId | long | 是 | 门店ID |

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "口腔检查",
      "description": "常规口腔健康检查"
    },
    {
      "id": 2,
      "name": "洗牙",
      "description": "超声波洁牙服务"
    }
  ]
}
```

---

#### 5.3 查询医生列表

获取指定门店的医生列表，可按科室筛选。

**请求**

```
GET /api/v1/doctors?clinicId=1&departmentId=
Authorization: Bearer <access_token>
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| clinicId | long | 是 | 门店ID |
| departmentId | long | 否 | 科室ID，不传则返回该门店所有医生 |

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "张医生",
      "title": "主治医师",
      "avatar": null,
      "introduction": "10年口腔临床经验",
      "specialties": "口腔检查,洗牙,补牙"
    }
  ]
}
```

---

#### 5.4 查询可预约时段

获取指定医生在指定日期的可预约时段及剩余名额。

**请求**

```
GET /api/v1/appointments/slots?doctorId=1&date=2026-09-01
Authorization: Bearer <access_token>
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| doctorId | long | 是 | 医生ID |
| date | string | 是 | 日期（yyyy-MM-dd） |

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "startTime": "09:00",
      "endTime": "09:30",
      "remaining": 2,
      "maxAppointments": 2
    },
    {
      "startTime": "09:30",
      "endTime": "10:00",
      "remaining": 1,
      "maxAppointments": 2
    }
  ]
}
```

---

#### 5.5 创建预约

提交新的预约申请。

**请求**

```
POST /api/v1/appointments
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**

```json
{
  "clinicId": 1,
  "doctorId": 1,
  "departmentId": 1,
  "appointmentDate": "2026-09-01",
  "startTime": "09:00",
  "remark": "首次就诊"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| clinicId | long | 是 | 门店ID |
| doctorId | long | 是 | 医生ID |
| departmentId | long | 否 | 科室ID |
| appointmentDate | string | 是 | 预约日期（yyyy-MM-dd） |
| startTime | string | 是 | 时段开始时间（HH:mm） |
| remark | string | 否 | 备注 |

**响应示例**

```json
{
  "code": 0,
  "message": "预约成功",
  "data": {
    "appointmentId": 1
  }
}
```

**错误响应**

```json
{
  "code": 400,
  "message": "该时段已满",
  "data": null
}
```

```json
{
  "code": 400,
  "message": "您在该时段已有预约",
  "data": null
}
```

---

#### 5.6 我的预约列表

获取当前用户的预约记录列表，可按状态筛选。

**请求**

```
GET /api/v1/appointments/my?status=
Authorization: Bearer <access_token>
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | string | 否 | 状态筛选：pending/confirmed/arrived/completed/cancelled/no_show |

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "clinicName": "阳光口腔门诊",
      "doctorName": "张医生",
      "appointmentDate": "2026-09-01",
      "startTime": "09:00",
      "endTime": "09:30",
      "status": "pending",
      "remark": "首次就诊",
      "createdAt": "2026-08-29T10:00:00"
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| id | long | 预约ID |
| clinicName | string | 门店名称 |
| doctorName | string | 医生姓名 |
| appointmentDate | string | 预约日期 |
| startTime | string | 开始时间 |
| endTime | string | 结束时间 |
| status | string | 状态 |
| remark | string/null | 备注 |
| createdAt | string | 创建时间 |

---

#### 5.7 取消预约

用户自主取消待就诊的预约。

**请求**

```
PUT /api/v1/appointments/{id}/cancel
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "取消成功",
  "data": null
}
```

**错误响应**

```json
{
  "code": 400,
  "message": "预约已完成，无法取消",
  "data": null
}
```

---

#### 5.8 改约

修改已预约的时间段。

**请求**

```
PUT /api/v1/appointments/{id}/reschedule
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**

```json
{
  "appointmentDate": "2026-09-05",
  "startTime": "10:00"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| appointmentDate | string | 是 | 新预约日期（yyyy-MM-dd） |
| startTime | string | 是 | 新时段开始时间（HH:mm） |

**响应示例**

```json
{
  "code": 0,
  "message": "改约成功",
  "data": null
}
```

**错误响应**

```json
{
  "code": 400,
  "message": "不能改约到过去的日期",
  "data": null
}
```

```json
{
  "code": 400,
  "message": "该时段已满",
  "data": null
}
```

---

### 6. 积分体系

#### 6.1 查询积分余额

获取当前用户的积分总额。

**请求**

```
GET /api/v1/points/balance
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalBalance": 150,
    "updatedAt": "2026-08-29T10:00:00"
  }
}
```

---

#### 6.2 查询积分明细

分页获取积分流水记录。

**请求**

```
GET /api/v1/points/records?page=1&pageSize=20
Authorization: Bearer <access_token>
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | int | 否 | 页码，默认1 |
| pageSize | int | 否 | 每页数量，默认20 |

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "changeAmount": 50,
        "balanceAfter": 150,
        "sourceType": "referral_registration",
        "description": "推荐注册奖励",
        "expiresAt": "2027-08-29 10:00:00",
        "createdAt": "2026-08-29 10:00:00"
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| changeAmount | int | 变动数量（正=获得，负=消耗） |
| balanceAfter | int | 变动后余额 |
| sourceType | string | 来源类型 |
| description | string/null | 说明 |
| expiresAt | string | 过期时间 |
| createdAt | string | 获得时间 |

---

#### 6.3 每日签到

每日签到获得积分（每日限1次）。

**请求**

```
POST /api/v1/points/checkin
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "签到成功",
  "data": {
    "pointsEarned": 2
  }
}
```

**错误响应**

```json
{
  "code": 400,
  "message": "今日已签到",
  "data": null
}
```

---

#### 6.4 完善信息奖励

完善个人信息后获得一次性积分奖励。

**请求**

```
POST /api/v1/points/profile-complete
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "奖励发放成功",
  "data": {
    "pointsEarned": 50
  }
}
```

**错误响应**

```json
{
  "code": 400,
  "message": "已完成过信息完善奖励",
  "data": null
}
```

---

### 7. 推荐裂变

#### 7.1 获取推荐码

获取个人专属推荐码和分享链接。

**请求**

```
GET /api/v1/referral/code
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "referralCode": "REF12345678",
    "shareLink": "https://example.com/register?code=REF12345678"
  }
}
```

---

#### 7.2 查询推荐记录

获取被推荐人列表及奖励状态。

**请求**

```
GET /api/v1/referral/records?page=1&pageSize=20
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "refereeName": "用户28",
        "registeredAt": "2026-08-29 10:00:00",
        "firstConsumedAt": "2026-08-29 11:00:00",
        "rewardStatus": "full"
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| refereeName | string | 被推荐人昵称 |
| registeredAt | string | 注册时间 |
| firstConsumedAt | string/null | 首次消费时间 |
| rewardStatus | string | 奖励状态：pending/partial/full |

---

#### 7.3 绑定推荐关系

新用户注册时使用推荐码建立推荐关系。

**请求**

```
POST /api/v1/referral/register
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**

```json
{
  "referralCode": "REF12345678"
}
```

**响应示例**

```json
{
  "code": 0,
  "message": "推荐关系绑定成功",
  "data": null
}
```

**错误响应**

```json
{
  "code": 400,
  "message": "无效的推荐码",
  "data": null
}
```

```json
{
  "code": 400,
  "message": "该用户已被推荐",
  "data": null
}
```

---

### 8. 优惠券

#### 8.1 我的优惠券

获取用户持有的优惠券列表，可按状态筛选。

**请求**

```
GET /api/v1/coupons?status=unused
Authorization: Bearer <access_token>
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | string | 否 | 状态筛选：unused/used/expired |

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "couponCode": "NEW20057523",
      "templateName": "推荐新人券",
      "couponType": "cash",
      "faceValue": 100.0,
      "minConsumption": 0.0,
      "validFrom": "2026-08-29 10:00:00",
      "validTo": "2026-11-27 10:00:00",
      "status": "unused",
      "usedAt": null
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| couponCode | string | 券码 |
| templateName | string | 模板名称 |
| couponType | string | 类型：cash/discount/service |
| faceValue | double | 面值 |
| minConsumption | double | 使用门槛 |
| validFrom | string | 生效时间 |
| validTo | string | 失效时间 |
| status | string | 状态 |
| usedAt | string/null | 使用时间 |

---

#### 8.2 核销优惠券

使用优惠券进行抵扣。

**请求**

```
POST /api/v1/coupons/redeem
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**

```json
{
  "couponCode": "NEW20057523",
  "appointmentId": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| couponCode | string | 是 | 券码 |
| appointmentId | long | 否 | 关联的预约ID |

**响应示例**

```json
{
  "code": 0,
  "message": "核销成功",
  "data": {
    "couponName": "推荐新人券",
    "faceValue": 100.0
  }
}
```

**错误响应**

```json
{
  "code": 400,
  "message": "优惠券已使用或已过期",
  "data": null
}
```

```json
{
  "code": 400,
  "message": "优惠券已过期",
  "data": null
}
```

---

### 9. 消息通知

#### 9.1 消息列表

分页获取用户的站内消息列表。

**请求**

```
GET /api/v1/messages?page=1&pageSize=20&msgType=&isRead=
Authorization: Bearer <access_token>
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | int | 否 | 页码 |
| pageSize | int | 否 | 每页数量 |
| msgType | string | 否 | 消息类型：system/consumption/activity |
| isRead | boolean | 否 | 是否已读：true/false |

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "title": "系统通知",
        "content": "欢迎使用牙科门诊小程序",
        "msgType": "system",
        "isRead": false,
        "relatedId": null,
        "createdAt": "2026-08-29 10:00:00",
        "readAt": null
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20
  }
}
```

---

#### 9.2 未读数量

获取未读消息数量，用于红点提醒。

**请求**

```
GET /api/v1/messages/unread-count
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "count": 3
  }
}
```

---

#### 9.3 标记单条已读

**请求**

```
PUT /api/v1/messages/{id}/read
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "标记已读成功",
  "data": null
}
```

---

#### 9.4 全部标记已读

**请求**

```
PUT /api/v1/messages/read-all
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "全部标记已读",
  "data": {
    "markedCount": 3
  }
}
```

---

### 10. 文件上传

#### 10.1 上传文件

上传图片文件（医生头像、门店封面等）。

**请求**

```
POST /api/v1/files/upload
Authorization: Bearer <access_token>
Content-Type: multipart/form-data
```

**表单字段**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| file | file | 是 | 图片文件 |

**支持格式**: jpg, jpeg, png, gif, webp  
**大小限制**: 最大 5MB

**响应示例**

```json
{
  "code": 0,
  "message": "上传成功",
  "data": {
    "url": "/api/v1/files/20260829102314_bb23e2f1.png",
    "fileName": "20260829102314_bb23e2f1.png",
    "size": 1024
  }
}
```

---

#### 10.2 访问文件

直接访问已上传的文件。

**请求**

```
GET /api/v1/files/{fileName}
```

**响应**: 返回文件二进制内容

---

#### 10.3 删除文件

删除已上传的文件（需所有权验证）。

**请求**

```
DELETE /api/v1/files/{fileName}
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "删除成功",
  "data": null
}
```

**错误响应**

```json
{
  "code": 400,
  "message": "文件不存在或无权限删除",
  "data": null
}
```

---

## 管理端 API

### 1. 管理端认证

#### 1.1 管理员登录

使用用户名和密码登录管理后台。

**请求**

```
POST /api/admin/auth/login
Content-Type: application/json
```

**请求体**

```json
{
  "username": "admin",
  "password": "admin123"
}
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "userInfo": {
      "id": 1,
      "username": "admin",
      "displayName": "系统管理员",
      "email": "admin@dentalclinic.com",
      "avatar": null,
      "roles": []
    },
    "menus": [],
    "permissions": []
  }
}
```

**账户锁定机制**

连续5次登录失败后，账户将被锁定30分钟，期间即使输入正确密码也无法登录。

```json
{
  "code": 401,
  "message": "账户已锁定，请30分钟后重试",
  "data": null
}
```

---

#### 1.2 刷新 Access Token

**请求**

```
POST /api/admin/auth/refresh
Content-Type: application/json
```

**请求体**

```json
{
  "refreshToken": "eyJhbGci..."
}
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": "eyJhbGci..."
}
```

---

#### 1.3 获取当前用户信息

**请求**

```
GET /api/admin/auth/profile
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "",
    "refreshToken": "",
    "userInfo": {
      "id": 1,
      "username": "admin",
      "displayName": "系统管理员",
      "email": "admin@dentalclinic.com",
      "avatar": null,
      "roles": []
    },
    "menus": [],
    "permissions": []
  }
}
```

---

#### 1.4 Vben Admin 适配接口

以下接口用于适配 Vben Admin 前端框架：

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/admin/user/info` | GET | 获取当前用户信息（Vben格式） |
| `/api/admin/auth/codes` | GET | 获取当前用户权限码列表 |
| `/api/admin/menu/all` | GET | 获取完整菜单树 |

---

#### 1.5 修改密码

**请求**

```
PUT /api/admin/auth/password
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**

```json
{
  "oldPassword": "admin123",
  "newPassword": "newPassword123"
}
```

**响应示例**

```json
{
  "code": 0,
  "message": "密码修改成功",
  "data": null
}
```

---

#### 1.6 登出

使当前 refresh token 失效。

**请求**

```
POST /api/admin/auth/logout
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

---

### 2. 用户管理

所有用户管理接口需要 `user:*` 权限。

#### 2.1 用户列表

**请求**

```
GET /api/admin/users?page=1&pageSize=10&keyword=&roleId=
Authorization: Bearer <access_token>
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | int | 否 | 页码，默认1 |
| pageSize | int | 否 | 每页数量，默认10 |
| keyword | string | 否 | 搜索关键词（用户名/姓名） |
| roleId | long | 否 | 按角色筛选 |

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "username": "admin",
        "displayName": "系统管理员",
        "email": "admin@dentalclinic.com",
        "avatar": null,
        "status": 1,
        "lastLoginAt": "2026-08-28T12:00:00",
        "createdAt": "2026-08-28T10:00:00",
        "roles": [{"id": 1, "roleCode": "super_admin", "roleName": "超级管理员"}]
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 10
  }
}
```

#### 2.2 创建用户

**请求**

```
POST /api/admin/users
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**

```json
{
  "username": "operator01",
  "password": "password123",
  "displayName": "操作员01",
  "email": "op01@dentalclinic.com",
  "roleIds": [2]
}
```

#### 2.3 更新用户

**请求**

```
PUT /api/admin/users/{id}
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**

```json
{
  "displayName": "新名称",
  "email": "new@email.com",
  "roleIds": [2, 3]
}
```

#### 2.4 更新用户状态

**请求**

```
PUT /api/admin/users/{id}/status
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**

```json
{
  "status": 0
}
```

| status | 说明 |
|---|---|
| 1 | 启用 |
| 0 | 禁用 |

#### 2.5 重置密码

**请求**

```
PUT /api/admin/users/{id}/password
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**

```json
{
  "newPassword": "newPassword123"
}
```

#### 2.6 删除用户

**请求**

```
DELETE /api/admin/users/{id}
Authorization: Bearer <access_token>
```

---

### 3. 角色管理

所有角色管理接口需要 `role:*` 权限。

#### 3.1 角色列表

**请求**

```
GET /api/admin/roles
Authorization: Bearer <access_token>
```

#### 3.2 角色详情

**请求**

```
GET /api/admin/roles/{id}
Authorization: Bearer <access_token>
```

#### 3.3 角色权限列表

**请求**

```
GET /api/admin/roles/permissions
Authorization: Bearer <access_token>
```

返回所有可用权限，按模块分组。

#### 3.4 创建角色

**请求**

```
POST /api/admin/roles
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**

```json
{
  "roleCode": "clinic_manager",
  "roleName": "门诊经理",
  "description": "管理单个门诊的日常运营",
  "permissionIds": [1, 2, 3, 5]
}
```

#### 3.5 更新角色

**请求**

```
PUT /api/admin/roles/{id}
Authorization: Bearer <access_token>
Content-Type: application/json
```

#### 3.6 删除角色

**请求**

```
DELETE /api/admin/roles/{id}
Authorization: Bearer <access_token>
```

注意：系统内置角色（isSystem=1）不可删除。

---

### 4. 菜单管理

所有菜单管理接口需要 `menu:*` 权限。

#### 4.1 菜单列表

**请求**

```
GET /api/admin/menus
Authorization: Bearer <access_token>
```

返回完整的菜单树结构。

#### 4.2 创建菜单

**请求**

```
POST /api/admin/menus
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**

```json
{
  "parentId": 0,
  "menuName": "客户管理",
  "menuType": "directory",
  "routePath": "/customers",
  "component": null,
  "permissionCode": null,
  "icon": "UserOutlined",
  "sortOrder": 10,
  "isVisible": 1,
  "isCache": 0
}
```

| menuType | 说明 |
|---|---|
| directory | 目录 |
| menu | 菜单页面 |
| button | 按钮权限 |

#### 4.3 更新菜单

**请求**

```
PUT /api/admin/menus/{id}
Authorization: Bearer <access_token>
Content-Type: application/json
```

#### 4.4 删除菜单

**请求**

```
DELETE /api/admin/menus/{id}
Authorization: Bearer <access_token>
```

注意：删除目录或菜单会级联删除其子菜单。

---

### 5. 数据看板

所有看板接口需要 `dashboard:view` 权限。

#### 5.1 实时概览

获取今日核心指标：预约数、到店数、营收、新增客户。

**请求**

```
GET /api/admin/dashboard/summary
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "todayAppointments": 25,
    "todayArrivals": 18,
    "todayRevenue": 15600.0,
    "todayNewCustomers": 8
  }
}
```

#### 5.2 门店对比

获取各门店本月预约量、营收、新客数对比。

**请求**

```
GET /api/admin/dashboard/clinic-comparison
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "clinicId": 1,
      "clinicName": "朝阳门诊",
      "appointmentCount": 120,
      "revenue": 85000.0,
      "newCustomerCount": 35,
      "customerSatisfaction": null
    },
    {
      "clinicId": 2,
      "clinicName": "海淀门诊",
      "appointmentCount": 95,
      "revenue": 72000.0,
      "newCustomerCount": 28,
      "customerSatisfaction": null
    }
  ]
}
```

#### 5.3 趋势数据

获取近N天的预约、营收、新客趋势（默认30天，可指定7-90天）。

**请求**

```
GET /api/admin/dashboard/trends?days=30
Authorization: Bearer <access_token>
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| days | int | 否 | 天数，默认30，范围7-90 |

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "date": "2026-08-01",
      "appointmentCount": 20,
      "revenue": 12000.0,
      "newCustomerCount": 5
    },
    {
      "date": "2026-08-02",
      "appointmentCount": 25,
      "revenue": 15000.0,
      "newCustomerCount": 8
    }
  ]
}
```

#### 5.4 客户等级分布

获取各会员等级的客户数量分布。

**请求**

```
GET /api/admin/dashboard/customer-levels
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    { "levelCode": 1, "levelName": "普通会员", "count": 500 },
    { "levelCode": 2, "levelName": "银卡会员", "count": 200 },
    { "levelCode": 3, "levelName": "金卡会员", "count": 80 },
    { "levelCode": 4, "levelName": "钻石会员", "count": 20 }
  ]
}
```

#### 5.5 客户来源分布

获取客户来源渠道占比。

**请求**

```
GET /api/admin/dashboard/customer-sources
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": [
    { "sourceType": "self", "sourceLabel": "自主注册", "count": 400, "percentage": 50.0 },
    { "sourceType": "referral", "sourceLabel": "推荐注册", "count": 300, "percentage": 37.5 },
    { "sourceType": "store_entry", "sourceLabel": "门店录入", "count": 100, "percentage": 12.5 }
  ]
}
```

#### 5.6 推荐统计

获取推荐转化率、TOP10推荐人、月度推荐新客数和营收。

**请求**

```
GET /api/admin/dashboard/referral
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "conversionRate": 65.0,
    "topReferrers": [
      { "userId": 10, "nickname": "张三", "referralCount": 15 },
      { "userId": 25, "nickname": "李四", "referralCount": 12 }
    ],
    "monthlyNewCustomers": 45,
    "monthlyRevenue": 28000.0
  }
}
```

#### 5.7 客户留存

获取复诊率和流失率。

**请求**

```
GET /api/admin/dashboard/retention
Authorization: Bearer <access_token>
```

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "repeatVisitRate": 45.0,
    "churnRate": 12.0
  }
}
```

---

### 6. 配置管理

所有配置接口需要 `config:*` 权限。

#### 6.1 系统配置列表

**请求**

```
GET /api/admin/config
Authorization: Bearer <access_token>
```

#### 6.2 通知规则列表

**请求**

```
GET /api/admin/config/notifications
Authorization: Bearer <access_token>
```

#### 6.3 通知历史

**请求**

```
GET /api/admin/config/notifications/history
Authorization: Bearer <access_token>
```

#### 6.4 更新配置

**请求**

```
PUT /api/admin/config/{key}
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**

```json
{
  "value": "new_value"
}
```

#### 6.5 创建通知规则

**请求**

```
POST /api/admin/config/notifications
Authorization: Bearer <access_token>
Content-Type: application/json
```

#### 6.6 删除通知规则

**请求**

```
DELETE /api/admin/config/notifications/{id}
Authorization: Bearer <access_token>
```

---

### 7. 审计日志

所有审计日志接口需要 `audit:view` 权限。

#### 7.1 日志列表

**请求**

```
GET /api/admin/audit/logs?page=1&pageSize=20&userId=&operationType=&targetResource=
Authorization: Bearer <access_token>
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | int | 否 | 页码 |
| pageSize | int | 否 | 每页数量 |
| userId | long | 否 | 按用户筛选 |
| operationType | string | 否 | 操作类型（create/update/delete） |
| targetResource | string | 否 | 目标资源 |

**响应示例**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "userId": 1,
        "username": "admin",
        "operationType": "update",
        "targetResource": "user",
        "targetId": "5",
        "requestParams": "{\"displayName\":\"新名称\"}",
        "responseCode": 200,
        "ipAddress": "127.0.0.1",
        "userAgent": "Mozilla/5.0...",
        "createdAt": "2026-08-28T12:00:00"
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20
  }
}
```

#### 7.2 日志详情

**请求**

```
GET /api/admin/audit/logs/{id}
Authorization: Bearer <access_token>
```

---

## 附录

### A. 充值赠送规则

| 充值金额 | 赠送比例 | 赠送金额 |
|---|---|---|
| ≥ 10000 | 25% | 2500 |
| ≥ 5000 | 20% | 1000 |
| ≥ 3000 | 15% | 450 |
| ≥ 1000 | 10% | 100 |
| < 1000 | 0% | 0 |

### B. 会员等级规则

| 等级 | 名称 | 升级条件（累计消费） | 诊疗折扣 |
|---|---|---|---|
| Lv1 | 普通会员 | 注册即为 | 无折扣 |
| Lv2 | 银卡会员 | ≥ 3000元 | 9.5折 |
| Lv3 | 金卡会员 | ≥ 10000元 | 9折 |
| Lv4 | 钻石会员 | ≥ 30000元 | 8.5折 |

### C. 积分规则

| 获取方式 | 积分数量 | 说明 |
|---|---|---|
| 消费 | 每1元 = 1分 | 含余额支付和微信支付 |
| 充值 | 每1元 = 0.5分 | 仅计算实际支付金额 |
| 每日签到 | 2分 | 每日限1次 |
| 完善信息 | 50分 | 一次性奖励 |
| 就诊评价 | 10分 | 每月限4次 |
| 推荐注册 | 50分 | 被推荐人完成首次消费后发放 |

积分有效期为获取后12个月，过期自动清零。
