# 就诊记录 API 测试指南

## 📋 测试前准备

### 1. 启动服务

确保后端服务已启动：
```bash
cd /Users/wgp/Desktop/DentalClinic/panel/XianTong
./gradlew :server:run
```

服务默认运行在 `http://localhost:8080`

---

### 2. 准备测试数据

执行 SQL 脚本创建测试数据：
```bash
# 连接数据库
psql -U postgres -d dental_clinic

# 执行测试数据脚本
\i /Users/wgp/Desktop/DentalClinic/doc/就诊记录-测试数据准备.sql
```

**注意**: 请将 SQL 脚本中的 `user_id = 100` 替换为实际的测试用户 ID。

---

## 🧪 测试方法（三选一）

### 方法一：使用 Bash 测试脚本（推荐）

**优点**: 自动化测试，一键执行所有接口  
**缺点**: 需要配置 token

```bash
# 赋予执行权限
chmod +x /Users/wgp/Desktop/DentalClinic/doc/就诊记录API测试脚本.sh

# 运行测试脚本
cd /Users/wgp/Desktop/DentalClinic/doc
./就诊记录API测试脚本.sh
```

**如果自动登录失败**，手动设置 token：
```bash
export USER_TOKEN="your_user_token_here"
export ADMIN_TOKEN="your_admin_token_here"
export TEST_APPOINTMENT_ID="1001"

./就诊记录API测试脚本.sh
```

---

### 方法二：使用 Postman（可视化测试）

**优点**: 图形界面，易于调试  
**缺点**: 需要手动导入集合

#### 步骤：

1. **导入 Postman 集合**
   - 打开 Postman
   - 点击 Import
   - 选择文件: `/Users/wgp/Desktop/DentalClinic/doc/就诊记录API-Postman集合.json`

2. **设置环境变量**
   - 点击集合名称 "就诊记录模块 API 测试"
   - 点击 Variables 标签
   - 设置以下变量：
     - `base_url`: `http://localhost:8080`
     - `user_token`: (先执行第1个请求获取)
     - `admin_token`: (先执行第2个请求获取)
     - `appointment_id`: `1001` (或实际预约ID)

3. **按顺序执行请求**
   - 1️⃣ 用户登录 → 自动保存 user_token
   - 2️⃣ 管理员登录 → 自动保存 admin_token
   - 3️⃣ 用户查询就诊记录列表
   - 4️⃣ 用户查询就诊记录详情
   - 5️⃣ 管理员查看预约详情
   - 6️⃣ 管理员编辑病历摘要
   - 7️⃣ 验证病历更新

4. **查看测试结果**
   - 每个请求的 Tests 标签页会显示通过/失败状态
   - 绿色 ✓ 表示测试通过
   - 红色 ✗ 表示测试失败

---

### 方法三：使用 curl 命令（手动测试）

**优点**: 灵活，适合单个接口测试  
**缺点**: 需要手动复制粘贴

#### 测试步骤：

##### Step 1: 用户登录获取 Token
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "code": "test_code",
    "userInfo": {
      "nickName": "测试用户",
      "avatarUrl": "https://example.com/avatar.png"
    }
  }'

# 从响应中提取 accessToken
export USER_TOKEN="提取到的token"
```

##### Step 2: 管理员登录获取 Token
```bash
curl -X POST http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'

# 从响应中提取 accessToken
export ADMIN_TOKEN="提取到的token"
```

##### Step 3: 查询就诊记录列表
```bash
curl -X GET "http://localhost:8080/api/v1/medical-records?page=1&pageSize=10" \
  -H "Authorization: Bearer $USER_TOKEN"
```

**预期响应**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1001,
        "appointmentDate": "2026-09-05",
        "clinicName": "朝阳门诊",
        "doctorName": "张医生",
        "diagnosis": "龋齿补牙",
        "consumptionAmount": 500.0
      }
    ],
    "total": 2,
    "page": 1,
    "pageSize": 10
  }
}
```

##### Step 4: 查询就诊记录详情
```bash
# 使用上一步返回的 id
export APPOINTMENT_ID=1001

curl -X GET "http://localhost:8080/api/v1/medical-records/$APPOINTMENT_ID" \
  -H "Authorization: Bearer $USER_TOKEN"
```

