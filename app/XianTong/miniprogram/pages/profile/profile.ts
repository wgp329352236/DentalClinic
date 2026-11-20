// pages/profile/profile.ts
import { exportProfile, deleteAccount, getUserProfile, getCoupons, getUnreadCount } from '../../utils/api'
import { checkLogin } from '../../utils/auth'
import { resolveFileUrl } from '../../utils/util'
import type { UserInfo, Coupon } from '../../types'

Page({
  data: {
    userInfo: null as UserInfo | null,
    coupons: [] as Coupon[],
    unreadCount: 0,
    loading: false,
    progressPercent: 0, // 会员升级进度条宽度
    discountText: '' // 折扣文案，如「9.5 折」；无折扣时为空串，wxml 据此隐藏该行
  },

  onLoad() {
    checkLogin()
  },

  /**
   * 每次显示都重新拉取。
   *
   * 不能只读 app.globalData.userInfo：从「编辑资料」返回时全局态里还是改前的旧值，
   * 用户改完昵称/头像却看不到变化。onShow 在 onLoad 之后必然触发，故 onLoad 只做登录校验。
   */
  onShow() {
    const app = getApp<IAppOption>()
    // 未登录时不再调 checkLogin()：它会 reLaunch 到首页，而 onShow 可能因 reLaunch 再次触发，
    // 形成跳转循环；此处静默返回即可，登录态由 onLoad 的 checkLogin 负责
    if (!app?.globalData?.token) return
    this.loadUserInfo()
    this.loadCoupons()
    this.loadUnreadCount()
  },

  /**
   * 计算会员升级进度百分比。
   *
   * nextLevelCost 为 null（已是最高等级）或 0 时返回 0，由 wxml 的 wx:if 隐藏进度条，
   * 而不是显示一根「还需消费 0 元」的空进度条。
   */
  calcProgress(userInfo: UserInfo): number {
    const total = userInfo.totalSpent || 0
    const next = userInfo.memberLevel.nextLevelCost || 0
    if (next <= 0) return 0
    return Math.min(100, Math.round((total / (total + next)) * 100))
  },

  /**
   * 折扣率 → 中文折扣文案。
   *
   * discountRate 为 1（无折扣）时返回空串：展示「10 折」反而让人以为有优惠。
   * 用 toFixed(2) 去掉浮点尾差后再删多余的 0，9.50 → 9.5、9.00 → 9。
   */
  formatDiscount(discount: number): string {
    if (!discount || discount >= 1) return ''
    const zhe = Number((discount * 10).toFixed(2))
    return `${zhe} 折`
  },

  // 加载用户资料（GET /profile/me，映射为本地 UserInfo 结构）
  async loadUserInfo() {
    try {
      const result = await getUserProfile()
      if (result.code === 0 && result.data) {
        const d = result.data
        const userInfo: UserInfo = {
          id: d.userId,
          name: d.nickname || d.realName || '',
          nickname: d.nickname || '',
          phone: d.phone || '',
          gender: d.gender === 2 ? 'female' : 'male',
          age: d.age || 0,
          // 后端存的是 /api/v1/files/xxx.png 形式的相对地址，<image> 需要绝对地址
          avatar: resolveFileUrl(d.avatar),
          clinicId: d.primaryClinicId || 0,
          // 等级、折扣、权益、累计消费、升级差额全部由 /profile/me 下发，
          // 不再前端硬编码：此前写死 Lv1 导致等级卡片长期展示假数据
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
        this.setData({
          userInfo,
          progressPercent: this.calcProgress(userInfo),
          discountText: this.formatDiscount(userInfo.memberLevel.discount)
        })
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
          couponCode: c.couponCode,
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
    wx.navigateTo({ url: '/pages/profile-edit/profile-edit' })
  },

  // 查看就诊记录
  goToVisitRecords() {
    wx.navigateTo({ url: '/pages/medical-records/medical-records' })
  },

  // 查看消息中心
  goToMessages() {
    wx.navigateTo({ url: '/pages/messages/messages' })
  },

  // 查看优惠券
  goToCoupons() {
    wx.navigateTo({ url: '/pages/coupon/coupon' })
  },

  // 积分商城
  goToMall() {
    wx.navigateTo({ url: '/pages/mall/mall' })
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
  },

  // 导出个人数据（需求 5.2 数据可携带权）：JSON 复制到剪贴板
  async handleExport() {
    try {
      const result = await exportProfile()
      if (result.code === 0 && result.data) {
        const json = JSON.stringify(result.data, null, 2)
        await wx.setClipboardData({ data: json })
        wx.showModal({
          title: '导出成功',
          content: '个人数据（JSON）已复制到剪贴板，可粘贴到备忘录或文件自行保存。',
          showCancel: false,
          confirmText: '知道了'
        })
      } else {
        wx.showToast({ title: result.message || '导出失败', icon: 'none' })
      }
    } catch (error: any) {
      wx.showToast({ title: error?.message || '导出失败', icon: 'none' })
    }
  },

  // 注销账户（需求 5.2 用户数据删除）：二次确认后软删除并退出
  handleDeleteAccount() {
    wx.showModal({
      title: '注销账户',
      content: '注销后账户将无法登录，相关权益（余额/积分/优惠券）不可恢复。确定要注销吗？',
      confirmColor: '#ff4d4f',
      success: async (res) => {
        if (!res.confirm) return
        try {
          const result = await deleteAccount()
          if (result.code === 0) {
            wx.showToast({ title: '账户已注销', icon: 'success' })
            const app = getApp<IAppOption>()
            app.logout()
            setTimeout(() => {
              wx.reLaunch({ url: '/pages/index/index' })
            }, 1200)
          } else {
            wx.showToast({ title: result.message || '注销失败', icon: 'none' })
          }
        } catch (error: any) {
          wx.showToast({ title: error?.message || '注销失败', icon: 'none' })
        }
      }
    })
  }
})
