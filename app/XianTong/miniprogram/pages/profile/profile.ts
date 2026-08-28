// pages/profile/profile.ts
import { get } from '../../utils/request'
import type { UserInfo, Coupon, Message } from '../../types'

Page({
  data: {
    userInfo: null as UserInfo | null,
    coupons: [] as Coupon[],
    messages: [] as Message[],
    unreadCount: 0,
    loading: false
  },

  onLoad() {
    this.loadUserInfo()
    this.loadCoupons()
    this.loadMessages()
  },

  onShow() {
    // 刷新用户信息
    const app = getApp<IAppOption>()
    if (app.globalData.userInfo) {
      this.setData({
        userInfo: app.globalData.userInfo
      })
    }
  },

  async loadUserInfo() {
    const result = await get<UserInfo>('/profile')
    if (result.code === 0 && result.data) {
      this.setData({ userInfo: result.data })
      const app = getApp<IAppOption>()
      app.setUserInfo(result.data)
    }
  },

  async loadCoupons() {
    const result = await get<Coupon[]>('/coupons')
    if (result.code === 0 && result.data) {
      this.setData({ coupons: result.data })
    }
  },

  async loadMessages() {
    const result = await get<{ list: Message[]; unreadCount: number }>('/messages')
    if (result.code === 0 && result.data) {
      this.setData({
        messages: result.data.list,
        unreadCount: result.data.unreadCount
      })
    }
  },

  // 编辑个人信息
  editProfile() {
    wx.showToast({ title: '编辑功能开发中', icon: 'none' })
  },

  // 查看就诊记录
  goToVisitRecords() {
    wx.showToast({ title: '就诊记录开发中', icon: 'none' })
  },

  // 查看消息中心
  goToMessages() {
    wx.showToast({ title: '消息中心开发中', icon: 'none' })
  },

  // 登出
  handleLogout() {
    wx.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          const app = getApp<IAppOption>()
          app.logout()
          wx.reLaunch({
            url: '/pages/index/index'
          })
        }
      }
    })
  }
})
