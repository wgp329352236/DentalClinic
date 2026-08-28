import { requestClient } from '#/api/request';

export async function getGameList(params: any) {
  return requestClient.get('/games', { params });
}

export async function getGameDetail(code: string) {
  return requestClient.get(`/games/${code}`);
}

export async function createGame(data: any) {
  return requestClient.post('/games', data);
}

export async function updateGameBasic(code: string, data: any) {
  return requestClient.put(`/games/${code}`, data);
}

export async function updatePlatformConfig(code: string, platform: string, config: any) {
  return requestClient.put(`/games/${code}/config/${platform}`, { config });
}

export async function cloneGame(code: string, data: { newCode: string; newName: string }) {
  return requestClient.post(`/games/${code}/clone`, data);
}

export async function deleteGame(code: string) {
  return requestClient.delete(`/games/${code}`);
}

export async function getGamePlatformStatus(code: string) {
  return requestClient.get(`/games/${code}/status`);
}

export async function testFeishuConnection(code: string) {
  return requestClient.post(`/games/${code}/feishu/test`);
}

export async function getFeishuSheetMapping(code: string) {
  return requestClient.get(`/games/${code}/feishu/sheets`);
}
