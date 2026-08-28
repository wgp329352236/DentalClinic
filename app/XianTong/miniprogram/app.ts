// app.ts
import type { UserInfo } from './types'

interface AppGlobalData {
  userInfo: UserInfo | null
  token: string
  clinics: any[]
}

App<IAppOption>({
  globalData: {
    userInfo: null,
    token: '',
    clinics: []
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

  // 登出
  logout() {
    this.globalData.userInfo = null
    this.globalData.token = ''
    wx.removeStorageSync('token')
    wx.removeStorageSync('userInfo')
  }
})