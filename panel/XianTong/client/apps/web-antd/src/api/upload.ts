import { requestClient } from '#/api/request';

/**
 * 后台素材上传（轮播图、门店封面、医生头像等）。
 *
 * 后端为 POST /api/admin/files/upload，需要 content:manage 权限；
 * 返回的 url 形如 /api/v1/files/{fileName}，是公开的读取端点，
 * 因此管理端与小程序端都能直接用这个相对路径渲染图片。
 */

export interface UploadedImage {
  fileName: string;
  size: number;
  url: string;
}

export async function uploadImage(file: Blob | File) {
  return requestClient.upload<UploadedImage>(
    '/files/upload',
    { file },
    {
      // 默认 10s 超时对 5MB 图片偏紧，弱网下容易在传完前就被中断
      timeout: 60_000,
    },
  );
}

export async function deleteUploadedImage(fileName: string) {
  return requestClient.delete(`/files/${fileName}`);
}
