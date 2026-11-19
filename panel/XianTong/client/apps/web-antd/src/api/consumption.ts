import { requestClient } from '#/api/request';

// ============================================================
// 消费管理
// ============================================================

// 消费流水（分页，支持按客户/门店/日期区间筛选）
export async function getConsumptions(params?: any) {
  return requestClient.get('/consumptions', { params });
}

/**
 * 录入消费并完成结算。
 *
 * 后端在同一事务内处理：会员折扣 → 券核销 → 扣钱包余额 → 发消费积分 →
 * 累计消费触发等级升级 → 推荐人首消/持续奖励 → 站内消息，返回值为结算明细。
 */
export async function createConsumption(data: any) {
  return requestClient.post('/consumptions', data);
}
