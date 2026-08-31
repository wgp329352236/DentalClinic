// API 请求封装（支持模拟数据模式）
import type { ApiResponse } from '../types'
import { mockApiService } from './mockData'

// 后端地址 - 根据环境切换
const BASE_URL = 'http://localhost:8080/api/v1' // 开发环境
// const BASE_URL = 'https://your-domain.com/api/v1' // 生产环境

// ==================== 模拟数据开关 ====================
// true: 使用模拟数据（离线开发/演示）
// false: 连接真实后端 API
const USE_MOCK = false

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  header?: Record<string, string>
  needAuth?: boolean
}

/**
 * 通用请求方法
 */
export function request<T = any>(options: RequestOptions): Promise<ApiResponse<T>> {
  // 模拟数据模式
  if (USE_MOCK) {
    console.log('[Mock]', options.method || 'GET', options.url)
    return mockRequest(options)
  }

  // 真实 API 模式
  const needAuth = options.needAuth !== false

  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync('token') || ''

    if (needAuth && !token) {
      wx.redirectTo({ url: '/pages/index/index' })
      reject(new Error('未登录'))
      return
    }

    wx.request({
      url: `${BASE_URL}${options.url}`,
      method: options.method || 'GET',
      data: options.data,
      header: {
        'Content-Type': 'application/json',
        ...(needAuth && token ? { 'Authorization': `Bearer ${token}` } : {}),
        ...options.header
      },
      success: (res) => {
        const data = res.data as ApiResponse<T>

        if (res.statusCode === 401) {
          wx.removeStorageSync('token')
          wx.removeStorageSync('userInfo')
          wx.redirectTo({ url: '/pages/index/index' })
          reject(new Error('登录已过期，请重新登录'))
          return
        }

        if (res.statusCode === 200 && data) {
          resolve(data)
        } else {
          reject(new Error(data?.message || `请求失败: ${res.statusCode}`))
        }
      },
      fail: (err) => {
        console.error('[Request Failed]', err)
        reject(err)
      }
    })
  })
}

/**
 * GET 请求
 */
export function get<T = any>(url: string, params?: Record<string, any>, needAuth = true): Promise<ApiResponse<T>> {
  const queryString = params ? '?' + Object.entries(params)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&') : ''
  return request<T>({ url: url + queryString, method: 'GET', needAuth })
}

/**
 * POST 请求
 */
export function post<T = any>(url: string, data?: any, needAuth = true): Promise<ApiResponse<T>> {
  return request<T>({ url, method: 'POST', data, needAuth })
}

/**
 * PUT 请求
 */
export function put<T = any>(url: string, data?: any, needAuth = true): Promise<ApiResponse<T>> {
  return request<T>({ url, method: 'PUT', data, needAuth })
}

/**
 * DELETE 请求
 */
export function del<T = any>(url: string, needAuth = true): Promise<ApiResponse<T>> {
  return request<T>({ url, method: 'DELETE', needAuth })
}

/**
 * 文件上传
 */
export function uploadFile<T = any>(url: string, filePath: string, name = 'file'): Promise<ApiResponse<T>> {
  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync('token') || ''

    wx.uploadFile({
      url: `${BASE_URL}${url}`,
      filePath,
      name,
      header: {
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const data = JSON.parse(res.data) as ApiResponse<T>
        if (res.statusCode === 200 && data) {
          resolve(data)
        } else {
          reject(new Error(data?.message || '上传失败'))
        }
      },
      fail: reject
    })
  })
}

// ==================== 模拟数据服务 ====================

async function mockRequest<T = any>(options: RequestOptions): Promise<ApiResponse<T>> {
  // 模拟网络延迟
  await new Promise(resolve => setTimeout(resolve, 300))

  const { url, method, data } = options
  const path = url.split('?')[0]

  try {
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
