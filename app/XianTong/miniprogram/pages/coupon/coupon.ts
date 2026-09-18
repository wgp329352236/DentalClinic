// pages/coupon/coupon.ts
import { getCoupons } from '../../utils/api'
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
    if (!getApp<IAppOption>().globalData.token) return
    this.loadCoupons()
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
