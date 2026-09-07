# 消费记录模块 API 文档

## 概述

消费记录模块提供了完整的用户消费历史查询和统计功能，包括消费明细、支付方式、关联就诊信息等。

---

## 一、用户端 API

### 1.1 获取消费记录列表

**接口**: `GET /api/v1/consumption/records`

**权限**: 需要 JWT 认证

**查询参数**:

| 参数 | 类型 | 必填 | 说明 | 示例 |
|-----|------|------|------|------|
| page | Integer | 否 | 页码，默认 1 | `page=1` |
| pageSize | Integer | 否 | 每页数量，默认 20 | `pageSize=10` |
| startDate | String | 否 | 开始日期（yyyy-MM-dd） | `startDate=2026-01-01` |
| endDate | String | 否 | 结束日期（yyyy-MM-dd） | `endDate=2026-12-31` |
| clinicId | Long | 否 | 诊所 ID | `clinicId=1` |

**请求示例**:
```bash
GET /api/v1/consumption/records?page=1&pageSize=20&startDate=2026-09-01&endDate=2026-09-30
Authorization: Bearer <user_token>
```

**响应示例**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "amount": 500.0,
        "balanceUsed": 500.0,
        "wechatPaid": 0.0,
        "pointsEarned": 50,
        "description": "树脂补牙费用",
        "clinicName": "阳光口腔门诊",
        "appointmentDate": "2026-09-05",
        "doctorName": "张医生",
        "departmentName": "口腔检查",
        "createdAt": "2026-09-05 10:35:00"
      }
    ],
    "total": 15,
    "page": 1,
    "pageSize": 20
  }
}
```

**字段说明**:
- `amount`: 消费总金额
- `balanceUsed`: 使用余额支付的金额
- `wechatPaid`: 使用微信支付的金额
- `pointsEarned`: 本次消费获得的积分
- `appointmentDate`: 关联的就诊日期
- `doctorName`: 就诊医生
- `departmentName`: 就诊科室

---

### 1.2 获取消费统计

**接口**: `GET /api/v1/consumption/statistics`

**权限**: 需要 JWT 认证

**请求示例**:
```bash
GET /api/v1/consumption/statistics
Authorization: Bearer <user_token>
```

**响应示例**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalAmount": 5000.0,
    "totalRecords": 15,
    "monthAmount": 700.0,
    "monthRecords": 2
  }
}
```

**字段说明**:
- `totalAmount`: 累计消费总额
- `totalRecords`: 累计消费次数
- `monthAmount`: 本月消费金额
- `monthRecords`: 本月消费次数

---

## 二、使用场景

### 场景1: 查看本月消费记录
```bash
GET /api/v1/consumption/records?startDate=2026-09-01&endDate=2026-09-30
```

### 场景2: 查看某家诊所的消费记录
```bash
GET /api/v1/consumption/records?clinicId=1
```

### 场景3: 查看我的钱包页面
```bash
# 先获取统计数据
GET /api/v1/consumption/statistics

# 再获取最近的消费记录
GET /api/v1/consumption/records?page=1&pageSize=10
```

---

## 三、数据关联

消费记录与以下数据关联：

```
ConsumptionRecords (消费记录)
    ├─ Users (用户表) - userId
    ├─ Clinics (诊所表) - clinicId
    ├─ Appointments (预约表) - appointmentId (可选)
    │   ├─ Doctors (医生表) - doctorId
    │   └─ Departments (科室表) - departmentId
```

---

## 四、技术实现

### Service 层

**ConsumptionRecordService.kt**:
- `getConsumptionRecords()` - 支持多条件筛选的分页查询
- `getConsumptionStatistics()` - 消费统计分析

### 筛选逻辑

1. **日期范围筛选**: 数据库级过滤，性能优秀
2. **诊所筛选**: 直接 SQL WHERE 条件
3. **关联信息查询**: 自动加载诊所、医生、科室名称

---

## 五、注意事项

1. **所有筛选参数都是可选的**，不传则不进行该条件筛选
2. **日期格式必须为 yyyy-MM-dd**
3. **分页在所有筛选条件下都有效**
4. **消费记录只读**，不支持修改或删除

---

## 六、测试用例

### 测试1: 查询消费记录列表
```bash
curl -X GET "http://localhost:8080/api/v1/consumption/records?page=1&pageSize=10" \
  -H "Authorization: Bearer <user_token>"
```

### 测试2: 按日期范围筛选
```bash
curl -X GET "http://localhost:8080/api/v1/consumption/records?startDate=2026-09-01&endDate=2026-09-30" \
  -H "Authorization: Bearer <user_token>"
```

### 测试3: 获取消费统计
```bash
curl -X GET "http://localhost:8080/api/v1/consumption/statistics" \
  -H "Authorization: Bearer <user_token>"
```

---

## 七、与需求文档的对应关系

根据《牙科门诊客户管理小程序_需求文档.docx》：

| 需求项 | 实现状态 | 接口 |
|-------|---------|------|
| 展示消费时间 | ✅ 已实现 | createdAt 字段 |
| 展示消费金额 | ✅ 已实现 | amount 字段 |
| 展示关联就诊记录 | ✅ 已实现 | appointmentDate/doctorName |
| 展示操作门店 | ✅ 已实现 | clinicName 字段 |
| 余额明细（消费记录） | ✅ 已实现 | GET /api/v1/consumption/records |
| 本月消费金额 | ✅ 已实现 | statistics.monthAmount |
| 累计消费金额 | ✅ 已实现 | statistics.totalAmount |

---

**版本**: v1.0  
**更新时间**: 2026-09-07  
**构建状态**: ✅ BUILD SUCCESSFUL
