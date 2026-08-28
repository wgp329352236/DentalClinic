// pages/index/index.ts
import { get } from '../../utils/request'
import type { Clinic, UserInfo } from '../../types'

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

  // 检查登录状态
  checkLogin() {
    const app = getApp<IAppOption>()
    if (!app.globalData.token) {
      // 未登录，引导登录
      wx.showModal({
        title: '提示',
        content: '请先登录后使用',
        confirmText: '去登录',
        success: (res) => {
          if (res.confirm) {
            this.handleLogin()
          }
        }
      })
    } else {
      this.setData({
        userInfo: app.globalData.userInfo
      })
    }
  },

  // 微信登录
  handleLogin() {
    this.setData({ loading: true })

    wx.login({
      success: async (res) => {
        try {
          // 调用后端登录接口（当前使用模拟数据）
          const result = await get<{ token: string; userInfo: UserInfo; isNewUser: boolean }>('/auth/wechat/login', {
            code: res.code
          })

          if (result.code === 0 && result.data) {
            const app = getApp<IAppOption>()
            app.setToken(result.data.token)
            app.setUserInfo(result.data.userInfo)

            this.setData({
              userInfo: result.data.userInfo,
              loading: false
            })

            wx.showToast({
              title: '登录成功',
              icon: 'success'
            })
          }
        } catch (error) {
          console.error('登录失败:', error)
          this.setData({ loading: false })
          wx.showToast({
            title: '登录失败',
            icon: 'none'
          })
        }
      },
      fail: () => {
        this.setData({ loading: false })
        wx.showToast({
          title: '登录失败',
          icon: 'none'
        })
      }
    })
  },

  // 加载门店列表
  async loadClinics() {
    try {
      const result = await get<Clinic[]>('/clinics')
      if (result.code === 0 && result.data) {
        this.setData({
          clinics: result.data
        })
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
