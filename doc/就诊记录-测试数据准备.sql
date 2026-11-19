-- ============================================================
-- 就诊记录模块 - 测试数据准备 SQL
-- ============================================================
-- 用途: 创建测试用的预约、消费记录和病历数据
-- 使用: 在数据库中执行此脚本
-- ============================================================

-- 1. 确保有测试用户（假设 userId = 100）
-- 如果不存在，请先通过小程序注册一个测试用户

-- 2. 确保有测试诊所和医生
-- 假设 clinicId = 1, doctorId = 1, departmentId = 1

-- 3. 创建已完成的预约记录
INSERT INTO appointment (
    user_id,
    clinic_id,
    doctor_id,
    department_id,
    appointment_date,
    start_time,
    end_time,
    status,
    arrived_at,
    completed_at,
    diagnosis,
    treatment_plan,
    remark,
    created_at,
    updated_at
) VALUES (
    100,                    -- user_id (请替换为实际用户ID)
    1,                      -- clinic_id
    1,                      -- doctor_id
    1,                      -- department_id
    '2026-09-05',          -- appointment_date
    '10:00',               -- start_time
    '10:30',               -- end_time
    'completed',           -- status
    '2026-09-05 09:55:00', -- arrived_at
    '2026-09-05 10:35:00', -- completed_at
    '龋齿补牙',            -- diagnosis
    '树脂充填治疗',        -- treatment_plan
    '患者配合良好',        -- remark
    NOW(),                 -- created_at
    NOW()                  -- updated_at
);

-- 获取刚插入的预约 ID
SET @appointment_id = LAST_INSERT_ID();

-- 4. 创建消费记录
INSERT INTO consumption_record (
    user_id,
    clinic_id,
    appointment_id,
    amount,
    balance_used,
    wechat_paid,
    points_earned,
    description,
    created_at
) VALUES (
    100,                    -- user_id
    1,                      -- clinic_id
    @appointment_id,        -- appointment_id
    500.00,                -- amount
    500.00,                -- balance_used
    0.00,                  -- wechat_paid
    50,                    -- points_earned
    '树脂补牙费用',        -- description
    NOW()                  -- created_at
);

-- 5. 再创建一个预约用于测试列表分页
INSERT INTO appointment (
    user_id,
    clinic_id,
    doctor_id,
    department_id,
    appointment_date,
    start_time,
    end_time,
    status,
    arrived_at,
    completed_at,
    diagnosis,
    treatment_plan,
    remark,
    created_at,
    updated_at
) VALUES (
    100,                    -- user_id
    1,                      -- clinic_id
    1,                      -- doctor_id
    1,                      -- department_id
    '2026-09-01',          -- appointment_date
    '14:00',               -- start_time
    '14:30',               -- end_time
    'completed',           -- status
    '2026-09-01 13:55:00', -- arrived_at
    '2026-09-01 14:35:00', -- completed_at
    '洗牙',                -- diagnosis
    '超声波洁治',          -- treatment_plan
    '建议定期洗牙',        -- remark
    NOW(),                 -- created_at
    NOW()                  -- updated_at
);

SET @appointment_id_2 = LAST_INSERT_ID();

INSERT INTO consumption_record (
    user_id,
    clinic_id,
    appointment_id,
    amount,
    balance_used,
    wechat_paid,
    points_earned,
    description,
    created_at
) VALUES (
    100,                    -- user_id
    1,                      -- clinic_id
    @appointment_id_2,      -- appointment_id
    200.00,                -- amount
    200.00,                -- balance_used
    0.00,                  -- wechat_paid
    20,                    -- points_earned
    '洗牙费用',            -- description
    NOW()                  -- created_at
);

-- 6. 验证数据插入
SELECT '=== 预约记录 ===' AS info;
SELECT id, user_id, appointment_date, status, diagnosis, treatment_plan
FROM appointment
WHERE user_id = 100 AND status = 'completed'
ORDER BY appointment_date DESC;

SELECT '=== 消费记录 ===' AS info;
SELECT id, user_id, appointment_id, amount, description
FROM consumption_record
WHERE user_id = 100
ORDER BY created_at DESC;

-- 7. 查询测试用的预约 ID（用于 API 测试）
SELECT '=== 测试用预约 ID ===' AS info;
SELECT id AS appointment_id, appointment_date, diagnosis
FROM appointment
WHERE user_id = 100 AND status = 'completed'
ORDER BY appointment_date DESC
LIMIT 5;
