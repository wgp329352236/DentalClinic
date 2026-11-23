-- ============================================================
-- 小程序演示数据（与 app/XianTong/miniprogram/utils/mockData.ts 对齐）
--
-- 用途：让小程序切到真实接口（USE_MOCK=false）后，首页/预约/积分/钱包/记录
--       等页面都有数据可看，方便联调服务端。
--
-- 使用方式：
--   psql -h localhost -U postgres -d dental_clinic -f demo-data.sql
--
-- 约定：
--   1. 全脚本幂等：公共数据按名称去重，用户维度数据「该用户已有数据即整批跳过」，
--      重复执行不会产生重复行，也不会覆盖你在页面上改过的数据。
--   2. 演示用户 openid 固定为 mock_openid_demo，与 UserAuthService 未配置微信
--      凭证时的登录 openid 一致，所以小程序登录后看到的就是这个账号。
--   3. 会员等级/充值档位/积分规则/消息模板由 DataSeedService 启动时自动播种，
--      本脚本只额外补一条母亲节活动及其轮播图（按活动标题、activity_id 去重）。
--   4. 日期全部相对 CURRENT_DATE/NOW() 生成，隔天重跑也不会出现「所有预约都过期」。
-- ============================================================

-- ---------- 1. 门店 ----------
INSERT INTO clinic (name, address, phone, open_time, close_time, business_days, description, cover_image, status, sort_order)
SELECT v.name, v.address, v.phone, v.open_time, v.close_time, '1,2,3,4,5,6,7',
       v.description, v.cover_image, 1, v.sort_order
FROM (VALUES
    ('牙科门诊（总店）', '北京市朝阳区建国路88号', '010-12345678', '09:00', '18:00',
     '总店，含正畸、种植与儿童齿科', '/images/clinic1.jpg', 1),
    ('牙科门诊（分店A）', '北京市海淀区中关村大街100号', '010-87654321', '09:00', '18:00',
     '中关村店，主打正畸与种植', '/images/clinic2.jpg', 2),
    ('牙科门诊（分店B）', '北京市西城区金融街7号长安中心1层', '010-55667788', '09:00', '19:00',
     '金融街店，营业至 19:00', '/images/clinic3.jpg', 3),
    ('儿童齿科中心（望京店）', '北京市朝阳区望京SOHO T1 座 3 层', '010-66778899', '09:00', '17:30',
     '专注儿童口腔预防与早期矫治', '/images/clinic4.jpg', 4)
) AS v(name, address, phone, open_time, close_time, description, cover_image, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM clinic c WHERE c.name = v.name);

-- ---------- 2. 科室（每个门店一套，预约页按门店过滤） ----------
INSERT INTO department (clinic_id, name, description, status)
SELECT c.id, v.name, v.description, 1
FROM clinic c
CROSS JOIN (VALUES
    ('口腔检查', '全面口腔健康检查，含口内检查与拍片建议'),
    ('洗牙', '超声波洁牙 + 抛光，清除牙结石与色素'),
    ('补牙', '龋齿充填修复，树脂/玻璃离子可选'),
    ('正畸', '青少年及成人矫正，金属/隐形矫治'),
    ('种植', '单颗/多颗种植修复，含植骨方案'),
    ('牙周治疗', '牙龈炎、牙周炎系统治疗与维护'),
    ('儿童齿科', '涂氟、窝沟封闭、乳牙治疗')
) AS v(name, description)
WHERE c.name IN ('牙科门诊（总店）', '牙科门诊（分店A）', '牙科门诊（分店B）', '儿童齿科中心（望京店）')
  AND NOT EXISTS (SELECT 1 FROM department d WHERE d.clinic_id = c.id AND d.name = v.name);

