// pages/recharge/recharge.ts
import { get, post } from '../../utils/request'

interface RechargePlan {
  amount: number
  giftedAmount: number
  giftRatio: number
}

Page({
  data: {
    plans: [] as RechargePlan[],
    selectedPlan: null as RechargePlan | null,
    customAmount: '',
    loading: false
  },

  onLoad() {
    this.loadPlans()
  },

  async loadPlans() {
    const result = await get<RechargePlan[]>('/recharge/plans')
    if (result.code === 0 && result.data) {
      this.setData({ plans: result.data })
    }
  },

  // 选择充值档位
  onSelectPlan(e: any) {
    const index = Number(e.currentTarget.dataset.index)
    const plan = this.data.plans[index]
    this.setData({ selectedPlan: plan, customAmount: '' })
  },

  // 自定义金额输入
  onCustomAmountInput(e: any) {
    this.setData({ customAmount: e.detail.value, selectedPlan: null })
  },

  // 确认充值
  async handleRecharge() {
    const { selectedPlan, customAmount } = this.data

    let amount = 0
    let giftedAmount = 0

    if (selectedPlan) {
      amount = selectedPlan.amount
      giftedAmount = selectedPlan.giftedAmount
    } else if (customAmount) {
      amount = Number(customAmount)
      if (amount < 100) {
        wx.showToast({ title: '最低充值100元', icon: 'none' })
        return
      }
      // 自定义金额按10%赠送
      giftedAmount = Math.floor(amount * 0.1)
    } else {
      wx.showToast({ title: '请选择充值档位或输入金额', icon: 'none' })
      return
    }

    wx.showModal({
      title: '确认充值',
      content: `充值${amount}元，赠送${giftedAmount}元，实际到账${amount + giftedAmount}元`,
      success: async (res) => {
        if (res.confirm) {
          this.doRecharge(amount, giftedAmount)
        }
      }
    })
  },

  async doRecharge(amount: number, giftedAmount: number) {
    this.setData({ loading: true })

    try {
      // 模拟微信支付流程
      wx.showLoading({ title: '支付中...' })

      const result = await post('/recharge', { amount, giftedAmount })

      wx.hideLoading()

      if (result.code === 0) {
        wx.showToast({ title: '充值成功', icon: 'success' })
        setTimeout(() => {
          wx.navigateBack()
        }, 1500)
      } else {
        wx.showToast({ title: result.message || '充值失败', icon: 'none' })
      }
    } catch (error) {
      wx.hideLoading()
      console.error('充值失败:', error)
      wx.showToast({ title: '充值失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  }
})
