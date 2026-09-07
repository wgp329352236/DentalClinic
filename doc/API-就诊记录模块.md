# 就诊记录模块 API 文档

## 概述

就诊记录模块提供了完整的就诊历史查询和病历管理功能，包括用户端查看就诊记录和消费详情，以及管理端查看预约详情和编辑电子病历摘要。

---

## 一、用户端 API（小程序）

### 1.1 获取就诊记录列表

**接口**: `GET /api/v1/medical-records`

**权限**: 需要 JWT 认证

**查询参数**:
- `page` (可选): 页码，默认 1
- `pageSize` (可选): 每页数量，默认 20

**请求示例**:
```
GET /api/v1/medical-records?page=1&pageSize=20
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
        "id": 1001,
        "appointmentDate": "2026-09-05",
        "startTime": "10:00",
        "endTime": "10:30",
        "clinicName": "朝阳门诊",
        "doctorName": "张医生",
        "doctorTitle": "主治医师",
        "departmentName": "口腔修复科",
        "diagnosis": "龋齿补牙",
        "remark": "患者配合良好",
        "consumptionAmount": 500.0,
        "completedAt": "2026-09-05 10:35:00"
      }
    ],
    "total": 15,
    "page": 1,
    "pageSize": 20
  }
}
```

**说明**:
- 只返回状态为 `completed`（已完成）的预约
- 按预约日期倒序排列
- `consumptionAmount` 为该次就诊的总消费金额

---

### 1.2 获取就诊记录详情

**接口**: `GET /api/v1/medical-records/{id}`

**权限**: 需要 JWT 认证

**路径参数**:
- `id`: 预约ID

**请求示例**:
```
GET /api/v1/medical-records/1001
Authorization: Bearer <user_token>
```

**响应示例**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1001,
    "appointmentDate": "2026-09-05",
    "startTime": "10:00",
    "endTime": "10:30",
    "clinicName": "朝阳门诊",
    "clinicAddress": "北京市朝阳区xxx路xxx号",
    "doctorName": "张医生",
    "doctorTitle": "主治医师",
    "doctorIntroduction": "擅长口腔修复、种植牙...",
    "departmentName": "口腔修复科",
    "status": "completed",
    "diagnosis": "龋齿补牙",
    "treatmentPlan": "树脂充填治疗",
    "remark": "患者配合良好，建议定期复查",
    "arrivedAt": "2026-09-05 09:55:00",
    "completedAt": "2026-09-05 10:35:00",
    "totalAmount": 500.0,
    "consumptionDetails": [
      {
        "id": 2001,
        "amount": 500.0,
        "balanceUsed": 500.0,
        "wechatPaid": 0.0,
        "pointsEarned": 50,
        "description": "树脂补牙费用",
        "createdAt": "2026-09-05 10:35:00"
      }
    ]
  }
}
```

**说明**:
- 包含完整的就诊信息、医生介绍、诊断和治疗方案
- 显示所有消费明细（可能有多笔）
- 仅能查看属于自己的就诊记录

---

## 二、管理端 API（后台）

### 2.1 获取预约详情（含病历）

**接口**: `GET /api/admin/appointments/{id}`

**权限**: `appointment:view`

**路径参数**:
- `id`: 预约ID

**请求示例**:
```
GET /api/admin/appointments/1001
Authorization: Bearer <admin_token>
```

**响应示例**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "appointment": {
      "id": 1001,
      "userId": 123,
      "userName": "张三",
      "userPhone": "13800138000",
      "clinicId": 1,
      "clinicName": "朝阳门诊",
      "doctorId": 10,
      "doctorName": "张医生",
      "departmentId": 5,
      "departmentName": "口腔修复科",
      "appointmentDate": "2026-09-05",
      "startTime": "10:00",
      "endTime": "10:30",
      "status": "completed",
      "diagnosis": "龋齿补牙",
      "treatmentPlan": "树脂充填治疗",
      "remark": "患者配合良好",
      "arrivedAt": "2026-09-05 09:55:00",
      "completedAt": "2026-09-05 10:35:00",
      "createdAt": "2026-09-04 15:30:00"
    },
    "consumption": {
      "totalAmount": 500.0,
      "recordCount": 1
    }
  }
}
```

---

### 2.2 编辑病历摘要