-- ---------- 3. 医生 ----------
INSERT INTO doctor (name, title, avatar, introduction, specialties, status)
SELECT v.name, v.title, v.avatar, v.introduction, v.specialties, 1
FROM (VALUES
    ('张伟', '主治医师', '/images/doctor1.jpg',
     '从事口腔临床工作15年，擅长龋齿充填与牙周基础治疗，年接诊2000+人次', '口腔检查,补牙,洗牙'),
    ('李静', '副主任医师', '/images/doctor2.jpg',
     '口腔正畸专科医师，10年正畸经验，完成隐形矫正病例800余例', '正畸,种植'),
    ('王强', '主任医师', '/images/doctor3.jpg',
     '种植牙专家，完成各类种植手术2000+例，擅长上颌窦提升与即刻种植', '种植,口腔检查'),
    ('陈晓', '医师', '/images/doctor4.jpg',
     '儿童口腔医学硕士，擅长儿童行为诱导、涂氟与窝沟封闭', '儿童齿科,洗牙'),
    ('刘敏', '主治医师', '/images/doctor5.jpg',
     '牙周病学硕士，擅长牙周系统治疗与美学树脂修复', '补牙,牙周治疗'),
    ('赵磊', '副主任医师', '/images/doctor6.jpg',
     '正畸与牙周联合治疗方向，擅长成人复杂病例的跨学科方案设计', '正畸,牙周治疗'),
    ('孙悦', '医师', '/images/doctor7.jpg',
     '专注儿童口腔预防与早期矫治，善于安抚低龄儿童就诊情绪', '儿童齿科,洗牙'),
    ('周涛', '主任医师', '/images/doctor8.jpg',
     '口腔修复与种植双学科背景，专注咬合重建与复杂种植修复', '种植,补牙')
) AS v(name, title, avatar, introduction, specialties)
WHERE NOT EXISTS (SELECT 1 FROM doctor d WHERE d.name = v.name);

-- ---------- 4. 医生-门店关联 ----------
INSERT INTO doctor_clinic_rel (doctor_id, clinic_id, is_primary)
SELECT d.id, c.id, CASE WHEN v.primary_clinic = v.clinic_name THEN 1 ELSE 0 END
FROM (VALUES
    ('张伟', '牙科门诊（总店）', '牙科门诊（总店）'),
    ('李静', '牙科门诊（总店）', '牙科门诊（总店）'),
    ('李静', '牙科门诊（分店A）', '牙科门诊（总店）'),
    ('王强', '牙科门诊（分店A）', '牙科门诊（分店A）'),
    ('王强', '牙科门诊（分店B）', '牙科门诊（分店A）'),
    ('陈晓', '牙科门诊（总店）', '牙科门诊（总店）'),
    ('陈晓', '儿童齿科中心（望京店）', '牙科门诊（总店）'),
    ('刘敏', '牙科门诊（分店A）', '牙科门诊（分店A）'),
    ('赵磊', '牙科门诊（总店）', '牙科门诊（总店）'),
    ('赵磊', '牙科门诊（分店B）', '牙科门诊（总店）'),
    ('孙悦', '儿童齿科中心（望京店）', '儿童齿科中心（望京店）'),
    ('周涛', '牙科门诊（总店）', '牙科门诊（总店）'),
    ('周涛', '牙科门诊（分店A）', '牙科门诊（总店）'),
    ('周涛', '牙科门诊（分店B）', '牙科门诊（总店）')
) AS v(doctor_name, clinic_name, primary_clinic)
JOIN doctor d ON d.name = v.doctor_name
JOIN clinic c ON c.name = v.clinic_name
WHERE NOT EXISTS (
    SELECT 1 FROM doctor_clinic_rel r WHERE r.doctor_id = d.id AND r.clinic_id = c.id
);

-- ---------- 5. 医生排班（周一至周日，上下午各一段，30 分钟一档，每档 3 人） ----------
INSERT INTO doctor_schedule (doctor_id, day_of_week, start_time, end_time, slot_duration, max_appointments, is_enabled)
SELECT d.id, g.day, t.start_time, t.end_time, 30, 3, 1
FROM doctor d
CROSS JOIN generate_series(1, 7) AS g(day)
CROSS JOIN (VALUES ('09:00', '12:00'), ('14:00', '17:00')) AS t(start_time, end_time)
WHERE d.name IN ('张伟', '李静', '王强', '陈晓', '刘敏', '赵磊', '孙悦', '周涛')
  AND NOT EXISTS (
      SELECT 1 FROM doctor_schedule s
      WHERE s.doctor_id = d.id AND s.day_of_week = g.day AND s.start_time = t.start_time
  );

