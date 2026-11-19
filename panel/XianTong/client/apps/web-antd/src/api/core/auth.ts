import type { HttpResponse } from '@vben/request';

import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    password?: string;
    username?: string;
  }

  /** 登录接口返回值 */
  export interface LoginResult {
    accessToken: string;
    refreshToken?: string;
  }

  /**
   * 刷新 Token 返回值。
   * refreshTokenApi 走 baseRequestClient（responseReturn='raw'），RequestClient.post<T>
   * 运行时返回原始 AxiosResponse，故此处描述的是 AxiosResponse 形状：
   * data 是后端 {code,data,message} 包装（真正的新 token 在 data.data），status 是 HTTP 状态码。
   */
  export interface RefreshTokenResult {
    data: HttpResponse<string>;
    status: number;
  }
}

/**
 * 登录
 */
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>('/auth/login', data);
}

/**
 * 刷新accessToken
 */
export async function refreshTokenApi() {
  // 从 localStorage 获取 refreshToken 并通过 body 发送
  const refreshToken = localStorage.getItem('refreshToken') || '';
  return baseRequestClient.post<AuthApi.RefreshTokenResult>('/auth/refresh', {
    refreshToken,
  });
}

/**
 * 退出登录
 */
export async function logoutApi() {
  const refreshToken = localStorage.getItem('refreshToken') || '';
  return baseRequestClient.post('/auth/logout', {
    refreshToken,
  });
}

/**
 * 获取用户权限码
 */
export async function getAccessCodesApi() {
  return requestClient.get<string[]>('/auth/codes');
}
