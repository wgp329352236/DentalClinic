import { requestClient } from '#/api/request';

// ============================================================
// 小程序内容配置（需求 4.11：首页轮播图、公告内容）
// ============================================================

/** 跳转类型：与后端 ContentLinkType 一致 */
export type ContentLinkType = 'none' | 'page' | 'webview';

export interface BannerItem {
  createdBy?: string;
  endAt?: string;
  id: number;
  imageUrl: string;
  linkTarget?: string;
  linkType: ContentLinkType;
  sortOrder: number;
  startAt?: string;
  status: number;
  title: string;
  updatedAt: string;
}

export interface NoticeItem {
  content: string;
  createdBy?: string;
  endAt?: string;
  id: number;
  linkTarget?: string;
  linkType: ContentLinkType;
  sortOrder: number;
  startAt?: string;
  status: number;
  updatedAt: string;
}

/** 轮播图列表。status 不传表示查全部（含停用与未生效） */
export async function getBanners(status?: number) {
  return requestClient.get<BannerItem[]>('/banners', {
    params: status === undefined ? {} : { status },
  });
}

export async function createBanner(data: Partial<BannerItem>) {
  return requestClient.post('/banners', data);
}

export async function updateBanner(id: number, data: Partial<BannerItem>) {
  return requestClient.put(`/banners/${id}`, data);
}

export async function deleteBanner(id: number) {
  return requestClient.delete(`/banners/${id}`);
}

export async function getNotices(status?: number) {
  return requestClient.get<NoticeItem[]>('/notices', {
    params: status === undefined ? {} : { status },
  });
}

export async function createNotice(data: Partial<NoticeItem>) {
  return requestClient.post('/notices', data);
}

export async function updateNotice(id: number, data: Partial<NoticeItem>) {
  return requestClient.put(`/notices/${id}`, data);
}

export async function deleteNotice(id: number) {
  return requestClient.delete(`/notices/${id}`);
}
