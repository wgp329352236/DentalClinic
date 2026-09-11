import { requestClient } from '#/api/request';

// ============================================================
// 预约管理
// ============================================================

// 预约列表（传 page/pageSize 则分页，不传返回全量供下拉选择器使用）
export async function getAppointments(params?: any) {
  return requestClient.get('/appointments', { params });
}

// 预约详情（含病历与消费汇总）
export async function getAppointmentDetail(id: number) {
  return requestClient.get(`/appointments/${id}`);
}

// 更新预约状态
export async function updateAppointmentStatus(id: number, data: any) {
  return requestClient.put(`/appointments/${id}/status`, data);
}

// 取消预约
export async function cancelAppointment(id: number, reason?: string) {
  return requestClient.put(`/appointments/${id}/cancel`, { reason });
}

// 更新病历（诊断 / 治疗方案）
export async function updateMedicalRecord(id: number, data: any) {
  return requestClient.put(`/appointments/${id}/medical-record`, data);
}
