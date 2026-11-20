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

const WEEKDAY_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

export interface UserLocation {
  latitude: number
  longitude: number
}

/** 获取一次当前定位；用户拒绝授权或设备不可用时静默返回 null。 */
export function getCurrentLocation(): Promise<UserLocation | null> {
  return new Promise((resolve) => {
    wx.getLocation({
      type: 'gcj02',
      success: (res) => {
        // 开发者工具默认定位为 (0,0)，这类无效坐标会算出上万公里距离，直接按未定位处理
        if (!isValidCoordinate(res.latitude, res.longitude)) {
          console.warn('[Location] 定位坐标无效，已隐藏距离信息:', res.latitude, res.longitude)
          resolve(null)
          return
        }
        resolve({ latitude: res.latitude, longitude: res.longitude })
      },
      fail: (error) => {
        console.warn('[Location] 获取定位失败，已隐藏距离信息:', error)
        resolve(null)
      }
    })
  })
}

/** 把后端 `1,2,3` 格式的营业日转换成去重、升序的星期数字。 */
export function parseBusinessDays(raw?: number[] | string | null): number[] {
  const values = Array.isArray(raw)
    ? raw
    : String(raw || '')
        .split(',')
        .map((item) => Number(item.trim()))
  return [...new Set(values)]
    .filter((day) => Number.isInteger(day) && day >= 1 && day <= 7)
    .sort((a, b) => a - b)
}

export function formatBusinessDays(raw?: number[] | string | null): string {
  const days = parseBusinessDays(raw)
  if (days.length === 7) return '每天'
  if (days.length === 0) return '营业日待完善'

  // 连续星期合并为区间，避免「周一、周二、周三…」这类冗长文案
  const parts: string[] = []
  let rangeStart = days[0]
  let rangeEnd = days[0]
  const flush = () => {
    parts.push(
      rangeStart === rangeEnd
        ? WEEKDAY_LABELS[rangeStart - 1]
        : `${WEEKDAY_LABELS[rangeStart - 1]}至${WEEKDAY_LABELS[rangeEnd - 1]}`
    )
  }
  for (const day of days.slice(1)) {
    if (day === rangeEnd + 1) {
      rangeEnd = day
      continue
    }
    flush()
    rangeStart = day
    rangeEnd = day
  }
  flush()
  return parts.join('、')
}

/** 校验经纬度是否是可用的国内坐标；(0,0)、越界与空值都按无效处理。 */
export function isValidCoordinate(
  latitude?: number | null,
  longitude?: number | null
): boolean {
  if (latitude == null || longitude == null) return false
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return false
  if (Math.abs(latitude) < 1e-6 && Math.abs(longitude) < 1e-6) return false
  return latitude >= 3.86 && latitude <= 53.55 && longitude >= 73.66 && longitude <= 135.08
}

/** 根据当前星期与营业时间生成顾客可快速判断的营业状态。 */
export function getBusinessStatus(
  businessDays: number[] | string | null | undefined,
  openTime?: string,
  closeTime?: string,
  now = new Date()
): string {
  const mondayBasedDay = now.getDay() === 0 ? 7 : now.getDay()
  if (!parseBusinessDays(businessDays).includes(mondayBasedDay)) return '今日休息'

  const currentMinutes = now.getHours() * 60 + now.getMinutes()
  const openMinutes = parseTimeToMinutes(openTime)
  const closeMinutes = parseTimeToMinutes(closeTime)
  if (openMinutes == null || closeMinutes == null) return '今日营业'
  if (currentMinutes < openMinutes) return `今日 ${openTime} 开门`
  if (currentMinutes >= closeMinutes) return '今日已结束'
  return '营业中'
}

function parseTimeToMinutes(value?: string): number | null {
  if (!value) return null
  const [hour, minute] = value.split(':').map(Number)
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) return null
  return hour * 60 + minute
}

/** 球面距离（米），输入为 GCJ-02 经纬度。 */
export function getDistanceMeters(from: UserLocation, to: UserLocation): number {
  const earthRadius = 6_371_000
  const toRadians = (degree: number) => (degree * Math.PI) / 180
  const latitudeDelta = toRadians(to.latitude - from.latitude)
  const longitudeDelta = toRadians(to.longitude - from.longitude)
  const a =
    Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(toRadians(from.latitude)) *
      Math.cos(toRadians(to.latitude)) *
      Math.sin(longitudeDelta / 2) ** 2
  return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

export function formatDistance(meters: number): string {
  if (!Number.isFinite(meters) || meters < 0) return ''
  if (meters < 100) return '<100m'
  if (meters < 1000) return `${Math.round(meters / 10) * 10}m`
  const kilometers = meters / 1000
  return `${kilometers < 10 ? kilometers.toFixed(1) : kilometers.toFixed(0)}km`
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
