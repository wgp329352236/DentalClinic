import { API_ORIGIN } from './request'

export const formatTime = (date: Date) => {
  const year = date.getFullYear()
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = date.getHours()
  const minute = date.getMinutes()
  const second = date.getSeconds()

  return (
    [year, month, day].map(formatNumber).join('/') +
    ' ' +
    [hour, minute, second].map(formatNumber).join(':')
  )
}

const formatNumber = (n: number) => {
  const s = n.toString()
  return s[1] ? s : '0' + s
}


/**
 * 把后端返回的相对文件地址补成可直接用于 <image src> 的绝对地址。
 *
 * 已是绝对地址（http/https/协议相对）、base64 或微信本地临时文件（wxfile://、
 * http://tmp/）的原样返回：这些都能被 <image> 直接渲染，再拼主机名反而会拼坏。
 * 小程序包内资源（/images/...）同样原样返回，拼后端域名会变成 404。
 */
export function resolveFileUrl(url?: null | string): string {
  if (!url) return ''
  if (
    url.startsWith('//') ||
    url.startsWith('data:') ||
    url.startsWith('wxfile://') ||
    url.startsWith('http://tmp/') ||
    url.startsWith('/images/') ||
    /^https?:\/\//.test(url)
  ) {
    return url
  }
  return `${API_ORIGIN}${url.startsWith('/') ? '' : '/'}${url}`
}
import { getSubscribeTemplates } from './api'

/**
 * 订阅消息 templateId 缓存（key → id）。
 *
 * 必须在业务点击事件的**同步路径**里调用 wx.requestSubscribeMessage，
 * 而拉取模板 ID 是异步的 HTTP 请求——所以在 App.onLaunch 预加载好缓存，
 * 真正的授权引导只从缓存读，不触发网络请求。
 *
 * 缓存生命周期：随小程序进程，重启小程序会重新拉一次——后端 templateId 很少变，
 * 这种粒度足够；如需强制刷新可手动 resetSubscribeCache()。
 */
let subscribeCache: Map<string, string> | null = null

export function resetSubscribeCache() {
  subscribeCache = null
}

/**
 * 预加载订阅消息模板 ID（App.onLaunch / onShow 时调一次即可）。
 * 即使后端还没配置 templateId（返回空数组）也 OK，后续 requestSubscribeMessage 会静默跳过。
 */
export async function preloadSubscribeTemplates(): Promise<void> {
  if (subscribeCache !== null) return
  try {
    const result = await getSubscribeTemplates()
    const templates = result?.data ?? []
    subscribeCache = new Map(
      templates
        .filter((t) => t.templateId && t.templateId.length > 0)
        .map((t) => [t.templateKey, t.templateId])
    )
  } catch (e) {
    // 网络错误等：留 null，下次业务触发时会再试一次
    console.warn('[Subscribe] 预加载模板失败:', e)
  }
}

/**
 * 引导用户授权一次性订阅消息。
 *
 * ⚠️ 必须在用户点击事件的**同步回调链**里调（如 tap 事件 handler 里直接调），
 * 不能放在 await 之后、setTimeout、Promise.then 里——微信会静默拦截。
 *
 * @param keys 需要授权的模板 key，如 ['appointment_confirmed', 'appointment_remind_evening']
 *             最多 3 个（微信硬性限制），超过会被截断。
 */
export function requestSubscribeMessage(keys: string[]): void {
  if (!subscribeCache) {
    // 缓存没准备好，静默跳过——本次点击不弹授权框，等下次预加载好再说
    console.warn('[Subscribe] 缓存未就绪，跳过授权引导')
    return
  }
  const tmplIds = keys
    .map((k) => subscribeCache!.get(k))
    .filter((id): id is string => !!id && id.length > 0)
    .slice(0, 3)

  if (tmplIds.length === 0) return

  wx.requestSubscribeMessage({
    tmplIds,
    fail: (e) => {
      console.warn('[Subscribe] requestSubscribeMessage 失败:', e)
    }
  })
}
