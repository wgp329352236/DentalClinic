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

/**
 * 获取推广二维码。
 *
 * mode=miniprogram_code：微信小程序码（图片 base64，扫码直达小程序）；
 * mode=plain_text：未配置微信凭证，payload 为 promo=<templateId>，前端 qrcode 生成普通二维码。
 */
export async function getCouponPromoQr(id: number) {
  return requestClient.get<{
    mode: 'miniprogram_code' | 'plain_text';
    imageBase64?: string;
    payload?: string;
    name: string;
    faceValue: number;
    hint: string;
  }>(`/coupons/${id}/promo-qr`);
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
