// app.ts
import type { UserInfo } from './types'

interface AppGlobalData {
  userInfo: UserInfo | null
  token: string
  clinics: any[]
  /**
   * 首页点门店后要带进预约页的门诊 id。
   *
   * 预约页是 tabBar 页面，只能用 wx.switchTab 跳转，而 switchTab 不支持 query 参数，
   * 因此用全局态中转；预约页在 onShow 里读取并立即清空，避免下次进入还残留旧值。
   */
  pendingClinicId: number | null
}

App<IAppOption>({
  globalData: {
    userInfo: null,
    token: '',
    clinics: [],
    pendingClinicId: null
  } as AppGlobalData,

  onLaunch() {
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
  },

  // 设置用户信息
  setUserInfo(userInfo: UserInfo) {
    this.globalData.userInfo = userInfo
    wx.setStorageSync('userInfo', userInfo)
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

  // 登出
  logout() {
    this.globalData.userInfo = null
    this.globalData.token = ''
    wx.removeStorageSync('token')
    wx.removeStorageSync('userInfo')
  }
})