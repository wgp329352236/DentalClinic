import { requestClient } from '#/api/request';

// ============================================================
// 用户管理
// ============================================================
export async function getUserList(params: any) {
  return requestClient.get('/users', { params });
}

export async function createUser(data: any) {
  return requestClient.post('/users', data);
}

export async function updateUser(id: number, data: any) {
  return requestClient.put(`/users/${id}`, data);
}

export async function updateUserStatus(id: number, status: number) {
  return requestClient.put(`/users/${id}/status`, { status });
}

export async function resetUserPassword(id: number, newPassword: string) {
  return requestClient.put(`/users/${id}/password`, { newPassword });
}

export async function deleteUser(id: number) {
  return requestClient.delete(`/users/${id}`);
}

// ============================================================
// 角色管理
// ============================================================
export async function getRoleList() {
  return requestClient.get('/roles');
}

export async function createRole(data: any) {
  return requestClient.post('/roles', data);
}

export async function updateRole(id: number, data: any) {
  return requestClient.put(`/roles/${id}`, data);
}

export async function deleteRole(id: number) {
  return requestClient.delete(`/roles/${id}`);
}

export async function getAllPermissions() {
  return requestClient.get('/roles/permissions');
}

// ============================================================
// 菜单管理
// ============================================================
export async function getMenuTree() {
  return requestClient.get('/menus');
}

export async function createMenu(data: any) {
  return requestClient.post('/menus', data);
}

export async function updateMenu(id: number, data: any) {
  return requestClient.put(`/menus/${id}`, data);
}

export async function deleteMenu(id: number) {
  return requestClient.delete(`/menus/${id}`);
}
