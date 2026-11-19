// pages/mall/mall.ts
import { exchangePointsGoods, getMyExchangeOrders, getPointsBalance, getPointsGoods } from '../../utils/api'
import { checkLogin } from '../../utils/auth'
import { resolveFileUrl } from '../../utils/util'
import type { ExchangeOrder, PointsGoods } from '../../types'

Page({
  data: {
    activeTab: 'goods', // goods | orders
    pointsBalance: 0,
    goods: [] as (PointsGoods & { coverImageResolved: string })[],
    orders: [] as (ExchangeOrder & { statusText: string })[],
    loading: false,
    exchanging: false
  },

  onLoad() {
    if (!checkLogin()) return
    this.loadBalance()
    this.loadGoods()
  },

  onShow() {
    if (!getApp<IAppOption>().globalData.token) return
    this.loadBalance()
    if (this.data.activeTab === 'orders') this.loadOrders()
  },

  onTabChange(e: any) {
    const tab = e.currentTarget.dataset.tab
    this.setData({ activeTab: tab })
    if (tab === 'goods') this.loadGoods()
    else this.loadOrders()
  },

  async loadBalance() {
    try {
      const result = await getPointsBalance()
      if (result.code === 0 && result.data) {
        this.setData({ pointsBalance: result.data.totalBalance || 0 })
      }
    } catch (error) {
      console.warn('[Mall] 加载积分余额失败:', error)
    }
  },

  async loadGoods() {
    this.setData({ loading: true })
    try {
      const result = await getPointsGoods()
      if (result.code === 0 && Array.isArray(result.data)) {
        const goods = result.data.map((g: any) => ({
          id: g.id,
          name: g.name,
          goodsType: g.goodsType,
          pointsPrice: g.pointsPrice,
          coverImage: g.coverImage,
          coverImageResolved: resolveFileUrl(g.coverImage || ''),
          description: g.description,
          remainingStock: g.remainingStock,
          sortOrder: g.sortOrder
        }))
        this.setData({ goods })
      }
    } catch (error) {
      console.warn('[Mall] 加载商品失败:', error)
    } finally {
      this.setData({ loading: false })
    }
  },

  async loadOrders() {
    this.setData({ loading: true })
    try {
      const result = await getMyExchangeOrders()
      if (result.code === 0 && Array.isArray(result.data)) {
        const orders = result.data.map((o: any) => ({
          id: o.id,
          goodsId: o.goodsId,
          goodsName: o.goodsName,
          pointsCost: o.pointsCost,
          redeemCode: o.redeemCode,
          status: o.status,
          statusText: o.status === 'unused' ? '待核销' : '已核销',
          redeemedAt: o.redeemedAt,
          createdAt: o.createdAt
        }))
        this.setData({ orders })
      }
    } catch (error) {
      console.warn('[Mall] 加载兑换记录失败:', error)
    } finally {
      this.setData({ loading: false })
    }
  },

  // 兑换商品
  onExchange(e: any) {
    const goodsId = e.currentTarget.dataset.id
    const goods = this.data.goods.find(g => g.id === goodsId)
    if (!goods) return
    if (goods.remainingStock === 0) {
      wx.showToast({ title: '已售罄', icon: 'none' })
      return
    }
    if (this.data.pointsBalance < goods.pointsPrice) {
      wx.showToast({ title: '积分不足', icon: 'none' })
      return
    }
    wx.showModal({
      title: '确认兑换',
      content: `确定使用 ${goods.pointsPrice} 积分兑换「${goods.name}」？`,
      success: async (res) => {
        if (!res.confirm) return
        this.setData({ exchanging: true })
        try {
          const result = await exchangePointsGoods(goodsId)
          if (result.code === 0 && result.data) {
            const code = result.data.redeemCode
            wx.showModal({
              title: '兑换成功',
              content: `核销码：${code}\n请凭此码到店使用`,
              showCancel: false,
              confirmText: '我知道了'
            })
            this.loadBalance()
            this.loadGoods()
          } else {
            wx.showToast({ title: result.message || '兑换失败', icon: 'none' })
          }
        } catch (error: any) {
          const msg = error?.message || error?.errMsg || '兑换失败'
          wx.showToast({ title: msg, icon: 'none' })
        } finally {
          this.setData({ exchanging: false })
        }
      }
    })
  }
})
