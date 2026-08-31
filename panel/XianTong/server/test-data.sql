-- ============================================================
-- 牙科门诊小程序测试数据
-- ============================================================

-- 1. 门店数据
INSERT INTO clinic (name, address, phone, open_time, close_time, business_days, description, status, sort_order) VALUES
('阳光口腔诊所-总店', '北京市朝阳区建国路88号', '010-12345678', '09:00', '18:00', '1,2,3,4,5,6,7', '总店位于CBD核心区，设备先进', 1, 1),
('阳光口腔诊所-分店', '北京市海淀区中关村大街100号', '010-87654321', '09:00', '18:00', '1,2,3,4,5,6', '分店位于高校区，服务师生', 1, 2);

-- 2. 科室/项目数据
INSERT INTO department (clinic_id, name, description, status) VALUES
(1, '牙齿矫正', '隐形矫正、金属托槽矫正等', 1),
(1, '牙齿美白', '冷光美白、贴面美白', 1),
(1, '种植牙', '进口种植牙、国产种植牙', 1),
(1, '洗牙护理', '超声波洗牙、喷砂抛光', 1),
(2, '儿童齿科', '儿童龋齿防治、涂氟窝沟封闭', 1),
(2, '牙齿矫正', '青少年矫正专科', 1);

-- 3. 医生数据
INSERT INTO doctor (name, title, introduction, specialties, status) VALUES
('张医生', '主任医师', '从事口腔医学20年，擅长各类牙齿矫正', '牙齿矫正,隐形矫正,种植', 1),
('李医生', '副主任医师', '专注牙齿美学修复15年', '牙齿美白,贴面,美学修复', 1),
('王医生', '主治医师', '儿童齿科专家，耐心细致', '儿童齿科,龋齿防治,涂氟', 1),
('赵医生', '住院医师', '擅长种植牙手术', '种植牙,口腔外科', 1);

-- 4. 医生-门店关联
INSERT INTO doctor_clinic_rel (doctor_id, clinic_id, is_primary) VALUES
(1, 1, 1), -- 张医生主诊总店
(1, 2, 0), -- 张医生也在分店坐诊
(2, 1, 1), -- 李医生主诊总店
(3, 2, 1), -- 王医生主诊分店
(4, 1, 1); -- 赵医生主诊总店

-- 5. 医生排班（按周循环）
-- 张医生：周一、三、五上午
INSERT INTO doctor_schedule (doctor_id, day_of_week, start_time, end_time, slot_duration, max_appointments) VALUES
(1, 1, '09:00', '12:00', 30, 1),
(1, 3, '09:00', '12:00', 30, 1),
(1, 5, '09:00', '12:00', 30, 1);

-- 李医生：周二、四下午
INSERT INTO doctor_schedule (doctor_id, day_of_week, start_time, end_time, slot_duration, max_appointments) VALUES
(2, 2, '14:00', '17:00', 30, 1),
(2, 4, '14:00', '17:00', 30, 1);

-- 王医生：周一到周六全天
INSERT INTO doctor_schedule (doctor_id, day_of_week, start_time, end_time, slot_duration, max_appointments) VALUES
(3, 1, '09:00', '17:00', 30, 1),
(3, 2, '09:00', '17:00', 30, 1),
(3, 3, '09:00', '17:00', 30, 1),
(3, 4, '09:00', '17:00', 30, 1),
(3, 5, '09:00', '17:00', 30, 1),
(3, 6, '09:00', '17:00', 30, 1);

-- 赵医生：周三、六上午
INSERT INTO doctor_schedule (doctor_id, day_of_week, start_time, end_time, slot_duration, max_appointments) VALUES
(4, 3, '09:00', '12:00', 30, 1),
(4, 6, '09:00', '12:00', 30, 1);

-- 6. 会员等级配置
INSERT INTO member_level (level_code, level_name, min_consumption, discount_rate, benefits, status) VALUES
(1, '普通会员', 0, 1.00, '基础服务', 1),
(2, '银卡会员', 5000, 0.95, '95折优惠，优先预约', 1),
(3, '金卡会员', 20000, 0.90, '9折优惠，专属客服', 1),
(4, '钻石会员', 50000, 0.85, '85折优惠，VIP通道', 1);

-- 7. 积分规则配置
INSERT INTO points_rule (rule_name, rule_type, action_type, points_per_unit, unit_description, daily_limit, monthly_limit, validity_months, status) VALUES
('消费积分', 'earn', 'consumption', 10, '每消费1元获得10积分', NULL, NULL, 12, 1),
('充值积分', 'earn', 'recharge', 5, '每充值1元获得5积分', NULL, NULL, 12, 1),
('完善资料', 'earn', 'profile_complete', 100, '完善个人资料一次性奖励', 1, 1, 12, 1),
('签到积分', 'earn', 'signup', 5, '每日签到奖励', 1, 30, 1, 1),
('评价积分', 'earn', 'review', 20, '完成就诊评价奖励', 1, 10, 12, 1),
('推荐积分', 'earn', 'referral', 500, '成功推荐一人注册', NULL, NULL, 12, 1),
('积分兑换', 'spend', 'redeem', -1, '积分兑换商品或服务', NULL, NULL, 0, 1);

-- 8. 优惠券模板
INSERT INTO coupon_template (name, coupon_type, face_value, min_consumption, applicable_departments, valid_from, valid_to, total_quantity, issued_quantity, used_quantity, status) VALUES
('新人专享券', 'cash', 50.00, 100.00, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '90 days', 1000, 0, 0, 1),
('牙齿美白8折券', 'discount', 0.80, 0, '[2]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '60 days', 500, 0, 0, 1),
('洗牙体验券', 'service', 0, 0, '[4]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '30 days', 200, 0, 0, 1),
('种植牙抵用券', 'cash', 500.00, 3000.00, '[3]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '180 days', 100, 0, 0, 1);

-- 9. 消息模板配置
INSERT INTO message_template (template_key, template_id, title, content, trigger_event, status) VALUES
('appointment_confirm', 'tmpl_001', '预约成功通知', '您已成功预约{{date}} {{time}}的{{doctor}}医生', 'appointment.created', 1),
('appointment_reminder', 'tmpl_002', '就诊提醒', '温馨提示：您明天{{time}}有预约，请准时到达', 'appointment.reminder', 1),
('consumption_notify', 'tmpl_003', '消费通知', '您本次消费{{amount}}元，剩余余额{{balance}}元', 'consumption.completed', 1),
('points_change', 'tmpl_004', '积分变动通知', '您的积分{{change}}，当前余额{{balance}}', 'points.changed', 1);

-- 10. 系统配置
INSERT INTO admin_system_config (config_key, config_value, config_type, category, description, is_sensitive, reload_type, updated_by) VALUES
('recharge_bonus_rate', '0.1', 'decimal', 'wallet', '充值赠送比例（充100送10%）', 0, 'hot', 'system'),
('points_expire_months', '12', 'integer', 'points', '积分有效期（月）', 0, 'hot', 'system'),
('max_referral_reward', '1000', 'integer', 'referral', '推荐奖励上限（积分）', 0, 'hot', 'system');

-- ============================================================
-- 说明：
-- 1. 用户数据需要通过小程序注册流程创建（需要微信openid）
-- 2. 预约数据需要通过预约接口创建
-- 3. 管理员账号通过初始化脚本创建
-- ============================================================
