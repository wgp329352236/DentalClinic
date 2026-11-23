import { requestClient } from '#/api/request';

// ============================================================
// 医生排班
// ============================================================

// 排班列表（后端要求必须带 doctorId）
export async function getSchedules(params: { doctorId: number }) {
  return requestClient.get('/schedules', { params });
}

// 创建排班
export async function createSchedule(data: any) {
  return requestClient.post('/schedules', data);
}

// 编辑排班
export async function updateSchedule(id: number, data: any) {
  return requestClient.put(`/schedules/${id}`, data);
}

// 删除排班
export async function deleteSchedule(id: number) {
  return requestClient.delete(`/schedules/${id}`);
}
