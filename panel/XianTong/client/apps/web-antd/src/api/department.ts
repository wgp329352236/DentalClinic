import { requestClient } from '#/api/request';

// ============================================================
// 科室管理
// ============================================================

// 科室列表（可按门诊筛选）
export async function getDepartments(params?: any) {
  return requestClient.get('/departments', { params });
}

// 创建科室
export async function createDepartment(data: any) {
  return requestClient.post('/departments', data);
}

// 更新科室
export async function updateDepartment(id: number, data: any) {
  return requestClient.put(`/departments/${id}`, data);
}

// 删除科室
export async function deleteDepartment(id: number) {
  return requestClient.delete(`/departments/${id}`);
}
