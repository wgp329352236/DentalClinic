// pages/wallet/wallet.ts
import { getWalletDetails } from '../../utils/api'
import { checkLogin } from '../../utils/auth'
import type { Wallet, RechargeRecord } from '../../types'

Page({
  data: {
    wallet: null as Wallet | null,
    rechargeRecords: [] as RechargeRecord[],
    consumptionRecords: [] as any[], // 后端暂无消费记录接口，保留空列表
    activeTab: 'recharge', // recharge | consumption
    loading: false
  },

  onLoad() {
    if (!this.checkLogin()) return
    this.loadWallet()
  },

  onShow() {
    // 从登录页返回时刷新数据；未登录则回首页
    if (!this.checkLogin()) return
    if (!this.data.wallet) {
      this.loadWallet()
    }
  },

  // 校验登录态：未登录时回首页引导登录（避免请求未认证接口触发 MiniProgramError）
  checkLogin(): boolean {
    return checkLogin()
  },

  // 加载钱包详情（GET /wallet/details，充值记录来自 rechargeHistory）
  async loadWallet() {
    try {
      const result = await getWalletDetails()
      if (result.code === 0 && result.data) {
        const d = result.data
        this.setData({
          wallet: {
            balance: d.balance,
            giftedBalance: d.bonusBalance,
            monthlySpent: 0, // 后端暂无本月消费，暂置 0
            totalSpent: d.totalBalance
          },
          rechargeRecords: (d.rechargeHistory || []).map((r: any) => ({
            id: r.id,
            amount: r.amount,
            giftedAmount: r.bonusAmount,
            payMethod: 'wechat',
            status: r.status,
            createdAt: r.createdAt
          }))
        })
      }
    } catch (error: any) {
      // 未登录/过期等认证错误由 request.ts 统一处理（跳回首页），这里兜底避免未捕获异常
      console.warn('[Wallet] 加载钱包失败:', error?.errMsg || error?.message || error)
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