**接口**: `PUT /api/admin/appointments/{id}/medical-record`

**权限**: `appointment:update`

**路径参数**:
- `id`: 预约ID

**请求体**:
```json
{
  "diagnosis": "龋齿补牙（左下第一磨牙）",
  "treatmentPlan": "树脂充填治疗，建议半年后复查"
}
```

**说明**:
- `diagnosis` 和 `treatmentPlan` 均为可选字段
- 可以单独更新其中一个，或同时更新两个
- 更新后会保留未传字段的原值

**响应示例**:
```json
{
  "code": 0,
  "message": "病历更新成功",
  "data": null
}
```

---

## 三、数据库表结构变更

### Appointments 表新增字段

| 字段名 | 类型 | 说明 |
|-------|------|------|
| diagnosis | TEXT | 诊断结果/电子病历摘要 |
| treatmentPlan | TEXT | 治疗方案 |

**注意**: 这两个字段都是可选的（nullable），不会影响现有数据。

---

## 四、业务流程说明

### 用户端就诊记录查询流程

1. 用户登录后，调用 `GET /api/v1/medical-records` 获取就诊历史列表
2. 点击某条记录，调用 `GET /api/v1/medical-records/{id}` 查看详情
3. 详情页展示：
   - 就诊基本信息（时间、门店、医生、科室）
   - 医生介绍
   - 诊断结果和治疗方案（由管理端录入）
   - 消费明细（金额、支付方式、获得积分）

### 管理端病历录入流程

1. 管理员在预约列表中点击某个已完成的预约
2. 调用 `GET /api/admin/appointments/{id}` 查看预约详情
3. 点击"编辑病历"按钮，填写诊断和治疗方案
4. 调用 `PUT /api/admin/appointments/{id}/medical-record` 保存
5. 用户在小程序中即可看到更新的病历信息

---

## 五、权限配置

需要在系统中配置以下权限：

| 权限代码 | 权限名称 | 说明 |
|---------|---------|------|
| appointment:view | 查看预约 | 查看预约详情和病历 |
| appointment:update | 更新预约 | 编辑病历摘要 |

---

## 六、注意事项

1. **隐私保护**: 就诊记录包含敏感医疗信息，需符合《医疗机构病历管理规定》
2. **数据隔离**: 用户只能查看自己的就诊记录，不能查看他人记录
3. **权限控制**: 管理端病历编辑需要相应权限，操作会记录审计日志
4. **数据一致性**: 就诊记录与消费记录通过 `appointmentId` 关联
5. **状态过滤**: 用户端只显示已完成的就诊记录，管理端可查看所有状态

---

## 七、测试用例

### 测试1: 用户查询就诊记录列表
```bash
curl -X GET "http://localhost:8080/api/v1/medical-records?page=1&pageSize=10" \
  -H "Authorization: Bearer <user_token>"
```

### 测试2: 用户查询就诊记录详情
```bash
curl -X GET "http://localhost:8080/api/v1/medical-records/1001" \
  -H "Authorization: Bearer <user_token>"
```

### 测试3: 管理员查看预约详情
```bash
curl -X GET "http://localhost:8080/api/admin/appointments/1001" \
  -H "Authorization: Bearer <admin_token>"
```

### 测试4: 管理员编辑病历
```bash
curl -X PUT "http://localhost:8080/api/admin/appointments/1001/medical-record" \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "diagnosis": "龋齿补牙（左下第一磨牙）",
    "treatmentPlan": "树脂充填治疗，建议半年后复查"
  }'
```

---

## 八、与需求文档的对应关系

根据《牙科门诊客户管理小程序_需求文档.docx》：

| 需求项 | 实现状态 | 接口 |
|-------|---------|------|
| 展示历史就诊记录列表 | ✅ 已实现 | GET /api/v1/medical-records |
| 就诊时间、门店、医生、诊断项目、消费金额 | ✅ 已实现 | 列表接口返回完整信息 |
| 查看电子病历摘要 | ✅ 已实现 | GET /api/v1/medical-records/{id} |
| 管理端录入病历 | ✅ 已实现 | PUT /api/admin/appointments/{id}/medical-record |

---

**版本**: v1.0  
**更新时间**: 2026-09-07  
**构建状态**: ✅ BUILD SUCCESSFUL
