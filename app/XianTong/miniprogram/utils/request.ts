// API 请求封装（当前使用模拟数据）
import type { ApiResponse } from '../types'

const BASE_URL = 'http://localhost:8080/api/v1' // 实际后端地址
const USE_MOCK = true // 切换为 false 可连接真实后端

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  header?: Record<string, string>
}

/**
 * 通用请求方法
 */
export function request<T = any>(options: RequestOptions): Promise<ApiResponse<T>> {
  if (USE_MOCK) {
    console.log('[Mock Request]', options.method || 'GET', options.url, options.data)
    return mockRequest(options)
  }

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${BASE_URL}${options.url}`,
      method: options.method || 'GET',
      data: options.data,
      header: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${wx.getStorageSync('token') || ''}`,
        ...options.header
      },
      success: (res) => {
        if (res.statusCode === 200) {
          resolve(res.data as ApiResponse<T>)
        } else {
          reject(new Error(`请求失败: ${res.statusCode}`))
        }
      },
      fail: (err) => {
        reject(err)
      }
    })
  })
}

/**
 * GET 请求
 */
export function get<T = any>(url: string, params?: Record<string, any>): Promise<ApiResponse<T>> {
  const queryString = params ? '?' + Object.entries(params)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&') : ''
  return request<T>({ url: url + queryString, method: 'GET' })
}

/**
 * POST 请求
 */
export function post<T = any>(url: string, data?: any): Promise<ApiResponse<T>> {
  return request<T>({ url, method: 'POST', data })
}

/**
 * PUT 请求
 */
export function put<T = any>(url: string, data?: any): Promise<ApiResponse<T>> {
  return request<T>({ url, method: 'PUT', data })
}

/**
 * DELETE 请求
 */
export function del<T = any>(url: string): Promise<ApiResponse<T>> {
  return request<T>({ url, method: 'DELETE' })
}

// ==================== 模拟数据服务 ====================

// 导入模拟数据
import { mockApiService } from './mockData'

async function mockRequest<T = any>(options: RequestOptions): Promise<ApiResponse<T>> {
  // 模拟网络延迟
  await new Promise(resolve => setTimeout(resolve, 300))

  const { url, method, data } = options
  const path = url.split('?')[0]

  try {
    // 根据路径和方法调用对应的模拟 API
    const result = await mockApiService.call(path, method || 'GET', data)
    return {
      code: 0,
      message: 'success',
      data: result as T
    }
  } catch (error: any) {
    return {
      code: error.code || 500,
      message: error.message || '服务器错误',
      data: null as T
    }
  }
}
