#!/bin/bash

# ============================================================
# 就诊记录模块 API 测试脚本
# ============================================================
# 用途: 测试用户端和管理端的就诊记录相关接口
# 使用: ./就诊记录API测试脚本.sh
# ============================================================

# 配置
BASE_URL="http://localhost:8080"
USER_TOKEN=""
ADMIN_TOKEN=""
TEST_APPOINTMENT_ID=""

# 颜色输出
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo_header() {
    echo -e "\n${YELLOW}========================================${NC}"
    echo -e "${YELLOW}$1${NC}"
    echo -e "${YELLOW}========================================${NC}\n"
}

echo_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

echo_error() {
    echo -e "${RED}✗ $1${NC}"
}

# ============================================================
# 第一步：登录获取 Token
# ============================================================
echo_header "Step 1: 用户登录"

USER_LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "test_user_code",
    "userInfo": {
      "nickName": "测试用户",
      "avatarUrl": "https://example.com/avatar.png"
    }
  }')

echo "用户登录响应:"
echo "$USER_LOGIN_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$USER_LOGIN_RESPONSE"

# 尝试提取 token（根据实际响应格式调整）
USER_TOKEN=$(echo "$USER_LOGIN_RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('data', {}).get('accessToken', ''))" 2>/dev/null)

if [ -z "$USER_TOKEN" ]; then
    echo_error "无法获取用户 Token，请手动设置 USER_TOKEN 变量"
    echo "提示: export USER_TOKEN='your_token_here'"
else
    echo_success "获取用户 Token 成功"
fi

# ============================================================
echo_header "Step 2: 管理员登录"

ADMIN_LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/admin/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }')

echo "管理员登录响应:"
echo "$ADMIN_LOGIN_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$ADMIN_LOGIN_RESPONSE"

# 尝试提取 token
ADMIN_TOKEN=$(echo "$ADMIN_LOGIN_RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('data', {}).get('accessToken', ''))" 2>/dev/null)

if [ -z "$ADMIN_TOKEN" ]; then
    echo_error "无法获取管理员 Token，请手动设置 ADMIN_TOKEN 变量"
    echo "提示: export ADMIN_TOKEN='your_token_here'"
else
    echo_success "获取管理员 Token 成功"
fi

# ============================================================
# 第二步：测试用户端 API
# ============================================================
echo_header "Step 3: 测试用户端 - 获取就诊记录列表"

if [ -n "$USER_TOKEN" ]; then
    MEDICAL_RECORDS_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v1/medical-records?page=1&pageSize=10" \
      -H "Authorization: Bearer $USER_TOKEN")

    echo "就诊记录列表响应:"
    echo "$MEDICAL_RECORDS_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$MEDICAL_RECORDS_RESPONSE"

    # 提取第一个预约 ID 用于后续测试
    TEST_APPOINTMENT_ID=$(echo "$MEDICAL_RECORDS_RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); records=data.get('data', {}).get('list', []); print(records[0]['id'] if records else '')" 2>/dev/null)

    if [ -n "$TEST_APPOINTMENT_ID" ]; then
        echo_success "获取就诊记录列表成功，找到预约 ID: $TEST_APPOINTMENT_ID"
    else
        echo_error "未找到就诊记录，可能需要先创建测试数据"
        echo "提示: export TEST_APPOINTMENT_ID='1001'"
    fi
else
    echo_error "跳过测试：缺少用户 Token"
fi

# ============================================================
echo_header "Step 4: 测试用户端 - 获取就诊记录详情"

if [ -n "$USER_TOKEN" ] && [ -n "$TEST_APPOINTMENT_ID" ]; then
    RECORD_DETAIL_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v1/medical-records/$TEST_APPOINTMENT_ID" \
      -H "Authorization: Bearer $USER_TOKEN")

    echo "就诊记录详情响应 (ID: $TEST_APPOINTMENT_ID):"
    echo "$RECORD_DETAIL_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RECORD_DETAIL_RESPONSE"

    # 检查是否包含关键字段
    if echo "$RECORD_DETAIL_RESPONSE" | grep -q "diagnosis"; then
        echo_success "就诊记录详情包含诊断信息"
    else
        echo_error "就诊记录详情缺少诊断信息"
    fi

    if echo "$RECORD_DETAIL_RESPONSE" | grep -q "consumptionDetails"; then
        echo_success "就诊记录详情包含消费明细"
    else
        echo_error "就诊记录详情缺少消费明细"
    fi
else
    echo_error "跳过测试：缺少用户 Token 或预约 ID"
fi

