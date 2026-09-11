import { requestClient } from '#/api/request';

// ============================================================
// 优惠券模板管理
// ============================================================
export async function getCouponTemplates() {
  return requestClient.get('/coupons/templates');
}

export async function createCouponTemplate(data: any) {
  return requestClient.post('/coupons/templates', data);
}

export async function updateCouponTemplate(id: number, data: any) {
  return requestClient.put(`/coupons/templates/${id}`, data);
}

export async function deleteCouponTemplate(id: number) {
  return requestClient.delete(`/coupons/templates/${id}`);
}

// ============================================================
// 优惠券发放与核销
// ============================================================
export async function grantCoupons(data: any) {
  return requestClient.post('/coupons/grant', data);
}

export async function scanRedeemCoupon(data: any) {
  return requestClient.post('/coupons/scan-redeem', data);
}

// ============================================================
// 优惠券记录与统计
// ============================================================
export async function getCouponRecords(params: any) {
  return requestClient.get('/coupons/records', { params });
}

export async function getCouponStatistics(params?: any) {
  return requestClient.get('/coupons/statistics', { params });
}
