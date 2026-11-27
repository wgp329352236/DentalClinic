// app.ts
import type { UserInfo } from './types'
import { preloadSubscribeTemplates, resolveFileUrl } from './utils/util'

/** 待绑定推荐码的本地缓存键：登录完成前可能经历多次冷启动，需持久化等待登录后消费 */
const REFERRAL_STORAGE_KEY = 'pendingReferralCode'

/** 待领取推广券模板的本地缓存键（扫码进入领取 2 张券，场景同推荐码） */
const PROMO_STORAGE_KEY = 'pendingPromoTemplateId'

/** 微信登录时取到的头像 URL（仅本地缓存，用于默认展示） */
const WECHAT_AVATAR_KEY = 'wechatAvatar'

/** 用户首次选择的头像文件路径（已保存到小程序本地文件，之后只读） */
const LOCAL_AVATAR_KEY = 'localAvatarPath'

/** 无任何头像时的兜底图（小程序包内资源，不依赖网络） */
const DEFAULT_AVATAR = '/images/avatar.jpg'

interface AppGlobalData {
  userInfo: UserInfo | null
  token: string
  clinics: any[]
  /**
   * 微信登录时拿到的手机号（open-type="getPhoneNumber" 授权得到）。
   *
   * 编辑资料页的手机号只读、取自此处，不允许用户手动改；
   * 仅在用户重新走微信手机号授权时更新。未授权手机号时为空串。
   */
  wechatPhone: string
  /** 微信登录时拿到的昵称，用于上报服务端并作为昵称默认值 */
  wechatNickname: string
  /**
   * 微信登录时取到的头像 URL（本地缓存，不上传服务器）。
   *
   * 新版微信 getUserProfile 返回的是统一灰色默认头像，仅作兜底展示；
   * 用户首次在编辑资料页选择头像后，优先使用 localAvatarPath。
   */
  wechatAvatar: string
  /** 用户首次选择的头像文件路径（保存在小程序本地文件系统，之后只读） */
  localAvatarPath: string
  /**
   * 首页点门店后要带进预约页的门诊 id。
   *
   * 预约页是 tabBar 页面，只能用 wx.switchTab 跳转，而 switchTab 不支持 query 参数，
   * 因此用全局态中转；预约页在 onShow 里读取并立即清空，避免下次进入还残留旧值。
   */
  pendingClinicId: number | null
  /**
   * 待改约的预约 id。
   *
   * 预约记录页不是 tabBar 页面，而预约页是 tabBar 页面；wx.switchTab 不支持 query，
   * 因此从记录页点「改约」时通过全局态中转，预约页 onShow 读取后立即清空。
   */
  pendingRescheduleId: number | null
  /**
   * 待绑定的推荐码。
   *
   * 来源有两个：
   * 1. 好友分享卡片 path 上的 ?ref=xxx（onLaunch/onShow 的 query.ref）；
   * 2. 扫推荐小程序码进入，参数在 scene 中（形如 "ref=xxx"，需 decodeURIComponent）。
   * 捕获后暂存到 globalData + storage，登录成功后由首页消费并清空。
   */
  pendingReferralCode: string | null
  /**
   * 待领取的推广优惠券模板 id（扫码进入领 2 张券）。
   *
   * 来源与推荐码一致：query.promo=xxx 或 scene 中 "promo=xxx"。
   * 由优惠券页在 onShow 消费：调推广领券接口后清空（含 storage）。
   */
  pendingPromoTemplateId: number | null
  /** 优惠券页「去使用」后，经 tabBar 中转给预约页的券码 */
  pendingCouponCode: string | null
}

/**
 * 从启动参数 query 中提取指定 key 的值。
 */
function extractQueryParam(options: any, key: string): string {
  const value = options?.query?.[key]
  return typeof value === 'string' && value.trim() ? value.trim() : ''
}

/**
 * 从小程序启动/切前台参数中提取「键值对」型参数（ref / promo）。
 *
 * 优先取 query.<key>（分享链接）；没有再解析 scene（小程序码扫码进入，
 * 微信传入的是 encodeURIComponent 后的 k=v&k=v 串）。提取失败返回空串。
 */
