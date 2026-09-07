// pages/points/points.ts
import { getPointsBalance, getPointsRecords, checkin } from '../../utils/api'
import { checkLogin } from '../../utils/auth'
import type { PointsInfo, PointsRecord } from '../../types'

Page({
  data: {
    pointsInfo: null as PointsInfo | null,
    pointsRecords: [] as PointsRecord[],
    loading: false,
    checkedIn: false
  },

  onLoad() {
    if (!checkLogin()) return
    this.loadPoints()
    this.loadPointsRecords()
    this.checkCheckinStatus()
  },

  // 加载积分余额（GET /points/balance）
  async loadPoints() {
    try {
      const result = await getPointsBalance()
      if (result.code === 0 && result.data) {
        this.setData({
          pointsInfo: {
            balance: result.data.totalBalance,
            monthlyEarned: 0, // 后端暂无本月获得/消耗统计
            monthlySpent: 0
          }
        })
      }
    } catch (error) {
      // 未登录/过期等认证错误由 request.ts 统一处理（跳回首页），这里兜底避免未捕获异常
      console.warn('[Points] 加载积分失败:', error)
    }
  },

  // 加载积分明细（GET /points/records，映射后端字段）
  async loadPointsRecords() {
    try {
      const result = await getPointsRecords()
      if (result.code === 0 && result.data) {
        const records: PointsRecord[] = (result.data.list || []).map((r: any) => ({
          id: r.id,
          type: r.changeAmount >= 0 ? 'earn' : 'spend',
          source: r.sourceType,
          points: Math.abs(r.changeAmount),
          relatedInfo: r.description,
          expireAt: r.expiresAt,
          createdAt: r.createdAt
        }))
        this.setData({ pointsRecords: records })
      }
    } catch (error) {
      console.warn('[Points] 加载积分记录失败:', error)
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
      const result = await checkin()
      if (result.code === 0) {
        wx.showToast({ title: `签到成功 +${result.data.pointsEarned}积分`, icon: 'success' })
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
