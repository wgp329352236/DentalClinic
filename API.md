# 牙科门诊小程序 API 文档

**版本**: v1.0  
**更新日期**: 2026-08-28  
**基础URL**: `http://localhost:8080`

---

## 目录

- [用户端 API](#用户端-api)
  - [认证](#1-认证)
  - [个人中心](#2-个人中心)
  - [钱包](#3-钱包)
  - [充值](#4-充值)
- [管理端 API](#管理端-api)
  - [认证](#1-管理端认证)

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
