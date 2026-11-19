import { requestClient } from '#/api/request';

// 系统配置
export async function getConfigs() {
  return requestClient.get('/config');
}

export async function updateConfig(key: string, data: any) {
  return requestClient.put(`/config/${key}`, data);
}

export async function getConcurrencyConfig() {
  return requestClient.get('/config/concurrency');
}

export async function updateConcurrencyConfig(data: any) {
  return requestClient.put('/config/concurrency', data);
}

// 通知规则
export async function getNotificationRules() {
  return requestClient.get('/config/notifications');
}

export async function createNotificationRule(data: any) {
  return requestClient.post('/config/notifications', data);
}

export async function updateNotificationRule(id: number, data: any) {
  return requestClient.put(`/config/notifications/${id}`, data);
}

export async function deleteNotificationRule(id: number) {
  return requestClient.delete(`/config/notifications/${id}`);
}

// 通知历史
export async function getNotificationHistory(params: any) {
  return requestClient.get('/config/notifications/history', { params });
}

// 审计日志
export async function getAuditLogs(params: any) {
  return requestClient.get('/audit/logs', { params });
}

export async function getAuditLogDetail(id: number) {
  return requestClient.get(`/audit/logs/${id}`);
}
