// pages/profile/profile.ts
import { getUserProfile, getCoupons, getUnreadCount } from '../../utils/api'
import { checkLogin } from '../../utils/auth'
import type { UserInfo, Coupon } from '../../types'

Page({
  data: {
    userInfo: null as UserInfo | null,
    coupons: [] as Coupon[],
    unreadCount: 0,
    loading: false,
    progressPercent: 0 // 会员升级进度条宽度
  },

  onLoad() {
    if (!checkLogin()) return
    this.loadUserInfo()
    this.loadCoupons()
    this.loadUnreadCount()
  },

  onShow() {
    // 刷新用户信息
    const app = getApp<IAppOption>()
    if (app.globalData.userInfo) {
      const userInfo = app.globalData.userInfo
      this.setData({
        userInfo,
        progressPercent: this.calcProgress(userInfo)
      })
    }
  },

  // 计算会员升级进度百分比
  calcProgress(userInfo: UserInfo): number {
    const total = userInfo.totalSpent || 0
    const next = userInfo.memberLevel.nextLevelCost || 0
    if (next <= 0) return 0
    return Math.min(100, Math.round((total / (total + next)) * 100))
  },

  // 加载用户资料（GET /profile/me，映射为本地 UserInfo 结构）
  async loadUserInfo() {
    try {
      const result = await getUserProfile()
      if (result.code === 0 && result.data) {
        const d = result.data
        const userInfo: UserInfo = {
          id: d.userId,
          name: d.realName || d.nickname || '',
          phone: d.phone || '',
          gender: d.gender === 2 ? 'female' : 'male',
          age: d.age || 0,
          avatar: d.avatar || '',
          clinicId: d.primaryClinicId || 0,
          memberLevel: {
            level: 1,
            name: '普通会员',
            discount: 1.0,
            benefits: ['预约优先', '生日祝福'],
            nextLevelCost: 3000
          },
          registerSource: 'self',
          totalSpent: 0 // TODO: 后端暂无累计消费，待补充
        }
        this.setData({ userInfo, progressPercent: this.calcProgress(userInfo) })
        const app = getApp<IAppOption>()
        app.setUserInfo(userInfo)
      }
    } catch (error) {
      // 未登录/过期等认证错误由 request.ts 统一处理（跳回首页），这里兜底避免未捕获异常
      console.warn('[Profile] 加载用户信息失败:', error)
    }
  },

  // 加载优惠券（GET /coupons，映射后端字段）
  async loadCoupons() {
    try {
      const result = await getCoupons()
      if (result.code === 0 && Array.isArray(result.data)) {
        const coupons: Coupon[] = result.data.map((c: any) => ({
          id: c.id,
          name: c.templateName,
          amount: c.faceValue,
          minAmount: c.minConsumption,
          scope: c.couponType,
          validFrom: c.validFrom,
          validTo: c.validTo,
          status: c.status
        }))
        this.setData({ coupons })
      }
    } catch (error) {
      console.warn('[Profile] 加载优惠券失败:', error)
    }
  },

  // 加载未读消息数（GET /messages/unread-count）
  async loadUnreadCount() {
    try {
      const result = await getUnreadCount()
      if (result.code === 0 && result.data) {
        this.setData({ unreadCount: result.data.count })
      }
    } catch (error) {
      console.warn('[Profile] 加载未读消息数失败:', error)
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
