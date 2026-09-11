import { requestClient } from '#/api/request';

// ============================================================
// 推荐关系与奖励记录
// ============================================================
export async function getReferralRelations(params: any) {
  return requestClient.get('/referrals/relations', { params });
}

export async function getReferralRewards(params: any) {
  return requestClient.get('/referrals/rewards', { params });
}

// ============================================================
// 推荐活动统计
// ============================================================
export async function getReferralStatistics() {
  return requestClient.get('/referrals/statistics');
}

// ============================================================
// 推荐奖励发放
// ============================================================
export async function grantReferralReward(data: any) {
  return requestClient.post('/referrals/rewards/grant', data);
}
