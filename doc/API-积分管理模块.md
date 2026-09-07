# 积分管理模块 API 文档

## 概述

积分管理模块提供了完整的积分规则配置、客户积分调整和积分流水查询功能，支持管理员对积分体系进行精细化管理。

---

## 1. 积分规则管理

### 1.1 获取所有积分规则

**接口**: `GET /api/admin/points/rules`

**权限**: `points:view`

**响应示例**:
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "ruleName": "每日签到",
      "ruleType": "earn",
      "actionType": "signup",
      "pointsPerUnit": 10,
      "unitDescription": "每日签到一次",
      "dailyLimit": 1,
      "monthlyLimit": null,
      "validityMonths": 12,
      "status": 1
    },
    {
      "id": 2,
      "ruleName": "消费奖励",
      "ruleType": "earn",
      "actionType": "consumption",
      "pointsPerUnit": 1,
      "unitDescription": "每消费1元",
      "dailyLimit": null,
      "monthlyLimit": null,
      "validityMonths": 12,
      "status": 1
    }
  ]
}
```

### 1.2 创建积分规则

**接口**: `POST /api/admin/points/rules`

**权限**: `points:manage`

**请求体**:
```json
{
  "ruleName": "完善资料奖励",
  "ruleType": "earn",
  "actionType": "profile_complete",
  "pointsPerUnit": 50,
  "unitDescription": "首次完善个人信息",
  "dailyLimit": null,
  "monthlyLimit": null,
  "validityMonths": 12,
  "status": 1
}
```

**响应示例**:
```json
{
  "code": 0,
  "message": "创建成功",
  "data": {
    "id": 5
  }
}
```

### 1.3 更新积分规则

**接口**: `PUT /api/admin/points/rules/{id}`

**权限**: `points:manage`

**请求体**:
```json
{
  "ruleName": "每日签到（更新）",
  "ruleType": "earn",
  "actionType": "signup",
  "pointsPerUnit": 15,
  "unitDescription": "每日签到一次",
  "dailyLimit": 1,
  "monthlyLimit": null,
  "validityMonths": 12,
  "status": 1
}
```

**响应示例**:
```json
{
  "code": 0,
  "message": "更新成功",
  "data": null
}
```

---

## 2. 客户积分调整

### 2.1 手动调整客户积分

**接口**: `PUT /api/admin/customers/{id}/points`

**权限**: `customer:update`

**路径参数**:
- `id`: 客户ID

**请求体**:
```json
{
  "amount": 100,
  "reason": "活动补偿"
}
```

**说明**:
- `amount`: 积分变动数量，正数表示增加，负数表示扣减
- `reason`: 调整原因，必填

**响应示例**:
```json
{
  "code": 0,
  "message": "积分调整成功",
  "data": null
}
```

**错误示例**（余额不足）:
```json
{
  "code": 400,
  "message": "积分余额不足，当前余额：50",
  "data": null
}
```

---

## 3. 积分流水查询

### 3.1 查询积分流水记录

**接口**: `GET /api/admin/points/records`

**权限**: `points:view`

**查询参数**:
- `userId` (可选): 用户ID，精确筛选特定用户
- `sourceType` (可选): 来源类型，如 `checkin`, `consumption`, `admin_adjust` 等
- `startDate` (可选): 开始日期，格式 `yyyy-MM-dd`
- `endDate` (可选): 结束日期，格式 `yyyy-MM-dd`
- `page` (可选): 页码，默认 1
- `pageSize` (可选): 每页数量，默认 20

**请求示例**:
```
GET /api/admin/points/records?userId=123&startDate=2026-09-01&endDate=2026-09-30&page=1&pageSize=20
```

**响应示例**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1001,
        "userId": 123,
        "changeAmount": 100,
        "balanceAfter": 350,
        "sourceType": "admin_adjust",
        "description": "管理员手动调整：活动补偿（操作人：admin）",
        "expiresAt": "2027-09-07 16:30:00",
        "createdAt": "2026-09-07 16:30:00"
      },
      {
        "id": 980,
        "userId": 123,
        "changeAmount": 10,
        "balanceAfter": 250,
        "sourceType": "checkin",
        "description": "每日签到奖励",
        "expiresAt": "2027-09-06 08:00:00",
        "createdAt": "2026-09-06 08:00:00"
      }
    ],
    "total": 45,
    "page": 1,
    "pageSize": 20
  }
}
```

---

## 数据来源类型说明

| sourceType | 说明 |
|------------|------|
| checkin | 每日签到 |
| consumption | 消费奖励 |
| recharge | 充值奖励 |
| signup | 注册奖励 |
| profile_complete | 完善资料 |
| referral_registration | 推荐注册 |
| referral_first_consumption | 推荐首消 |
| admin_adjust | 管理员手动调整 |
| coupon_redeem | 优惠券核销 |

---

## 权限配置

需要在系统中配置以下权限：

| 权限代码 | 权限名称 | 说明 |
|---------|---------|------|
| points:view | 查看积分 | 查看积分规则和流水 |
| points:manage | 管理积分 | 创建、更新积分规则 |
| customer:update | 更新客户信息 | 调整客户积分 |

---

## 注意事项

1. **积分不能为负**: 扣减积分时会校验余额，如果扣减后余额为负会返回错误
2. **积分有效期**: 管理员手动调整的积分默认有效期为12个月
3. **操作审计**: 所有积分调整操作都会记录操作人和调整原因
4. **事务安全**: 所有积分变动都在数据库事务中执行，保证数据一致性
5. **并发控制**: 使用 UserPointsSummary 表冗余计算结果，提高查询性能

---

## 测试用例

### 测试1: 创建积分规则
```bash
curl -X POST http://localhost:8080/api/admin/points/rules \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "ruleName": "测试规则",
    "ruleType": "earn",
    "actionType": "review",
    "pointsPerUnit": 20,
    "unitDescription": "发表评价",
    "dailyLimit": null,
    "monthlyLimit": 4,
    "validityMonths": 12,
    "status": 1
  }'
```

### 测试2: 调整客户积分
```bash
curl -X PUT http://localhost:8080/api/admin/customers/123/points \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": -50,
    "reason": "数据纠错"
  }'
```

### 测试3: 查询积分流水
```bash
curl -X GET "http://localhost:8080/api/admin/points/records?userId=123&page=1&pageSize=10" \
  -H "Authorization: Bearer <admin_token>"
```
