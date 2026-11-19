import { requestClient } from '#/api/request';

// ============================================================
// 会员等级（配置化 CRUD）
// ============================================================

export interface MemberLevel {
  id: number;
  levelCode: number;
  levelName: string;
  minConsumption: number;
  discountRate: number;
  benefits?: string;
  status: number;
  /** 该等级当前关联客户数（有关联时后端拒绝删除） */
  userCount?: number;
  createdAt?: string;
}

export interface MemberLevelParams {
  levelCode: number;
  levelName: string;
  minConsumption: number;
  discountRate: number;
  benefits?: string;
  status?: number;
}

// 会员等级列表（全量，按 levelCode 排序）
export async function getMemberLevels() {
  return requestClient.get<MemberLevel[]>('/member-levels');
}

// 新建会员等级
export async function createMemberLevel(data: MemberLevelParams) {
  return requestClient.post('/member-levels', data);
}

// 编辑会员等级
export async function updateMemberLevel(id: number, data: MemberLevelParams) {
  return requestClient.put(`/member-levels/${id}`, data);
}

// 删除会员等级（有关联客户时后端返回 409）
export async function deleteMemberLevel(id: number) {
  return requestClient.delete(`/member-levels/${id}`);
}
