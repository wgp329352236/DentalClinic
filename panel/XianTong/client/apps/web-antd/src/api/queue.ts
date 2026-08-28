import { requestClient } from '#/api/request';

// 队列状态
export async function getQueueStatus() {
  return requestClient.get('/queues/status');
}

// 死信队列
export async function getDlqList(params: any) {
  return requestClient.get('/queues/dlq', { params });
}

export async function retryDlqItem(id: number) {
  return requestClient.post(`/queues/dlq/${id}/retry`);
}

export async function retryAllDlq() {
  return requestClient.post('/queues/dlq/retry-all');
}

export async function discardDlqItem(id: number) {
  return requestClient.delete(`/queues/dlq/${id}`);
}

export async function clearDlq() {
  return requestClient.delete('/queues/dlq/clear');
}
