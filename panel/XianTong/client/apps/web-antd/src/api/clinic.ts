import { requestClient } from '#/api/request';

// ============================================================
// 门诊管理
// ============================================================

// 门诊列表
export async function getClinics() {
  return requestClient.get('/clinics');
}

// 创建门诊
export async function createClinic(data: any) {
  return requestClient.post('/clinics', data);
}

// 更新门诊
export async function updateClinic(id: number, data: any) {
  return requestClient.put(`/clinics/${id}`, data);
}

// 删除门诊
export async function deleteClinic(id: number) {
  return requestClient.delete(`/clinics/${id}`);
}

// 门店到店签到码（后端 HMAC 签名，确定性输出，可重复打印）
export async function getClinicCheckinCode(id: number) {
  return requestClient.get<{ clinicId: number; name: string; code: string }>(
    `/clinics/${id}/checkin-code`,
  );
}
