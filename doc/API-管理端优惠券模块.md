# 管理端优惠券模块 API 文档

## 概述

管理端优惠券模块提供了完整的优惠券模板管理、手动发放和统计分析功能。

---

## API 接口

### 1. 获取优惠券模板列表

**接口**: `GET /api/admin/coupons/templates`

**权限**: `coupon:view`

**响应示例**:
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "新人专享券",
      "couponType": "cash",
      "faceValue": 50.0,
      "minConsumption": 100.0,
      "applicableDepartments": null,
      "validFrom": "2026-01-01 00:00:00",
      "validTo": "2026-12-31 23:59:59",
      "totalQuantity": 1000,
      "issuedQuantity": 250,
      "usedQuantity": 80,
      "status": 1
    }
  ]
}
```

---

### 2. 创建优惠券模板

**接口**: `POST /api/admin/coupons/templates`

**权限**: `coupon:manage`

**请求体**:
```json
{
  "name": "春节特惠券",
  "couponType": "cash",
  "faceValue": 100.0,
  "minConsumption": 200.0,
  "applicableDepartments": "[1,2,3]",
  "validFrom": "2026-02-01",
  "validTo": "2026-02-28",
  "totalQuantity": 500,
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

---

### 3. 更新优惠券模板

**接口**: `PUT /api/admin/coupons/templates/{id}`

**权限**: `coupon:manage`

**请求体**:
```json
{
  "name": "春节特惠券（修改）",
  "couponType": "cash",
  "faceValue": 120.0,
  "minConsumption": 200.0,
  "applicableDepartments": "[1,2,3]",
  "validFrom": "2026-02-01",
  "validTo": "2026-02-28",
  "totalQuantity": 600,
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

### 4. 手动发放优惠券

**接口**: `POST /api/admin/coupons/grant`

**权限**: `coupon:manage`

**请求体**:
```json
{
  "templateId": 1,
  "userIds": [10, 20, 30, 40]
}
```

**响应示例（全部成功）**:
```json
{
  "code": 0,
  "message": "发放成功",
  "data": {
    "successCount": 4
  }
}
```

**响应示例（部分成功）**:
```json
{
  "code": 0,
  "message": "部分发放成功",
  "data": {
    "successCount": 3,
    "failedUsers": [40]
  }
}
```

---

### 5. 获取优惠券统计

**接口**: `GET /api/admin/coupons/statistics?templateId=1`

**权限**: `coupon:view`

**查询参数**:
- `templateId` (可选): 指定模板ID，不传则统计所有模板

**响应示例**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalTemplates": 5,
    "totalIssued": 1200,
    "totalUsed": 350,
    "totalRemaining": 800,
    "usageRate": 29.17
  }
}
```

**字段说明**:
- `totalTemplates`: 模板总数
- `totalIssued`: 已发放总数
- `totalUsed`: 已使用总数
- `totalRemaining`: 剩余库存
- `usageRate`: 使用率（%）

---

## 业务规则

### 模板创建规则
1. 名称不能为空
2. 面额必须大于0
3. 发放数量必须大于0
4. 有效期格式：yyyy-MM-dd

### 手动发放规则
1. 模板必须存在
2. 库存充足（remaining >= userIds.size）
3. 自动生成唯一券码（ADM + templateId + timestamp + random）
4. 支持批量发放
5. 记录操作人

### 统计数据计算
- 使用率 = (totalUsed / totalIssued) * 100%
- 剩余库存 = totalQuantity - issuedQuantity

---

## 使用场景

### 场景1: 创建新人优惠券模板
```bash
curl -X POST "http://localhost:8080/api/admin/coupons/templates" \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "新人专享券",
    "couponType": "cash",
    "faceValue": 50.0,
    "minConsumption": 0.0,
    "validFrom": "2026-01-01",
    "validTo": "2026-12-31",
    "totalQuantity": 1000,
    "status": 1
  }'
```

### 场景2: 向特定用户发放优惠券
```bash
curl -X POST "http://localhost:8080/api/admin/coupons/grant" \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "templateId": 1,
    "userIds": [100, 101, 102]
  }'
```

### 场景3: 查看优惠券使用情况
```bash
curl -X GET "http://localhost:8080/api/admin/coupons/statistics" \
  -H "Authorization: Bearer <admin_token>"
```

---

## 技术实现

### Service 层

**AdminCouponService.kt**:
- `getCouponTemplates()` - 获取所有模板
- `createCouponTemplate()` - 创建模板
- `updateCouponTemplate()` - 更新模板
- `grantCouponsToUsers()` - 手动发放
- `getCouponStatistics()` - 统计分析

### 券码生成规则

**用户主动领取**: `CPN{templateId}{timestamp}{random}`  
**管理员发放**: `ADM{templateId}{timestamp}{random}`

---

## 与需求文档的对应关系

根据《牙科门诊客户管理小程序_需求文档.docx》第 4.9 节"优惠券管理"：

| 需求项 | 实现状态 | 接口 |
|-------|---------|------|
| 创建和管理优惠券模板 | ✅ 已实现 | GET/POST/PUT /api/admin/coupons/templates |
| 配置券面额、适用项目、使用门槛、有效期、发放数量 | ✅ 已实现 | CreateCouponTemplateRequest |
| 手动向指定客户发放优惠券 | ✅ 已实现 | POST /api/admin/coupons/grant |
| 查看优惠券发放和使用统计 | ✅ 已实现 | GET /api/admin/coupons/statistics |

---

## 权限配置

需要在系统中配置以下权限：

| 权限代码 | 权限名称 | 说明 |
|---------|---------|------|
| coupon:view | 查看优惠券 | 查看模板和统计 |
| coupon:manage | 管理优惠券 | 创建、更新、发放 |

---

**版本**: v1.0  
**更新时间**: 2026-09-07  
**构建状态**: ✅ BUILD SUCCESSFUL
