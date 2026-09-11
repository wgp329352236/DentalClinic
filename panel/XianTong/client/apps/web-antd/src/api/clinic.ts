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