-- ---------- 6. 积分商品 ----------
INSERT INTO points_goods (name, goods_type, points_price, cover_image, description, total_stock, exchanged_count, sort_order, status)
SELECT v.name, v.goods_type, v.points_price, v.cover_image, v.description, v.total_stock, 0, v.sort_order, 1
FROM (VALUES
    ('超声波洁牙券', 'service', 500, NULL, '到店出示核销码即可享受一次超声波洁牙', -1, 1),
    ('口腔检查套餐', 'service', 200, NULL, '含口内检查 + 全口曲面断层片一次', -1, 2),
    ('儿童涂氟券', 'service', 400, NULL, '12岁以下儿童全口涂氟一次', -1, 3),
    ('成人护龈牙刷套装', 'item', 300, '/images/goods-toothbrush.jpg', '软毛牙刷3支装，到店自提', 8, 4),
    ('声波电动牙刷', 'item', 1200, '/images/goods-electric.jpg', '含3个刷头，到店自提', 3, 5)
) AS v(name, goods_type, points_price, cover_image, description, total_stock, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM points_goods g WHERE g.name = v.name);

-- ---------- 7. 公告 ----------
INSERT INTO mp_notice (content, link_type, link_target, sort_order, status, created_by)
SELECT v.content, v.link_type, v.link_target, v.sort_order, 1, 'demo-data.sql'
FROM (VALUES
    ('温馨提示：就诊当天请在前台扫码签到，可显著减少等待时间', 'none', NULL::varchar, 1),
    ('暑期正畸专场开启，免费拍片 + 方案设计，点击立即预约', 'page', '/pages/appointment/appointment', 2),
    ('推荐好友注册，双方各得100积分，多推多得', 'page', '/pages/referral/referral', 3)
) AS v(content, link_type, link_target, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM mp_notice n WHERE n.content = v.content);

-- ---------- 8. 演示用户及其钱包/会员等级/积分 ----------
-- 手机号 13800138000 若已被其他测试账号占用则不写（该列有唯一索引），演示用户仍可正常登录与浏览，
-- 手机号可在小程序「编辑资料」里补。
INSERT INTO "user" (openid, phone, nickname, real_name, gender, age, avatar, source_type, status, last_login_at)
SELECT 'mock_openid_demo',
       (SELECT '13800138000' WHERE NOT EXISTS (SELECT 1 FROM "user" WHERE phone = '13800138000')),
       '小张', '张三', 1, 30, '/images/avatar.jpg',
       'self', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM "user" u WHERE u.openid = 'mock_openid_demo');

-- 客户不归属单一门店：演示用户与总店建立一条业务关系，便于管理端门店维度演示
INSERT INTO customer_clinic_relation (user_id, clinic_id, first_visit_at, last_visit_at, source)
SELECT u.id, c.id, NOW(), NOW(), 'manual'
FROM "user" u, clinic c
WHERE u.openid = 'mock_openid_demo' AND c.name = '牙科门诊（总店）'
ON CONFLICT (user_id, clinic_id) DO NOTHING;

DO $$
DECLARE
    uid bigint;
