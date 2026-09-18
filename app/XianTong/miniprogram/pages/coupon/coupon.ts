// pages/coupon/coupon.ts
import { claimPromoCoupons, getCoupons } from '../../utils/api'
import { checkLogin } from '../../utils/auth'
import type { Coupon } from '../../types'

const TABS = [
  { key: 'unused', text: '未使用' },
  { key: 'used', text: '已使用' },
  { key: 'expired', text: '已过期' }
]

Page({
  data: {
    tabs: TABS,
    activeTab: 'unused',
    coupons: [] as (Coupon & { statusText: string; statusCls: string })[],
    loading: false
  },

  onLoad() {
    if (!checkLogin()) return
    this.loadCoupons()
  },

  onShow() {
    const app = getApp<IAppOption>()
    if (!app.globalData.token) return
    this.loadCoupons()
    // 扫码进入（scene/query 携带 promo=<templateId>）：自动领取推广优惠券
    if (app.globalData.pendingPromoTemplateId) {
      this.claimPromo(app.globalData.pendingPromoTemplateId)
    }
  },

  /**
   * 推广领券：调用后端一次领 2 张，成功后清空暂存并刷新列表。
   * 幂等在服务端：每人每模板限领一次，重复扫码提示已领取。
   */
  async claimPromo(templateId: number) {
    try {
      const result = await claimPromoCoupons(templateId)
      if (result.code === 0 && result.data && result.data.coupons.length > 0) {
        const coupons = result.data.coupons
        wx.showModal({
          title: '领取成功',
          content: `已领取 ${coupons.length} 张「${coupons[0].couponName}」，请查看下方优惠券列表`,
          showCancel: false,
          confirmText: '知道了'
        })
        // 消费完成：清空暂存，避免下次进入重复弹窗
        getApp<IAppOption>().setPendingPromo(null)
        this.loadCoupons()
      } else {
        // 幂等/业务失败（如已领取过）：提示并清空暂存，防止每次进入都弹
        wx.showToast({ title: result.message || '领取失败', icon: 'none' })
        getApp<IAppOption>().setPendingPromo(null)
      }
    } catch (error: any) {
      const msg = error?.message || error?.errMsg || '领取失败'
      wx.showToast({ title: msg, icon: 'none' })
      // 网络异常不清空暂存，下次进入重试
    }
  },

  onTabChange(e: any) {
    const tab = e.currentTarget.dataset.tab
    this.setData({ activeTab: tab })
    this.loadCoupons()
  },

  async loadCoupons() {
    this.setData({ loading: true })
    try {
      const result = await getCoupons(this.data.activeTab)
      if (result.code === 0 && Array.isArray(result.data)) {
        const coupons = result.data.map((c: any) => {
          const status = (c.status as Coupon['status']) || 'unused'
          const meta = status === 'unused'
            ? { text: '可使用', cls: 'badge-success' }
            : status === 'used'
              ? { text: '已使用', cls: 'badge-default' }
              : { text: '已过期', cls: 'badge-danger' }
          return {
            id: c.id,
            name: c.templateName || c.name,
            amount: c.faceValue || c.amount || 0,
            minAmount: c.minConsumption || c.minAmount || 0,
            scope: c.couponType || c.scope || '',
            validFrom: c.validFrom,
            validTo: c.validTo,
            status,
            statusText: meta.text,
            statusCls: meta.cls
          }
        })
        this.setData({ coupons })
      }
    } catch (error) {
      console.warn('[Coupon] 加载优惠券失败:', error)
    } finally {
      this.setData({ loading: false })
    }
  }
})
