import { requestClient } from '#/api/request';

// ============================================================
// 医生管理
// ============================================================

// 医生列表
export async function getDoctors() {
  return requestClient.get('/doctors');
}

// 创建医生
export async function createDoctor(data: any) {
  return requestClient.post('/doctors', data);
}

// 更新医生
export async function updateDoctor(id: number, data: any) {
  return requestClient.put(`/doctors/${id}`, data);
}

// 删除医生
export async function deleteDoctor(id: number) {
  return requestClient.delete(`/doctors/${id}`);
}