BEGIN
    SELECT id INTO uid FROM "user" WHERE openid = 'mock_openid_demo' LIMIT 1;
    IF uid IS NULL THEN
        RAISE NOTICE '演示用户不存在，跳过用户维度数据';
        RETURN;
    END IF;

    -- 钱包（已有则不动，避免覆盖你测试充值后的余额）
    INSERT INTO user_wallet (user_id, balance, bonus_balance, version)
    VALUES (uid, 2500.00, 450.00, 0)
    ON CONFLICT (user_id) DO NOTHING;

    -- 会员等级：金卡（累计消费 8500）
    INSERT INTO user_member_level (user_id, level_id, accumulated_consumption, last_year_consumption, effective_from)
    SELECT uid, ml.id, 8500.00, 8500.00, NOW() - INTERVAL '180 days'
    FROM member_level ml
    WHERE ml.level_code = 2
      AND NOT EXISTS (SELECT 1 FROM user_member_level uml WHERE uml.user_id = uid);

    -- 积分汇总（与下方流水累加一致：209 分）
    INSERT INTO user_points_summary (user_id, total_balance)
    VALUES (uid, 209)
    ON CONFLICT (user_id) DO NOTHING;

    -- 积分流水（该用户已有流水即整批跳过）
    IF NOT EXISTS (SELECT 1 FROM points_record WHERE user_id = uid) THEN
        INSERT INTO points_record (user_id, change_amount, balance_after, source_type, expires_at, description, created_at)
        SELECT uid, v.change_amount, v.balance_after, v.source_type, v.expires_at, v.description, v.created_at
        FROM (VALUES
            (1000, 1000, 'signup',            NOW() + INTERVAL '365 days', '新用户注册礼包',        NOW() - INTERVAL '120 days'),
            (38,   1038, 'consumption',       NOW() + INTERVAL '365 days', '种植消费3800元',        NOW() - INTERVAL '45 days'),
            (6,    1044, 'consumption',       NOW() + INTERVAL '365 days', '补牙消费600元',         NOW() - INTERVAL '20 days'),
            (1,    1045, 'consumption',       NOW() + INTERVAL '365 days', '口腔检查消费100元',     NOW() - INTERVAL '6 days'),
            (2,    1047, 'consumption',       NOW() + INTERVAL '365 days', '儿童涂氟消费200元',     NOW() - INTERVAL '70 days'),
            (30,   1077, 'recharge',          NOW() + INTERVAL '365 days', '充值3000元',            NOW() - INTERVAL '30 days'),
            (10,   1087, 'recharge',          NOW() + INTERVAL '365 days', '充值1000元',            NOW() - INTERVAL '60 days'),
            (100,  1187, 'referral',          NOW() + INTERVAL '365 days', '推荐好友王**首诊消费',  NOW() - INTERVAL '12 days'),
            (20,   1207, 'profile_complete',  NOW() + INTERVAL '365 days', '完善个人资料奖励',      NOW() - INTERVAL '90 days'),
            (2,    1209, 'signup',            NOW() + INTERVAL '365 days', '每日签到',              NOW() - INTERVAL '1 days'),
            (-100, 1109, 'points_deduct',     NOW() + INTERVAL '365 days', '抵扣诊疗费1.00元',      NOW() - INTERVAL '25 days'),
            (-500, 609,  'points_exchange',   NOW() + INTERVAL '365 days', '兑换超声波洁牙券',      NOW() - INTERVAL '8 days'),
            (-400, 209,  'points_exchange',   NOW() + INTERVAL '365 days', '兑换儿童涂氟券',        NOW() - INTERVAL '40 days')
        ) AS v(change_amount, balance_after, source_type, expires_at, description, created_at);
    END IF;

    -- 充值记录（有充值单即跳过；pending 单用于验证「待支付」状态）
    IF NOT EXISTS (SELECT 1 FROM recharge_record WHERE user_id = uid) THEN
        INSERT INTO recharge_record (user_id, clinic_id, amount, bonus_amount, payment_method, status, paid_at, created_at)
        SELECT uid,
               (SELECT id FROM clinic WHERE name = '牙科门诊（总店）'),
               v.amount, v.bonus_amount, 'wechat', v.status, v.paid_at, v.created_at
        FROM (VALUES
            (3000.00, 450.00, 'success', NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
            (1000.00, 100.00, 'success', NOW() - INTERVAL '60 days', NOW() - INTERVAL '60 days'),
            (500.00,  25.00,  'success', NOW() - INTERVAL '100 days', NOW() - INTERVAL '100 days'),
            (2000.00, 300.00, 'success', NOW() - INTERVAL '150 days', NOW() - INTERVAL '150 days'),
            (1000.00, 100.00, 'pending', NULL::timestamp, NOW() - INTERVAL '1 days')
        ) AS v(amount, bonus_amount, status, paid_at, created_at);
    END IF;

    -- 钱包流水（与钱包余额 2500/450 对账一致）
    IF NOT EXISTS (SELECT 1 FROM wallet_transaction WHERE user_id = uid) THEN
        INSERT INTO wallet_transaction (user_id, change_type, amount, bonus_amount, balance_before, balance_after, bonus_before, bonus_after, reason, created_at)
        VALUES
            (uid, 'recharge',  3000.00, 450.00, 0.00,    3000.00, 0.00,   450.00, '演示数据：充值3000元', NOW() - INTERVAL '150 days'),
            (uid, 'recharge',  1000.00, 0.00,   3000.00, 4000.00, 450.00, 450.00, '演示数据：充值1000元', NOW() - INTERVAL '100 days'),
            (uid, 'recharge',  500.00,  0.00,   4000.00, 4500.00, 450.00, 450.00, '演示数据：充值500元',  NOW() - INTERVAL '90 days'),
            (uid, 'recharge',  2000.00, 0.00,   4500.00, 6500.00, 450.00, 450.00, '演示数据：充值2000元', NOW() - INTERVAL '60 days'),
            (uid, 'consume',  -3800.00, 0.00,   6500.00, 2700.00, 450.00, 450.00, '演示数据：种植消费',   NOW() - INTERVAL '45 days'),
            (uid, 'consume',   -200.00, 0.00,   2700.00, 2500.00, 450.00, 450.00, '演示数据：儿童涂氟',   NOW() - INTERVAL '20 days');
    END IF;

    -- 积分兑换订单（与积分流水的两笔兑换扣分对应）
    IF NOT EXISTS (SELECT 1 FROM points_exchange_order WHERE user_id = uid) THEN
        INSERT INTO points_exchange_order (user_id, goods_id, goods_name, points_cost, redeem_code, status, redeemed_at, created_at)
        SELECT uid, g.id, g.name, v.points_cost, v.redeem_code, v.status, v.redeemed_at, v.created_at
        FROM (VALUES
            ('超声波洁牙券', 500, 'PE2026080112345678', 'unused', NULL::timestamp, NOW() - INTERVAL '8 days'),
            ('儿童涂氟券',   400, 'PE2026071012345671', 'used',   NOW() - INTERVAL '35 days', NOW() - INTERVAL '40 days')
        ) AS v(goods_name, points_cost, redeem_code, status, redeemed_at, created_at)
        JOIN points_goods g ON g.name = v.goods_name;
    END IF;

    -- 预约（该用户已有预约即整批跳过；completed 的几条同时充当就诊记录）
    IF NOT EXISTS (SELECT 1 FROM appointment WHERE user_id = uid) THEN
        INSERT INTO appointment (user_id, clinic_id, doctor_id, department_id, appointment_date,
                                 start_time, end_time, status, arrived_at, completed_at,
                                 cancel_reason, diagnosis, treatment_plan, remark, created_at)
        SELECT uid, c.id, d.id, dp.id, CURRENT_DATE + v.day_offset,
               v.start_time, v.end_time, v.status, v.arrived_at, v.completed_at,
               v.cancel_reason, v.diagnosis, v.treatment_plan, v.remark, v.created_at
        FROM (VALUES
            ('牙科门诊（总店）', '张伟', '洗牙', 0, '09:30', '10:00', 'confirmed',
             NULL::timestamp, NULL::timestamp, NULL::text, NULL::text, NULL::text,
             '牙龈易出血，请安排温和洁牙', NOW() - INTERVAL '3 days'),
            ('牙科门诊（分店A）', '李静', '正畸', 1, '10:00', '10:30', 'pending',
             NULL, NULL, NULL, NULL, NULL, NULL, NOW() - INTERVAL '1 days'),
            ('牙科门诊（总店）', '张伟', '补牙', 2, '14:30', '15:30', 'confirmed',
             NULL, NULL, NULL, NULL, NULL, NULL, NOW() - INTERVAL '2 days'),
            ('牙科门诊（总店）', '周涛', '种植', 0, '08:30', '09:00', 'arrived',
             NOW() - INTERVAL '2 hours', NULL, NULL, NULL, NULL, NULL, NOW() - INTERVAL '5 days'),
            ('牙科门诊（分店B）', '赵磊', '牙周治疗', 4, '16:00', '16:30', 'pending',
             NULL, NULL, NULL, NULL, NULL, NULL, NOW() - INTERVAL '1 days'),
            ('牙科门诊（总店）', '张伟', '口腔检查', -6, '09:00', '09:30', 'completed',
             NOW() - INTERVAL '6 days 1 hour', NOW() - INTERVAL '6 days' - INTERVAL '20 minutes',
             NULL, '牙列基本整齐，下颌前牙区牙结石Ⅰ度，牙龈轻度炎症',
             '建议每半年洁牙一次，日常使用牙线并采用巴氏刷牙法',
             '患者对冷刺激略敏感，已告知观察', NOW() - INTERVAL '9 days'),
            ('牙科门诊（分店A）', '刘敏', '补牙', -20, '15:00', '16:00', 'completed',
             NOW() - INTERVAL '20 days 1 hour', NOW() - INTERVAL '20 days' + INTERVAL '5 minutes',
             NULL, '左下第一磨牙深龋，牙髓活力正常',
             '去腐后树脂充填，1个月后复查；若出现自发痛需根管治疗',
             NULL, NOW() - INTERVAL '25 days'),
            ('牙科门诊（总店）', '周涛', '种植', -45, '10:30', '12:00', 'completed',
             NOW() - INTERVAL '45 days 1 hour', NOW() - INTERVAL '45 days' + INTERVAL '10 minutes',
             NULL, '右下第一磨牙缺失3个月，牙槽骨高度可，骨密度尚可',
             '一期植入种植体，3个月后行二期基台连接，再2周后冠修复',
             '术后24小时内避免漱口与患侧咀嚼', NOW() - INTERVAL '48 days'),
            ('儿童齿科中心（望京店）', '陈晓', '儿童齿科', -70, '14:00', '14:40', 'completed',
             NOW() - INTERVAL '70 days 1 hour', NOW() - INTERVAL '70 days' + INTERVAL '15 minutes',
             NULL, '乳磨牙窝沟较深，未见龋坏', '全口涂氟，建议3-6个月复查', NULL, NOW() - INTERVAL '73 days'),
            ('牙科门诊（总店）', '张伟', '洗牙', -15, '09:00', '09:30', 'no_show',
             NULL, NULL, NULL, NULL, NULL, NULL, NOW() - INTERVAL '18 days'),
            ('牙科门诊（分店B）', '赵磊', '牙周治疗', -33, '10:00', '11:00', 'completed',
             NOW() - INTERVAL '33 days 1 hour', NOW() - INTERVAL '33 days' + INTERVAL '5 minutes',
             NULL, '全口牙龈红肿，探诊出血明显', '牙周基础治疗后1个月复评', NULL, NOW() - INTERVAL '36 days'),
            ('儿童齿科中心（望京店）', '孙悦', '儿童齿科', 0, '15:00', '15:30', 'confirmed',
             NULL, NULL, NULL, NULL, NULL, '孩子首次涂氟，请提前告知注意事项', NOW() - INTERVAL '2 days'),
            ('牙科门诊（总店）', '陈晓', '洗牙', 3, '16:30', '17:00', 'pending',
             NULL, NULL, NULL, NULL, NULL, NULL, NOW() - INTERVAL '1 days'),
            ('牙科门诊（分店A）', '李静', '正畸', -13, '09:00', '09:30', 'cancelled',
             NULL, NULL, '行程有变，重新预约', NULL, NULL, NULL, NOW() - INTERVAL '16 days')
        ) AS v(clinic_name, doctor_name, department_name, day_offset, start_time, end_time, status,
               arrived_at, completed_at, cancel_reason, diagnosis, treatment_plan, remark, created_at)
        JOIN clinic c ON c.name = v.clinic_name
        JOIN doctor d ON d.name = v.doctor_name
        JOIN department dp ON dp.clinic_id = c.id AND dp.name = v.department_name;
    END IF;

    -- 消费记录（与积分流水的消费积分口径一致：100/600/3800/200）
    IF NOT EXISTS (SELECT 1 FROM consumption_record WHERE user_id = uid) THEN
        INSERT INTO consumption_record (user_id, clinic_id, amount, original_amount, discount_amount,
                                        pay_amount, balance_used, wechat_paid, points_earned,
                                        status, payment_status, description, created_at)
        SELECT uid,
               (SELECT id FROM clinic WHERE name = v.clinic_name),
               v.amount, v.amount, 0.00, v.amount, v.balance_used, v.wechat_paid, v.points_earned,
               'success', 'success', v.description, v.created_at
        FROM (VALUES
            ('牙科门诊（总店）', 100.00, 100.00, 0.00, 1, '口腔检查费', NOW() - INTERVAL '6 days'),
            ('牙科门诊（分店A）', 600.00, 500.00, 100.00, 6, '树脂充填（左下第一磨牙）', NOW() - INTERVAL '20 days'),
            ('牙科门诊（总店）', 3800.00, 3500.00, 300.00, 38, '种植体植入术', NOW() - INTERVAL '45 days'),
            ('儿童齿科中心（望京店）', 200.00, 200.00, 0.00, 2, '全口涂氟', NOW() - INTERVAL '70 days')
        ) AS v(clinic_name, amount, balance_used, wechat_paid, points_earned, description, created_at);
    END IF;

    -- 优惠券模板（公共，按名称去重）
    INSERT INTO coupon_template (name, coupon_type, face_value, min_consumption, valid_from, valid_to, total_quantity, issued_quantity, status)
    SELECT v.name, 'cash', v.face_value, v.min_consumption, NOW() - INTERVAL '60 days', NOW() + INTERVAL '90 days', 1000, 0, 1
    FROM (VALUES
        ('新人券', 100.00, 200.00),
        ('推荐奖励券', 50.00, 100.00),
        ('生日券', 80.00, 300.00),
        ('暑期活动券', 200.00, 500.00),
        ('会员日券', 300.00, 800.00)
    ) AS v(name, face_value, min_consumption)
    WHERE NOT EXISTS (SELECT 1 FROM coupon_template t WHERE t.name = v.name);

    -- 用户优惠券（有券即跳过）
    IF NOT EXISTS (SELECT 1 FROM user_coupon WHERE user_id = uid) THEN
        INSERT INTO user_coupon (user_id, template_id, coupon_code, status, used_at, created_at)
        SELECT uid, t.id, v.coupon_code, v.status, v.used_at, v.created_at
        FROM (VALUES
            ('新人券',     'NEW100',  'unused',  NULL::timestamp, NOW() - INTERVAL '30 days'),
            ('推荐奖励券', 'REF50',   'unused',  NULL,            NOW() - INTERVAL '20 days'),
            ('生日券',     'BIRTH80', 'unused',  NULL,            NOW()),
            ('暑期活动券', 'ACT200',  'expired', NULL,            NOW() - INTERVAL '60 days'),
            ('会员日券',   'VIP300',  'used',    NOW() - INTERVAL '10 days', NOW() - INTERVAL '40 days')
        ) AS v(template_name, coupon_code, status, used_at, created_at)
        JOIN coupon_template t ON t.name = v.template_name;
    END IF;

    -- 站内消息（有消息即跳过）
    IF NOT EXISTS (SELECT 1 FROM notification WHERE user_id = uid) THEN
        INSERT INTO notification (user_id, title, content, msg_type, is_read, read_at, created_at)
        VALUES
            (uid, '预约提醒',   '您今天09:30在牙科门诊（总店）有洗牙预约，到店后请在前台扫码签到', 'system', 0, NULL, NOW() - INTERVAL '2 hours'),
            (uid, '活动通知',   '暑期正畸专场：隐形矫正立减2000元，推荐好友再得100积分', 'activity', 0, NULL, NOW() - INTERVAL '2 days'),
            (uid, '签到提醒',   '每日签到可领2积分，连续签到奖励更丰富', 'system', 0, NULL, NOW() - INTERVAL '1 days'),
            (uid, '消费通知',   '您于6天前消费100元，余额支付100元，获得积分1分', 'consumption', 1, NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days'),
            (uid, '积分兑换',   '您使用500积分兑换了超声波洁牙券，请到店出示核销码', 'consumption', 1, NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days'),
            (uid, '预约提醒',   '您在牙科门诊（分店A）的口腔检查预约已取消，如有需要可重新预约', 'system', 1, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
            (uid, '充值到账',   '充值3000元已到账，赠送450元，赠送金额可用于全项目消费', 'consumption', 1, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
            (uid, '会员日',     '每月8日会员日，洁牙8折并赠送口腔检查一次', 'activity', 1, NOW() - INTERVAL '38 days', NOW() - INTERVAL '38 days');
    END IF;

    -- 推荐关系：本人占位行（推荐码）+ 一个已首消的被推荐人
    INSERT INTO referral_relation (referrer_id, referee_id, referral_code, registered_at, first_consumed_at, reward_status)
    SELECT uid, uid, 'DEMO2026', NOW() - INTERVAL '180 days', NULL, 'pending'
    WHERE NOT EXISTS (
        SELECT 1 FROM referral_relation r WHERE r.referrer_id = uid AND r.referee_id = uid
    );

    INSERT INTO "user" (openid, nickname, real_name, gender, source_type, referred_by, status)
    VALUES ('mock_openid_demo_referee', '小王', '王**', 2, 'referral', uid, 1)
    ON CONFLICT (openid) DO NOTHING;

    INSERT INTO referral_relation (referrer_id, referee_id, referral_code, registered_at, first_consumed_at, reward_status)
    SELECT uid, r.id, 'DEMO2026', NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days', 'full'
    FROM "user" r
    WHERE r.openid = 'mock_openid_demo_referee'
      AND NOT EXISTS (
          SELECT 1 FROM referral_relation x WHERE x.referee_id = r.id AND x.referrer_id = uid
      );

    INSERT INTO referral_reward (relation_id, referrer_id, referee_id, reward_type, reward_value, stage, status, granted_at)
    SELECT rel.id, uid, rel.referee_id, 'points', 100.00, 'first_consumption', 'granted', NOW() - INTERVAL '12 days'
    FROM referral_relation rel
    JOIN "user" r ON r.id = rel.referee_id
    WHERE r.openid = 'mock_openid_demo_referee'
      AND rel.referrer_id = uid
      AND NOT EXISTS (SELECT 1 FROM referral_reward rw WHERE rw.relation_id = rel.id);
END $$;

-- ---------- 9. 母亲节活动与首页轮播 ----------
-- 活动按标题去重，轮播图按 activity_id 唯一索引去重。
-- 封面用小程序包内图片 /images/mothers-day.jpg，避免依赖本地上传文件；
-- 时间相对当前日期生成，隔很久重跑也仍在前台可见。
INSERT INTO mp_activity (title, cover_image, summary, content, sort_order, status, start_at, end_at, created_by)
SELECT '母亲节献礼 · 母女同享口腔护理礼遇',
       '/images/mothers-day.jpg',
       '这个母亲节，带上妈妈一起做口腔检查。母女同行第二人半价，另赠康乃馨与口腔护理礼包。',
       E'【母女同行礼】\n女儿与母亲同日到店，第二人基础口腔检查半价。\n\n【洁牙双人套餐】\n双人超声波洁牙 + 抛光 + 口腔检查，原价 596 元，母亲节价 399 元。\n\n【到店即赠】\n参与活动的客户可领取康乃馨一支与口腔护理礼包一份（数量有限，先到先得）。\n\n【参与方式】\n点击下方「立即预约」，选择门店与时间，到店出示本活动页面即可。\n\n温馨提示：优惠不可与店内其他活动同享，具体以门店解释为准。',
       0, 1,
       CURRENT_DATE - INTERVAL '7 days',
       CURRENT_DATE + INTERVAL '1 year',
       'demo-data'
WHERE NOT EXISTS (
    SELECT 1 FROM mp_activity WHERE title = '母亲节献礼 · 母女同享口腔护理礼遇'
);

INSERT INTO mp_banner (title, image_url, activity_id, link_type, sort_order, status, created_by)
SELECT a.title, a.cover_image, a.id, 'none', 0, 1, 'demo-data'
FROM mp_activity a
WHERE a.title = '母亲节献礼 · 母女同享口腔护理礼遇'
  AND NOT EXISTS (SELECT 1 FROM mp_banner b WHERE b.activity_id = a.id);

-- ---------- 10. 结果核对 ----------
SELECT '门店' AS item, COUNT(*) FROM clinic
UNION ALL SELECT '科室', COUNT(*) FROM department
UNION ALL SELECT '医生', COUNT(*) FROM doctor
UNION ALL SELECT '排班', COUNT(*) FROM doctor_schedule
UNION ALL SELECT '积分商品', COUNT(*) FROM points_goods
UNION ALL SELECT '公告', COUNT(*) FROM mp_notice
UNION ALL SELECT '活动', COUNT(*) FROM mp_activity
UNION ALL SELECT '轮播图', COUNT(*) FROM mp_banner
UNION ALL SELECT '演示用户', COUNT(*) FROM "user" WHERE openid = 'mock_openid_demo'
UNION ALL SELECT '预约', COUNT(*) FROM appointment WHERE user_id = (SELECT id FROM "user" WHERE openid = 'mock_openid_demo')
UNION ALL SELECT '积分为', COUNT(*) FROM points_record WHERE user_id = (SELECT id FROM "user" WHERE openid = 'mock_openid_demo')
UNION ALL SELECT '消费记录', COUNT(*) FROM consumption_record WHERE user_id = (SELECT id FROM "user" WHERE openid = 'mock_openid_demo')
UNION ALL SELECT '消息', COUNT(*) FROM notification WHERE user_id = (SELECT id FROM "user" WHERE openid = 'mock_openid_demo');
