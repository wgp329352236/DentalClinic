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

// 同意用户提交的 2 小时内取消申请
export async function approveCancelRequest(id: number) {
  return requestClient.put(`/appointments/${id}/cancel-request/approve`);
}

// 驳回取消申请：预约保持原状态
export async function rejectCancelRequest(id: number) {
  return requestClient.put(`/appointments/${id}/cancel-request/reject`);
}

// 确认预约（仅待确认状态可确认）
export async function confirmAppointment(id: number) {
  return requestClient.put(`/appointments/${id}/confirm`);
}

// 拒绝预约（仅待确认状态可拒绝，需填写原因）
export async function rejectAppointment(id: number, reason: string) {
  return requestClient.put(`/appointments/${id}/reject`, { reason });
}

// 更新病历（诊断 / 治疗方案）
export async function updateMedicalRecord(id: number, data: any) {
  return requestClient.put(`/appointments/${id}/medical-record`, data);
}

// 代客预约（前台/客服替客户下单，后端默认 confirmed，并校验门店权限/排班/容量）
export async function createAppointmentByAdmin(data: {
  userId: number;
  clinicId: number;
  doctorId: number;
  departmentId?: number;
  /** yyyy-MM-dd */
  appointmentDate: string;
  /** HH:mm */
  startTime: string;
  remark?: string;
}) {
  return requestClient.post('/appointments', data);
}