function extractSceneParam(options: any, key: string): string {
  const queryValue = extractQueryParam(options, key)
  if (queryValue) return queryValue

  const scene = options?.scene
  if (typeof scene === 'string' && scene.trim()) {
    try {
      const decoded = decodeURIComponent(scene.trim())
      const pair = decoded
        .split('&')
        .map(part => part.split('='))
        .find(([k]) => k === key)
      const value = pair && pair.length > 1 ? pair.slice(1).join('=') : ''
      if (value) return value.trim()
    } catch (error) {
      console.warn(`[App] scene 参数解析失败(${key}):`, scene, error)
    }
  }

  return ''
}

/** 提取推荐码（兼容旧调用方与旧参数名） */
function extractReferralCode(options: any): string {
  return extractSceneParam(options, 'ref')
}

App<IAppOption>({
  globalData: {
    userInfo: null,
    token: '',
    clinics: [],
    wechatPhone: '',
    wechatNickname: '',
    wechatAvatar: '',
    localAvatarPath: '',
    pendingClinicId: null,
    pendingRescheduleId: null,
    pendingReferralCode: null,
    pendingPromoTemplateId: null,
    pendingCouponCode: null
  } as AppGlobalData,

  onLaunch(options) {
    // 检查本地登录状态
    const token = wx.getStorageSync('token')
    const userInfo = wx.getStorageSync('userInfo')

    if (token && userInfo) {
      this.globalData.token = token
      this.globalData.userInfo = userInfo
      console.log('[App] 已登录用户:', userInfo.name)
    } else {
      console.log('[App] 未登录，需要引导登录')
    }

    // 头像只落本地：微信头像 URL + 用户首次选择的本地文件路径。
    // 本地文件可能被系统清理，失效时丢弃缓存并回退微信头像/默认图。
    this.globalData.wechatAvatar = wx.getStorageSync(WECHAT_AVATAR_KEY) || ''
    const localAvatar = wx.getStorageSync(LOCAL_AVATAR_KEY) || ''
    if (localAvatar && this.isLocalAvatarUsable(localAvatar)) {
      this.globalData.localAvatarPath = localAvatar
    } else if (localAvatar) {
      wx.removeStorageSync(LOCAL_AVATAR_KEY)
    }

    // 推荐码：本次启动参数优先；未携带则沿用历史暂存（用户可能先扫码、后完成登录）
    const code = extractReferralCode(options) || wx.getStorageSync(REFERRAL_STORAGE_KEY) || ''
    if (code) {
      this.setPendingReferralCode(code)
    }

    // 推广领券：扫码/分享携带 promo=<templateId> 时暂存，待优惠券页消费
    const promo = extractSceneParam(options, 'promo') || wx.getStorageSync(PROMO_STORAGE_KEY) || ''
    if (promo) {
      this.setPendingPromo(promo)
    }

    // 预加载订阅消息模板 ID（业务点击事件里同步调 wx.requestSubscribeMessage 需要）
    preloadSubscribeTemplates().catch(() => {})
  },

  onShow(options) {
    // 小程序在后台时被新的分享卡片/小程序码唤起：以最新参数覆盖暂存推荐码
    const code = extractReferralCode(options)
    if (code) {
      this.setPendingReferralCode(code)
    }

    // 推广领券同推荐码：后台被唤起时以最新 promo 覆盖
    const promo = extractSceneParam(options, 'promo')
    if (promo) {
      this.setPendingPromo(promo)
    }
  },

  // 设置用户信息
  setUserInfo(userInfo: UserInfo) {
    this.globalData.userInfo = userInfo
    wx.setStorageSync('userInfo', userInfo)
  },

  // 设置微信登录手机号（更新全局态）
  setWechatPhone(phone: string) {
    this.globalData.wechatPhone = phone || ''
  },

  // 设置微信登录昵称（更新全局态）
  setWechatNickname(nickname: string) {
    this.globalData.wechatNickname = nickname || ''
  },

  /**
   * 缓存微信登录时取到的头像 URL（只存本地，不上传服务器）。
   * 传空串时清除缓存。
   */
  setWechatAvatar(avatar: string) {
    this.globalData.wechatAvatar = avatar || ''
    if (avatar) {
      wx.setStorageSync(WECHAT_AVATAR_KEY, avatar)
    } else {
      wx.removeStorageSync(WECHAT_AVATAR_KEY)
    }
  },

  /**
   * 记录用户首次选择的头像文件路径（已保存到小程序本地文件，之后只读）。
   * 传空串时清除缓存。
   */
  setLocalAvatarPath(path: string) {
    this.globalData.localAvatarPath = path || ''
    if (path) {
      wx.setStorageSync(LOCAL_AVATAR_KEY, path)
    } else {
      wx.removeStorageSync(LOCAL_AVATAR_KEY)
    }
  },

  /** 本地头像文件是否仍可访问（系统可能清理临时文件） */
  isLocalAvatarUsable(path: string): boolean {
    try {
      wx.getFileSystemManager().accessSync(path)
      return true
    } catch (error) {
      return false
    }
  },

  /**
   * 头像展示地址，优先级：本地选择的头像 → 微信登录头像 → 服务端旧头像 → 包内默认图。
   * 头像不再上传服务器，服务端头像仅用于兼容历史数据。
   */
  getAvatarUrl(serverAvatar = ''): string {
    if (this.globalData.localAvatarPath) return this.globalData.localAvatarPath
    if (this.globalData.wechatAvatar) return this.globalData.wechatAvatar
    return resolveFileUrl(serverAvatar) || DEFAULT_AVATAR
  },

  // 设置 Token
  setToken(token: string) {
    this.globalData.token = token
    wx.setStorageSync('token', token)
  },

  // 设置待选门诊（首页 → 预约页）
  setPendingClinicId(clinicId: number | null) {
    this.globalData.pendingClinicId = clinicId
  },

  // 设置待改约预约（预约记录页 → 预约页）
  setPendingRescheduleId(appointmentId: number | null) {
    this.globalData.pendingRescheduleId = appointmentId
  },

  /**
   * 暂存/清除待绑定推荐码。
   * 传 null 或空串时同时清掉 globalData 与 storage（登录后消费完成时调用）。
   */
  setPendingReferralCode(code: string | null) {
    const value = code && code.trim() ? code.trim() : null
    this.globalData.pendingReferralCode = value
    if (value) {
      wx.setStorageSync(REFERRAL_STORAGE_KEY, value)
    } else {
      wx.removeStorageSync(REFERRAL_STORAGE_KEY)
    }
  },

  /**
   * 暂存/清除待领取的推广券模板 id。
   * 传 null/空串/非法数字时同时清掉 globalData 与 storage（优惠券页消费完成时调用）。
   */
  setPendingPromo(promo: string | null) {
    const templateId = promo ? Number(promo) : NaN
    const value = Number.isFinite(templateId) && templateId > 0 ? templateId : null
    this.globalData.pendingPromoTemplateId = value
    if (value) {
      wx.setStorageSync(PROMO_STORAGE_KEY, String(value))
    } else {
      wx.removeStorageSync(PROMO_STORAGE_KEY)
    }
  },

  // 设置待使用的优惠券码（优惠券页 → 预约页）
  setPendingCouponCode(couponCode: string | null) {
    const value = couponCode && couponCode.trim() ? couponCode.trim() : null
    this.globalData.pendingCouponCode = value
  },

  // 登出
  logout() {
    // 头像属于账号隐私：登出时清掉本地缓存并删除已保存的头像文件
    const localAvatar = this.globalData.localAvatarPath
    this.setWechatAvatar('')
    this.setLocalAvatarPath('')
    if (localAvatar) {
      try {
        wx.getFileSystemManager().unlinkSync(localAvatar)
      } catch (error) {
        // 文件已被清理时忽略
      }
    }

    this.globalData.userInfo = null
    this.globalData.token = ''
    wx.removeStorageSync('token')
    wx.removeStorageSync('userInfo')
  }
})
