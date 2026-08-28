import { requestClient } from '#/api/request';

export async function getDashboardSummary() {
  return requestClient.get('/dashboard/summary');
}

export async function getDashboardGames() {
  return requestClient.get('/dashboard/games');
}

export async function getDashboardTrends() {
  return requestClient.get('/dashboard/trends');
}

export async function getDashboardChannels(gameCode: string) {
  return requestClient.get(`/dashboard/channels/${gameCode}`);
}

export async function getPushRecords(params: any) {
  return requestClient.get('/dashboard/push-records', { params });
}
