// pages/index/index.ts
import { wechatLogin, getUserProfile, updateUserProfile, getClinics, getHomeContent, registerWithReferral } from '../../utils/api'
import type { Clinic, UserInfo, Banner, Notice } from '../../types'
import {
  formatBusinessDays,
  formatDistance,
  getBusinessStatus,
  getCurrentLocation,
  getDistanceMeters,
  isValidCoordinate,
  resolveFileUrl
} from '../../utils/util'
import type { UserLocation } from '../../utils/util'

// tabBar 页面清单（与 app.json 的 tabBar.list 一致）。
// wx.navigateTo 跳 tabBar 页会静默失败，必须用 switchTab，
// 此前首页「预约挂号」快捷入口与门店卡片点了都没反应就是这个原因。
const TAB_BAR_PAGES = [
  '/pages/index/index',
  '/pages/appointment/appointment',
  '/pages/wallet/wallet',
  '/pages/profile/profile'
]

// 微信授权返回的用户资料
interface WechatProfile {
  userInfo: {
    nickName: string
    avatarUrl: string
    gender?: number
    country?: string
    province?: string
    city?: string
  }
  encryptedData: string
  iv: string
  signature: string
  rawData: string
}

Page({
  data: {
    userInfo: null as UserInfo | null,
    clinics: [] as Clinic[],
    // 轮播图与公告改由管理端「内容管理」下发（需求 4.11），
    // 不再硬编码：此前 /images/banner1.jpg 等文件根本不存在，首页三张轮播图全是空白块
    banners: [] as (Banner & { image: string })[],
    notices: [] as Notice[],
    // 图标用 emoji 而非图片路径：images/ 目录下没有任何图标文件，
    // 写图片路径等于渲染一个裂图；emoji 无需额外资源即可显示
    quickActions: [
      { id: 'appointment', name: '预约挂号', icon: '📅', path: '/pages/appointment/appointment' },
      { id: 'recharge', name: '充值', icon: '💰', path: '/pages/recharge/recharge' },
      { id: 'points', name: '积分兑换', icon: '🎁', path: '/pages/points/points' },
      { id: 'referral', name: '推荐好友', icon: '👥', path: '/pages/referral/referral' }
    ],
    loading: false,
    loginError: '' // 登录失败原因（页面可见，便于排查）
  },

  onLoad() {
    this.checkLogin()
    this.loadClinics()
    this.loadHomeContent()
  },

  onShow() {
    // 每次显示页面时刷新用户信息
    const app = getApp<IAppOption>()
    if (app.globalData.userInfo) {
      this.setData({
        userInfo: app.globalData.userInfo
      })
    }
    // 内容只在首次拉取失败（列表为空）时补拉：首页是 tabBar 落地页，
    // 每次切换都重新请求会白白增加首屏负担，而轮播图/公告本身低频变更
    if (this.data.banners.length === 0 && this.data.notices.length === 0) {
      this.loadHomeContent()
    }
  },

  // 检查登录状态（未登录时页面展示"立即登录"按钮，
  // 由用户点击触发微信授权；不能用 wx.showModal 回调调用 wx.getUserProfile）
  checkLogin() {
    const app = getApp<IAppOption>()
    if (app.globalData.token) {
      this.setData({
        userInfo: app.globalData.userInfo
      })
      // 已登录则加载用户详细信息
      this.loadUserProfile()
    }
    // 未登录：不弹窗打扰，页面下方"立即登录"按钮由用户主动点击
  },

  // 微信一键登录：open-type="getPhoneNumber" 调起微信手机号授权弹窗
  onGetPhoneNumber(e: any) {
    if (this.data.loading) return

    this.setData({ loginError: '' })

    // 授权成功，携带手机号动态令牌登录
    if (e.detail.errMsg === 'getPhoneNumber:ok' && e.detail.code) {
      this.setData({ loading: true })
      this.doWechatLogin(e.detail.code)
      return
    }

    // 用户取消授权，或手机号能力不可用（个人主体小程序/未开通/基础库不支持），
    // 降级为微信 code 静默登录，保证登录流程不被阻断
    console.warn('[Login] 手机号授权未完成:', e.detail.errMsg)
    this.setData({ loading: true })
    this.doWechatLogin()
  },

  // 微信登录：wx.login 获取 code（静默），配合手机号令牌调用后端换取登录态
  doWechatLogin(phoneCode?: string) {
    wx.login({
      success: async (loginRes) => {
        if (!loginRes.code) {
          this.loginFailed('获取微信登录凭证失败')
          return
        }

        try {
          // 获取当前微信用户资料（新版基础库返回匿名资料且不再弹窗，仅作兜底）
          const profile = await this.getWechatProfile()

          // 调用后端微信登录接口
          const result = await wechatLogin({
            code: loginRes.code,
            ...(phoneCode ? { phoneCode } : {}),
            ...(profile ? {
              userInfo: profile.userInfo,
              encryptedData: profile.encryptedData,
              iv: profile.iv,
              signature: profile.signature,
              rawData: profile.rawData
            } : {})
          })

          if (result.code !== 0 || !result.data) {
            throw new Error(result.message || '登录失败')
          }

          const app = getApp<IAppOption>()

          // 保存 token
          app.setToken(result.data.accessToken)
          wx.setStorageSync('refreshToken', result.data.refreshToken)

          // 微信登录手机号：授权手机号登录后由后端回带（mock 也回带），
          // 编辑资料页的手机号只读、取自此处
          const wechatPhone = result.data.phone || ''
          if (wechatPhone) {
            app.setWechatPhone(wechatPhone)
          }

          // 微信昵称：登录时已随用户资料取到，暂存全局供编辑资料页兜底
          const wechatNickname = (profile?.userInfo?.nickName || '').trim()
          if (wechatNickname) {
            app.setWechatNickname(wechatNickname)
          }

          // 新用户或本地无用户信息时，拉取用户资料
          if (result.data.isNewUser || !app.globalData.userInfo) {
            await this.loadUserProfile()
          } else {
            this.setData({
              userInfo: app.globalData.userInfo,
              loading: false
            })
          }

          // 兜底：后端未返回资料时，用微信资料生成本地用户信息
          if (!this.data.userInfo && profile) {
            const fallback = this.buildFallbackUserInfo(profile)
            app.setUserInfo(fallback)
            this.setData({ userInfo: fallback, loading: false })
          }

          // 登录接口未落库昵称时补报一次；手机号已通过 phoneCode 在登录接口上报
          await this.syncWechatProfile(profile, wechatPhone)

          this.setData({ loginError: '' })
          wx.showToast({
            title: '登录成功',
            icon: 'success'
          })

          // 消费分享链接/扫码进入携带的推荐码；失败静默，不影响登录主流程
          await this.bindPendingReferral()

          // 新用户引导完善头像昵称：微信新版 getUserProfile 仅返回匿名占位，
          // 真实资料需在编辑资料页用「头像昵称填写能力」（chooseAvatar + type=nickname）完成
          if (result.data.isNewUser) {
            wx.navigateTo({ url: '/pages/profile-edit/profile-edit' })
          }
        } catch (error: any) {
          // 完整输出失败原因（含 errno/errMsg/message），便于排查
          console.error('[Login] 登录接口失败详情:', error)
          const detail = error?.message
            || error?.errMsg
            || (error?.statusCode ? `请求失败(${error.statusCode})` : '')
            || '登录失败'
          this.loginFailed(detail)
        }
      },
      fail: (err) => {
        // 打印真实失败原因，便于排查（如 AppID 无效、网络异常等）
        console.error('[Login] wx.login 失败详情:', err)
        this.loginFailed(`微信登录失败：${err?.errMsg || '未知错误'}`)
      }
    })
  },

  /**
   * 绑定暂存的推荐码（登录成功后调用）。
   *
   * 推荐码来自好友分享链接的 ?ref=xxx 或推荐小程序码 scene，由 app.onLaunch/onShow
   * 捕获暂存。无论绑定成功与否都清空暂存：后端对无效码/自己的码/已被推荐都会拒绝，
   * 保留重试没有意义；整个过程静默，绝不影响登录主流程。
   */
  async bindPendingReferral() {
    const app = getApp<IAppOption>()
    const code = app?.globalData?.pendingReferralCode
    if (!code) return

    try {
      await registerWithReferral(code)
      console.log('[Referral] 推荐关系绑定成功:', code)
    } catch (error) {
      console.warn('[Referral] 推荐关系绑定失败（已静默忽略）:', error)
    } finally {
      app?.setPendingReferralCode(null)
    }
  },

  // 获取当前微信用户资料（未授权或基础库不支持时返回 null）
  getWechatProfile(): Promise<WechatProfile | null> {
    return new Promise((resolve) => {
      if (typeof wx.getUserProfile !== 'function') {
        resolve(null)
        return
      }
      wx.getUserProfile({
        desc: '用于完善您的会员资料',
        success: (res: any) => {
          resolve({
            userInfo: res.userInfo,
            encryptedData: res.encryptedData,
            iv: res.iv,
            signature: res.signature,
            rawData: res.rawData
          })
        },
        fail: () => resolve(null)
      })
    })
  },

  // 用微信资料兜底生成用户信息（后端未返回资料时使用）
  buildFallbackUserInfo(profile: WechatProfile): UserInfo {
    return {
      id: 0,
      name: profile.userInfo.nickName || '微信用户',
      nickname: profile.userInfo.nickName || '微信用户',
      phone: getApp<IAppOption>()?.globalData?.wechatPhone || '',
      gender: profile.userInfo.gender === 2 ? 'female' : 'male',
      age: 0,
      avatar: profile.userInfo.avatarUrl || '',
      clinicId: 0,
      memberLevel: {
        // 微信资料兜底路径拿不到会员信息：等级按最低的 Lv1 展示，
        // nextLevelCost 置 null 让进度条整块隐藏，而不是编一个「还需 3000」的假目标
        level: 1,
        name: '普通会员',
        discount: 1.0,
        benefits: [],
        nextLevelCost: null
      },
      registerSource: 'self',
      totalSpent: 0
    }
  },

  /**
   * 登录成功后把微信昵称补报到服务端。
   * 服务端已有昵称时视为用户自定义，直接沿用，不反向覆盖。
   */
  async syncWechatProfile(profile: WechatProfile | null, wechatPhone: string) {
    const nickname = (profile?.userInfo?.nickName || '').trim()
    // 新版微信 getUserProfile 不再弹窗，返回匿名昵称「微信用户」与默认灰头像，
    // 上报它没有意义（反而会把占位符写进库）；真实昵称改由编辑资料页的填写能力上报
    if (!nickname || nickname === '微信用户') return

    const app = getApp<IAppOption>()
    const current = this.data.userInfo
    if (current?.nickname && current.nickname !== nickname) return

    try {
      await updateUserProfile({ nickname })
      const base = current || (profile ? this.buildFallbackUserInfo(profile) : null)
      if (!base) return
      const updated: UserInfo = {
        ...base,
        nickname,
        name: nickname,
        ...(wechatPhone && !base.phone ? { phone: wechatPhone } : {})
      }
      app.setUserInfo(updated)
      this.setData({ userInfo: updated, loading: false })
    } catch (error) {
      // 上报失败不阻断登录；下次登录或编辑资料时仍可重试
      console.warn('[Login] 微信昵称上报失败（不影响登录）:', error)
    }
  },

  // 登录失败处理：toast 提示 + 页面可见的错误原因（便于排查）
  loginFailed(message: string) {
    console.error('登录失败:', message)
    this.setData({
      loading: false,
      loginError: message
    })
    wx.showToast({
      title: message,
      icon: 'none'
    })
  },

  // 加载用户详细信息
  async loadUserProfile() {
    try {
      const result = await getUserProfile()
      if (result.code === 0 && result.data) {
        const app = getApp<IAppOption>()
        
        // 转换为本地 UserInfo 格式
        const d = result.data
        const userInfo: UserInfo = {
          id: d.userId,
          name: d.nickname || d.realName || '',
          nickname: d.nickname || '',
          phone: d.phone || '',
          gender: d.gender === 2 ? 'female' : 'male',
          age: d.age || 0,
          avatar: resolveFileUrl(d.avatar),
          clinicId: d.primaryClinicId || 0,
          // 等级/折扣/权益/累计消费/升级差额全部取自 /profile/me，不再前端写死
          memberLevel: {
            level: d.levelCode,
            name: d.levelName,
            discount: d.discountRate,
            benefits: d.benefits || [],
            nextLevelCost: d.nextLevelCost
          },
          registerSource: 'self',
          totalSpent: d.accumulatedConsumption || 0,
          balance: d.balance || 0,
          bonusBalance: d.bonusBalance || 0
        }

        app.setUserInfo(userInfo)
        this.setData({
          userInfo,
          loading: false
        })
      }
    } catch (error) {
      console.error('加载用户信息失败:', error)
      this.setData({ loading: false })
    }
  },

  // 加载门店列表
  async loadClinics() {
    try {
      const result = await getClinics()
      if (result.code !== 0 || !result.data) return

      // 先用服务端数据渲染，避免定位授权弹窗阻塞列表；定位成功后只补距离。
      this.setData({ clinics: this.mapClinics(result.data, null) })

      const location = await getCurrentLocation()
      if (!location) return
      this.setData({ clinics: this.mapClinics(result.data, location) })
    } catch (error) {
      console.error('加载门店失败:', error)
    }
  },

  /** 把后端门店数据映射为页面展示结构，并按当前定位补充距离。 */
  mapClinics(items: any[], location: UserLocation | null): Clinic[] {
    const clinics: Clinic[] = items.map((item: any) => {
      const latitude = item.latitude == null ? null : Number(item.latitude)
      const longitude = item.longitude == null ? null : Number(item.longitude)
      const businessDays = item.businessDays || '1,2,3,4,5,6,7'

      // 只在坐标有效时构造坐标对象，避免用 hasCoordinates 布尔值配合非空断言绕过类型检查。
      const clinicLocation: UserLocation | null =
        isValidCoordinate(latitude, longitude)
          ? { latitude: latitude as number, longitude: longitude as number }
          : null

      // 后端距离可能是负数、NaN 或单位异常，先收敛为合法的非负有限数。
      const rawFallback = item.distance == null ? undefined : Number(item.distance)
      const fallbackDistance =
        rawFallback != null && Number.isFinite(rawFallback) && rawFallback >= 0
          ? rawFallback
          : undefined

      const computedDistance =
        location && clinicLocation
          ? getDistanceMeters(location, clinicLocation)
          : undefined
      const distance =
        computedDistance != null &&
        Number.isFinite(computedDistance) &&
        computedDistance >= 0
          ? computedDistance
          : fallbackDistance

      return {
        id: item.id,
        name: item.name,
        address: item.address,
        phone: item.phone,
        businessHours:
          item.businessHours ||
          (item.openTime && item.closeTime ? `${item.openTime}-${item.closeTime}` : ''),
        businessDays,
        latitude: clinicLocation ? clinicLocation.latitude : null,
        longitude: clinicLocation ? clinicLocation.longitude : null,
        distance,
        distanceText: distance == null ? '' : formatDistance(distance),
        businessDayText: formatBusinessDays(businessDays),
        openStatusText: getBusinessStatus(
          businessDays,
          item.openTime,
          item.closeTime
        ),
        image: resolveFileUrl(item.image || item.coverImage || '')
      }
    })

    // 「附近门诊」优先按真实距离排序；坐标缺失时保留服务端原顺序。
    clinics.sort((left, right) => {
      const leftDistance = left.distance == null ? Number.POSITIVE_INFINITY : left.distance
      const rightDistance = right.distance == null ? Number.POSITIVE_INFINITY : right.distance
      if (leftDistance === rightDistance) return 0
      return leftDistance - rightDistance
    })
    return clinics
  },

  /**
   * 加载首页内容（轮播图 + 公告），公开接口，未登录也展示。
   *
   * 失败时静默降级为空列表：首页其余部分（门店、登录入口）不该因内容接口挂掉而不可用，
   * wxml 对空列表有 wx:if 兜底，不会留下空白轮播框。
   */
  async loadHomeContent() {
    try {
      const result = await getHomeContent()
      if (result.code === 0 && result.data) {
        const banners = (result.data.banners || []).map((b) => ({
          ...b,
          // 后端存的是 /api/v1/files/xxx.png 相对地址，<image> 需要绝对地址
          image: resolveFileUrl(b.imageUrl)
        }))
        this.setData({ banners, notices: result.data.notices || [] })
      }
    } catch (error) {
      console.warn('[Index] 加载首页内容失败:', error)
    }
  },

  /**
   * 轮播图片加载失败时清空该项的 image。
   *
   * 触发场景：管理端填了一个失效地址，或生产环境未在公众平台配置 downloadFile 合法域名。
   * 清空后 wxml 的 wx:if 会让下层的标题占位块露出，不至于整张轮播图是一片白。
   */
  onBannerImageError(e: any) {
    const index = Number(e.currentTarget.dataset.index)
    const banner = this.data.banners[index]
    if (!banner || !banner.image) return
    console.warn('[Index] 轮播图片加载失败:', banner.imageUrl)
    this.setData({ [`banners[${index}].image`]: '' })
  },

  /**
   * 按内容配置执行跳转。
   *
   * linkType 取值与管理端「内容管理」一致：
   * - none：不跳转
   * - page：小程序内页面，tabBar 页必须走 switchTab
   * - webview：外部网页，需项目里存在承载页；当前没有 webview 页面，
   *   直接打开会跳到不存在的路由，因此明确提示而不是静默失败
   */
  navigateByContent(linkType: string, linkTarget?: string) {
    if (linkType === 'none' || !linkTarget) return
    if (linkType === 'webview') {
      wx.showToast({ title: '该活动暂不支持在小程序内打开', icon: 'none' })
      return
    }
    const path = linkTarget.startsWith('/') ? linkTarget : `/${linkTarget}`
    if (TAB_BAR_PAGES.includes(path.split('?')[0] as string)) {
      wx.switchTab({ url: path })
    } else {
      wx.navigateTo({
        url: path,
        fail: () => wx.showToast({ title: '页面不存在', icon: 'none' })
      })
    }
  },

  // 快捷操作点击
  onQuickAction(e: any) {
    const { path } = e.currentTarget.dataset
    if (!path) return
    if (TAB_BAR_PAGES.includes(path)) {
      wx.switchTab({ url: path })
    } else {
      wx.navigateTo({ url: path })
    }
  },

  // 轮播图点击：一张轮播图对应一个活动，统一进活动详情页
  onBannerTap(e: any) {
    const { activityid, linktype, linktarget } = e.currentTarget.dataset
    const activityId = Number(activityid)
    if (activityId) {
      wx.navigateTo({
        url: `/pages/activity/activity?id=${activityId}`,
        fail: () => wx.showToast({ title: '活动页打开失败', icon: 'none' })
      })
      return
    }
    // 正常不会走到这里：后端 /content/home 已过滤掉未绑定活动的轮播图。
    // 保留旧跳转仅用于兜住缓存里的历史数据，避免点击完全无反应。
    this.navigateByContent(linktype, linktarget)
  },

  // 公告点击
  onNoticeTap(e: any) {
    const { linktype, linktarget } = e.currentTarget.dataset
    this.navigateByContent(linktype, linktarget)
  },

  // 门店卡片点击
  onClinicTap(e: any) {
    const id = Number(e.currentTarget.dataset.id)
    // 预约页是 tabBar 页面，switchTab 带不了 query，门诊 id 走全局态中转
    const app = getApp<IAppOption>()
    app.setPendingClinicId(id)
    wx.switchTab({ url: '/pages/appointment/appointment' })
  }
})