# ============================================================
# 第三步：测试管理端 API
# ============================================================
echo_header "Step 5: 测试管理端 - 查看预约详情"

if [ -n "$ADMIN_TOKEN" ] && [ -n "$TEST_APPOINTMENT_ID" ]; then
    APPOINTMENT_DETAIL_RESPONSE=$(curl -s -X GET "$BASE_URL/api/admin/appointments/$TEST_APPOINTMENT_ID" \
      -H "Authorization: Bearer $ADMIN_TOKEN")

    echo "预约详情响应 (ID: $TEST_APPOINTMENT_ID):"
    echo "$APPOINTMENT_DETAIL_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$APPOINTMENT_DETAIL_RESPONSE"

    if echo "$APPOINTMENT_DETAIL_RESPONSE" | grep -q "appointment"; then
        echo_success "预约详情查询成功"
    else
        echo_error "预约详情查询失败"
    fi
else
    echo_error "跳过测试：缺少管理员 Token 或预约 ID"
fi

# ============================================================
echo_header "Step 6: 测试管理端 - 编辑病历摘要"

if [ -n "$ADMIN_TOKEN" ] && [ -n "$TEST_APPOINTMENT_ID" ]; then
    EDIT_MEDICAL_RESPONSE=$(curl -s -X PUT "$BASE_URL/api/admin/appointments/$TEST_APPOINTMENT_ID/medical-record" \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H "Content-Type: application/json" \
      -d '{
        "diagnosis": "龋齿补牙（左下第一磨牙）",
        "treatmentPlan": "树脂充填治疗，建议半年后复查"
      }')

    echo "编辑病历响应:"
    echo "$EDIT_MEDICAL_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$EDIT_MEDICAL_RESPONSE"

    if echo "$EDIT_MEDICAL_RESPONSE" | grep -q "病历更新成功"; then
        echo_success "病历编辑成功"
    else
        echo_error "病历编辑失败"
    fi
else
    echo_error "跳过测试：缺少管理员 Token 或预约 ID"
fi

# ============================================================
echo_header "Step 7: 验证病历更新 - 用户再次查看详情"

if [ -n "$USER_TOKEN" ] && [ -n "$TEST_APPOINTMENT_ID" ]; then
    VERIFY_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v1/medical-records/$TEST_APPOINTMENT_ID" \
      -H "Authorization: Bearer $USER_TOKEN")

    echo "验证更新后的详情:"
    echo "$VERIFY_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$VERIFY_RESPONSE"

    # 检查诊断信息是否已更新
    if echo "$VERIFY_RESPONSE" | grep -q "龋齿补牙"; then
        echo_success "病历更新验证成功：诊断信息已同步"
    else
        echo_error "病历更新验证失败：诊断信息未同步"
    fi
else
    echo_error "跳过测试：缺少用户 Token 或预约 ID"
fi

# ============================================================
echo_header "测试完成总结"

echo "
测试项目清单:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ✓ 用户登录获取 Token
2. ✓ 管理员登录获取 Token
3. ✓ 用户查询就诊记录列表
4. ✓ 用户查询就诊记录详情
5. ✓ 管理员查看预约详情
6. ✓ 管理员编辑病历摘要
7. ✓ 验证病历更新同步

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
注意事项:
- 确保服务已启动在 $BASE_URL
- 确保数据库中有测试数据（已完成状态的预约）
- Token 有效期可能有限，过期需重新登录
- 部分接口需要特定权限

如需手动测试，可使用以下 curl 命令:
"

echo "
# 用户查询就诊记录列表
curl -X GET \"$BASE_URL/api/v1/medical-records?page=1&pageSize=10\" \\
  -H \"Authorization: Bearer \$USER_TOKEN\"

# 用户查询就诊记录详情
curl -X GET \"$BASE_URL/api/v1/medical-records/\$TEST_APPOINTMENT_ID\" \\
  -H \"Authorization: Bearer \$USER_TOKEN\"

# 管理员查看预约详情
curl -X GET \"$BASE_URL/api/admin/appointments/\$TEST_APPOINTMENT_ID\" \\
  -H \"Authorization: Bearer \$ADMIN_TOKEN\"

# 管理员编辑病历
curl -X PUT \"$BASE_URL/api/admin/appointments/\$TEST_APPOINTMENT_ID/medical-record\" \\
  -H \"Authorization: Bearer \$ADMIN_TOKEN\" \\
  -H \"Content-Type: application/json\" \\
  -d '{
    \"diagnosis\": \"龋齿补牙\",
    \"treatmentPlan\": \"树脂充填治疗\"
  }'
"
