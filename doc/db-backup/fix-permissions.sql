--
-- dental_clinic 表权限修复脚本（方案 A：批量 GRANT，不改 owner）
--
-- 适用场景：数据库由宝塔面板创建，表却是由 postgres（或其他角色）导入/创建，
--           导致 dental_clinic 能连接数据库但读写表时报
--           ERROR: permission denied for table xxx
--
-- 执行方式（以超级用户身份连到目标库）：
--   sudo -u postgres /www/server/pgsql/bin/psql -d dental_clinic -f fix-permissions.sql
-- 或进入 psql 后：
--   \i /path/to/fix-permissions.sql
--
-- 幂等：可重复执行，不会改动或删除任何数据。
--

\set ON_ERROR_STOP on

-- ── 1. 先看现场：表真实 owner 是谁 ──────────────────────────────
SELECT current_user AS 当前用户, current_database() AS 当前库;
SELECT tablename AS 表名, tableowner AS 所有者
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY tableowner, tablename;

-- ── 2. schema 与现有对象授权 ────────────────────────────────────
GRANT USAGE ON SCHEMA public TO dental_clinic;
GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA public TO dental_clinic;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO dental_clinic;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO dental_clinic;

-- ── 3. 默认权限：以后 postgres 再建对象也自动授权 ────────────────
-- 注意：默认权限按“创建者角色”生效。若表是由其他角色创建的，
--       需把下面的 postgres 替换成该角色名重复执行。
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  GRANT ALL ON TABLES    TO dental_clinic;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  GRANT ALL ON SEQUENCES TO dental_clinic;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
  GRANT ALL ON FUNCTIONS TO dental_clinic;

-- ── 4. 校验：所有表四项权限都应为 t ─────────────────────────────
SELECT tablename AS 表名,
       tableowner AS 所有者,
       has_table_privilege('dental_clinic', format('public.%I', tablename), 'SELECT') AS 查,
       has_table_privilege('dental_clinic', format('public.%I', tablename), 'INSERT') AS 增,
       has_table_privilege('dental_clinic', format('public.%I', tablename), 'UPDATE') AS 改,
       has_table_privilege('dental_clinic', format('public.%I', tablename), 'DELETE') AS 删
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY 查, 增, 改, 删, tablename;
