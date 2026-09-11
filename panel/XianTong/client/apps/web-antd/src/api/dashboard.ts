import { requestClient } from '#/api/request';

// ============================================================
// 数据看板（需求 4.10）
//
// 全部为只读统计接口，需要 dashboard:view 权限。
// 与其它 api 文件不同，这里显式声明返回类型：看板页要把这些数据直接喂给
// echarts 的 series，字段名写错不会报错只会画出空图，类型约束能把问题提前到编译期。
// ============================================================

/** 实时数据：今日预约数 / 到店数 / 营收 / 新增客户数 */
export interface DashboardSummary {
  todayAppointments: number;
  todayArrivals: number;
  todayRevenue: number;
  todayNewCustomers: number;
}

/** 单个趋势数据点，date 为 yyyy-MM-dd */
export interface TrendDataPoint {
  appointmentCount: number;
  date: string;
  newCustomerCount: number;
  revenue: number;
}

/** 门店对比：本月各门店的预约量 / 营收 / 新客数 */
export interface ClinicComparison {
  appointmentCount: number;
  clinicId: number;
  clinicName: string;
  /** 满意度依赖客户评价功能，该功能尚未实现，后端恒为 null */
  customerSatisfaction: null | number;
  newCustomerCount: number;
  revenue: number;
}

/** 客户等级分布 */
export interface CustomerLevelDistribution {
  count: number;
  levelCode: number;
  levelName: string;
}

/** 客户来源分布，percentage 为 0-100 */
export interface CustomerSourceDistribution {
  count: number;
  percentage: number;
  sourceLabel: string;
  sourceType: string;
}

/** 推荐人 TOP10 单项 */
export interface TopReferrer {
  nickname: null | string;
  referralCount: number;
  userId: number;
}

/** 推荐数据 */
export interface ReferralStats {
  /** 推荐注册转化率，0-100 */
  conversionRate: number;
  /** 推荐带来的月度营收贡献 */
  monthlyRevenue: number;
  /** 推荐带来的月度新客数 */
  monthlyNewCustomers: number;
  topReferrers: TopReferrer[];
}

/** 客户留存，两个比率均为 0-100 */
export interface CustomerRetention {
  /** 流失率：近 90 天无活跃的客户占比 */
  churnRate: number;
  /** 复诊率：完成就诊 ≥2 次的客户占比 */
  repeatVisitRate: number;
}

export async function getDashboardSummary() {
  return requestClient.get<DashboardSummary>('/dashboard/summary');
}

/**
 * 趋势数据。
 *
 * @param days 天数，后端 coerce 到 7-90，非法值回落 30；返回连续的 N 天（无数据的日期值为 0）
 */
export async function getDashboardTrends(days: number = 30) {
  return requestClient.get<TrendDataPoint[]>('/dashboard/trends', {
    params: { days },
  });
}

export async function getDashboardClinicComparison() {
  return requestClient.get<ClinicComparison[]>('/dashboard/clinic-comparison');
}

export async function getDashboardCustomerLevels() {
  return requestClient.get<CustomerLevelDistribution[]>(
    '/dashboard/customer-levels',
  );
}

export async function getDashboardCustomerSources() {
  return requestClient.get<CustomerSourceDistribution[]>(
    '/dashboard/customer-sources',
  );
}

export async function getDashboardReferral() {
  return requestClient.get<ReferralStats>('/dashboard/referral');
}

export async function getDashboardRetention() {
  return requestClient.get<CustomerRetention>('/dashboard/retention');
}
