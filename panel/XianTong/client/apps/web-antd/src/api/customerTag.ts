import { requestClient } from '#/api/request';

// ============================================================
// 客户标签（配置化 CRUD + 客户贴标签）
// ============================================================

export interface CustomerTag {
  id: number;
  tagName: string;
  /** ant 标签色：blue/green/orange/red/purple 等 */
  color: string;
  remark?: string;
  status: number;
}

export interface CustomerTagParams {
  tagName: string;
  color?: string;
  remark?: string;
  status?: number;
}

// 标签列表（仅返回启用标签，供客户列表/打标弹窗使用）
export async function getCustomerTags() {
  return requestClient.get<CustomerTag[]>('/customer-tags');
}

// 新建标签
export async function createCustomerTag(data: CustomerTagParams) {
  return requestClient.post('/customer-tags', data);
}

// 编辑标签
export async function updateCustomerTag(id: number, data: CustomerTagParams) {
  return requestClient.put(`/customer-tags/${id}`, data);
}

// 删除标签（同时解除与客户的关联）
export async function deleteCustomerTag(id: number) {
  return requestClient.delete(`/customer-tags/${id}`);
}

// 查询某客户当前标签（打标弹窗回显）
export async function getUserTags(userId: number) {
  return requestClient.get<CustomerTag[]>(`/customer-tags/users/${userId}`);
}

// 全量覆盖某客户的标签（空数组 = 清空）
export async function setUserTags(userId: number, tagIds: number[]) {
  return requestClient.put(`/customer-tags/users/${userId}`, { tagIds });
}
