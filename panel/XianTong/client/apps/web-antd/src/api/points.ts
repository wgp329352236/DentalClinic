import { requestClient } from '#/api/request';

// ============================================================
// 积分规则
// ============================================================

// 积分规则列表
export async function getPointsRules() {
  return requestClient.get('/points/rules');
}

// 创建积分规则
export async function createPointsRule(data: any) {
  return requestClient.post('/points/rules', data);
}

// 更新积分规则
export async function updatePointsRule(id: number, data: any) {
  return requestClient.put(`/points/rules/${id}`, data);
}

// ============================================================
// 积分记录
// ============================================================

// 积分记录（分页）
export async function getPointsRecords(params?: any) {
  return requestClient.get('/points/records', { params });
}
