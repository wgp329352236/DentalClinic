// pages/recharge/recharge.ts
import { getRechargeTiers, createRecharge } from '../../utils/api'
import { checkLogin } from '../../utils/auth'

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
    if (!checkLogin()) return
    this.loadPlans()
  },

  // 加载充值档位（GET /recharge/tiers，映射后端字段）
  async loadPlans() {
    try {
      const result = await getRechargeTiers()
      if (result.code === 0 && Array.isArray(result.data)) {
        const plans: RechargePlan[] = result.data.map((t: any) => ({
          amount: t.amount,
          giftedAmount: t.bonus,
          giftRatio: t.amount ? t.bonus / t.amount : 0
        }))
        this.setData({ plans })
      }
    } catch (error) {
      // 未登录/过期等认证错误由 request.ts 统一处理（跳回首页），这里兜底避免未捕获异常
      console.warn('[Recharge] 加载充值档位失败:', error)
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

    if (selectedPlan) {
      amount = selectedPlan.amount
    } else if (customAmount) {
      amount = Number(customAmount)
      if (amount < 100) {
        wx.showToast({ title: '最低充值100元', icon: 'none' })
        return
      }
    } else {
      wx.showToast({ title: '请选择充值档位或输入金额', icon: 'none' })
      return
    }

    wx.showModal({
      title: '确认充值',
      content: `确认充值${amount}元？赠送金额由系统按档位规则计算。`,
      success: async (res) => {
        if (res.confirm) {
          this.doRecharge(amount)
        }
      }
    })
  },

  async doRecharge(amount: number) {
    this.setData({ loading: true })

    try {
      wx.showLoading({ title: '支付中...' })

      // 创建充值订单（POST /recharge/create，赠送金额由后端按规则计算）
      const app = getApp<IAppOption>()
      const clinicId = app.globalData.userInfo?.clinicId || 0
      const result = await createRecharge({ amount, clinicId })

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
