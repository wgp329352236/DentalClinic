// pages/wallet/wallet.ts
import { get } from '../../utils/request'
import type { Wallet, RechargeRecord, ConsumptionRecord } from '../../types'

Page({
  data: {
    wallet: null as Wallet | null,
    rechargeRecords: [] as RechargeRecord[],
    consumptionRecords: [] as ConsumptionRecord[],
    activeTab: 'recharge', // recharge | consumption
    loading: false
  },

  onLoad() {
    this.loadWallet()
    this.loadRechargeRecords()
    this.loadConsumptionRecords()
  },

  async loadWallet() {
    const result = await get<Wallet>('/wallet')
    if (result.code === 0 && result.data) {
      this.setData({ wallet: result.data })
    }
  },

  async loadRechargeRecords() {
    const result = await get<{ list: RechargeRecord[] }>('/recharge/records')
    if (result.code === 0 && result.data) {
      this.setData({ rechargeRecords: result.data.list })
    }
  },

  async loadConsumptionRecords() {
    const result = await get<{ list: ConsumptionRecord[] }>('/consumption/records')
    if (result.code === 0 && result.data) {
      this.setData({ consumptionRecords: result.data.list })
    }
  },

  onTabChange(e: any) {
    const tab = e.currentTarget.dataset.tab
    this.setData({ activeTab: tab })
  },

  // 去充值
  goToRecharge() {
    wx.navigateTo({
      url: '/pages/recharge/recharge'
    })
  }
})
