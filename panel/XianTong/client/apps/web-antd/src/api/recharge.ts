import { requestClient } from '#/api/request';

// ============================================================
// 充值档位（配置化 CRUD）
// ============================================================

export interface RechargeTier {
  id: number;
  tierName: string;
  minAmount: number;
  bonusRate: number;
  /** 展示用赠送额 = minAmount × bonusRate */
  bonusAmount: number;
  status: number;
  sortOrder: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface RechargeTierParams {
  tierName: string;
  minAmount: number;
  bonusRate: number;
  status?: number;
  sortOrder?: number;
}

// 充值档位列表（全量，按 sortOrder 排序）
export async function getRechargeTiers() {
  return requestClient.get<RechargeTier[]>('/recharge-tiers');
}

// 新建充值档位
export async function createRechargeTier(data: RechargeTierParams) {
  return requestClient.post('/recharge-tiers', data);
}

// 编辑充值档位
export async function updateRechargeTier(
  id: number,
  data: RechargeTierParams,
) {
  return requestClient.put(`/recharge-tiers/${id}`, data);
}

// 删除充值档位
export async function deleteRechargeTier(id: number) {
  return requestClient.delete(`/recharge-tiers/${id}`);
}

// ============================================================
// 充值流水
// ============================================================

export interface RechargeRecord {
  id: number;
  userId: number;
  userName?: string;
  userPhone?: string;
  clinicId: number;
  clinicName: string;
  amount: number;
  bonusAmount: number;
  paymentMethod: string;
  wechatOrderId?: string;
  outTradeNo?: string;
  /** pending/success/failed/refunded */
  status: string;
  paidAt?: string;
  createdAt: string;
}

export interface RechargeRecordQuery {
  keyword?: string;
  clinicId?: number;
  status?: string;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  pageSize?: number;
}

// 充值流水分页查询
export async function getRechargeRecords(params?: RechargeRecordQuery) {
  return requestClient.get<{
    list: RechargeRecord[];
    total: number;
    page: number;
    pageSize: number;
  }>('/recharge-records', { params });
}
