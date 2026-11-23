// API 请求封装（支持模拟数据模式）
import type { ApiResponse } from '../types'
import { mockApiService } from './mockData'

/**
 * 后端服务源（协议 + 主机 + 端口），不含任何路径。切换环境只改这一处。
 *
 * 单独导出是因为后端返回的文件地址形如 `/api/v1/files/xxx.png`（相对路径），
 * 小程序的 <image src> 不会自动补主机名，必须用本常量拼成绝对地址才能渲染。
 *
 * 注意：
 * 1. 开发者工具调试：若请求报 "url not in domain list"，请在 详情 -> 本地设置 勾选
 *    "不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书"
 * 2. 真机预览：localhost 指向手机自身，需改成电脑局域网 IP（如 http://192.168.1.100:8080）
 * 3. 生产环境：必须使用 HTTPS 域名，并在微信公众平台配置 request 合法域名
 */
export const API_ORIGIN = 'http://localhost:8080' // 开发环境
// export const API_ORIGIN = 'https://your-domain.com' // 生产环境

const BASE_URL = `${API_ORIGIN}/api/v1`

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
      handleAuthExpired('未登录', reject)
      return
    }

    wx.request({
      url: `${BASE_URL}${options.url}`,
      method: options.method || 'GET',
      data: options.data,
      timeout: 10000, // 防止弱网下请求长时间挂起
      header: {
        'Content-Type': 'application/json',
        ...(needAuth && token ? { 'Authorization': `Bearer ${token}` } : {}),
        ...options.header
      },
      success: (res) => {
        const data = res.data as ApiResponse<T>

        // 登录失效：清理登录态并回到首页
        if (res.statusCode === 401) {
          handleAuthExpired('登录已过期，请重新登录', reject)
          return
        }

        // HTTP 层失败
        if (res.statusCode !== 200 || !data) {
          reject(new Error(data?.message || `请求失败: ${res.statusCode}`))
          return
        }

        // 业务层失败（code !== 0）统一 reject，调用方用 try-catch 即可捕获，
        // 与 mock 模式错误语义保持一致
        if (data.code !== 0) {
          // 带上业务码，调用方才能区分「活动已结束(404)」这类正常分支与网络异常
          // 同时打印完整响应，便于定位后端只回「请稍后重试」这类泛化文案的问题
          console.warn('[Request] 业务失败:', options.method || 'GET', options.url, 'code=', data.code, 'message=', data.message, data)
          reject(Object.assign(new Error(data.message || '请求失败'), { code: data.code }))
          return
        }

        resolve(data)
      },
      fail: (err) => {
        console.error('[Request Failed]', err)
        reject(err)
      }
    })
  })
}

/**
 * 处理登录失效：清理本地与全局登录态，并回到首页
 * 注意：首页是 tabBar 页面，必须用 reLaunch（redirectTo 无法跳转 tabBar，会静默失败）
 */
function handleAuthExpired(message: string, reject: (reason?: any) => void) {
  wx.removeStorageSync('token')
  wx.removeStorageSync('refreshToken')
  wx.removeStorageSync('userInfo')

  // 同步清理全局登录态，避免 app.globalData 残留导致页面误判为已登录
  const app = getApp<IAppOption>()
  if (app && app.globalData) {
    app.globalData.token = ''
    app.globalData.userInfo = null
  }

  // 已在首页时不重复跳转
  const pages = getCurrentPages()
  const current = pages[pages.length - 1]
  if (!current || current.route !== 'pages/index/index') {
    wx.reLaunch({ url: '/pages/index/index' })
  }

  reject(new Error(message))
}

/**
 * GET 请求
 */
export function get<T = any>(url: string, params?: Record<string, any>, needAuth = true): Promise<ApiResponse<T>> {
  // 过滤空值参数，避免拼出 "key=undefined" 或多余的 "?"
  const pairs = params
    ? Object.entries(params).filter(([, v]) => v !== undefined && v !== null)
    : []
  const queryString = pairs.length
    ? '?' + pairs.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&')
    : ''
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
      timeout: 30000, // 上传大文件时给予更长超时
      header: {
        // 未登录时不发送空的 Authorization 头
        ...(token ? { 'Authorization': `Bearer ${token}` } : {})
      },
      success: (res) => {
        // 登录失效：清理登录态并回到首页
        if (res.statusCode === 401) {
          handleAuthExpired('登录已过期，请重新登录', reject)
          return
        }

        // 后端可能返回非 JSON（如 HTML 错误页），解析需容错，避免 Promise 永不 settle
        let data: ApiResponse<T> | null = null
        try {
          data = JSON.parse(res.data) as ApiResponse<T>
        } catch (e) {
          reject(new Error(`上传失败: ${res.statusCode}`))
          return
        }

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

/**
 * 解析 query string 为对象（mock 模式下用于还原 GET 参数）
 */
function parseQueryString(query?: string): Record<string, string> {
  const params: Record<string, string> = {}
  if (!query) return params
  query.split('&').forEach(pair => {
    if (!pair) return
    const [key, value] = pair.split('=')
    params[decodeURIComponent(key)] = decodeURIComponent(value || '')
  })
  return params
}

async function mockRequest<T = any>(options: RequestOptions): Promise<ApiResponse<T>> {
  // 模拟网络延迟
  await new Promise(resolve => setTimeout(resolve, 300))

  const { url, method, data } = options
  const [path, queryString] = url.split('?')

  // GET 的参数在 query 里（data 为 undefined），需一并交给 mock，
  // 否则 mock 拿不到筛选/分页参数，列表永远是全部数据
  const query = parseQueryString(queryString)

  // mock 调用失败时直接 reject（async 函数抛出即 reject），
  // 与真实 API 模式"业务错误统一 reject"的语义保持一致
  const result = await mockApiService.call(path, method || 'GET', { ...query, ...(data || {}) })

  // 兼容 mock 处理器直接返回业务错误对象（{ code, message }）的情况：
  // 不把失败响应包装成成功，避免调用方拿到 code !== 0 的"假成功"
  if (result && typeof result === 'object' && typeof result.code === 'number' && result.code !== 0) {
    throw new Error(result.message || '请求失败')
  }

  return {
    code: 0,
    message: 'success',
    data: result as T
  }
}
