import { requestClient } from '#/api/request';

// ============================================================
// 推荐关系与奖励记录
// ============================================================
export async function getReferralRelations(params: any) {
  return requestClient.get('/referrals/relations', { params });
}

// 推荐链路树节点（需求 4.4 谁推荐了谁）
export interface ReferralTreeNode {
  userId: number;
  name: string;
  phone?: string;
  recommendedAt?: string;
  converted: boolean;
  children: ReferralTreeNode[];
}

// 推荐链路树：以某客户为根，向下展开其推荐的下级
export async function getReferralTree(userId: number, depth = 3) {
  return requestClient.get<ReferralTreeNode>('/referrals/tree', {
    params: { userId, depth },
  });
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
