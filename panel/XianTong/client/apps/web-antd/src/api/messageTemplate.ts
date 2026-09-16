import { requestClient } from '#/api/request';

// ============================================================
// 微信订阅消息模板（配置化 CRUD）
// ============================================================

export interface MessageTemplate {
  id: number;
  /** 业务标识，唯一；内置模板不可修改 */
  templateKey: string;
  /** 微信公众平台模板ID */
  templateId: string;
  title: string;
  content: string;
  triggerEvent: string;
  status: number;
  /** 内置模板只可编辑不可删除、templateKey 不可改 */
  builtIn: boolean;
  updatedAt?: string;
}

export interface MessageTemplateParams {
  templateKey: string;
  templateId?: string;
  title: string;
  content: string;
  triggerEvent?: string;
  status?: number;
}

// 模板列表（全量）
export async function getMessageTemplates() {
  return requestClient.get<MessageTemplate[]>('/message-templates');
}

// 新建模板
export async function createMessageTemplate(data: MessageTemplateParams) {
  return requestClient.post('/message-templates', data);
}

// 编辑模板（内置模板的 templateKey 后端会拒绝变更）
export async function updateMessageTemplate(
  id: number,
  data: MessageTemplateParams,
) {
  return requestClient.put(`/message-templates/${id}`, data);
}

// 删除模板（内置模板后端拒绝删除）
export async function deleteMessageTemplate(id: number) {
  return requestClient.delete(`/message-templates/${id}`);
}
