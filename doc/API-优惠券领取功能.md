# 优惠券领取功能 API 文档

## 概述

为用户端添加了优惠券领取功能，用户可以按模板ID主动领取优惠券。

---

## API 接口

### 领取优惠券

**接口**: `POST /api/v1/coupons/claim/{templateId}`

**权限**: 需要 JWT 认证

**路径参数**:
- `templateId`: 优惠券模板 ID

**请求示例**:
```bash
POST /api/v1/coupons/claim/1
Authorization: Bearer <user_token>
```

**成功响应**:
```json
{
  "code": 0,
  "message": "领取成功",
  "data": {
    "couponCode": "CPN1234567890123",
    "couponName": "新人专享券",
    "faceValue": 50.0,
    "validTo": "2026-12-31 23:59:59"
  }
}
```

**错误响应**:
```json
{
  "code": 400,
  "message": "优惠券已领完",
  "data": null
}
```

---

## 业务规则

### 领取条件检查

1. **模板存在性**: 验证优惠券模板是否存在
2. **模板状态**: 模板必须处于启用状态 (status = 1)
3. **有效期检查**: 当前时间必须在 validFrom 和 validTo 之间
4. **库存检查**: issuedQuantity < totalQuantity（还有剩余）

### 领取流程

1. 生成唯一券码（格式：CPN + templateId + 时间戳 + 随机数）
2. 在 UserCoupons 表中创建用户优惠券记录
3. 更新 CouponTemplates 表的 issuedQuantity（已发放数量+1）
4. 返回券码和优惠券信息

### 券码生成规则

```
CPN{templateId}{timestamp_last6}{random_4digits}
```

示例：`CPN1234567890123`
- CPN: 前缀
- 1: 模板ID
- 234567: 时间戳后6位
- 8901: 4位随机数

---

## 使用场景

### 场景1: 新人注册领取优惠券
```bash
# 用户注册后自动展示可领取的新人券
POST /api/v1/coupons/claim/1
```

### 场景2: 活动页面领取
```bash
# 用户在活动页面点击领取按钮
POST /api/v1/coupons/claim/5
```

### 场景3: 推荐奖励领取
```bash
# 完成推荐任务后领取奖励券
POST /api/v1/coupons/claim/10
```

---

## 错误码说明

| 错误信息 | 说明 | 处理建议 |
|---------|------|---------|
| 优惠券模板不存在 | templateId 无效 | 检查模板ID是否正确 |
| 优惠券已停用 | 模板 status != 1 | 联系管理员启用模板 |
| 优惠券不在有效期内 | 当前时间不在有效期内 | 等待生效或已过期 |
| 优惠券已领完 | issuedQuantity >= totalQuantity | 等待下次活动 |

---

## 技术实现

### Service 层

**CouponService.kt**:
```kotlin
suspend fun claimCoupon(userId: Long, templateId: Long): CouponClaimResult
```

**验证逻辑**:
1. 查询模板信息
2. 检查模板状态、有效期、库存
3. 生成唯一券码
4. 插入用户优惠券记录
5. 更新模板已发放数量

### 并发控制

- 使用数据库事务保证原子性
- issuedQuantity 更新在事务中执行，避免超发

---

## 测试用例

### 测试1: 正常领取
```bash
curl -X POST "http://localhost:8080/api/v1/coupons/claim/1" \
  -H "Authorization: Bearer <user_token>"
```

**预期**: 返回 200，包含券码信息

### 测试2: 重复领取
```bash
# 同一用户再次领取同一模板（如果模板允许领取多次）
curl -X POST "http://localhost:8080/api/v1/coupons/claim/1" \
  -H "Authorization: Bearer <user_token>"
```

**预期**: 根据业务规则决定是否允许重复领取

### 测试3: 库存不足
```bash
# 当 issuedQuantity >= totalQuantity 时
curl -X POST "http://localhost:8080/api/v1/coupons/claim/1" \
  -H "Authorization: Bearer <user_token>"
```

**预期**: 返回 400，"优惠券已领完"

---

## 与需求文档的对应关系

根据《牙科门诊客户管理小程序_需求文档.docx》第 3.7.7 节：

| 需求项 | 实现状态 |
|-------|---------|
| 展示用户持有的优惠券列表 | ✅ 已实现（之前） |
| 支持在预约或消费时选择使用 | ✅ 已实现（redeem接口） |
| **用户主动领取优惠券** | ✅ **本次新增** |

---

**版本**: v1.1  
**更新时间**: 2026-09-07  
**构建状态**: ✅ BUILD SUCCESSFUL
