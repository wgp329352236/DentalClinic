import { requestClient } from '#/api/request';

// ============================================================
// 客户管理
// ============================================================

// 客户列表（传 page/pageSize 则分页，不传返回全量供下拉选择器使用）
export async function getCustomers(params?: any) {
  return requestClient.get('/customers', { params });
}

// 客户详情（含钱包与会员等级）
export async function getCustomerDetail(id: number) {
  return requestClient.get(`/customers/${id}`);
}

// 更新客户备注
export async function updateCustomerRemark(id: number, remark: string) {
  return requestClient.put(`/customers/${id}/remark`, { remark });
}

// 调整余额（reason 必填，后端写入资金流水；返回调整后的余额与版本号）
export async function adjustWallet(id: number, data: any) {
  return requestClient.put(`/customers/${id}/wallet`, data);
}

// 调整会员等级
export async function adjustLevel(id: number, data: any) {
  return requestClient.put(`/customers/${id}/level`, data);
}

// 调整积分
export async function adjustPoints(id: number, data: any) {
  return requestClient.put(`/customers/${id}/points`, data);
}
