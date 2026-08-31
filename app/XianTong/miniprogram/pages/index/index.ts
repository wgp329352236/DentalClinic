// pages/index/index.ts
import { wechatLogin, getUserProfile, getClinics } from '../../utils/api'
import type { Clinic, UserInfo } from '../../types'

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
    banners: [
      { id: 1, image: '/images/banner1.jpg', title: '新用户注册送100元券' },
      { id: 2, image: '/images/banner2.jpg', title: '推荐好友得双倍积分' },
      { id: 3, image: '/images/banner3.jpg', title: '会员充值最高送25%' }
    ],
    quickActions: [
      { id: 'appointment', name: '预约挂号', icon: '/images/icon-appointment.png', path: '/pages/appointment/appointment' },
      { id: 'recharge', name: '充值', icon: '/images/icon-recharge.png', path: '/pages/recharge/recharge' },
      { id: 'points', name: '积分兑换', icon: '/images/icon-points.png', path: '/pages/points/points' },
      { id: 'referral', name: '推荐好友', icon: '/images/icon-referral.png', path: '/pages/referral/referral' }
    ],
    loading: false
  },

  onLoad() {
    this.checkLogin()
    this.loadClinics()
  },

  onShow() {
    // 每次显示页面时刷新用户信息
    const app = getApp<IAppOption>()
    if (app.globalData.userInfo) {
      this.setData({
        userInfo: app.globalData.userInfo
      })
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

    // 用户取消授权
    if (e.detail.errMsg !== 'getPhoneNumber:ok') {
      this.loginFailed('已取消微信授权登录')
      return
    }

    this.setData({ loading: true })
    // e.detail.code 为微信手机号授权返回的动态令牌，可换取用户手机号
    this.doWechatLogin(e.detail.code)
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

          wx.showToast({
            title: '登录成功',
            icon: 'success'
          })
        } catch (error: any) {
          this.loginFailed(error?.message || '登录失败')
        }
      },
      fail: () => {
        this.loginFailed('微信登录失败')
      }
    })
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
      phone: '',
      gender: profile.userInfo.gender === 2 ? 'female' : 'male',
      age: 0,
      avatar: profile.userInfo.avatarUrl || '',
      clinicId: 0,
      memberLevel: {
        level: 1,
        name: '普通会员',
        discount: 1.0,
        benefits: ['预约优先', '生日祝福'],
        nextLevelCost: 3000
      },
      registerSource: 'self',
      totalSpent: 0
    }
  },

  // 登录失败处理
  loginFailed(message: string) {
    console.error('登录失败:', message)
    this.setData({ loading: false })
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
        const userInfo: UserInfo = {
          id: result.data.userId,
          name: result.data.realName || result.data.nickname,
          phone: result.data.phone,
          gender: result.data.gender === 1 ? 'male' : 'female',
          age: result.data.age,
          avatar: result.data.avatar,
          clinicId: result.data.primaryClinicId,
          memberLevel: {
            level: 1, // TODO: 从后端获取会员等级
            name: '普通会员',
            discount: 1.0,
            benefits: ['预约优先', '生日祝福'],
            nextLevelCost: 3000
          },
          registerSource: 'self',
          totalSpent: 0 // TODO: 从后端获取
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
      const result = await getClinics() // 不需要认证
      if (result.code === 0 && result.data) {
        // 转换数据格式
        const clinics: Clinic[] = result.data.map((item: any) => ({
          id: item.id,
          name: item.name,
          address: item.address,
          phone: item.phone,
          businessHours: item.businessHours,
          distance: item.distance,
          image: item.image
        }))
        this.setData({ clinics })
      }
    } catch (error) {
      console.error('加载门店失败:', error)
    }
  },

  // 快捷操作点击
  onQuickAction(e: any) {
    const { path } = e.currentTarget.dataset
    if (path) {
      wx.navigateTo({
        url: path
      })
    }
  },

  // 轮播图点击
  onBannerTap(e: any) {
    const { id } = e.currentTarget.dataset
    console.log('点击轮播图:', id)
    // TODO: 跳转到对应活动页面
  },

  // 门店卡片点击
  onClinicTap(e: any) {
    const { id } = e.currentTarget.dataset
    // 跳转到预约页面并选中该门店
    wx.navigateTo({
      url: `/pages/appointment/appointment?clinicId=${id}`
    })
  }
})
