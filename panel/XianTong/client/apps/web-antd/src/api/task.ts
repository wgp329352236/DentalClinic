import { requestClient } from '#/api/request';

// 手动触发任务
export async function triggerTask(type: string, data: any) {
  return requestClient.post(`/tasks/trigger/${type}`, data);
}

// 任务历史
export async function getTaskHistory(params: any) {
  return requestClient.get('/tasks/history', { params });
}

export async function getTaskDetail(taskId: string) {
  return requestClient.get(`/tasks/history/${taskId}`);
}

// 批次进度
export async function getBatchProgress(taskId: string) {
  return requestClient.get(`/tasks/batch/${taskId}/progress`);
}

// 定时任务列表（后端可能未实现此端点，graceful fallback）
export async function getScheduledTasks() {
  try {
    return await requestClient.get('/tasks/scheduled');
  } catch (_) {
    return [];
  }
}