**预期响应**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1001,
    "appointmentDate": "2026-09-05",
    "clinicName": "朝阳门诊",
    "doctorName": "张医生",
    "diagnosis": "龋齿补牙",
    "treatmentPlan": "树脂充填治疗",
    "totalAmount": 500.0,
    "consumptionDetails": [
      {
        "amount": 500.0,
        "description": "树脂补牙费用",
        "pointsEarned": 50
      }
    ]
  }
}
```

##### Step 5: 管理员编辑病历
```bash
curl -X PUT "http://localhost:8080/api/admin/appointments/$APPOINTMENT_ID/medical-record" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "diagnosis": "龋齿补牙（左下第一磨牙）",
    "treatmentPlan": "树脂充填治疗，建议半年后复查"
  }'
```

**预期响应**:
```json
{
  "code": 0,
  "message": "病历更新成功",
  "data": null
}
```

##### Step 6: 验证病历更新
```bash
curl -X GET "http://localhost:8080/api/v1/medical-records/$APPOINTMENT_ID" \
  -H "Authorization: Bearer $USER_TOKEN"
```

检查响应中的 `diagnosis` 和 `treatmentPlan` 是否已更新。

---

## ✅ 测试检查清单

### 功能测试

- [ ] 用户能成功查询就诊记录列表
- [ ] 列表只返回状态为 completed 的预约
- [ ] 列表按预约日期倒序排列
- [ ] 用户能成功查询就诊记录详情
- [ ] 详情包含诊断和治疗方案
- [ ] 详情包含消费明细列表
- [ ] 用户只能查看自己的就诊记录（权限隔离）
- [ ] 管理员能成功查看预约详情
- [ ] 管理员能成功编辑病历摘要
- [ ] 支持部分更新（只传 diagnosis 或 treatmentPlan）
- [ ] 编辑后用户端能看到更新的内容

### 边界测试

- [ ] 查询不存在的预约 ID 返回 404
- [ ] 无 token 访问返回 401
- [ ] 用户使用他人的 appointment_id 无法查看详情
- [ ] 分页参数正确工作（page, pageSize）
- [ ] 空列表时返回正确的响应格式

### 性能测试

- [ ] 列表查询响应时间 < 1秒
- [ ] 详情查询响应时间 < 500ms
- [ ] 并发请求不会导致数据错误

---

## 🐛 常见问题排查

### 问题1: 401 Unauthorized

**原因**: Token 无效或过期  
**解决**:
```bash
# 重新登录获取新 token
curl -X POST http://localhost:8080/api/v1/auth/login ...
export USER_TOKEN="new_token"
```

### 问题2: 就诊记录列表为空

**原因**: 数据库中没有 completed 状态的预约  
**解决**:
```sql
-- 检查是否有测试数据
SELECT COUNT(*) FROM appointment WHERE user_id = 100 AND status = 'completed';

-- 如果没有，执行测试数据准备脚本
```

### 问题3: 404 Not Found

**原因**: 预约 ID 不存在或不属于当前用户  
**解决**:
```sql
-- 查询有效的预约 ID
SELECT id FROM appointment WHERE user_id = 100 AND status = 'completed';
```

### 问题4: 编译错误

**原因**: 代码未编译或编译失败  
**解决**:
```bash
cd /Users/wgp/Desktop/DentalClinic/panel/XianTong
./gradlew :server:clean :server:build
```

---

## 📊 测试报告模板

测试完成后，填写以下报告：

```markdown
## 就诊记录 API 测试报告

**测试日期**: 2026-09-07  
**测试人员**: [姓名]  
**测试环境**: 本地开发环境

### 测试结果

| 测试项 | 状态 | 备注 |
|-------|------|------|
| 用户查询列表 | ✅ / ❌ | |
| 用户查询详情 | ✅ / ❌ | |
| 管理员查看详情 | ✅ / ❌ | |
| 管理员编辑病历 | ✅ / ❌ | |
| 权限隔离 | ✅ / ❌ | |
| 分页功能 | ✅ / ❌ | |

### 发现的问题

1. [问题描述]
2. [问题描述]

### 总体评价

[对 API 质量和稳定性的评价]
```

---

## 📚 相关文档

- [API 详细文档](./API-就诊记录模块.md)
- [实现说明](./就诊记录模块实现说明.md)
- [快速参考](./就诊记录-快速参考.md)

---

**版本**: v1.0  
**更新时间**: 2026-09-07
