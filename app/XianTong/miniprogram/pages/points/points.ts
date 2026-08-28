// pages/points/points.ts
import { get, post } from '../../utils/request'
import type { PointsInfo, PointsRecord } from '../../types'

Page({
  data: {
    pointsInfo: null as PointsInfo | null,
    pointsRecords: [] as PointsRecord[],
    loading: false,
    checkedIn: false
  },

  onLoad() {
    this.loadPoints()
    this.loadPointsRecords()
    this.checkCheckinStatus()
  },

  async loadPoints() {
    const result = await get<PointsInfo>('/points')
    if (result.code === 0 && result.data) {
      this.setData({ pointsInfo: result.data })
    }
  },

  async loadPointsRecords() {
    const result = await get<{ list: PointsRecord[] }>('/points/records')
    if (result.code === 0 && result.data) {
      this.setData({ pointsRecords: result.data.list })
    }
  },

  // 检查今日是否已签到
  checkCheckinStatus() {
    const lastCheckin = wx.getStorageSync('lastCheckinDate')
    const today = new Date().toISOString().split('T')[0]
    this.setData({ checkedIn: lastCheckin === today })
  },

  // 签到
  async handleCheckin() {
    if (this.data.checkedIn) {
      wx.showToast({ title: '今日已签到', icon: 'none' })
      return
    }

    this.setData({ loading: true })
    try {
      const result = await post('/points/checkin')
      if (result.code === 0) {
        wx.showToast({ title: `签到成功 +${result.data.points}积分`, icon: 'success' })
        wx.setStorageSync('lastCheckinDate', new Date().toISOString().split('T')[0])
        this.setData({ checkedIn: true })
        this.loadPoints()
        this.loadPointsRecords()
      }
    } catch (error) {
      console.error('签到失败:', error)
      wx.showToast({ title: '签到失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  }
})
